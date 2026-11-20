package com.noirbooks.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipFile

/** Entrada del índice del libro. [href] es la ruta dentro del EPUB (sin '#'), [anchor] el fragmento. */
data class TocEntry(val title: String, val href: String, val anchor: String?, val level: Int)

/** Marca de página de la edición en papel (page-list del EPUB): etiqueta ("123"), capítulo y ancla. */
data class PageMark(val label: String, val href: String, val anchor: String?)

data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)

/** Libro abierto: estructura mínima para leerlo. No guarda capítulos en memoria (se leen del zip cuando hacen falta). */
class EpubBook(
    val title: String,
    val author: String,
    val language: String,
    val spine: List<String>,          // rutas de los capítulos en orden de lectura
    val toc: List<TocEntry>,
    val coverPath: String?,
    val manifest: Map<String, ManifestItem>, // por ruta
    /** Identificador único del libro (para desproteger fuentes ofuscadas). */
    val uniqueId: String = "",
    /** Identificador urn:uuid (ofuscación de Adobe), si lo hay. */
    val uuidId: String = "",
    /** Fuentes ofuscadas: ruta → algoritmo. */
    val obfuscated: Map<String, String> = emptyMap(),
    /** Páginas de la edición impresa, si el libro las trae. */
    val pageList: List<PageMark> = emptyList(),
)

class DrmException : IOException("Este libro tiene protección DRM y no se puede abrir.")
class NotEpubException(msg: String = "El archivo no es un EPUB válido.") : IOException(msg)

object Epub {

    /** Lee la estructura del EPUB. Lanza [DrmException] o [NotEpubException] con un mensaje para el usuario. */
    fun open(file: File, fallbackTitle: String? = null): EpubBook {
        val zip = try { ZipFile(file) } catch (e: Exception) { throw NotEpubException() }
        return zip.use { z ->
            checkDrm(z)
            val container = z.text("META-INF/container.xml") ?: throw NotEpubException()
            val opfPath = xml(container).selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
                ?: throw NotEpubException()
            val opfText = z.text(opfPath) ?: throw NotEpubException()
            val opf = xml(opfText)
            val base = opfPath.substringBeforeLast('/', "")

            val manifestById = LinkedHashMap<String, ManifestItem>()
            opf.select("manifest > item, opf|manifest > opf|item").forEach { el ->
                val id = el.attr("id")
                val href = el.attr("href")
                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifestById[id] = ManifestItem(id, resolve(base, href), el.attr("media-type"), el.attr("properties"))
                }
            }
            val manifestByPath = manifestById.values.associateBy { it.href }

            val spineEl = opf.selectFirst("spine, opf|spine")
            val spine = spineEl?.select("itemref, opf|itemref")
                ?.filter { it.attr("linear") != "no" }
                ?.mapNotNull { manifestById[it.attr("idref")]?.href }
                ?.filter { z.getEntry(it) != null }
                .orEmpty()
            if (spine.isEmpty()) throw NotEpubException("El EPUB no tiene capítulos que se puedan leer.")

            val meta = opf.selectFirst("metadata")
            val title = meta?.firstText("dc|title", "title")?.ifBlank { null } ?: fallbackTitle ?: file.nameWithoutExtension
            val author = meta?.select("dc|creator, creator")?.map { it.text().trim() }?.filter { it.isNotEmpty() }
                ?.distinct()?.joinToString(", ").orEmpty()
            val lang = meta?.firstText("dc|language", "language").orEmpty()

            // Portada: EPUB 3 (properties="cover-image"), EPUB 2 (<meta name="cover">) o un archivo llamado "cover".
            val cover = manifestById.values.firstOrNull { "cover-image" in it.properties.split(' ') }?.href
                ?: opf.select("meta[name=cover]").firstOrNull()?.attr("content")?.let { c ->
                    manifestById[c]?.href ?: manifestById.values.firstOrNull { it.href.endsWith(c) }?.href
                }?.takeIf { p -> manifestByPath[p]?.mediaType?.startsWith("image/") == true }
                ?: manifestById.values.firstOrNull {
                    it.mediaType.startsWith("image/") && (it.id.contains("cover", true) || it.href.substringAfterLast('/').contains("cover", true))
                }?.href
                ?: firstImageOf(z, spine.first())

            // Índice: EPUB 3 (nav) y, si no hay, EPUB 2 (NCX).
            var toc = emptyList<TocEntry>()
            manifestById.values.firstOrNull { "nav" in it.properties.split(' ') }?.let { nav ->
                toc = runCatching { parseNav(z, nav.href) }.getOrDefault(emptyList())
            }
            if (toc.isEmpty()) {
                val ncxId = spineEl?.attr("toc").orEmpty()
                val ncx = manifestById[ncxId] ?: manifestById.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
                if (ncx != null) toc = runCatching { parseNcx(z, ncx.href) }.getOrDefault(emptyList())
            }
            if (toc.isEmpty()) {
                toc = spine.mapIndexed { i, p -> TocEntry("Capítulo ${i + 1}", p, null, 0) }
            }

            val uidRef = opf.selectFirst("package")?.attr("unique-identifier").orEmpty()
            val ids = meta?.select("dc|identifier, identifier").orEmpty()
            val uniqueId = (ids.firstOrNull { it.attr("id") == uidRef } ?: ids.firstOrNull())?.text()?.trim().orEmpty()
            val uuidId = ids.map { it.text().trim() }.firstOrNull { it.startsWith("urn:uuid:", true) }.orEmpty()
            // Páginas de la edición en papel (EPUB 3 page-list o pageList del NCX).
            var pages = emptyList<PageMark>()
            manifestById.values.firstOrNull { "nav" in it.properties.split(' ') }?.let { nav ->
                pages = runCatching { parseNavPages(z, nav.href) }.getOrDefault(emptyList())
            }
            if (pages.isEmpty()) {
                val ncx = manifestById[spineEl?.attr("toc").orEmpty()] ?: manifestById.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
                if (ncx != null) pages = runCatching { parseNcxPages(z, ncx.href) }.getOrDefault(emptyList())
            }
            EpubBook(title.trim(), author, lang, spine, toc, cover, manifestByPath, uniqueId, uuidId, obfuscatedFonts(z), pages)
        }
    }

    /**
     * Detecta DRM: Adobe (rights.xml) o archivos cifrados que no sean solo tipografías ofuscadas
     * (la ofuscación de fuentes es normal y no impide leer el libro).
     */
    private fun checkDrm(z: ZipFile) {
        if (z.getEntry("META-INF/rights.xml") != null) throw DrmException()
        val enc = z.text("META-INF/encryption.xml") ?: return
        val doc = xml(enc)
        val fontAlgos = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")
        doc.select("EncryptedData, enc|EncryptedData").forEach { ed ->
            val algo = ed.selectFirst("EncryptionMethod, enc|EncryptionMethod")?.attr("Algorithm").orEmpty()
            val uri = ed.selectFirst("CipherReference, enc|CipherReference")?.attr("URI").orEmpty().lowercase()
            val isFont = uri.endsWith(".ttf") || uri.endsWith(".otf") || uri.endsWith(".woff") || uri.endsWith(".woff2")
            if (algo !in fontAlgos && !isFont) throw DrmException()
        }
    }

    private fun obfuscatedFonts(z: ZipFile): Map<String, String> = runCatching {
        val enc = z.text("META-INF/encryption.xml") ?: return emptyMap()
        val out = HashMap<String, String>()
        xml(enc).select("EncryptedData, enc|EncryptedData").forEach { ed ->
            val algo = ed.selectFirst("EncryptionMethod, enc|EncryptionMethod")?.attr("Algorithm").orEmpty()
            val uri = ed.selectFirst("CipherReference, enc|CipherReference")?.attr("URI").orEmpty()
            if (uri.isNotEmpty()) out[resolve("", uri)] = algo
        }
        out
    }.getOrDefault(emptyMap())

    /**
     * Desprotege una fuente ofuscada (algoritmos IDPF y Adobe). No es DRM: solo evita que la fuente
     * se copie fuera del libro, y sin desprotegerla el navegador la ignora.
     */
    fun deobfuscate(bytes: ByteArray, algo: String, book: EpubBook): ByteArray {
        val out = bytes.copyOf()
        when (algo) {
            "http://www.idpf.org/2008/embedding" -> {
                val id = book.uniqueId.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
                if (id.isEmpty()) return bytes
                val key = java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray(Charsets.UTF_8))
                for (i in 0 until minOf(1040, out.size)) out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            "http://ns.adobe.com/pdf/enc#RC" -> {
                val hex = book.uuidId.substringAfter("urn:uuid:").replace("-", "")
                if (hex.length < 32) return bytes
                val key = ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
                for (i in 0 until minOf(1024, out.size)) out[i] = (out[i].toInt() xor key[i % 16].toInt()).toByte()
            }
            else -> return bytes
        }
        return out
    }

    private fun parseNav(z: ZipFile, path: String): List<TocEntry> {
        val text = z.text(path) ?: return emptyList()
        val doc = Jsoup.parse(text)
        val base = path.substringBeforeLast('/', "")
        val nav = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" || it.attr("role") == "doc-toc" }
            ?: doc.selectFirst("nav") ?: return emptyList()
        val out = ArrayList<TocEntry>()
        fun walk(ol: Element, level: Int) {
            ol.children().filter { it.tagName() == "li" }.forEach { li ->
                val a = li.children().firstOrNull { it.tagName() == "a" || it.tagName() == "span" }
                val title = a?.text()?.trim().orEmpty()
                val href = a?.attr("href").orEmpty()
                if (title.isNotEmpty() && href.isNotEmpty()) {
                    val (p, frag) = splitHref(base, href)
                    out += TocEntry(title, p, frag, level)
                }
                li.children().firstOrNull { it.tagName() == "ol" || it.tagName() == "ul" }?.let { walk(it, level + 1) }
            }
        }
        nav.children().firstOrNull { it.tagName() == "ol" || it.tagName() == "ul" }?.let { walk(it, 0) }
        return out
    }

    private fun parseNavPages(z: ZipFile, path: String): List<PageMark> {
        val text = z.text(path) ?: return emptyList()
        val doc = Jsoup.parse(text)
        val base = path.substringBeforeLast('/', "")
        val nav = doc.select("nav").firstOrNull { it.attr("epub:type").split(' ').contains("page-list") || it.attr("role") == "doc-pagelist" }
            ?: return emptyList()
        return nav.select("a[href]").mapNotNull { a ->
            val label = a.text().trim()
            if (label.isEmpty()) return@mapNotNull null
            val (p, frag) = splitHref(base, a.attr("href"))
            PageMark(label, p, frag)
        }
    }

    private fun parseNcxPages(z: ZipFile, path: String): List<PageMark> {
        val text = z.text(path) ?: return emptyList()
        val doc = xml(text)
        val base = path.substringBeforeLast('/', "")
        return doc.getAllElements().filter { it.tagName().endsWith("pageTarget") }.mapNotNull { pt ->
            val label = pt.children().firstOrNull { it.tagName().endsWith("navLabel") }?.text()?.trim().orEmpty()
                .ifEmpty { pt.attr("value") }
            val src = pt.children().firstOrNull { it.tagName().endsWith("content") }?.attr("src").orEmpty()
            if (label.isEmpty() || src.isEmpty()) return@mapNotNull null
            val (p, frag) = splitHref(base, src)
            PageMark(label, p, frag)
        }
    }

    private fun parseNcx(z: ZipFile, path: String): List<TocEntry> {
        val text = z.text(path) ?: return emptyList()
        val doc = xml(text)
        val base = path.substringBeforeLast('/', "")
        val out = ArrayList<TocEntry>()
        fun walk(parent: Element, level: Int) {
            parent.children().filter { it.tagName().endsWith("navPoint") }.forEach { np ->
                val title = np.children().firstOrNull { it.tagName().endsWith("navLabel") }?.text()?.trim().orEmpty()
                val src = np.children().firstOrNull { it.tagName().endsWith("content") }?.attr("src").orEmpty()
                if (title.isNotEmpty() && src.isNotEmpty()) {
                    val (p, frag) = splitHref(base, src)
                    out += TocEntry(title, p, frag, level)
                }
                walk(np, level + 1)
            }
        }
        doc.select("navMap, ncx|navMap").firstOrNull()?.let { walk(it, 0) }
        return out
    }

    private fun firstImageOf(z: ZipFile, chapterPath: String): String? = runCatching {
        val doc = Jsoup.parse(z.text(chapterPath) ?: return null)
        val base = chapterPath.substringBeforeLast('/', "")
        val src = doc.selectFirst("img[src]")?.attr("src")
            ?: doc.selectFirst("image")?.let { it.attr("xlink:href").ifEmpty { it.attr("href") } }
            ?: return null
        resolve(base, src).takeIf { z.getEntry(it) != null }
    }.getOrNull()

    /** Texto plano de un capítulo (para calcular su longitud y el tiempo de lectura). */
    fun plainTextLength(z: ZipFile, path: String): Int = runCatching {
        Jsoup.parse(z.text(path) ?: return 0).body()?.text()?.length ?: 0
    }.getOrDefault(0)

    fun splitHref(base: String, href: String): Pair<String, String?> {
        val frag = href.substringAfter('#', "").ifEmpty { null }
        val p = href.substringBefore('#')
        return (if (p.isEmpty()) "" else resolve(base, p)) to frag
    }

    /** Resuelve una ruta relativa ("../Text/cap1.xhtml") respecto a la carpeta [base] dentro del zip. */
    fun resolve(base: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
        if (decoded.startsWith("/")) return normalize(decoded.trimStart('/'))
        return normalize(if (base.isEmpty()) decoded else "$base/$decoded")
    }

    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        path.split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(seg)
            }
        }
        return out.joinToString("/")
    }

    private fun xml(text: String): Document = Jsoup.parse(text, "", Parser.xmlParser())

    private fun Element.firstText(vararg selectors: String): String? =
        selectors.firstNotNullOfOrNull { s -> selectFirst(s)?.text()?.trim()?.takeIf { it.isNotEmpty() } }
}

/** Lee una entrada del zip como texto (UTF-8 por defecto, respetando la codificación declarada si es otra). */
fun ZipFile.text(path: String): String? {
    val e = getEntry(path) ?: return null
    val bytes = getInputStream(e).use { it.readBytes() }
    return decodeText(bytes)
}

fun decodeText(bytes: ByteArray): String {
    val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
    val enc = Regex("""encoding=["']([A-Za-z0-9_\-]+)["']""").find(head)?.groupValues?.get(1)
    val cs = enc?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
    var s = String(bytes, cs)
    if (s.startsWith('﻿')) s = s.substring(1)
    return s
}
