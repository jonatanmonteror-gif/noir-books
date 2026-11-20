package com.noirbooks.app.data

import android.content.Context
import android.net.Uri
import java.text.Normalizer

/**
 * Importa el archivo "My Clippings.txt" del Kindle (carpeta documents del Kindle conectado por USB).
 * Cada subrayado se añade como cita al libro con el mismo título; si no existe, se crea como libro de Kindle.
 */
object Clippings {
    data class Result(val quotes: Int, val books: Int, val newBooks: Int)

    private data class Clip(val title: String, val author: String, val kind: Char, val location: Int, val text: String)

    suspend fun import(ctx: Context, uri: Uri): Result {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return Result(0, 0, 0)
        val text = String(raw, Charsets.UTF_8).replace("﻿", "").replace("\r", "")
        val clips = text.split("==========").mapNotNull { parse(it) }
        if (clips.isEmpty()) return Result(0, 0, 0)

        var quotes = 0
        var newBooks = 0
        val touched = HashSet<String>()
        val byBook = clips.groupBy { key(it.title) }
        for ((k, list) in byBook) {
            val first = list.first()
            var book = Library.visible.firstOrNull { key(it.title) == k }
                ?: Library.visible.firstOrNull { k.length > 8 && (key(it.title).startsWith(k) || k.startsWith(key(it.title))) && key(it.title).length > 8 }
            if (book == null) {
                book = Library.addManual(first.title, first.author, BookFormat.Kindle, 0, ProgressUnit.Location, null, "", "", "", BookStatus.Reading)
                newBooks++
            }
            val id = book.id
            var added = 0
            Library.update(id) { b ->
                val hs = b.highlights.toMutableList()
                var nextId = System.currentTimeMillis()
                for (c in list.sortedBy { it.location }) {
                    if (c.kind == 'n') {
                        // Las notas del Kindle van justo después de su subrayado (misma posición).
                        val i = hs.indexOfLast { it.chapter == -1 && kotlin.math.abs((it.fraction * 1_000_000).toInt() - c.location) <= 3 }
                        if (i >= 0 && hs[i].note.isBlank()) { hs[i] = hs[i].copy(note = c.text); continue }
                        if (hs.any { it.note == c.text }) continue
                    }
                    val t = if (c.kind == 'n') "" else c.text
                    if (t.isNotEmpty() && hs.any { fold(it.text) == fold(t) }) continue
                    hs += Highlight(
                        id = nextId++, chapter = -1, fraction = c.location / 1_000_000f, range = "",
                        text = t.ifEmpty { c.text }, color = 0, note = if (c.kind == 'n') c.text else "",
                        chapterTitle = if (c.location > 0) "Posición ${c.location}" else "",
                    )
                    added++
                }
                b.copy(highlights = hs)
            }
            if (added > 0) touched += id
            quotes += added
        }
        return Result(quotes, touched.size, newBooks)
    }

    private fun parse(block: String): Clip? {
        val lines = block.trim().lines()
        if (lines.size < 3) return null
        val head = lines[0].trim()
        val meta = lines[1].lowercase()
        val kind = when {
            "marcador" in meta || "bookmark" in meta || "signet" in meta -> return null
            "nota" in meta || "note" in meta -> 'n'
            else -> 'h'
        }
        val body = lines.drop(2).joinToString("\n").trim()
        if (body.isEmpty()) return null
        // "Título (Autor)": el autor va en el último paréntesis.
        val m = Regex("^(.*)\\(([^()]*)\\)\\s*$").find(head)
        val title = (m?.groupValues?.get(1) ?: head).trim().ifEmpty { head }
        val author = m?.groupValues?.get(2)?.trim().orEmpty()
        val loc = Regex("(?:posici[oó]n|location|emplacement|posizione)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(lines[1])?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)").find(lines[1])?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return Clip(title, author, kind, loc, body)
    }

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
    private fun key(s: String) = fold(s).replace(Regex("[^a-z0-9]"), "")
}
