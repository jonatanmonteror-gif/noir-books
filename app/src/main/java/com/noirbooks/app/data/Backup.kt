package com.noirbooks.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copias de seguridad de todo Noir Books en un solo .zip:
 *  - books/library.json, books/vocabulary.json, books/<id>/cover.jpg y, si se elige, books/<id>/book.epub
 *  - stats.json y sessions.json (diario de lectura)
 *  - prefs.json (ajustes)
 *  - manifest.json (qué es y de qué versión)
 * Restaurar sustituye todos los datos del móvil por los de la copia y reinicia la app.
 */
object Backup {
    private const val KIND = "noirbooks-backup"

    /** Mientras se restaura no se guarda nada de memoria, para no pisar los datos recién restaurados. */
    @Volatile var restoring = false

    data class Info(val createdAt: Long, val appVersion: String, val books: Int, val withBooks: Boolean)

    fun suggestedName(prefix: String = "NoirBooks-copia"): String =
        "$prefix-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".zip"

    /** Escribe la copia en [out]. Con [withBooks] incluye los EPUB (ocupa más). */
    suspend fun write(ctx: Context, out: OutputStream, withBooks: Boolean) = withContext(Dispatchers.IO) {
        Stats.flush()
        val root = Library.rootDir
        val files = ctx.filesDir
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, f: File) {
                if (!f.isFile) return
                zip.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
            fun putText(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val books = Library.books.value
            putText("manifest.json", JSONObject()
                .put("kind", KIND).put("format", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("appVersion", com.noirbooks.app.BuildConfig.VERSION_NAME)
                .put("books", books.size).put("withBooks", withBooks).toString())
            put("books/library.json", File(root, "library.json"))
            put("books/vocabulary.json", File(root, "vocabulary.json"))
            books.forEach { b ->
                put("books/${b.id}/cover.jpg", Library.coverOf(b.id))
                if (withBooks && b.isEpub) put("books/${b.id}/book.epub", Library.epubOf(b.id))
            }
            put("stats.json", File(files, "stats.json"))
            put("sessions.json", File(files, "sessions.json"))
            putText("prefs.json", Prefs.exportJson().toString())
        }
    }

    /** Lee solo la cabecera de una copia, para mostrarla antes de restaurar. */
    suspend fun info(input: InputStream): Info? = withContext(Dispatchers.IO) {
        runCatching {
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == "manifest.json") {
                        val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        if (o.optString("kind") != KIND) return@runCatching null
                        return@runCatching Info(o.optLong("createdAt"), o.optString("appVersion"), o.optInt("books"), o.optBoolean("withBooks"))
                    }
                    e = zip.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Restaura una copia. Primero se descomprime aparte y se comprueba; solo si está bien se sustituyen los datos.
     * Los EPUB que ya hay en el móvil se conservan si la copia no los trae.
     * Después hay que reiniciar la app ([restart]).
     */
    suspend fun restore(ctx: Context, input: InputStream) = withContext(Dispatchers.IO) {
        val staging = File(ctx.cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            Stats.flush()
            var ok = false
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val target = File(staging, e.name).canonicalFile
                    // Seguridad: ningún archivo puede salir de la carpeta temporal.
                    if (!target.path.startsWith(staging.canonicalPath + File.separator)) throw IOException("Copia no válida")
                    if (!e.isDirectory) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it, 64 * 1024) }
                        if (e.name == "manifest.json") ok = JSONObject(target.readText()).optString("kind") == KIND
                    }
                    e = zip.nextEntry
                }
            }
            if (!ok || !File(staging, "books/library.json").isFile) throw IOException("Este archivo no es una copia de Noir Books")
            JSONObject(File(staging, "books/library.json").readText()) // comprueba que se puede leer
            restoring = true

            // Sustituir: libros (conservando los EPUB que ya estén y la copia no traiga), diario y ajustes.
            val root = Library.rootDir
            val newBooks = File(staging, "books")
            root.listFiles()?.forEach { old ->
                if (old.isDirectory) {
                    val epub = File(old, "book.epub")
                    val incoming = File(newBooks, old.name)
                    if (epub.isFile && !File(incoming, "book.epub").exists()) {
                        incoming.mkdirs()
                        epub.copyTo(File(incoming, "book.epub"), overwrite = true)
                    }
                }
            }
            root.deleteRecursively()
            if (!newBooks.renameTo(root)) { newBooks.copyRecursively(root, overwrite = true) }
            for (name in listOf("stats.json", "sessions.json")) {
                val src = File(staging, name)
                val dst = File(ctx.filesDir, name)
                if (src.isFile) src.copyTo(dst, overwrite = true) else dst.delete()
            }
            File(staging, "prefs.json").takeIf { it.isFile }?.let { Prefs.importJson(JSONObject(it.readText())) }
        } catch (e: Exception) {
            restoring = false
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Reinicia la app para cargar los datos restaurados. */
    fun restart(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    // ---- Exportar en formatos abiertos (CSV + JSON) ----

    private fun csv(vararg cells: Any?): String = cells.joinToString(";") { c ->
        val s = c?.toString().orEmpty()
        if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Exporta libros, lecturas, citas y palabras en CSV (se abren con Excel o Google Sheets) dentro de un .zip. */
    suspend fun exportData(out: OutputStream) = withContext(Dispatchers.IO) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val books = Library.books.value.filter { !it.inTrash }
        val byId = books.associateBy { it.id }
        ZipOutputStream(out.buffered()).use { zip ->
            fun putText(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                // BOM para que Excel lea bien las tildes.
                zip.write(("﻿" + text).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            putText("libros.csv", buildString {
                appendLine(csv("Título", "Autor", "Formato", "Estado", "Valoración", "Progreso %", "Páginas", "ISBN", "Editorial", "Año", "Añadido", "Terminado", "Veces leído", "Minutos leídos", "Opinión", "Estanterías"))
                books.forEach { b ->
                    appendLine(csv(b.title, b.author, b.format.label, b.status.label, if (b.rating > 0) b.rating else "",
                        "%.1f".format(Locale.US, b.progress * 100), if (b.pages > 0) b.pages else "", b.isbn, b.publisher, b.year,
                        day.format(Date(b.addedAt)), b.readings.lastOrNull { it.end > 0 }?.let { day.format(Date(it.end)) } ?: "",
                        b.timesRead, Stats.secondsForBook(b.id) / 60, b.review, b.shelves.joinToString(", ")))
                }
            })
            putText("lecturas.csv", buildString {
                appendLine(csv("Libro", "Autor", "Inicio", "Fin", "Minutos", "Páginas", "Origen"))
                Stats.sessions().forEach { s ->
                    val b = byId[s.bookId]
                    appendLine(csv(b?.title ?: "", b?.author ?: "", time.format(Date(s.start)), time.format(Date(s.end)),
                        s.seconds / 60, s.pages, if (s.manual) "Apuntada a mano" else "Leída en la app"))
                }
            })
            putText("lectura-por-dia.csv", buildString {
                appendLine(csv("Día", "Libro", "Minutos", "Páginas"))
                Stats.all().sortedBy { it.date }.forEach { d ->
                    appendLine(csv(d.date, byId[d.bookId]?.title ?: "", d.seconds / 60, d.pages))
                }
            })
            putText("citas.csv", buildString {
                appendLine(csv("Libro", "Autor", "Capítulo", "Cita", "Nota", "Color"))
                books.forEach { b ->
                    b.highlights.forEach { h ->
                        appendLine(csv(b.title, b.author, h.chapterTitle, h.text, h.note, HighlightColor.of(h.color).meaning))
                    }
                }
            })
            putText("palabras.csv", buildString {
                appendLine(csv("Palabra", "Frase", "Libro", "Fecha"))
                Library.vocabulary.value.forEach { w -> appendLine(csv(w.word, w.sentence, w.bookTitle, day.format(Date(w.createdAt)))) }
            })
            zip.putNextEntry(ZipEntry("biblioteca.json"))
            File(Library.rootDir, "library.json").takeIf { it.isFile }?.inputStream()?.use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    suspend fun writeTo(ctx: Context, uri: Uri, block: suspend (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        val os = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No se pudo crear el archivo")
        os.use { block(it) }
    }
}
