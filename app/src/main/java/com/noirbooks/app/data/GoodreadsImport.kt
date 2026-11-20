package com.noirbooks.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Importa la exportación de Goodreads (Mis libros → Importar y exportar → Exportar biblioteca: un .csv).
 * Se trae título, autor, ISBN, páginas, editorial, año, estado (leído, leyendo, pendiente), valoración,
 * fechas de añadido y de lectura, veces leído y la opinión. La portada se busca por ISBN en Open Library.
 * Los libros que ya están en la biblioteca (mismo título y autor) no se duplican.
 */
object GoodreadsImport {
    data class Result(val added: Int, val skipped: Int, val total: Int)

    /** Lector de CSV con comillas, comas y saltos de línea dentro de los campos. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(cell.toString()); cell.clear() }
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList() }
                '\r' -> {}
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private fun key(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]"), "")

    /** Goodreads guarda los ISBN como ="9788427..." */
    private fun isbn(raw: String) = raw.filter { it.isDigit() || it == 'X' }

    suspend fun import(ctx: Context, uri: Uri, progress: (Int, Int) -> Unit): Result = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }?.removePrefix("﻿")
            ?: throw java.io.IOException("No se pudo leer el archivo")
        val rows = parseCsv(text).filter { r -> r.any { it.isNotBlank() } }
        if (rows.size < 2) throw java.io.IOException("El archivo está vacío")
        val head = rows[0].map { it.trim().lowercase() }
        fun col(name: String) = head.indexOf(name.lowercase())
        val cTitle = col("Title"); val cAuthor = col("Author")
        if (cTitle < 0 || cAuthor < 0) throw java.io.IOException("Este archivo no es una exportación de Goodreads")
        val cIsbn = col("ISBN"); val cIsbn13 = col("ISBN13"); val cRating = col("My Rating")
        val cPublisher = col("Publisher"); val cBinding = col("Binding"); val cPages = col("Number of Pages")
        val cYear = col("Year Published"); val cOrigYear = col("Original Publication Year")
        val cRead = col("Date Read"); val cAdded = col("Date Added"); val cShelf = col("Exclusive Shelf")
        val cReview = col("My Review"); val cCount = col("Read Count")
        val date = SimpleDateFormat("yyyy/MM/dd", Locale.US)

        val existing = Library.books.value.map { key(it.title) + "|" + key(it.author.substringBefore(',')) }.toHashSet()
        var added = 0
        var skipped = 0
        val data = rows.drop(1)
        data.forEachIndexed { i, r ->
            progress(i, data.size)
            fun get(c: Int) = if (c in r.indices) r[c].trim() else ""
            val title = get(cTitle)
            val author = get(cAuthor)
            if (title.isBlank()) { skipped++; return@forEachIndexed }
            val k = key(title) + "|" + key(author.substringBefore(','))
            if (!existing.add(k)) { skipped++; return@forEachIndexed }

            val code = isbn(get(cIsbn13)).ifEmpty { isbn(get(cIsbn)) }
            val binding = get(cBinding).lowercase()
            val format = when {
                "kindle" in binding || "ebook" in binding -> BookFormat.Kindle
                "audio" in binding -> BookFormat.Audio
                else -> BookFormat.Paper
            }
            val status = when (get(cShelf)) {
                "read" -> BookStatus.Read
                "currently-reading" -> BookStatus.Reading
                else -> BookStatus.ToRead
            }
            val pages = get(cPages).toIntOrNull() ?: 0
            val year = get(cOrigYear).ifEmpty { get(cYear) }
            val cover = if (code.isNotEmpty()) "https://covers.openlibrary.org/b/isbn/$code-L.jpg?default=false" else null
            val b = runCatching {
                Library.addManual(title, author, format, pages, if (format == BookFormat.Paper && pages > 0) ProgressUnit.Page else ProgressUnit.Percent,
                    cover, code, get(cPublisher), year, status)
            }.getOrNull() ?: run { skipped++; return@forEachIndexed }

            val addedAt = runCatching { date.parse(get(cAdded))?.time }.getOrNull()
            val readAt = runCatching { date.parse(get(cRead))?.time }.getOrNull()
            val times = (get(cCount).toIntOrNull() ?: 0).coerceAtLeast(if (status == BookStatus.Read) 1 else 0)
            val rating = (get(cRating).toIntOrNull() ?: 0).toFloat()
            Library.update(b.id) { cur ->
                val readings = when {
                    status == BookStatus.Read -> {
                        val end = readAt ?: addedAt ?: cur.addedAt
                        // Relecturas anteriores sin fecha: se apuntan con la misma fecha de fin.
                        List(times) { Reading(end, end) }
                    }
                    else -> cur.readings
                }
                cur.copy(
                    addedAt = addedAt ?: cur.addedAt,
                    rating = rating,
                    review = get(cReview).replace("<br/>", "\n").replace("<br />", "\n").replace(Regex("<[^>]+>"), ""),
                    readings = readings,
                )
            }
            added++
        }
        progress(data.size, data.size)
        Result(added, skipped, data.size)
    }
}
