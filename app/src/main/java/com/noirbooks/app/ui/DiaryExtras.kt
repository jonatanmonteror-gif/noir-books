package com.noirbooks.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.noirbooks.app.R
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.DayRead
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.durationLabel
import java.io.File
import java.util.Calendar

val MONTH_NAMES = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

private fun dayKey(c: Calendar) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

private fun finishedAt(b: Book): Long = b.readings.lastOrNull { it.end > 0 }?.end ?: 0L

// ---------------------------------------------------------------------------------------------
// Meta diaria
// ---------------------------------------------------------------------------------------------

/** «Hoy: 18 de 30 min». Tocar para cambiar la meta (0 = sin meta diaria). */
@Composable
fun DailyGoalCard(all: List<DayRead>) {
    var goal by remember { mutableIntStateOf(Prefs.dailyGoalMin) }
    var edit by remember { mutableStateOf(false) }
    val today = Stats.today()
    val mins = (all.filter { it.date == today }.sumOf { it.seconds } / 60).toInt()
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface)
            .clickable { edit = true }.padding(18.dp),
    ) {
        Text("HOY", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$mins", fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = NB.text)
            Text(if (goal > 0) " de $goal min" else " min leídos · toca para ponerte una meta diaria",
                fontFamily = Inter, fontSize = 14.sp, color = NB.text2, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (goal > 0) {
            Spacer(Modifier.height(8.dp))
            ThinProgress((mins.toFloat() / goal).coerceAtMost(1f), Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(if (mins >= goal) "Meta de hoy cumplida" else "Te quedan ${goal - mins} min para la meta de hoy",
                fontSize = 13.sp, color = NB.text3)
        }
    }
    if (edit) {
        var v by remember { mutableIntStateOf(goal) }
        AlertDialog(
            onDismissRequest = { edit = false }, containerColor = NB.surface2,
            title = { Text("Meta diaria", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        PillButton("−") { v = (v - 5).coerceAtLeast(0) }
                        Text(if (v == 0) "Sin meta" else "$v min", fontFamily = InterDisplay, fontSize = 28.sp, color = NB.text,
                            textAlign = TextAlign.Center, modifier = Modifier.width(140.dp))
                        PillButton("+") { v = (v + 5).coerceAtMost(600) }
                    }
                    Text("minutos de lectura al día", color = NB.text3, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { Prefs.dailyGoalMin = v; goal = v; edit = false }) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancelar", color = NB.text2) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Vista anual del calendario
// ---------------------------------------------------------------------------------------------

/** Los 12 meses con un cuadrito por día; cuanto más leíste, más oscuro. Tocar un mes lo abre. */
@Composable
fun YearCalendar(year: Int, days: Map<String, List<DayRead>>, onMonth: (Int) -> Unit) {
    val today = Stats.today()
    val totalDays = days.keys.count { it.startsWith("$year-") }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("$totalDays ${if (totalDays == 1) "día" else "días"} con lectura en $year", color = NB.text2, fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        (0 until 12).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { m ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onMonth(m) }.padding(4.dp)) {
                        Text(MONTH_NAMES[m], color = NB.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        val cal = Calendar.getInstance().apply { clear(); set(year, m, 1) }
                        val first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                        val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val rows = (first + dim + 6) / 7
                        for (r in 0 until rows) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 2.dp)) {
                                for (c in 0 until 7) {
                                    val d = r * 7 + c - first + 1
                                    val key = "%04d-%02d-%02d".format(year, m + 1, d)
                                    val secs = days[key]?.sumOf { it.seconds } ?: 0L
                                    val color = when {
                                        d !in 1..dim -> androidx.compose.ui.graphics.Color.Transparent
                                        secs <= 0 -> NB.line.copy(alpha = if (key > today) 0.35f else 0.8f)
                                        secs < 15 * 60 -> NB.text.copy(alpha = 0.35f)
                                        secs < 45 * 60 -> NB.text.copy(alpha = 0.65f)
                                        else -> NB.text
                                    }
                                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(color))
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Text("Menos", color = NB.text3, fontSize = 11.sp)
            listOf(0.8f to NB.line, 0.35f to NB.text, 0.65f to NB.text, 1f to NB.text).forEachIndexed { i, (a, c) ->
                Box(Modifier.padding(horizontal = 2.dp).width(10.dp).height(10.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i == 0) c.copy(alpha = a) else c.copy(alpha = a)))
            }
            Text("Más", color = NB.text3, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Informe semanal y mensual
// ---------------------------------------------------------------------------------------------

data class Report(
    val title: String, val range: String, val seconds: Long, val pages: Int, val days: Int,
    val books: List<Book>, val finished: List<Book>, val prevSeconds: Long, val prevLabel: String,
)

fun buildReport(weekly: Boolean, all: List<DayRead>, books: List<Book>): Report {
    val byId = books.associateBy { it.id }
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (weekly) { firstDayOfWeek = Calendar.MONDAY; while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1) }
        else set(Calendar.DAY_OF_MONTH, 1)
    }
    val prevStart = (start.clone() as Calendar).apply { if (weekly) add(Calendar.DAY_OF_MONTH, -7) else add(Calendar.MONTH, -1) }
    val from = dayKey(start)
    val prevFrom = dayKey(prevStart)
    val reads = all.filter { it.date >= from }
    val prev = all.filter { it.date >= prevFrom && it.date < from }
    val finished = books.filter { !it.inTrash && it.status == BookStatus.Read && finishedAt(it) >= start.timeInMillis }
    val df = java.text.SimpleDateFormat("d MMM", java.util.Locale("es", "ES"))
    return Report(
        title = if (weekly) "Mi semana" else "Mi mes",
        range = if (weekly) "Desde el lunes ${df.format(start.time)}" else "${MONTH_NAMES[start.get(Calendar.MONTH)]} ${start.get(Calendar.YEAR)}",
        seconds = reads.sumOf { it.seconds }, pages = reads.sumOf { it.pages }, days = reads.map { it.date }.distinct().size,
        books = reads.groupBy { it.bookId }.entries.sortedByDescending { e -> e.value.sumOf { it.seconds } }.mapNotNull { byId[it.key] },
        finished = finished,
        prevSeconds = prev.sumOf { it.seconds }, prevLabel = if (weekly) "la semana pasada" else "el mes pasado",
    )
}

private fun compareLine(r: Report): String = when {
    r.prevSeconds <= 0L && r.seconds <= 0L -> "Aún no hay lecturas guardadas"
    r.prevSeconds <= 0L -> "Más que ${r.prevLabel}"
    else -> {
        val pct = ((r.seconds - r.prevSeconds) * 100 / r.prevSeconds).toInt()
        when {
            pct > 0 -> "$pct % más que ${r.prevLabel}"
            pct < 0 -> "${-pct} % menos que ${r.prevLabel}"
            else -> "Igual que ${r.prevLabel}"
        }
    }
}

/** Tarjeta con el informe de esta semana o de este mes, y botón para compartirlo como imagen. */
@Composable
fun ReportCard(all: List<DayRead>, books: List<Book>) {
    val ctx = LocalContext.current
    var weekly by remember { mutableStateOf(true) }
    val r = remember(all, books, weekly) { buildReport(weekly, all, books) }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("INFORME", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Chip("Semana", weekly) { weekly = true }
            Spacer(Modifier.width(6.dp))
            Chip("Mes", !weekly) { weekly = false }
        }
        Spacer(Modifier.height(10.dp))
        Text(r.title, fontFamily = Playfair, fontSize = 22.sp, color = NB.text)
        Text(r.range, color = NB.text3, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportNumber(if (r.seconds > 0) durationLabel(r.seconds) else "0 min", "leyendo")
            ReportNumber("${r.pages}", "páginas")
            ReportNumber("${r.days}", if (r.days == 1) "día" else "días")
            ReportNumber("${r.finished.size}", if (r.finished.size == 1) "terminado" else "terminados")
        }
        Spacer(Modifier.height(8.dp))
        Text(compareLine(r), color = NB.text2, fontSize = 13.sp)
        if (r.books.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.books.take(5).forEach { b -> BookCover(b, Modifier.width(44.dp), titleSize = 7) }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton("Compartir como imagen") {
            shareBitmap(ctx, renderReport(ctx, r), "informe", "${r.title} en Noir Books")
        }
    }
}

@Composable
private fun ReportNumber(value: String, label: String) {
    Column {
        Text(value, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = NB.text)
        Text(label, color = NB.text3, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Imágenes para compartir (mes del calendario e informes)
// ---------------------------------------------------------------------------------------------

private class Fonts(ctx: Context) {
    val serif: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair_regular) }.getOrNull() ?: Typeface.SERIF
    val sans: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_regular) }.getOrNull() ?: Typeface.SANS_SERIF
    val bold: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_semi_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
}

private const val BG = 0xFF0B0B0C.toInt()
private const val FG = 0xFFF2F2F2.toInt()
private const val MUTED = 0xFF9A9AA0.toInt()
private const val CELL = 0xFF1E1E21.toInt()

private fun paint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color }

private fun loadCover(b: Book?, w: Int): Bitmap? {
    if (b == null || !b.hasCover) return null
    val f = Library.coverOf(b.id)
    if (!f.exists()) return null
    return runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= w) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()
}

private fun drawCover(c: Canvas, bmp: Bitmap?, r: RectF, fallback: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (bmp == null) { p.color = fallback; c.drawRoundRect(r, 10f, 10f, p); return }
    // Recorte centrado (como "cover")
    val sr = bmp.width.toFloat() / bmp.height
    val dr = r.width() / r.height()
    val src = if (sr > dr) {
        val w = (bmp.height * dr).toInt(); val x = (bmp.width - w) / 2
        android.graphics.Rect(x, 0, x + w, bmp.height)
    } else {
        val h = (bmp.width / dr).toInt(); val y = (bmp.height - h) / 2
        android.graphics.Rect(0, y, bmp.width, y + h)
    }
    c.save()
    val path = android.graphics.Path().apply { addRoundRect(r, 10f, 10f, android.graphics.Path.Direction.CW) }
    c.clipPath(path)
    c.drawBitmap(bmp, src, r, p)
    c.restore()
}

private fun brand(c: Canvas, f: Fonts, w: Int, h: Int) {
    val p = paint(f.serif, 30f, 0xFF78787E.toInt())
    val label = "Noir Books"
    c.drawText(label, w - 80f - p.measureText(label), h - 60f, p)
}

/** El mes del calendario con la portada del libro de cada día leído. */
fun renderMonth(ctx: Context, year: Int, month: Int, days: Map<String, List<DayRead>>, books: List<Book>): Bitmap {
    val f = Fonts(ctx)
    val w = 1080; val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(BG)
    val byId = books.associateBy { it.id }
    c.drawText("${MONTH_NAMES[month]} $year", 80f, 150f, paint(f.serif, 72f, FG))

    val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    val first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val gap = 12f
    val left = 80f
    val cw = (w - 2 * left - 6 * gap) / 7
    val ch = cw * 1.42f
    val wd = paint(f.sans, 26f, MUTED)
    listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, s ->
        c.drawText(s, left + i * (cw + gap) + cw / 2 - wd.measureText(s) / 2, 225f, wd)
    }
    var top = 250f
    val rows = (first + dim + 6) / 7
    val numP = paint(f.bold, 22f, FG)
    val cellP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CELL }
    var secs = 0L; var pages = 0; var readDays = 0
    val covers = HashMap<String, Bitmap?>()
    for (r in 0 until rows) {
        for (col in 0 until 7) {
            val d = r * 7 + col - first + 1
            if (d !in 1..dim) continue
            val rect = RectF(left + col * (cw + gap), top, left + col * (cw + gap) + cw, top + ch)
            val key = "%04d-%02d-%02d".format(year, month + 1, d)
            val reads = days[key]
            if (reads.isNullOrEmpty()) {
                c.drawRoundRect(rect, 10f, 10f, cellP)
                c.drawText("$d", rect.left + 10f, rect.top + 30f, paint(f.sans, 22f, MUTED))
            } else {
                secs += reads.sumOf { it.seconds }; pages += reads.sumOf { it.pages }; readDays++
                val main = reads.maxByOrNull { it.seconds }?.bookId
                val cover = covers.getOrPut(main ?: "") { loadCover(main?.let { byId[it] }, cw.toInt()) }
                drawCover(c, cover, rect, 0xFF55555A.toInt())
                val badge = RectF(rect.left + 6f, rect.top + 6f, rect.left + 44f, rect.top + 38f)
                c.drawRoundRect(badge, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() })
                c.drawText("$d", badge.centerX() - numP.measureText("$d") / 2, badge.bottom - 8f, numP)
            }
        }
        top += ch + gap
    }
    covers.values.forEach { it?.recycle() }
    val finished = books.count { b ->
        b.status == BookStatus.Read && finishedAt(b).let { t -> t > 0 && Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month } }
    }
    val line = listOf(
        if (secs > 0) durationLabel(secs) else "0 min", "$pages páginas",
        "$readDays ${if (readDays == 1) "día" else "días"}", "$finished ${if (finished == 1) "terminado" else "terminados"}",
    ).joinToString("  ·  ")
    c.drawText(line, 80f, h - 130f, paint(f.sans, 32f, FG))
    brand(c, f, w, h)
    return bmp
}

/** El informe de la semana o del mes como imagen. */
fun renderReport(ctx: Context, r: Report): Bitmap {
    val f = Fonts(ctx)
    val w = 1080; val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(BG)
    c.drawText(r.title, 80f, 170f, paint(f.serif, 88f, FG))
    c.drawText(r.range, 80f, 230f, paint(f.sans, 34f, MUTED))
    val nums = listOf(
        (if (r.seconds > 0) durationLabel(r.seconds) else "0 min") to "leyendo",
        "${r.pages}" to "páginas",
        "${r.days}" to if (r.days == 1) "día con lectura" else "días con lectura",
        "${r.finished.size}" to if (r.finished.size == 1) "libro terminado" else "libros terminados",
    )
    nums.forEachIndexed { i, (v, l) ->
        val x = 80f + (i % 2) * 470f
        val y = 380f + (i / 2) * 190f
        c.drawText(v, x, y, paint(f.bold, 76f, FG))
        c.drawText(l, x, y + 50f, paint(f.sans, 32f, MUTED))
    }
    c.drawText(compareLine(r), 80f, 790f, paint(f.sans, 34f, FG))
    val list = r.books.take(5)
    if (list.isNotEmpty()) {
        val cw = 160f; val ch = 240f; val gap = 22f
        list.forEachIndexed { i, b ->
            val rect = RectF(80f + i * (cw + gap), 880f, 80f + i * (cw + gap) + cw, 880f + ch)
            val cover = loadCover(b, cw.toInt())
            drawCover(c, cover, rect, 0xFF333338.toInt())
            cover?.recycle()
        }
    }
    brand(c, f, w, h)
    return bmp
}

/** Guarda la imagen y abre «Compartir». */
fun shareBitmap(ctx: Context, bmp: Bitmap, name: String, text: String) {
    runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$name-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { ErrorLog.note("Compartir imagen", it); AppState.toast("No se pudo crear la imagen") }
}

// ---------------------------------------------------------------------------------------------
// Género
// ---------------------------------------------------------------------------------------------

/** Elegir el género de un libro (o escribir otro). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GenreDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(if (current.isNotBlank() && current !in com.noirbooks.app.data.Genres.ALL) current else "") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text("Género", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.noirbooks.app.data.Genres.ALL.forEach { g -> Chip(g, g == current) { onPick(g) } }
                }
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.OutlinedTextField(
                    custom, { custom = it.take(40) }, singleLine = true,
                    placeholder = { Text("Otro género", color = NB.text3) },
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text,
                        focusedTextColor = NB.text, unfocusedTextColor = NB.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (custom.isNotBlank()) onPick(custom.trim()) else onDismiss() }) {
                Text(if (custom.isNotBlank()) "Guardar" else "Cerrar", color = NB.text, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { if (current.isNotBlank()) TextButton(onClick = { onPick("") }) { Text("Quitar género", color = NB.text2) } },
    )
}


/** Tiempo de lectura del año por género, con los libros terminados de cada uno. */
@Composable
fun GenreStats(yearReads: List<DayRead>, books: List<Book>, year: Int) {
    val byId = books.associateBy { it.id }
    val rows = yearReads.groupBy { byId[it.bookId]?.genre?.ifBlank { null } ?: "Sin género" }
        .mapValues { (_, l) -> l.sumOf { it.seconds } }
        .entries.sortedByDescending { it.value }
    if (rows.isEmpty()) return
    val max = rows.first().value.coerceAtLeast(1)
    val finishedBy = books.filter { b ->
        b.status == BookStatus.Read && finishedAt(b).let { t -> t > 0 && Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == year }
    }.groupingBy { it.genre.ifBlank { "Sin género" } }.eachCount()
    Column(Modifier.padding(horizontal = 20.dp)) {
        rows.take(8).forEach { (g, secs) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g, color = NB.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(120.dp))
                Box(Modifier.weight(1f).height(10.dp)) {
                    Box(Modifier.fillMaxWidth((secs.toFloat() / max).coerceIn(0.03f, 1f)).height(10.dp)
                        .clip(RoundedCornerShape(3.dp)).background(if (g == "Sin género") NB.line else NB.text))
                }
                Spacer(Modifier.width(8.dp))
                val n = finishedBy[g] ?: 0
                Text(durationLabel(secs) + if (n > 0) " · $n" else "", color = NB.text3, fontSize = 12.sp)
            }
        }
        Text("El número tras el tiempo son los libros terminados de ese género. Cambia el género desde el menú de cada libro.",
            color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
