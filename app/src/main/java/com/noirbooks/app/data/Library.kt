package com.noirbooks.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

data class Bookmark(
    val chapter: Int,
    val fraction: Float,
    val label: String,
    val snippet: String,
    val createdAt: Long,
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val addedAt: Long,
    val lastOpened: Long = 0L,
    /** Capítulo actual (posición en el orden de lectura) y fracción leída dentro de él (0..1). */
    val chapter: Int = 0,
    val fraction: Float = 0f,
    /** Letras de cada capítulo: sirve para el % del libro y el tiempo que queda. */
    val chapterLengths: List<Int> = emptyList(),
    val finished: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val hasCover: Boolean = false,
) {
    val totalChars: Int get() = chapterLengths.sum().coerceAtLeast(1)

    /** Progreso del libro entero (0..1). */
    fun progressAt(ch: Int, f: Float): Float {
        if (chapterLengths.isEmpty()) return 0f
        val before = chapterLengths.take(ch.coerceIn(0, chapterLengths.size)).sum()
        val cur = chapterLengths.getOrElse(ch) { 0 }
        return ((before + cur * f) / totalChars).coerceIn(0f, 1f)
    }

    val progress: Float get() = if (finished) 1f else progressAt(chapter, fraction)
    val started: Boolean get() = lastOpened > 0L
}

/** Velocidad de lectura media (letras por minuto, unas 230 palabras/min en español). */
const val CHARS_PER_MINUTE = 1300

fun minutesLabel(chars: Int): String {
    val min = (chars / CHARS_PER_MINUTE.toFloat()).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
    return if (min < 60) "$min min" else {
        val h = min / 60
        val m = min % 60
        if (m == 0) "$h h" else "$h h $m min"
    }
}

/** Biblioteca: los libros se copian dentro de la app (así no se pierden si se mueve el archivo original). */
object Library {
    private lateinit var root: File
    private val lock = Mutex()
    val books = mutableStateOf<List<Book>>(emptyList())

    fun init(ctx: Context) {
        if (::root.isInitialized) return
        root = File(ctx.filesDir, "books").apply { mkdirs() }
        books.value = runCatching { load() }.getOrElse {
            ErrorLog.note("Leer biblioteca", it)
            emptyList()
        }
    }

    fun dirOf(id: String) = File(root, id)
    fun epubOf(id: String) = File(dirOf(id), "book.epub")
    fun coverOf(id: String) = File(dirOf(id), "cover.jpg")
    fun get(id: String): Book? = books.value.firstOrNull { it.id == id }

    sealed class ImportResult {
        data class Added(val book: Book) : ImportResult()
        data class Existing(val book: Book) : ImportResult()
        data class Failed(val name: String, val reason: String) : ImportResult()
    }

    /** Importa un EPUB desde el móvil (selector de archivos, "abrir con" o "compartir"). */
    suspend fun import(ctx: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(ctx, uri)
        val tmp = File(ctx.cacheDir, "import-${System.nanoTime()}.epub")
        try {
            ctx.contentResolver.openInputStream(uri).use { input ->
                input ?: throw IOException("No se pudo abrir el archivo.")
                tmp.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
            val id = sha1(tmp).take(16)
            get(id)?.let { tmp.delete(); return@withContext ImportResult.Existing(it) }

            val epub = Epub.open(tmp, name?.substringBeforeLast('.'))
            val lengths = ZipFile(tmp).use { z -> epub.spine.map { Epub.plainTextLength(z, it) } }

            val dir = dirOf(id).apply { mkdirs() }
            val hasCover = epub.coverPath?.let { saveCover(tmp, it, coverOf(id)) } ?: false
            if (!tmp.renameTo(epubOf(id))) {
                tmp.copyTo(epubOf(id), overwrite = true)
                tmp.delete()
            }
            val book = Book(
                id = id,
                title = epub.title,
                author = epub.author,
                addedAt = System.currentTimeMillis(),
                chapterLengths = lengths,
                hasCover = hasCover,
            )
            if (!dir.exists()) throw IOException("No se pudo guardar el libro.")
            lock.withLock {
                books.value = books.value + book
                save()
            }
            ImportResult.Added(book)
        } catch (e: DrmException) {
            tmp.delete()
            ImportResult.Failed(name ?: "Libro", e.message ?: "Tiene DRM.")
        } catch (e: NotEpubException) {
            tmp.delete()
            ImportResult.Failed(name ?: "Archivo", e.message ?: "No es un EPUB válido.")
        } catch (e: Throwable) {
            tmp.delete()
            ErrorLog.note("Importar libro", e)
            ImportResult.Failed(name ?: "Archivo", "No se pudo importar (${e.javaClass.simpleName}).")
        }
    }

    suspend fun update(id: String, transform: (Book) -> Book) = withContext(Dispatchers.IO) {
        lock.withLock {
            books.value = books.value.map { if (it.id == id) transform(it) else it }
            runCatching { save() }.onFailure { ErrorLog.note("Guardar biblioteca", it) }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            books.value = books.value.filterNot { it.id == id }
            runCatching { save() }
            dirOf(id).deleteRecursively()
        }
    }

    private fun saveCover(epub: File, path: String, out: File): Boolean = runCatching {
        val bytes = ZipFile(epub).use { z -> z.getEntry(path)?.let { e -> z.getInputStream(e).use { it.readBytes() } } }
            ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= 720) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        true
    }.getOrDefault(false)

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun sha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---- Guardado en JSON ----

    private val indexFile get() = File(root, "library.json")

    private fun save() {
        val arr = JSONArray()
        books.value.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id); put("title", b.title); put("author", b.author)
                put("addedAt", b.addedAt); put("lastOpened", b.lastOpened)
                put("chapter", b.chapter); put("fraction", b.fraction.toDouble())
                put("lengths", JSONArray(b.chapterLengths))
                put("finished", b.finished); put("hasCover", b.hasCover)
                put("bookmarks", JSONArray().apply {
                    b.bookmarks.forEach { m ->
                        put(JSONObject().apply {
                            put("chapter", m.chapter); put("fraction", m.fraction.toDouble())
                            put("label", m.label); put("snippet", m.snippet); put("createdAt", m.createdAt)
                        })
                    }
                })
            })
        }
        val tmp = File(root, "library.json.tmp")
        tmp.writeText(JSONObject().put("version", 1).put("books", arr).toString())
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun load(): List<Book> {
        val f = indexFile
        if (!f.exists()) return emptyList()
        val arr = JSONObject(f.readText()).optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty() || !epubOf(id).exists()) return@mapNotNull null
            val lengths = o.optJSONArray("lengths")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
            val marks = o.optJSONArray("bookmarks")?.let { a ->
                (0 until a.length()).mapNotNull { j ->
                    val m = a.optJSONObject(j) ?: return@mapNotNull null
                    Bookmark(
                        m.optInt("chapter"), m.optDouble("fraction").toFloat(),
                        m.optString("label"), m.optString("snippet"), m.optLong("createdAt"),
                    )
                }
            }.orEmpty()
            Book(
                id = id,
                title = o.optString("title"),
                author = o.optString("author"),
                addedAt = o.optLong("addedAt"),
                lastOpened = o.optLong("lastOpened"),
                chapter = o.optInt("chapter"),
                fraction = o.optDouble("fraction", 0.0).toFloat(),
                chapterLengths = lengths,
                finished = o.optBoolean("finished"),
                bookmarks = marks,
                hasCover = o.optBoolean("hasCover") && coverOf(id).exists(),
            )
        }
    }
}
