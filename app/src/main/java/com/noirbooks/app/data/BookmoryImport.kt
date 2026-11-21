package com.noirbooks.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Importa la exportación de Bookmory: el Excel (.xlsx) de Ajustes › Copia de seguridad › Exportar,
 * o ese mismo Excel guardado como CSV.
 *
 * Bookmory no publica cómo se llaman sus columnas (y cambian con el idioma de la app), así que cada
 * columna se reconoce por su nombre en inglés, español o coreano: título, autor, ISBN, editorial,
 * páginas, estado, valoración, fechas de inicio y fin, reseña y formato. Lo que no se reconoce se ignora.
 * Los libros que ya están en la biblioteca (mismo título y autor) no se duplican.
 */
object BookmoryImport {

    // ---------- Lectura del archivo ----------

    /** Lee .xlsx (es un zip) o CSV y devuelve las filas de la hoja que tenga columna de título. */
    private fun readRows(bytes: ByteArray): List<List<String>> {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) {
            val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
            // Excel en español a veces guarda el CSV con punto y coma
            val firstLine = text.lineSequence().firstOrNull().orEmpty()
            val sep = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
            return parseDelimited(text, sep)
        }
        val files = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.startsWith("xl/") && e.name.endsWith(".xml")) files[e.name] = z.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val sheets = files.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        if (sheets.isEmpty()) throw java.io.IOException("El Excel no tiene hojas")
        val all = sheets.map { sheetRows(files.getValue(it), shared) }
        // La hoja de los libros es la que tiene una columna de título
        return all.firstOrNull { rows -> rows.take(5).any { r -> r.any { colKind(it) == "title" } } } ?: all.first()
    }

    /** CSV con el separador indicado, comillas y saltos de línea dentro de los campos. */
    private fun parseDelimited(text: String, sep: Char): List<List<String>> {
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
                sep -> { row.add(cell.toString()); cell.clear() }
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList() }
                '\r' -> {}
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        android.util.Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(bytes.inputStream(), "UTF-8")
        }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = parser(bytes)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var inRph = false
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> inT = true
                    "rPh" -> inRph = true // lecturas fonéticas (japonés): no son texto
                }
                XmlPullParser.TEXT -> if (inSi && inT && !inRph) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "si" -> { out.add(sb.toString()); inSi = false }
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
                XmlPullParser.END_DOCUMENT -> return out
            }
        }
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else break }
        return n - 1
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = HashMap<Int, String>()
        var col = 0
        var type = ""
        val value = StringBuilder()
        var inValue = false
        val p = parser(bytes)
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = HashMap()
                    "c" -> {
                        col = p.getAttributeValue(null, "r")?.let { colIndex(it) } ?: (row.keys.maxOrNull()?.plus(1) ?: 0)
                        type = p.getAttributeValue(null, "t").orEmpty()
                        value.clear()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        row[col] = if (type == "s") raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty() else raw
                    }
                    "row" -> {
                        val max = row.keys.maxOrNull() ?: -1
                        rows.add(List(max + 1) { row[it].orEmpty() })
                    }
                }
                XmlPullParser.END_DOCUMENT -> return rows
            }
        }
    }

    // ---------- Reconocer columnas ----------

    /** Normaliza: minúsculas, sin acentos y solo letras y números (de cualquier alfabeto). */
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

    private val names: List<Pair<String, List<String>>> = listOf(
        // El orden importa: las fechas antes que «título» para que «fecha de lectura» no se tome por otra cosa
        "start" to listOf("startdate", "started", "datestarted", "startedat", "readingstart", "fechadeinicio", "fechainicio", "inicio", "empezado", "시작일", "읽기시작", "시작날짜", "독서시작일"),
        "end" to listOf("finishdate", "enddate", "finished", "datefinished", "dateread", "completeddate", "finishedat", "completed", "fechadefin", "fechafin", "fechadefinalizacion", "terminado", "fin", "완독일", "종료일", "독서종료일", "다읽은날"),
        "added" to listOf("dateadded", "added", "createdat", "registered", "registrationdate", "fechadeanadido", "fechaderegistro", "anadido", "등록일", "추가일"),
        "subtitle" to listOf("subtitle", "subtitulo", "부제"),
        "title" to listOf("title", "booktitle", "titulo", "titulodellibro", "libro", "제목", "책제목", "도서명", "책이름"),
        "author" to listOf("author", "authors", "autor", "autores", "autora", "저자", "작가", "지은이"),
        "isbn" to listOf("isbn", "isbn13", "isbn10"),
        "publisher" to listOf("publisher", "editorial", "editor", "출판사"),
        "pages" to listOf("pages", "page", "totalpages", "numberofpages", "pagecount", "paginas", "totalpaginas", "numerodepaginas", "페이지", "쪽수", "총페이지", "전체페이지"),
        "status" to listOf("status", "readingstatus", "state", "estado", "estadodelectura", "상태", "독서상태", "읽기상태"),
        "rating" to listOf("rating", "myrating", "rate", "stars", "star", "score", "valoracion", "puntuacion", "calificacion", "estrellas", "nota", "별점", "평점", "점수"),
        "review" to listOf("review", "myreview", "comment", "comments", "memo", "notes", "note", "resena", "opinion", "comentario", "comentarios", "notas", "리뷰", "감상", "한줄평", "서평", "메모"),
        "format" to listOf("type", "format", "booktype", "formato", "tipo", "tipodelibro", "형태", "종류", "책종류"),
        "year" to listOf("year", "publishedyear", "publicationyear", "publishdate", "publisheddate", "ano", "anodepublicacion", "fechadepublicacion", "출판일", "출간일", "발행일"),
    )

    private fun colKind(header: String): String? {
        val h = norm(header)
        if (h.isEmpty()) return null
        names.forEach { (kind, list) -> if (h in list) return kind }
        // Coincidencia parcial solo con nombres largos, para no confundir «fin» o «tipo» dentro de otras palabras
        names.forEach { (kind, list) -> if (list.any { it.length >= 5 && h.contains(it) }) return kind }
        return null
    }

    private fun status(raw: String): BookStatus? {
        val v = norm(raw)
        if (v.isEmpty()) return null
        return when {
            listOf("abandon", "dnf", "drop", "stop", "pause", "abandonado", "dejado", "중단", "포기", "보류").any { it in v } -> BookStatus.Abandoned
            listOf("toread", "want", "wish", "plan", "quiero", "porleer", "pendiente", "읽고싶", "읽을").any { it in v } -> BookStatus.ToRead
            listOf("reading", "leyendo", "current", "progress", "읽는중", "읽고있").any { it in v } -> BookStatus.Reading
            listOf("read", "finish", "complete", "done", "leido", "terminado", "acabado", "완독", "다읽").any { it in v } -> BookStatus.Read
            else -> null
        }
    }

    private val dateFormats = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm", "yyyy/MM/dd", "yyyy.MM.dd HH:mm", "yyyy.MM.dd", "yyyy. MM. dd", "yyyy. M. d",
        "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
    )

    /** Fecha en texto (varios formatos) o número de serie de Excel. 0 si no se entiende. */
    private fun date(raw: String): Long {
        val s = raw.trim().removeSuffix(".")
        if (s.isEmpty()) return 0L
        s.toDoubleOrNull()?.let { d ->
            // Excel cuenta días desde el 30/12/1899
            if (d in 20000.0..80000.0) return ((d - 25569.0) * 86_400_000.0).toLong()
            return 0L
        }
        for (f in dateFormats) {
            val fmt = SimpleDateFormat(f, Locale.US).apply { isLenient = false; timeZone = TimeZone.getDefault() }
            val t = runCatching { fmt.parse(s)?.time }.getOrNull()
            if (t != null && t > 0) return t
        }
        return 0L
    }

    private fun bookKey(title: String, author: String) = norm(title) + "|" + norm(author.substringBefore(','))

    // ---------- Importar ----------

    suspend fun import(ctx: Context, uri: Uri, progress: (Int, Int) -> Unit): GoodreadsImport.Result = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException("No se pudo leer el archivo")
        val all = readRows(bytes).filter { r -> r.any { it.isNotBlank() } }
        // La cabecera es la primera fila (de las 5 primeras) que tenga columna de título
        val headIdx = all.take(5).indexOfFirst { r -> r.any { colKind(it) == "title" } }
        if (headIdx < 0) throw java.io.IOException("No encuentro la columna del título. ¿Es la exportación de Bookmory?")
        val cols = HashMap<String, Int>()
        all[headIdx].forEachIndexed { i, h -> colKind(h)?.let { k -> cols.putIfAbsent(k, i) } }
        val data = all.drop(headIdx + 1)
        if (data.isEmpty()) throw java.io.IOException("El archivo no tiene libros")

        val existing = Library.books.value.map { bookKey(it.title, it.author) }.toHashSet()
        var added = 0
        var skipped = 0
        data.forEachIndexed { i, r ->
            progress(i, data.size)
            fun get(k: String) = cols[k]?.let { c -> r.getOrNull(c)?.trim() }.orEmpty()
            val title = get("title")
            val author = get("author")
            if (title.isBlank()) { skipped++; return@forEachIndexed }
            if (!existing.add(bookKey(title, author))) { skipped++; return@forEachIndexed }

            val code = get("isbn").filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
                .let { if (it.length == 10 || it.length == 13) it else "" }
            val fmt = norm(get("format"))
            val format = when {
                listOf("ebook", "kindle", "digital", "electron", "전자").any { it in fmt } -> BookFormat.Kindle
                listOf("audio", "오디오").any { it in fmt } -> BookFormat.Audio
                else -> BookFormat.Paper
            }
            val start = date(get("start"))
            val end = date(get("end"))
            val st = status(get("status")) ?: when {
                end > 0 -> BookStatus.Read
                start > 0 -> BookStatus.Reading
                else -> BookStatus.ToRead
            }
            val pages = get("pages").substringBefore('.').filter(Char::isDigit).toIntOrNull()?.coerceAtMost(20000) ?: 0
            val year = Regex("(1[5-9]|20)\\d{2}").find(get("year"))?.value.orEmpty()
            var rating = get("rating").replace(',', '.').toFloatOrNull() ?: 0f
            if (rating > 5f) rating /= 2f // escala de 10
            rating = (kotlin.math.round(rating * 2f) / 2f).coerceIn(0f, 5f)
            val cover = if (code.isNotEmpty()) "https://covers.openlibrary.org/b/isbn/$code-L.jpg?default=false" else null
            val fullTitle = get("subtitle").let { sub -> if (sub.isNotBlank() && !title.contains(sub, true)) "$title: $sub" else title }

            val b = runCatching {
                Library.addManual(fullTitle, author, format, pages,
                    if (format == BookFormat.Paper && pages > 0) ProgressUnit.Page else ProgressUnit.Percent,
                    cover, code, get("publisher"), year, st)
            }.getOrNull() ?: run { skipped++; return@forEachIndexed }

            val addedAt = date(get("added")).takeIf { it > 0 } ?: start.takeIf { it > 0 }
            Library.update(b.id) { cur ->
                val readings = when (st) {
                    BookStatus.Read -> { val e = if (end > 0) end else (start.takeIf { it > 0 } ?: cur.addedAt); listOf(Reading(if (start > 0) start else e, e)) }
                    BookStatus.Reading -> listOf(Reading(if (start > 0) start else cur.addedAt))
                    BookStatus.Abandoned -> if (start > 0) listOf(Reading(start, if (end > 0) end else start)) else emptyList()
                    else -> emptyList()
                }
                cur.copy(
                    addedAt = addedAt ?: cur.addedAt,
                    rating = rating,
                    review = get("review"),
                    readings = readings,
                    lastOpened = if (st == BookStatus.Reading) (if (start > 0) start else cur.lastOpened) else cur.lastOpened,
                )
            }
            added++
        }
        progress(data.size, data.size)
        GoodreadsImport.Result(added, skipped, data.size)
    }
}
