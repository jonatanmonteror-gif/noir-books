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

/** Estados de un libro (como Openreads): leyendo, pendiente, leído o abandonado. */
enum class BookStatus(val key: String, val label: String) {
    Reading("reading", "Leyendo"),
    /** El siguiente que vas a leer. */
    Next("next", "Próximo"),
    ToRead("toread", "Pendiente"),
    Read("read", "Leído"),
    Abandoned("abandoned", "Abandonado");

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: ToRead
    }
}

/** Dónde se lee el libro. Solo los EPUB se abren en la app; el resto se apunta a mano. */
enum class BookFormat(val key: String, val label: String) {
    Epub("epub", "EPUB en la app"),
    Pdf("pdf", "PDF en la app"),
    /** Cualquier lector electrónico o app (Kindle, Kobo, Apple Books…). La clave sigue siendo «kindle» por compatibilidad. */
    Kindle("kindle", "Libro electrónico"),
    Paper("paper", "Papel"),
    Audio("audio", "Audiolibro"),
    Other("other", "Otro lector");

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: Epub
    }
}

/** Cómo se apunta el progreso de un libro que no se lee en la app. */
enum class ProgressUnit(val key: String, val label: String) {
    Percent("percent", "Porcentaje"),
    Page("page", "Página"),
    Location("location", "Posición"),
    /** Audiolibros: horas y minutos. El total (en minutos) se guarda en [Book.pages]. */
    Time("time", "Tiempo (audiolibro)");

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: Percent
    }
}

/** Colores de los subrayados, cada uno con un significado (se puede ignorar). */
enum class HighlightColor(val label: String, val meaning: String, val argb: Long, val css: String) {
    Yellow("Amarillo", "Frase bonita", 0xFFE8C547, "rgba(232,197,71,0.42)"),
    Blue("Azul", "Idea", 0xFF6FA8DC, "rgba(111,168,220,0.40)"),
    Green("Verde", "Para recordar", 0xFF7BC67E, "rgba(123,198,126,0.40)"),
    Pink("Rosa", "Duda o desacuerdo", 0xFFE88AA8, "rgba(232,138,168,0.40)");

    companion object {
        fun of(i: Int) = entries.getOrElse(i) { Yellow }
    }
}

data class Bookmark(
    val chapter: Int,
    val fraction: Float,
    val label: String,
    val snippet: String,
    val createdAt: Long,
)

/** Subrayado o nota. [range] es la posición exacta dentro del capítulo, tal como la devuelve el lector. */
data class Highlight(
    val id: Long,
    val chapter: Int,
    val fraction: Float,
    val range: String,
    val text: String,
    val color: Int,
    val note: String = "",
    val chapterTitle: String = "",
    /** Etiquetas propias («amor», «para el discurso»…), para agrupar y filtrar citas. */
    val tags: List<String> = emptyList(),
)

/** Una lectura completa o en curso (para las relecturas). */
data class Reading(val start: Long, val end: Long = 0L)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val addedAt: Long,
    val lastOpened: Long = 0L,
    /** Capítulo actual y fracción leída dentro de él (0..1). */
    val chapter: Int = 0,
    val fraction: Float = 0f,
    /** Posición exacta dentro del capítulo (elemento + letra), para no moverse al cambiar la letra. */
    val anchor: String = "",
    /** Letras de cada capítulo: sirve para el % del libro y el tiempo que queda. */
    val chapterLengths: List<Int> = emptyList(),
    val status: BookStatus = BookStatus.ToRead,
    /** Valoración en medias estrellas, de 0,5 a 5 (0 = sin valorar). */
    val rating: Float = 0f,
    /** Valoración por aspectos (0 = sin valorar): trama, personajes y prosa. */
    val ratingPlot: Float = 0f,
    val ratingChars: Float = 0f,
    val ratingProse: Float = 0f,
    val abandonReason: String = "",
    val review: String = "",
    val readings: List<Reading> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val hasCover: Boolean = false,
    /** En la papelera desde esta fecha (0 = no está en la papelera). */
    val deletedAt: Long = 0L,
    val format: BookFormat = BookFormat.Epub,
    /** Páginas del libro (papel) o posiciones totales (Kindle), si se conocen. */
    val pages: Int = 0,
    /** Las páginas en papel se buscaron solas en el catálogo (false si las puso el usuario). */
    val pagesAuto: Boolean = false,
    val unit: ProgressUnit = ProgressUnit.Percent,
    /** Progreso apuntado a mano (0..1) para libros que no se leen en la app. */
    val manualProgress: Float = 0f,
    val isbn: String = "",
    val publisher: String = "",
    val year: String = "",
    /** Sinopsis. */
    val description: String = "",
    /** Estanterías propias en las que está el libro. */
    val shelves: List<String> = emptyList(),
    /** Género (se busca solo en los catálogos y se puede cambiar). */
    val genre: String = "",
    /** Lista de deseos: lo quieres pero aún no lo tienes. */
    val wish: Boolean = false,
    /** Saga: nombre, número dentro de ella (texto: «3», «2.5») y total de libros si se sabe (0 = no se sabe). */
    val series: String = "",
    val seriesNo: String = "",
    val seriesTotal: Int = 0,
    /** Préstamo de un libro de papel: a quién, desde cuándo y para cuándo debería volver (0 = sin fecha). */
    val loanTo: String = "",
    val loanAt: Long = 0L,
    val loanDue: Long = 0L,
    /** Retomar un abandonado: cuándo se preguntó por última vez y si no quieres que se vuelva a preguntar. */
    val resurfaceAskedAt: Long = 0L,
    val noResurface: Boolean = false,
) {
    val onLoan: Boolean get() = loanTo.isNotBlank()
    /** «3 de 7», «n.º 3» o vacío. */
    val seriesLabel: String get() = when {
        series.isBlank() -> ""
        seriesNo.isNotBlank() && seriesTotal > 0 -> "$seriesNo de $seriesTotal"
        seriesNo.isNotBlank() -> "n.º $seriesNo"
        else -> ""
    }
    val isEpub: Boolean get() = format == BookFormat.Epub
    val isPdf: Boolean get() = format == BookFormat.Pdf
    /** Se lee dentro de la app (EPUB o PDF). */
    val inApp: Boolean get() = isEpub || isPdf
    val totalChars: Int get() = chapterLengths.sum().coerceAtLeast(1)
    val finished: Boolean get() = status == BookStatus.Read
    val inTrash: Boolean get() = deletedAt > 0L

    /** Progreso del libro entero (0..1). */
    fun progressAt(ch: Int, f: Float): Float {
        if (chapterLengths.isEmpty()) return 0f
        val before = chapterLengths.take(ch.coerceIn(0, chapterLengths.size)).sum()
        val cur = chapterLengths.getOrElse(ch) { 0 }
        return ((before + cur * f) / totalChars).coerceIn(0f, 1f)
    }

    val progress: Float get() = when {
        finished -> 1f
        !isEpub -> manualProgress.coerceIn(0f, 1f)
        else -> progressAt(chapter, fraction)
    }

    /** Progreso en la unidad del libro, para mostrarlo ("p. 120 de 340", "37 %"). */
    fun progressLabel(p: Float = progress): String = when {
        unit == ProgressUnit.Page && pages > 0 -> "p. ${(p * pages).toInt()} de $pages"
        unit == ProgressUnit.Location && pages > 0 -> "posición ${(p * pages).toInt()} de $pages"
        unit == ProgressUnit.Time && pages > 0 -> "${hmLabel((p * pages).toInt())} de ${hmLabel(pages)}"
        else -> percentLabel(p)
    }
    val started: Boolean get() = lastOpened > 0L
    val timesRead: Int get() = readings.count { it.end > 0L }
}

/** Minutos como «4 h 20 min», «45 min», «11 h». */
fun hmLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

/** «4:20», «4h20», «4 h 20 min» → 260 minutos; un número solo son horas («11» → 660). null si no se entiende. */
fun parseHm(raw: String): Int? {
    val s = raw.trim().lowercase().replace(" ", "")
    if (s.isEmpty()) return null
    Regex("""^(\d{1,3})[:h.,'](\d{1,2})(m|min)?$""").find(s)?.let { m ->
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (mi < 60) h * 60 + mi else null
    }
    Regex("""^(\d{1,3})h?$""").find(s)?.let { return it.groupValues[1].toInt() * 60 }
    Regex("""^(\d{1,4})(m|min)$""").find(s)?.let { return it.groupValues[1].toInt() }
    return null
}

/** Porcentaje con un decimal, a la española: "37,4 %". */
fun percentLabel(p: Float): String = String.format(java.util.Locale("es", "ES"), "%.1f %%", (p * 100f).coerceIn(0f, 100f))

/** Letras de una página impresa media (libro de bolsillo en español), para estimar la página. */
const val CHARS_PER_PRINTED_PAGE = 1800

/**
 * Página de la edición en papel por la que vas en un EPUB: exacta si el libro trae las páginas (page-list),
 * proporcional si sabes cuántas páginas tiene en papel, o estimada por las letras.
 */
fun Book.printedPageLabel(p: Float, exact: String = "", exactTotal: String = ""): String {
    if (exact.isNotBlank()) return "p. $exact" + if (exactTotal.isNotBlank()) " de $exactTotal" else ""
    if (pages > 0) return "p. ${kotlin.math.ceil(p * pages).toInt().coerceIn(1, pages)} de $pages"
    if (!isEpub) return progressLabel(p)
    val total = kotlin.math.ceil(totalChars / CHARS_PER_PRINTED_PAGE.toFloat()).toInt().coerceAtLeast(1)
    return "p. ≈${kotlin.math.ceil(p * total).toInt().coerceIn(1, total)} de ≈$total"
}

/** Palabra buscada en el diccionario, con la frase del libro. */
data class VocabWord(val word: String, val sentence: String, val bookId: String, val bookTitle: String, val createdAt: Long)

/** Velocidad de lectura media de partida (letras por minuto, unas 230 palabras/min en español). */
const val CHARS_PER_MINUTE = 1300

fun minutesLabel(chars: Int, charsPerMinute: Int = CHARS_PER_MINUTE): String {
    val min = (chars / charsPerMinute.coerceAtLeast(200).toFloat()).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
    return durationLabel(min * 60L)
}

fun durationLabel(seconds: Long): String {
    val min = ((seconds + 59) / 60).toInt()
    if (min < 60) return "$min min"
    val h = min / 60
    val m = min % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

/** Biblioteca: los libros se copian dentro de la app (así no se pierden si se mueve el archivo original). */
object Library {
    private lateinit var root: File
    private val lock = Mutex()
    /** Todos los libros, incluidos los de la papelera. Para mostrar, usar [visible]. */
    val books = mutableStateOf<List<Book>>(emptyList())
    val vocabulary = mutableStateOf<List<VocabWord>>(emptyList())
    /** Estanterías propias, en el orden en que las creaste. */
    val shelves = mutableStateOf<List<String>>(emptyList())

    val visible: List<Book> get() = books.value.filter { !it.inTrash }

    fun init(ctx: Context) {
        if (::root.isInitialized) return
        root = File(ctx.filesDir, "books").apply { mkdirs() }
        books.value = runCatching { load() }.getOrElse {
            ErrorLog.note("Leer biblioteca", it)
            emptyList()
        }
        vocabulary.value = runCatching { loadVocab() }.getOrDefault(emptyList())
        // La papelera se vacía sola a los 30 días.
        val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        val old = books.value.filter { it.inTrash && it.deletedAt < limit }
        if (old.isNotEmpty()) {
            books.value = books.value - old.toSet()
            old.forEach { dirOf(it.id).deleteRecursively() }
            runCatching { save() }
        }
    }

    fun dirOf(id: String) = File(root, id)
    fun epubOf(id: String) = File(dirOf(id), "book.epub")
    fun coverOf(id: String) = File(dirOf(id), "cover.jpg")
    fun pdfOf(id: String) = File(dirOf(id), "book.pdf")
    fun get(id: String): Book? = books.value.firstOrNull { it.id == id }

    /** ¿Falta el archivo EPUB? (copia restaurada sin los libros). */
    fun fileMissing(b: Book): Boolean = (b.isEpub && !epubOf(b.id).exists()) || (b.isPdf && !pdfOf(b.id).exists())

    /** Carpeta raíz de la biblioteca (para las copias de seguridad). */
    val rootDir: File get() = root

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
            if (isPdf(tmp)) return@withContext importPdf(tmp, name)
            val id = sha1(tmp).take(16)
            get(id)?.let { existing ->
                if (existing.isEpub && !epubOf(id).exists()) {
                    // Libro que venía de una copia sin el archivo: se recupera con todo su progreso.
                    dirOf(id).mkdirs()
                    if (!tmp.renameTo(epubOf(id))) { tmp.copyTo(epubOf(id), overwrite = true); tmp.delete() }
                    update(id) { it.copy(deletedAt = 0L) }
                    return@withContext ImportResult.Added(existing)
                }
                tmp.delete()
                if (existing.inTrash) {
                    update(id) { it.copy(deletedAt = 0L) }
                    return@withContext ImportResult.Added(existing.copy(deletedAt = 0L))
                }
                return@withContext ImportResult.Existing(existing)
            }

            val epub = Epub.open(tmp, name?.substringBeforeLast('.'))
            val lengths = ZipFile(tmp).use { z -> epub.spine.map { Epub.plainTextLength(z, it) } }

            val dir = dirOf(id).apply { mkdirs() }
            val hasCover = epub.coverPath?.let { saveCover(tmp, it, coverOf(id)) } ?: false
            if (!tmp.renameTo(epubOf(id))) {
                tmp.copyTo(epubOf(id), overwrite = true)
                tmp.delete()
            }
            if (!dir.exists()) throw IOException("No se pudo guardar el libro.")
            val book = Book(
                id = id,
                title = epub.title,
                author = epub.author,
                addedAt = System.currentTimeMillis(),
                chapterLengths = lengths,
                hasCover = hasCover,
                description = epub.description, publisher = epub.publisher, year = epub.year,
                isbn = epub.isbn,
                series = epub.series.ifBlank { Epub.seriesFromTitle(epub.title)?.second.orEmpty() },
                seriesNo = if (epub.series.isNotBlank()) epub.seriesIndex else Epub.seriesFromTitle(epub.title)?.third.orEmpty(),
            )
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

    private fun isPdf(f: File): Boolean = runCatching {
        f.inputStream().use { val h = ByteArray(5); it.read(h) == 5 && String(h, Charsets.US_ASCII) == "%PDF-" }
    }.getOrDefault(false)

    /** Importa un PDF: cuenta sus páginas y hace la portada con la primera. */
    private suspend fun importPdf(tmp: File, name: String?): ImportResult {
        val id = sha1(tmp).take(16)
        get(id)?.let { existing ->
            if (!pdfOf(id).exists()) {
                dirOf(id).mkdirs()
                if (!tmp.renameTo(pdfOf(id))) { tmp.copyTo(pdfOf(id), overwrite = true); tmp.delete() }
                update(id) { it.copy(deletedAt = 0L) }
                return ImportResult.Added(existing)
            }
            tmp.delete()
            if (existing.inTrash) {
                update(id) { it.copy(deletedAt = 0L) }
                return ImportResult.Added(existing.copy(deletedAt = 0L))
            }
            return ImportResult.Existing(existing)
        }
        var count = 0
        val dir = dirOf(id).apply { mkdirs() }
        try {
            android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { r ->
                    count = r.pageCount
                    if (count == 0) throw IOException("El PDF no tiene páginas.")
                    r.openPage(0).use { p ->
                        val w = 600
                        val h = (w.toLong() * p.height / p.width.coerceAtLeast(1)).toInt().coerceIn(200, 1600)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        p.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        coverOf(id).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                        bmp.recycle()
                    }
                }
            }
        } catch (e: SecurityException) {
            tmp.delete(); dir.deleteRecursively()
            return ImportResult.Failed(name ?: "PDF", "Tiene contraseña: no se puede abrir.")
        } catch (e: Exception) {
            tmp.delete(); dir.deleteRecursively()
            return ImportResult.Failed(name ?: "PDF", "No se pudo leer el PDF.")
        }
        if (!tmp.renameTo(pdfOf(id))) { tmp.copyTo(pdfOf(id), overwrite = true); tmp.delete() }
        val book = Book(
            id = id,
            title = (name?.substringBeforeLast('.') ?: "Documento PDF").replace('_', ' ').trim(),
            author = "",
            addedAt = System.currentTimeMillis(),
            hasCover = true,
            format = BookFormat.Pdf,
            pages = count,
            unit = ProgressUnit.Page,
        )
        lock.withLock {
            books.value = books.value + book
            save()
        }
        return ImportResult.Added(book)
    }

    suspend fun update(id: String, transform: (Book) -> Book) = withContext(Dispatchers.IO) {
        lock.withLock {
            books.value = books.value.map { if (it.id == id) transform(it) else it }
            runCatching { save() }.onFailure { ErrorLog.note("Guardar biblioteca", it) }
        }
    }

    // ---- Estados y lecturas ----

    /** Al abrir un libro: pasa a "Leyendo" y empieza una lectura si no había una abierta. */
    suspend fun markOpened(id: String) = update(id) { b ->
        val now = System.currentTimeMillis()
        val open = b.readings.any { it.end == 0L }
        val status = if (b.status == BookStatus.Reading) b.status
        else if (b.status == BookStatus.Read && !open) BookStatus.Reading // relectura
        else BookStatus.Reading
        val restart = b.status == BookStatus.Read
        b.copy(
            lastOpened = now,
            status = status,
            readings = if (open) b.readings else b.readings + Reading(now),
            chapter = if (restart) 0 else b.chapter,
            fraction = if (restart) 0f else b.fraction,
            anchor = if (restart) "" else b.anchor,
            manualProgress = if (restart && b.isPdf) 0f else b.manualProgress,
            abandonReason = if (b.status == BookStatus.Abandoned) "" else b.abandonReason,
        )
    }

    suspend fun setStatus(id: String, status: BookStatus, reason: String = "") {
        val wasRead = get(id)?.status == BookStatus.Read
        setStatusOnly(id, status, reason)
        if (status == BookStatus.Read && !wasRead) runCatching { Praise.onFinished(id) }
    }

    private suspend fun setStatusOnly(id: String, status: BookStatus, reason: String) = update(id) { b ->
        val now = System.currentTimeMillis()
        val closing = status == BookStatus.Read || status == BookStatus.Abandoned
        val readings = when {
            closing && b.readings.any { it.end == 0L } -> b.readings.map { if (it.end == 0L) it.copy(end = now) else it }
            status == BookStatus.Read && b.readings.isEmpty() -> listOf(Reading(b.addedAt, now))
            else -> b.readings
        }
        b.copy(
            status = status,
            readings = readings,
            abandonReason = if (status == BookStatus.Abandoned) reason else "",
            fraction = if (status == BookStatus.ToRead) 0f else b.fraction,
        )
    }

    /** Reiniciar: el libro vuelve al principio (las citas, marcadores y el tiempo leído se quedan). */
    suspend fun restart(id: String) = update(id) { b ->
        val now = System.currentTimeMillis()
        val open = b.readings.any { it.end == 0L }
        b.copy(
            chapter = 0, fraction = 0f, anchor = "", manualProgress = 0f,
            status = if (b.status == BookStatus.Read || b.status == BookStatus.Abandoned) BookStatus.Reading else b.status,
            readings = if (open || b.status == BookStatus.ToRead || b.status == BookStatus.Next) b.readings else b.readings + Reading(now),
            abandonReason = "",
        )
    }

    /**
     * Completa la sinopsis si falta: primero la del propio EPUB, si no, la de Google Books.
     * Solo se busca en internet una vez por libro.
     */
    suspend fun ensureDescription(id: String) = withContext(Dispatchers.IO) {
        val b = get(id) ?: return@withContext
        if (b.description.isNotBlank()) return@withContext
        var d = ""
        if (b.isEpub) d = runCatching { Epub.open(epubOf(id)).description }.getOrDefault("")
        if (d.isBlank() && !Prefs.descLookedUp(id)) {
            Prefs.markDescLookedUp(id)
            d = runCatching { BookSearch.description(b.title, b.author, b.isbn) }.getOrDefault("")
        }
        if (d.isNotBlank()) update(id) { it.copy(description = d) }
    }

    /**
     * Páginas de la edición en papel de un EPUB que no las trae: se buscan una vez en Google Books / Open Library
     * (por ISBN o por título y autor). Si no hay conexión se reintenta otro día; si no aparece, se queda la estimación.
     */
    suspend fun ensurePaperPages(id: String) = withContext(Dispatchers.IO) {
        val b = get(id) ?: return@withContext
        if (!b.isEpub || b.pages > 0 || Prefs.paperLookedUp(id)) return@withContext
        val epub = runCatching { Epub.open(epubOf(id)) }.getOrNull()
        if (epub != null && epub.pageList.isNotEmpty()) { Prefs.markPaperLookedUp(id); return@withContext }
        val isbn = b.isbn.ifBlank { epub?.isbn.orEmpty() }
        val n = try {
            BookSearch.paperPages(b.title, b.author, isbn, epub?.language.orEmpty())
        } catch (e: Exception) {
            return@withContext // sin conexión: se intentará más adelante
        }
        Prefs.markPaperLookedUp(id)
        if (n > 0) update(id) { if (it.pages > 0) it else it.copy(pages = n, pagesAuto = true, isbn = it.isbn.ifBlank { isbn }) }
    }

    /** Género de un libro: primero los temas del EPUB; si no, los catálogos. Se busca una vez (si no hay conexión, otro día). */
    suspend fun ensureGenre(id: String) = withContext(Dispatchers.IO) {
        val b = get(id) ?: return@withContext
        if (b.genre.isNotBlank() || Prefs.genreLookedUp(id)) return@withContext
        val fromEpub = if (b.isEpub) runCatching { Genres.from(Epub.open(epubOf(id)).subjects) }.getOrDefault("") else ""
        val g = fromEpub.ifBlank {
            try { BookSearch.genre(b.title, b.author, b.isbn) } catch (e: Exception) { return@withContext }
        }
        Prefs.markGenreLookedUp(id)
        if (g.isNotBlank()) update(id) { if (it.genre.isBlank()) it.copy(genre = g) else it }
    }

    suspend fun ensureAllGenres() {
        books.value.filter { !it.inTrash && it.genre.isBlank() && !Prefs.genreLookedUp(it.id) }
            .forEach { runCatching { ensureGenre(it.id) }; kotlinx.coroutines.delay(500) }
    }

    /**
     * Saga de los libros que aún no la tienen: del propio EPUB (Calibre / EPUB 3) o, si no, del título
     * («Alas de sangre (Empíreo 1)»). Se mira una sola vez por libro; lo que pongas a mano no se toca.
     */
    suspend fun ensureAllSeries() = withContext(Dispatchers.IO) {
        books.value.filter { !it.inTrash && it.series.isBlank() && !Prefs.seriesLookedUp(it.id) }.forEach { b ->
            runCatching {
                var name = ""; var no = ""
                if (b.isEpub && epubOf(b.id).exists()) {
                    val e = Epub.open(epubOf(b.id), b.title)
                    name = e.series; no = e.seriesIndex
                }
                if (name.isBlank()) Epub.seriesFromTitle(b.title)?.let { (_, n, i) -> name = n; no = i }
                if (name.isNotBlank()) update(b.id) { if (it.series.isBlank()) it.copy(series = name, seriesNo = no) else it }
            }
            Prefs.markSeriesLookedUp(b.id)
            kotlinx.coroutines.delay(150)
        }
    }

    /** Busca las páginas en papel de todos los EPUB que aún no las tienen (en segundo plano, de uno en uno). */
    suspend fun ensureAllPaperPages() {
        books.value.filter { it.isEpub && !it.inTrash && it.pages == 0 && !Prefs.paperLookedUp(it.id) }
            .forEach { runCatching { ensurePaperPages(it.id) }; kotlinx.coroutines.delay(400) }
    }

    // ---- Estanterías propias ----

    suspend fun createShelf(name: String) = withContext(Dispatchers.IO) {
        val n = name.trim()
        if (n.isEmpty()) return@withContext
        lock.withLock {
            if (shelves.value.none { it.equals(n, ignoreCase = true) }) { shelves.value = shelves.value + n; save() }
        }
    }

    suspend fun renameShelf(old: String, new: String) = withContext(Dispatchers.IO) {
        val n = new.trim()
        if (n.isEmpty() || n == old) return@withContext
        lock.withLock {
            shelves.value = shelves.value.map { if (it == old) n else it }.distinct()
            books.value = books.value.map { b -> if (old in b.shelves) b.copy(shelves = b.shelves.map { if (it == old) n else it }.distinct()) else b }
            save()
        }
    }

    /** Borra la estantería (los libros se quedan en la biblioteca). */
    suspend fun deleteShelf(name: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            shelves.value = shelves.value - name
            books.value = books.value.map { b -> if (name in b.shelves) b.copy(shelves = b.shelves - name) else b }
            save()
        }
    }

    /** Pone (o quita) varios libros en una estantería de una vez. */
    suspend fun setShelf(ids: Collection<String>, name: String, inShelf: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (inShelf && name !in shelves.value) shelves.value = shelves.value + name
            books.value = books.value.map { b ->
                if (b.id !in ids) b
                else if (inShelf) b.copy(shelves = (b.shelves + name).distinct()) else b.copy(shelves = b.shelves - name)
            }
            save()
        }
    }

    /** Cambia el estado de varios libros a la vez. */
    suspend fun setStatusMany(ids: Collection<String>, status: BookStatus) { ids.forEach { setStatus(it, status) } }

    suspend fun moveToTrash(id: String) = update(id) { it.copy(deletedAt = System.currentTimeMillis()) }
    suspend fun restore(id: String) = update(id) { it.copy(deletedAt = 0L) }

    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            books.value = books.value.filterNot { it.id == id }
            runCatching { save() }
            dirOf(id).deleteRecursively()
        }
    }

    // ---- Libros que no se leen en la app (Kindle, papel, audiolibro) ----

    /** Añade un libro buscado en el catálogo o escrito a mano. Descarga la portada si hay enlace. */
    suspend fun addManual(
        title: String, author: String, format: BookFormat, pages: Int, unit: ProgressUnit,
        coverUrl: String?, isbn: String, publisher: String, year: String, status: BookStatus,
        description: String = "", genre: String = "",
    ): Book = withContext(Dispatchers.IO) {
        val id = "m" + java.lang.Long.toHexString(System.currentTimeMillis()) + (100..999).random()
        dirOf(id).mkdirs()
        val hasCover = coverUrl?.let { url -> downloadCover(url, coverOf(id)) } ?: false
        val now = System.currentTimeMillis()
        val book = Book(
            id = id, title = title.trim(), author = author.trim(), addedAt = now,
            status = status, format = format, pages = pages, unit = unit, hasCover = hasCover,
            isbn = isbn, publisher = publisher, year = year, description = description, genre = genre,
            readings = if (status == BookStatus.Reading) listOf(Reading(now)) else if (status == BookStatus.Read) listOf(Reading(now, now)) else emptyList(),
            lastOpened = if (status == BookStatus.Reading) now else 0L,
            manualProgress = if (status == BookStatus.Read) 1f else 0f,
        )
        lock.withLock {
            books.value = books.value + book
            save()
        }
        book
    }

    /** Guarda una foto o imagen elegida como portada. */
    suspend fun setCover(ctx: Context, id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val ok = runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching false
            saveCoverBytes(bytes, coverOf(id))
        }.getOrDefault(false)
        if (ok) update(id) { it.copy(hasCover = true) }
    }

    /**
     * Busca y pone portada a los libros que no tienen. Devuelve (encontradas, que faltaban).
     * No toca las portadas que ya hay.
     */
    suspend fun fetchMissingCovers(progress: (Int, Int) -> Unit = { _, _ -> }): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val missing = books.value.filter { !it.hasCover && !it.inTrash }
        var found = 0
        missing.forEachIndexed { i, b ->
            progress(i, missing.size)
            val urls = runCatching { BookSearch.coverCandidates(b.title, b.author, b.isbn) }.getOrDefault(emptyList())
            val ok = urls.any { u -> downloadCover(u, coverOf(b.id)) }
            if (ok) { update(b.id) { it.copy(hasCover = true) }; found++ }
        }
        progress(missing.size, missing.size)
        found to missing.size
    }

    private fun downloadCover(url: String, out: File): Boolean = runCatching {
        val req = okhttp3.Request.Builder().url(url.replace("http://", "https://")).header("User-Agent", Http.UA).build()
        val bytes = Http.client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } ?: return false
        if (bytes.size < 1500) return false // Open Library devuelve una imagen vacía si no hay portada
        saveCoverBytes(bytes, out)
    }.getOrDefault(false)

    private fun saveCoverBytes(bytes: ByteArray, out: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 1) return false
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= 720) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        out.parentFile?.mkdirs()
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        return true
    }

    /**
     * Apunta hasta dónde se ha leído (0..1). En un EPUB mueve también la posición del lector;
     * si llega al final, el libro pasa a "Leído".
     */
    suspend fun setProgress(id: String, p: Float) {
        val v = p.coerceIn(0f, 1f)
        update(id) { b ->
            val base = b.copy(lastOpened = System.currentTimeMillis(), status = if (b.status == BookStatus.ToRead || b.status == BookStatus.Next || b.status == BookStatus.Abandoned) BookStatus.Reading else b.status,
                readings = if (b.readings.any { it.end == 0L }) b.readings else b.readings + Reading(System.currentTimeMillis()))
            if (!b.isEpub) base.copy(manualProgress = v)
            else {
                var pos = v * b.totalChars
                var ch = 0
                var f = 0f
                for (i in b.chapterLengths.indices) {
                    val len = b.chapterLengths[i]
                    if (pos <= len || i == b.chapterLengths.lastIndex) { ch = i; f = if (len > 0) (pos / len).coerceIn(0f, 0.999f) else 0f; break }
                    pos -= len
                }
                base.copy(chapter = ch, fraction = f, anchor = "")
            }
        }
        if (v >= 0.999f) setStatus(id, BookStatus.Read)
    }

    // ---- Vocabulario ----

    suspend fun addWord(w: VocabWord) = withContext(Dispatchers.IO) {
        lock.withLock {
            val clean = w.word.trim()
            if (clean.isEmpty()) return@withLock
            vocabulary.value = listOf(w.copy(word = clean)) + vocabulary.value.filterNot { it.word.equals(clean, true) }
            runCatching { saveVocab() }
        }
    }

    suspend fun removeWord(word: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            vocabulary.value = vocabulary.value.filterNot { it.word == word }
            runCatching { saveVocab() }
        }
    }

    // ---- Portadas, nombres y huellas ----

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

    // ---- Guardado en JSON (escritura atómica: archivo temporal y renombrar) ----

    private val indexFile get() = File(root, "library.json")
    private val vocabFile get() = File(root, "vocabulary.json")

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    private fun save() {
        if (Backup.restoring) return
        Prefs.dataChangedAt = System.currentTimeMillis()
        val arr = JSONArray()
        books.value.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id); put("title", b.title); put("author", b.author)
                put("addedAt", b.addedAt); put("lastOpened", b.lastOpened)
                put("chapter", b.chapter); put("fraction", b.fraction.toDouble()); put("anchor", b.anchor)
                put("lengths", JSONArray(b.chapterLengths))
                put("status", b.status.key); put("rating", b.rating.toDouble())
                if (b.genre.isNotBlank()) put("genre", b.genre)
                if (b.wish) put("wish", true)
                if (b.series.isNotBlank()) { put("series", b.series); put("seriesNo", b.seriesNo); put("seriesTotal", b.seriesTotal) }
                if (b.loanTo.isNotBlank()) { put("loanTo", b.loanTo); put("loanAt", b.loanAt); put("loanDue", b.loanDue) }
                if (b.resurfaceAskedAt > 0) put("resurfaceAskedAt", b.resurfaceAskedAt)
                if (b.noResurface) put("noResurface", true)
                if (b.ratingPlot > 0f) put("rPlot", b.ratingPlot.toDouble())
                if (b.ratingChars > 0f) put("rChars", b.ratingChars.toDouble())
                if (b.ratingProse > 0f) put("rProse", b.ratingProse.toDouble())
                put("abandonReason", b.abandonReason); put("review", b.review)
                put("hasCover", b.hasCover); put("deletedAt", b.deletedAt)
                put("format", b.format.key); put("pages", b.pages); put("pagesAuto", b.pagesAuto); put("unit", b.unit.key)
                put("manualProgress", b.manualProgress.toDouble()); put("isbn", b.isbn)
                put("publisher", b.publisher); put("year", b.year); put("description", b.description)
                put("shelves", JSONArray(b.shelves))
                put("readings", JSONArray().apply {
                    b.readings.forEach { r -> put(JSONObject().put("start", r.start).put("end", r.end)) }
                })
                put("bookmarks", JSONArray().apply {
                    b.bookmarks.forEach { m ->
                        put(JSONObject().apply {
                            put("chapter", m.chapter); put("fraction", m.fraction.toDouble())
                            put("label", m.label); put("snippet", m.snippet); put("createdAt", m.createdAt)
                        })
                    }
                })
                put("highlights", JSONArray().apply {
                    b.highlights.forEach { h ->
                        put(JSONObject().apply {
                            put("id", h.id); put("chapter", h.chapter); put("fraction", h.fraction.toDouble())
                            put("range", h.range); put("text", h.text); put("color", h.color)
                            put("note", h.note); put("chapterTitle", h.chapterTitle)
                            if (h.tags.isNotEmpty()) put("tags", JSONArray(h.tags))
                        })
                    }
                })
            })
        }
        writeAtomic(indexFile, JSONObject().put("version", 2).put("shelves", JSONArray(shelves.value)).put("books", arr).toString())
    }

    private fun load(): List<Book> {
        val f = indexFile
        if (!f.exists()) return emptyList()
        val root = JSONObject(f.readText())
        shelves.value = root.optJSONArray("shelves")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        val arr = root.optJSONArray("books") ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val format = BookFormat.of(o.optString("format", "epub"))
            // Un EPUB sin su archivo (p. ej. copia restaurada sin libros) se conserva: se recupera al importarlo otra vez.
            if (id.isEmpty() || !seen.add(id)) return@mapNotNull null
            val lengths = o.optJSONArray("lengths")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
            val marks = o.optJSONArray("bookmarks")?.let { a ->
                (0 until a.length()).mapNotNull { j ->
                    val m = a.optJSONObject(j) ?: return@mapNotNull null
                    Bookmark(
                        m.optInt("chapter"), m.optDouble("fraction", 0.0).toFloat(),
                        m.optString("label"), m.optString("snippet"), m.optLong("createdAt"),
                    )
                }
            }.orEmpty()
            val highlights = o.optJSONArray("highlights")?.let { a ->
                (0 until a.length()).mapNotNull { j ->
                    val h = a.optJSONObject(j) ?: return@mapNotNull null
                    Highlight(
                        id = h.optLong("id"), chapter = h.optInt("chapter"),
                        fraction = h.optDouble("fraction", 0.0).toFloat(), range = h.optString("range"),
                        text = h.optString("text"), color = h.optInt("color"), note = h.optString("note"),
                        chapterTitle = h.optString("chapterTitle"),
                        tags = h.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                    )
                }.distinctBy { it.id }
            }.orEmpty()
            val readings = o.optJSONArray("readings")?.let { a ->
                (0 until a.length()).mapNotNull { j ->
                    val r = a.optJSONObject(j) ?: return@mapNotNull null
                    Reading(r.optLong("start"), r.optLong("end"))
                }
            }.orEmpty()
            // Versión 1: "finished" en lugar de "status".
            val status = if (o.has("status")) BookStatus.of(o.optString("status"))
            else if (o.optBoolean("finished")) BookStatus.Read
            else if (o.optLong("lastOpened") > 0) BookStatus.Reading else BookStatus.ToRead
            Book(
                id = id,
                title = o.optString("title"),
                author = o.optString("author"),
                addedAt = o.optLong("addedAt"),
                lastOpened = o.optLong("lastOpened"),
                chapter = o.optInt("chapter"),
                fraction = o.optDouble("fraction", 0.0).toFloat(),
                anchor = o.optString("anchor"),
                chapterLengths = lengths,
                status = status,
                rating = (kotlin.math.round(o.optDouble("rating", 0.0) * 2) / 2).toFloat(), // medias estrellas
                genre = o.optString("genre"),
                wish = o.optBoolean("wish"),
                series = o.optString("series"),
                seriesNo = o.optString("seriesNo"),
                seriesTotal = o.optInt("seriesTotal"),
                loanTo = o.optString("loanTo"),
                loanAt = o.optLong("loanAt"),
                loanDue = o.optLong("loanDue"),
                resurfaceAskedAt = o.optLong("resurfaceAskedAt"),
                noResurface = o.optBoolean("noResurface"),
                ratingPlot = o.optDouble("rPlot", 0.0).toFloat(),
                ratingChars = o.optDouble("rChars", 0.0).toFloat(),
                ratingProse = o.optDouble("rProse", 0.0).toFloat(),
                abandonReason = o.optString("abandonReason"),
                review = o.optString("review"),
                readings = readings,
                bookmarks = marks,
                highlights = highlights,
                hasCover = o.optBoolean("hasCover") && coverOf(id).exists(),
                deletedAt = o.optLong("deletedAt"),
                format = format,
                pages = o.optInt("pages"),
                pagesAuto = o.optBoolean("pagesAuto"),
                unit = ProgressUnit.of(o.optString("unit")),
                manualProgress = o.optDouble("manualProgress", 0.0).toFloat(),
                isbn = o.optString("isbn"),
                publisher = o.optString("publisher"),
                year = o.optString("year"),
                description = o.optString("description"),
                shelves = o.optJSONArray("shelves")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            )
        }
    }

    private fun saveVocab() {
        val arr = JSONArray()
        vocabulary.value.forEach { w ->
            arr.put(JSONObject().apply {
                put("word", w.word); put("sentence", w.sentence); put("bookId", w.bookId)
                put("bookTitle", w.bookTitle); put("createdAt", w.createdAt)
            })
        }
        writeAtomic(vocabFile, arr.toString())
    }

    private fun loadVocab(): List<VocabWord> {
        val f = vocabFile
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            VocabWord(o.optString("word"), o.optString("sentence"), o.optString("bookId"), o.optString("bookTitle"), o.optLong("createdAt"))
        }.distinctBy { it.word.lowercase() }
    }
}
