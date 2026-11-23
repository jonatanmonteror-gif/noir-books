package com.noirbooks.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Recomendaciones con IA (OpenRouter).
 *
 * La IA propone libros a partir de lo que has leído y puntuado; después la app busca cada uno en el
 * catálogo (Google Books / Open Library) para traer portada e ISBN y comprobar que existe. Con
 * «buscar en internet» la IA consulta la web y conoce también lo que acaba de salir.
 *
 * Cupo: 3 al mes sin Pro y 20 con Pro. FASE DE PRUEBAS: la clave de OpenRouter se pega en
 * Ajustes (vista desarrollador) y no se cuenta el cupo en esa vista. ANTES DE PUBLICAR las
 * consultas tienen que ir por un servidor propio (la clave no puede ir en el móvil de cada usuario).
 */
object AiRecommender {
    const val FREE_PER_MONTH = 1
    const val PRO_PER_MONTH = 10

    /** Modelos para probar costes (id de OpenRouter → nombre). */
    val MODELS = listOf(
        "google/gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite (barato)",
        "google/gemini-3.1-flash-lite" to "Gemini 3.1 Flash Lite",
        "google/gemini-3.5-flash" to "Gemini 3.5 Flash",
        "google/gemini-3.8-flash" to "Gemini 3.8 Flash (mejor, más caro)",
    )

    /** [cover]: la portada ya descargada, para pintarla al instante junto al texto. */
    data class Suggestion(val title: String, val author: String, val year: String, val why: String, val found: FoundBook?,
                          val cover: android.graphics.Bitmap? = null, val coverUrl: String? = null)
    data class Result(
        val items: List<Suggestion>, val model: String, val promptTokens: Int, val completionTokens: Int,
        val costUsd: Double, val web: Boolean,
    )

    private fun monthKey(): String { val c = Calendar.getInstance(); return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}" }

    fun usedThisMonth(): Int = if (Prefs.aiMonth == monthKey()) Prefs.aiUsed else 0
    fun limit(): Int = if (Edition.isPro) PRO_PER_MONTH else FREE_PER_MONTH
    /** En la vista de desarrollador no hay límite (es para probar). */
    fun left(): Int = if (Edition.isDev) Int.MAX_VALUE else (limit() - usedThisMonth()).coerceAtLeast(0)

    private fun countOne() {
        val k = monthKey()
        if (Prefs.aiMonth != k) { Prefs.aiMonth = k; Prefs.aiUsed = 0 }
        Prefs.aiUsed = Prefs.aiUsed + 1
    }

    /** Resumen de tus gustos para la IA: lo leído con nota, lo que lees y lo que ya tienes (para no repetirlo). */
    private fun profile(books: List<Book>): String {
        val live = books.filter { !it.inTrash }
        val read = live.filter { it.status == BookStatus.Read }
            .sortedWith(compareByDescending<Book> { Recommender.stars(it) }.thenByDescending { it.readings.maxOfOrNull { r -> r.end } ?: 0L })
            .take(30)
        fun line(b: Book): String {
            val st = Recommender.stars(b)
            return "- ${b.title} — ${b.author.ifBlank { "?" }}" +
                (if (b.genre.isNotBlank()) " [${b.genre}]" else "") +
                (if (st > 0f) " ${"%.1f".format(st)}★" else "") +
                (if (b.review.isNotBlank()) " (opinión: ${b.review.take(80).replace('\n', ' ')})" else "")
        }
        val reading = live.filter { it.status == BookStatus.Reading }.take(10)
        val dropped = live.filter { it.status == BookStatus.Abandoned }.take(10)
        val owned = live.filter { it.status == BookStatus.ToRead }.take(40)
        return buildString {
            appendLine("LEÍDOS (con mi nota de 1 a 5):"); read.forEach { appendLine(line(it)) }
            if (reading.isNotEmpty()) { appendLine("LEYENDO AHORA:"); reading.forEach { appendLine("- ${it.title} — ${it.author}") } }
            if (dropped.isNotEmpty()) { appendLine("ABANDONADOS (no me engancharon):"); dropped.forEach { appendLine("- ${it.title} — ${it.author}") } }
            if (owned.isNotEmpty()) { appendLine("YA LOS TENGO PENDIENTES (no los recomiendes):"); owned.forEach { appendLine("- ${it.title} — ${it.author}") } }
        }
    }

    suspend fun recommend(ctx: android.content.Context, books: List<Book>, wish: String, answers: Recommender.Answers?): Result = withContext(Dispatchers.IO) {
        val key = Prefs.aiKey.trim()
        if (key.isBlank()) throw IllegalStateException("Las recomendaciones no están disponibles ahora mismo")
        if (left() <= 0) throw IllegalStateException("Ya has usado tus recomendaciones de este mes (${limit()})")
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val ask = buildString {
            answers?.let { a ->
                a.mood?.let { append("Me apetece: ${it.label} (${it.hint}). ") }
                a.genre?.let { append("Quiero algo de este género: $it. Básate sobre todo en lo que he leído y puntuado de ese género. ") }
                if (a.mood == null && a.fiction != null) append(if (a.fiction) "Quiero ficción. " else "Quiero no ficción. ")
                if (a.known != Recommender.Known.Any) append("Preferencia: ${a.known.label}. ")
                if (a.length != Recommender.Length.Any) append("Duración: ${a.length.hint}. ")
            }
            if (wish.isNotBlank()) append("Lo que pido: $wish")
        }.ifBlank { "Recomiéndame lo que más me pueda gustar ahora." }
        val system = """
            Eres un librero experto que recomienda libros a un lector de España. Conoces muy bien su historial.
            Reglas:
            - Recomienda 5 libros concretos que NO estén en su lista (ni leídos, ni pendientes, ni abandonados).
            - Que estén publicados en español (da el título de la edición española si existe).
            - Nada genérico: cada recomendación debe apoyarse en libros concretos que ha puntuado alto o en lo que pide.
            - Incluye al menos uno publicado en ${year - 1} o $year si encaja (si no estás seguro de que exista, no lo inventes).
            - Evita lo que se parezca a lo que abandonó o puntuó bajo.
            Responde SOLO con JSON: {"items":[{"title":"","author":"","year":"","isbn":"ISBN-13 de la edición española si lo sabes, si no vacío","why":"una frase concreta, máx. 25 palabras, en español, citando sus libros"}]}
        """.trimIndent()
        val user = profile(books) + "\nPETICIÓN: " + ask
        val model = Prefs.aiModel
        val web = Prefs.aiWeb
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0.7)
            .put("max_tokens", 700)
            // Sin «pensar» de más: para esto no hace falta y se cobra como salida
            .put("reasoning", JSONObject().put("effort", "minimal").put("exclude", true))
            .put("usage", JSONObject().put("include", true))
            // Búsqueda web con Parallel «fast» (≈0,001 $). Sin indicar buscador, Gemini usa el suyo, mucho más caro.
            .apply { if (web) put("plugins", JSONArray().put(JSONObject().put("id", "web").put("engine", "parallel").put("mode", "fast").put("max_results", 4))) }
        val raw = Http.postJson("https://openrouter.ai/api/v1/chat/completions", body.toString(), mapOf(
            "Authorization" to "Bearer $key",
            "HTTP-Referer" to "https://jonatanmontero.es/noirbooks",
            "X-Title" to "Noir Books",
        ))
        val resp = JSONObject(raw)
        val content = resp.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        val json = content.substringAfter("{", "").let { if (it.isEmpty()) "{}" else "{" + it.substringBeforeLast("}") + "}" }
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull() ?: JSONArray()
        val usage = resp.optJSONObject("usage")
        val owned = books.map { norm(it.title) }.toSet()
        val raw5 = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .filter { o -> o.optString("title").isNotBlank() && norm(o.optString("title")) !in owned }
        // Catálogo y portadas EN PARALELO, y las portadas se descargan antes de enseñar nada:
        // así las recomendaciones salen ya con su portada, todas a la vez.
        val loader = coil.Coil.imageLoader(ctx)
        val list = kotlinx.coroutines.coroutineScope {
            raw5.map { o ->
                async {
                    val t = o.optString("title").trim(); val a = o.optString("author").trim()
                    val isbn = o.optString("isbn").filter { it.isDigit() || it == 'X' }
                    val found = kotlinx.coroutines.withTimeoutOrNull(6000) { runCatching { BookSearch.quick(t, a, isbn) }.getOrNull() }
                    // Portada: Amazon por ISBN (como al buscar el libro en imágenes), Google Books u Open Library.
                    // Se descarga aquí, así sale a la vez que el texto.
                    suspend fun fetch(u: String): android.graphics.Bitmap? = kotlinx.coroutines.withTimeoutOrNull(4000) {
                        runCatching {
                            val res = loader.execute(coil.request.ImageRequest.Builder(ctx).data(u).allowHardware(false).size(300, 450).build())
                            (res as? coil.request.SuccessResult)?.drawable?.let { d -> (d as? android.graphics.drawable.BitmapDrawable)?.bitmap }
                        }.getOrNull()
                    }?.takeIf { it.width > 40 && it.height > 60 } // Amazon devuelve un punto de 1×1 si no la tiene
                    val first = listOfNotNull(BookSearch.amazonCover(isbn), BookSearch.amazonCover(found?.isbn.orEmpty()), found?.coverUrl).distinct()
                    var bmp: android.graphics.Bitmap? = null
                    var used: String? = null
                    for (u in first) { bmp = fetch(u); if (bmp != null) { used = u; break } }
                    if (bmp == null) {
                        val more = kotlinx.coroutines.withTimeoutOrNull(6000) {
                            runCatching { BookSearch.coverCandidates(t, a, isbn.ifBlank { found?.isbn.orEmpty() }) }.getOrDefault(emptyList())
                        }.orEmpty().filter { it !in first }.take(6)
                        for (u in more) { bmp = fetch(u); if (bmp != null) { used = u; break } }
                    }
                    Suggestion(t, a, o.optString("year"), o.optString("why"), found, bmp, used)
                }
            }.map { it.await() }
        }
        countOne()
        val cost = usage?.optDouble("cost", 0.0) ?: 0.0
        Prefs.aiSpentUsd = Prefs.aiSpentUsd + cost
        Prefs.aiCalls = Prefs.aiCalls + 1
        Result(list, model, usage?.optInt("prompt_tokens") ?: 0, usage?.optInt("completion_tokens") ?: 0, cost, web)
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]"), "").take(30)
}
