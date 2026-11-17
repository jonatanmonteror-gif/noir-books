package com.noirbooks.app.data

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Sirve el contenido del EPUB al WebView desde https://book.local/… sin descomprimir nada en disco.
 * A cada capítulo se le añade el maquetador de páginas (assets/reader). Bloquea cualquier otra dirección.
 */
class BookServer(ctx: Context, file: File, private val book: EpubBook) : Closeable {
    private val app = ctx.applicationContext
    private val zip = ZipFile(file)

    fun urlOf(path: String): String = "https://$HOST/" + path.split('/').joinToString("/") { Uri.encode(it) }

    /** Ruta dentro del EPUB a partir de una dirección https://book.local/… (sin '#'). */
    fun pathOf(url: String): Pair<String, String?>? {
        val u = Uri.parse(url)
        if (u.host != HOST) return null
        val p = (u.path ?: "").trimStart('/')
        return decode(p) to u.fragment
    }

    fun handle(url: Uri): WebResourceResponse {
        if (url.host != HOST) return empty(403)
        val path = decode((url.path ?: "").trimStart('/'))
        return runCatching {
            if (path.startsWith("__noir/")) return asset(path.removePrefix("__noir/"))
            val bytes = synchronized(zip) {
                val e = zip.getEntry(path) ?: return empty(404)
                zip.getInputStream(e).use { it.readBytes() }
            }
            val mime = book.manifest[path]?.mediaType?.takeIf { it.isNotBlank() } ?: mimeOf(path)
            if (mime == "application/xhtml+xml" || mime == "text/html") {
                val html = inject(decodeText(bytes))
                WebResourceResponse(mime, "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
            } else {
                WebResourceResponse(mime, null, ByteArrayInputStream(bytes))
            }
        }.getOrElse {
            ErrorLog.note("Leer contenido del libro", it)
            empty(500)
        }
    }

    private fun asset(name: String): WebResourceResponse {
        val mime = mimeOf(name)
        val stream = app.assets.open("reader/$name")
        return WebResourceResponse(mime, if (mime.startsWith("text/") || mime.endsWith("javascript")) "utf-8" else null, stream).apply {
            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=86400")
        }
    }

    private fun inject(html: String): String {
        val tags = HEAD_TAGS
        // Quita las declaraciones de codificación distintas de UTF-8: el texto ya se sirve en UTF-8.
        var s = html.replaceFirst(Regex("""(<\?xml[^>]*encoding=["'])[^"']+(["'])"""), "$1utf-8$2")
        val head = Regex("""<head(\s[^>]*)?>""", RegexOption.IGNORE_CASE).find(s)
        if (head != null) return s.substring(0, head.range.last + 1) + tags + s.substring(head.range.last + 1)
        val htmlTag = Regex("""<html(\s[^>]*)?>""", RegexOption.IGNORE_CASE).find(s)
        if (htmlTag != null) return s.substring(0, htmlTag.range.last + 1) + "<head>$tags</head>" + s.substring(htmlTag.range.last + 1)
        s = "<head>$tags</head>$s"
        return s
    }

    override fun close() {
        runCatching { synchronized(zip) { zip.close() } }
    }

    companion object {
        const val HOST = "book.local"

        private const val HEAD_TAGS =
            """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>""" +
                """<style type="text/css">html{visibility:hidden}</style>""" +
                """<link rel="stylesheet" type="text/css" href="https://book.local/__noir/fonts.css"/>""" +
                """<script type="text/javascript" src="https://book.local/__noir/reader.js"></script>"""

        fun decode(p: String): String = runCatching { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") }.getOrDefault(p)

        fun empty(code: Int) = WebResourceResponse("text/plain", "utf-8", code, if (code == 404) "Not Found" else "Error",
            emptyMap(), ByteArrayInputStream(ByteArray(0)))

        fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "xhtml", "xht", "xml" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
