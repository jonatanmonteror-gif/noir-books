package com.noirbooks.app.data

import java.util.Calendar

/**
 * Recomendaciones sin servidor:
 *  - [pick]: «¿Qué leo ahora?» entre tus pendientes y tu lista de deseos, según las respuestas del cuestionario.
 *  - [news]: novedades de tus autores favoritos, el siguiente de tus sagas y lo recién salido de tus géneros
 *    (Google Books ordenado por fecha, así que sí conoce lo que acaba de salir).
 */
object Recommender {

    // ---------- Respuestas del cuestionario ----------

    /** Qué te apetece (cada opción agrupa géneros de la app). */
    enum class Mood(val label: String, val hint: String, val fiction: Boolean, val genres: Set<String>) {
        Intriga("Intriga y tensión", "Que no puedas soltarlo", true, setOf("Misterio y thriller")),
        Mundos("Otros mundos", "Fantasía o ciencia ficción", true, setOf("Fantasía", "Ciencia ficción")),
        Amor("Una historia de amor", "Romántica", true, setOf("Romántica")),
        Miedo("Pasar miedo", "Terror", true, setOf("Terror")),
        Profundo("Algo que me remueva", "Novela literaria o histórica", true, setOf("Novela", "Novela histórica", "Poesía", "Teatro")),
        Ligero("Algo ligero", "Juvenil, cómic o romántica", true, setOf("Juvenil", "Cómic", "Romántica", "Infantil")),
        Mejorar("Mejorar algo de mí", "Hábitos, mente, autoayuda", false, setOf("Autoayuda", "Psicología", "Filosofía")),
        Entender("Entender el mundo", "Historia, ciencia, ensayo", false, setOf("Historia", "Ciencia", "Ensayo", "Política")),
        Vidas("Vidas reales", "Biografías y memorias", false, setOf("Biografía y memorias")),
        Trabajo("Trabajo y dinero", "Negocios y economía", false, setOf("Negocios y economía")),
        Aficiones("Una afición", "Viajes, cocina, arte, deporte", false, setOf("Viajes", "Cocina", "Arte", "Deporte", "Religión")),
    }

    enum class Known(val label: String, val hint: String) {
        Saga("Seguir una saga", "El siguiente de una que ya empezaste"),
        Author("Un autor que me gusta", "De quien ya has puntuado bien"),
        New("Probar algo distinto", "Autores que aún no has leído"),
        Any("Me da igual", ""),
    }

    enum class Length(val label: String, val hint: String, val min: Int, val max: Int) {
        Short("Corto", "Se lee en un finde (menos de 5 h)", 0, 5 * 60),
        Medium("Normal", "Un par de semanas (5 a 10 h)", 4 * 60, 11 * 60),
        Long("Largo", "Sin prisa, para perderme (más de 10 h)", 9 * 60, Int.MAX_VALUE),
        Any("Me da igual", "", 0, Int.MAX_VALUE),
    }

    enum class Way(val label: String) { App("En la app"), Paper("En papel"), Audio("Escuchándolo"), Ebook("En otro lector"), Any("Me da igual") }

    data class Answers(
        val fiction: Boolean? = null,
        val mood: Mood? = null,
        val known: Known = Known.Any,
        val length: Length = Length.Any,
        val way: Way = Way.Any,
        /** Atajo por género (desde Inicio): solo libros de este género. */
        val genre: String? = null,
    ) {
        /** Géneros que se piden: los del ánimo elegido o el del atajo. */
        val genres: Set<String>? get() = mood?.genres ?: genre?.let { setOf(it) }
        val wantLabel: String? get() = mood?.label ?: genre
    }

    data class Pick(val book: Book, val score: Float, val reasons: List<String>)

    // ---------- Datos de tus gustos ----------

    /** Nota de un libro: la general o, si puntúas por aspectos, la media de los aspectos. */
    fun stars(b: Book): Float = if (b.rating > 0f) b.rating else
        listOf(b.ratingPlot, b.ratingChars, b.ratingProse).filter { it > 0f }.let { if (it.isEmpty()) 0f else it.average().toFloat() }

    private fun authorKey(a: String) = a.substringBefore(',').substringBefore('&').trim().lowercase()

    /** Media de tus notas por autor (solo autores que has leído y puntuado). */
    fun authorScores(books: List<Book>): Map<String, Float> = books.filter { it.status == BookStatus.Read && stars(it) > 0f && it.author.isNotBlank() }
        .groupBy { authorKey(it.author) }.mapValues { (_, l) -> l.map { stars(it) }.average().toFloat() }

    private fun genreScores(books: List<Book>): Map<String, Float> = books.filter { it.status == BookStatus.Read && stars(it) > 0f && it.genre.isNotBlank() }
        .groupBy { it.genre }.mapValues { (_, l) -> l.map { stars(it) }.average().toFloat() }

    /** Minutos aproximados que lleva leer un libro. 0 si no se sabe. */
    fun minutesOf(b: Book): Int = when {
        b.unit == ProgressUnit.Time && b.pages > 0 -> b.pages
        b.isEpub && b.chapterLengths.isNotEmpty() -> b.totalChars / CHARS_PER_MINUTE
        b.pages > 0 && b.unit != ProgressUnit.Location -> (b.pages * 1.4f).toInt()
        else -> 0
    }

    private fun hours(min: Int) = if (min < 90) "$min min" else "unas ${(min + 30) / 60} h"

    /** Libros entre los que elegir: pendientes y lista de deseos (no los que ya lees, terminaste o dejaste). */
    fun candidates(books: List<Book>) = books.filter { !it.inTrash && it.status == BookStatus.ToRead }

    /** Formas de leer que tienes entre los candidatos (para no preguntar por lo que no hay). */
    fun waysAvailable(books: List<Book>): List<Way> {
        val c = candidates(books)
        return buildList {
            if (c.any { it.isEpub || it.format == BookFormat.Pdf }) add(Way.App)
            if (c.any { it.format == BookFormat.Paper }) add(Way.Paper)
            if (c.any { it.format == BookFormat.Audio }) add(Way.Audio)
            if (c.any { it.format == BookFormat.Kindle || it.format == BookFormat.Other }) add(Way.Ebook)
        }
    }

    private fun wayOf(b: Book) = when {
        b.isEpub || b.format == BookFormat.Pdf -> Way.App
        b.format == BookFormat.Paper -> Way.Paper
        b.format == BookFormat.Audio -> Way.Audio
        else -> Way.Ebook
    }

    // ---------- ¿Qué leo ahora? ----------

    fun pick(books: List<Book>, a: Answers): List<Pick> {
        val authors = authorScores(books)
        val genres = genreScores(books)
        val readAuthors = books.filter { it.status == BookStatus.Read }.map { authorKey(it.author) }.toSet()
        // Por saga: el número más alto que has terminado
        val sagaDone = books.filter { it.series.isNotBlank() && it.status == BookStatus.Read }
            .groupBy { it.series.lowercase() }.mapValues { (_, l) -> l.maxOf { it.seriesNo.toFloatOrNull() ?: 0f } }

        var pool = candidates(books)
        if (a.way != Way.Any) pool = pool.filter { wayOf(it) == a.way }.ifEmpty { pool }

        return pool.map { b ->
            var score = 0f
            val why = mutableListOf<String>()
            val g = b.genre
            val want = a.genres
            if (want != null) {
                if (g in want) { score += 6f; why += "Es ${g.lowercase()}, justo lo que te apetece" }
                else if (g.isNotBlank()) score -= 4f
            } else if (a.fiction != null && g.isNotBlank()) {
                val isFiction = Mood.entries.filter { it.fiction }.any { g in it.genres }
                score += if (isFiction == a.fiction) 2f else -3f
            }
            // Tus gustos por género
            genres[g]?.let { avg -> score += (avg - 3f) * 1.2f; if (avg >= 4f) why += "Puntúas alto lo que lees de ${g.lowercase()} (${"%.1f".format(avg).replace('.', ',')}★)" }
            // Sagas
            val done = sagaDone[b.series.lowercase()]
            val no = b.seriesNo.toFloatOrNull()
            if (b.series.isNotBlank() && done != null && no != null && no > done) {
                val next = no - done <= 1f
                score += if (next) 5f else 2f
                if (a.known == Known.Saga) score += 4f
                why += if (next) "Es el siguiente de «${b.series}», que ya empezaste" else "Sigue tu saga «${b.series}»"
            } else if (a.known == Known.Saga) score -= 3f
            // Autores
            val au = authors[authorKey(b.author)]
            if (au != null) {
                score += (au - 3f) * 1.5f
                if (au >= 4f) why += "De ${b.author.substringBefore(',')}, que te gusta (${"%.1f".format(au).replace('.', ',')}★ de media)"
                if (a.known == Known.Author) score += if (au >= 3.5f) 4f else 0f
            } else if (a.known == Known.Author) score -= 2f
            if (a.known == Known.New) {
                if (authorKey(b.author) !in readAuthors) { score += 3f; why += "Un autor nuevo para ti" } else score -= 2f
            }
            // Duración
            val min = minutesOf(b)
            if (a.length != Length.Any && min > 0) {
                if (min in a.length.min..a.length.max) { score += 3f; why += "Se lee en ${hours(min)}" } else score -= 3f
            } else if (min > 0) why += "Se lee en ${hours(min)}"
            // Pequeños empujones: lo que quieres de verdad y lo que añadiste hace poco
            if (b.wish) score += 0.5f
            score += (b.addedAt / 86_400_000L % 1000) / 100_000f
            Pick(b, score, why.distinct().take(3))
        }.sortedByDescending { it.score }
    }

    // ---------- Novedades para ti ----------

    data class NewBook(val found: FoundBook, val why: String)
    data class News(val authors: List<NewBook>, val sagas: List<NewBook>, val genre: List<NewBook>, val genreName: String)

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]"), "")

    private fun titleKey(t: String) = norm(t.substringBefore(':').substringBefore('(')).take(24)

    /**
     * Busca novedades. [onlyGenres]: si el cuestionario eligió algo, solo de esos géneros.
     * Se quedan solo libros de los 2 últimos años que no tengas ya.
     */
    suspend fun news(books: List<Book>, onlyGenres: Set<String>? = null): News {
        val have = books.map { titleKey(it.title) }.toHashSet()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        fun fresh(f: FoundBook) = (f.year.toIntOrNull() ?: 0) >= year - 1 && titleKey(f.title) !in have && f.title.length > 1
        val seen = HashSet<String>()
        fun keep(f: FoundBook) = seen.add(titleKey(f.title))

        // Autores que te gustan (de esos géneros, si se eligieron)
        val favAuthors = books.filter { it.status == BookStatus.Read && stars(it) >= 4f && it.author.isNotBlank() &&
            (onlyGenres == null || it.genre.isBlank() || it.genre in onlyGenres) }
            .groupBy { it.author.substringBefore(',').substringBefore('&').trim() }
            .entries.sortedByDescending { (_, l) -> l.map { stars(it) }.average() * l.size }.take(6).map { it.key }
        val byAuthor = favAuthors.flatMap { a ->
            BookSearch.newest("inauthor:\"$a\"").filter { fresh(it) && it.author.contains(a.split(' ').last(), true) }.take(2)
                .map { NewBook(it, "Nuevo de $a, que te gusta") }
        }.filter { keep(it.found) }.take(8)

        // Sagas que estás siguiendo
        val sagas = books.filter { it.series.isNotBlank() && (it.status == BookStatus.Read || it.status == BookStatus.Reading) }
            .groupBy { it.series }.keys.take(6)
        val bySaga = sagas.flatMap { s ->
            val author = books.first { it.series == s }.author.substringBefore(',').trim()
            BookSearch.newest("intitle:\"$s\"" + if (author.isNotBlank()) " inauthor:\"$author\"" else "", lang = "")
                .filter { fresh(it) }.take(2).map { NewBook(it, "Sigue tu saga «$s»") }
        }.filter { keep(it.found) }.take(6)

        // Lo recién salido de tu género favorito (o del elegido)
        val genre = onlyGenres?.firstOrNull() ?: genreScores(books).entries.sortedByDescending { it.value }.firstOrNull()?.key
            ?: books.filter { it.genre.isNotBlank() }.groupingBy { it.genre }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        val subject = Genres.subjectFor(genre)
        val byGenre = if (subject.isBlank()) emptyList() else
            BookSearch.newest("subject:$subject").filter { fresh(it) && it.coverUrl != null }
                .map { NewBook(it, "Recién salido en ${genre.lowercase()}") }.filter { keep(it.found) }.take(8)

        return News(byAuthor, bySaga, byGenre, genre)
    }
}
