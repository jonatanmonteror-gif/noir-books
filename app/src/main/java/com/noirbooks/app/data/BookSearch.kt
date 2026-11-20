package com.noirbooks.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer

data class FoundBook(
    val title: String,
    val author: String,
    val coverUrl: String?,
    val pages: Int,
    val isbn: String,
    val publisher: String,
    val year: String,
    val language: String,
    val source: String,
    val description: String = "",
)

/**
 * Busca libros en catálogos gratuitos: Google Books (mejor con libros en español)
 * y Open Library (respaldo, sin clave). Se consultan a la vez y se juntan los resultados.
 */
object BookSearch {

    suspend fun search(query: String): List<FoundBook> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val digits = q.filter { it.isDigit() || it == 'X' || it == 'x' }
        val isIsbn = (digits.length == 10 || digits.length == 13) && q.count { it.isLetter() && it != 'X' && it != 'x' } == 0
        coroutineScope {
            val g = async { runCatching { google(if (isIsbn) "isbn:$digits" else q) }.getOrDefault(emptyList()) }
            val o = async { runCatching { openLibrary(if (isIsbn) "isbn:$digits" else q) }.getOrDefault(emptyList()) }
            val all = g.await() + o.await()
            // Sin duplicados (mismo título y autor) y con los libros en español primero.
            all.distinctBy { key(it.title) + "|" + key(it.author.substringBefore(',')) }
                .sortedBy { if (it.language.startsWith("es") || it.language == "spa") 0 else 1 }
                .take(40)
        }
    }

    /** Sinopsis de un libro en Google Books (por ISBN o por título y autor). */
    suspend fun description(title: String, author: String, isbn: String): String = withContext(Dispatchers.IO) {
        val q = if (isbn.isNotBlank()) "isbn:$isbn" else "intitle:$title" + if (author.isNotBlank()) " inauthor:${author.substringBefore(',')}" else ""
        val list = runCatching { google(q) }.getOrDefault(emptyList())
        val t = key(title).take(14)
        (list.firstOrNull { it.description.isNotBlank() && key(it.title).startsWith(t) } ?: list.firstOrNull { it.description.isNotBlank() && isbn.isNotBlank() })
            ?.description.orEmpty()
    }

    /**
     * Número de páginas de la edición en papel. Con ISBN se pide esa edición exacta; si no, se juntan las ediciones
     * con el mismo título y autor (en el idioma del libro si se puede) y se toma la mediana. 0 si no aparece.
     * Lanza excepción si no hay conexión.
     */
    suspend fun paperPages(title: String, author: String, isbn: String, language: String): Int = withContext(Dispatchers.IO) {
        if (isbn.isNotBlank()) {
            val g = runCatching { google("isbn:$isbn") }
            val byIsbn = g.getOrDefault(emptyList()).firstOrNull { it.pages > 40 }?.pages
                ?: runCatching { openLibraryIsbn(isbn) }.getOrDefault(0)
            if (byIsbn > 40) return@withContext byIsbn
            g.exceptionOrNull()?.let { if (it is java.io.IOException) throw it }
        }
        val t = key(title.substringBefore(':').substringBefore('(')).take(16)
        if (t.length < 3) return@withContext 0
        val surname = key(author.substringBefore(',').substringBefore('&').trim().split(' ').lastOrNull().orEmpty())
        val q = "intitle:${title.substringBefore(':')}" + if (author.isNotBlank()) " inauthor:${author.substringBefore(',')}" else ""
        val g = runCatching { google(q) }
        val o = runCatching { openLibrary("${title.substringBefore(':')} ${author.substringBefore(',')}".trim()) }
        if (g.isFailure && o.isFailure) throw (g.exceptionOrNull() ?: java.io.IOException("Sin conexión"))
        val all = (g.getOrDefault(emptyList()) + o.getOrDefault(emptyList())).filter { f ->
            f.pages in 41..3000 && key(f.title).startsWith(t) &&
                (surname.length < 3 || key(f.author).contains(surname))
        }
        if (all.isEmpty()) return@withContext 0
        val lang = language.lowercase().take(2)
        val sameLang = all.filter { lang.isNotBlank() && (it.language.startsWith(lang) || (lang == "es" && it.language == "spa") || (lang == "en" && it.language == "eng")) }
        val pool = (sameLang.ifEmpty { all }).map { it.pages }.sorted()
        pool[pool.size / 2]
    }

    private suspend fun openLibraryIsbn(isbn: String): Int {
        val json = Http.get("https://openlibrary.org/isbn/$isbn.json")
        return JSONObject(json).optInt("number_of_pages")
    }

    private fun key(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]"), "")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun google(q: String): List<FoundBook> {
        val json = Http.get("https://www.googleapis.com/books/v1/volumes?q=${enc(q)}&maxResults=20&printType=books")
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val v = items.optJSONObject(i)?.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = listOf(v.optString("title"), v.optString("subtitle")).filter { it.isNotBlank() }.joinToString(": ")
            if (title.isBlank()) return@mapNotNull null
            val authors = v.optJSONArray("authors")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val ids = v.optJSONArray("industryIdentifiers")
            var isbn = ""
            if (ids != null) for (k in 0 until ids.length()) {
                val o = ids.optJSONObject(k) ?: continue
                if (o.optString("type") == "ISBN_13") isbn = o.optString("identifier")
                else if (isbn.isEmpty() && o.optString("type") == "ISBN_10") isbn = o.optString("identifier")
            }
            val img = v.optJSONObject("imageLinks")?.let { it.optString("thumbnail").ifEmpty { it.optString("smallThumbnail") } }
                ?.replace("http://", "https://")?.replace("&edge=curl", "")?.takeIf { it.isNotBlank() }
            FoundBook(
                title = title, author = authors.joinToString(", "), coverUrl = img,
                pages = v.optInt("pageCount"), isbn = isbn, publisher = v.optString("publisher"),
                year = v.optString("publishedDate").take(4), language = v.optString("language"), source = "Google Books",
                description = v.optString("description").let { d -> if ('<' in d) org.jsoup.Jsoup.parse(d).text() else d },
            )
        }
    }

    private suspend fun openLibrary(q: String): List<FoundBook> {
        val fields = "title,author_name,cover_i,number_of_pages_median,isbn,publisher,first_publish_year,language"
        val json = Http.get("https://openlibrary.org/search.json?q=${enc(q)}&limit=20&fields=$fields")
        val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val d = docs.optJSONObject(i) ?: return@mapNotNull null
            val title = d.optString("title")
            if (title.isBlank()) return@mapNotNull null
            val authors = d.optJSONArray("author_name")?.let { a -> (0 until minOf(3, a.length())).map { a.optString(it) } }.orEmpty()
            val cover = d.optInt("cover_i", 0).takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
            val isbns = d.optJSONArray("isbn")
            val isbn = isbns?.let { a -> (0 until a.length()).map { a.optString(it) }.firstOrNull { it.length == 13 } ?: a.optString(0) }.orEmpty()
            val langs = d.optJSONArray("language")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            FoundBook(
                title = title, author = authors.joinToString(", "), coverUrl = cover,
                pages = d.optInt("number_of_pages_median"), isbn = isbn,
                publisher = d.optJSONArray("publisher")?.optString(0).orEmpty(),
                year = d.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString().orEmpty(),
                language = if ("spa" in langs) "spa" else langs.firstOrNull().orEmpty(), source = "Open Library",
            )
        }
    }
}
