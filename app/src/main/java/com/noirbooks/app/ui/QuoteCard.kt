package com.noirbooks.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import com.noirbooks.app.R
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.HighlightColor
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Lo que se quiere compartir: la cita y de qué libro es. */
data class QuoteShare(val text: String, val title: String, val author: String, val bookId: String? = null, val color: Int = 0)

/** Diseños de la tarjeta de cita. */
enum class QuoteDesign(val key: String, val label: String) {
    Cover("cover", "Portada"),
    Paper("paper", "Papel"),
    Marker("marker", "Subrayado"),
    Noir("noir", "Noir");
    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: Cover }
}

/** Formatos: historia de Instagram (9:16) o publicación (4:5). */
enum class QuoteFormat(val key: String, val label: String, val h: Int) {
    Story("story", "Historia", 1920),
    Post("post", "Publicación", 1350);
    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: Story }
}

// ---------------------------------------------------------------------------------------------
// Pantalla para elegir diseño y formato, con vista previa
// ---------------------------------------------------------------------------------------------

@Composable
fun QuoteShareDialog(q: QuoteShare, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var design by remember { mutableStateOf(QuoteDesign.of(Prefs.quoteDesign)) }
    var format by remember { mutableStateOf(QuoteFormat.of(Prefs.quoteFormat)) }
    val preview by produceState<Bitmap?>(null, design, format) {
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching { renderQuoteCard(ctx, q, design, format) }.onFailure { ErrorLog.note("Tarjeta de cita", it) }.getOrNull()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(NB.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cerrar", tint = NB.text) }
                Text("Compartir cita", fontFamily = Playfair, fontSize = 22.sp, color = NB.text)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                val b = preview
                if (b == null) CircularProgressIndicator(Modifier.size(26.dp), color = NB.text, strokeWidth = 2.dp)
                else Image(b.asImageBitmap(), "Vista previa", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteDesign.entries.forEach { d -> Chip(d.label, d == design) { design = d; Prefs.quoteDesign = d.key } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteFormat.entries.forEach { f -> Chip(f.label, f == format) { format = f; Prefs.quoteFormat = f.key } }
                }
                Spacer(Modifier.height(12.dp))
                ButtonRow {
                    PillButton("Compartir", primary = true) {
                        preview?.let { shareBitmap(ctx, it.copy(Bitmap.Config.ARGB_8888, false), "cita", "“${q.text}” — ${q.title}") }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) PillButton("Guardar en la galería") {
                        preview?.let { AppState.toast(if (saveToGallery(ctx, it)) "Guardada en Imágenes › Noir Books" else "No se pudo guardar") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(if (format == QuoteFormat.Story) "Historia: 1080 × 1920, deja libres los bordes donde Instagram pone sus botones."
                    else "Publicación: 1080 × 1350, el formato vertical del feed.", color = NB.text3, fontSize = 12.sp)
            }
        }
    }
}

private fun saveToGallery(ctx: Context, bmp: Bitmap): Boolean = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "cita-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Noir Books")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    true
}.getOrDefault(false)

// ---------------------------------------------------------------------------------------------
// Dibujo de las tarjetas
// ---------------------------------------------------------------------------------------------

private class CardFonts(ctx: Context) {
    val serif: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair_regular) }.getOrNull() ?: Typeface.SERIF
    val serifItalic: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair_italic) }.getOrNull() ?: Typeface.SERIF
    val sans: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_regular) }.getOrNull() ?: Typeface.SANS_SERIF
    val sansMed: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_medium) }.getOrNull() ?: Typeface.SANS_SERIF
    val sansBold: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_semi_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
}

private fun tp(tf: Typeface, size: Float, color: Int, spacing: Float = 0f) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; letterSpacing = spacing }

private fun cleanQuote(s: String) = s.trim().trim('"', '“', '”', '«', '»', '\'').replace(Regex("\\s+"), " ")

/** Ajusta el tamaño de letra hasta que la cita cabe en [maxH]. */
private fun fit(text: String, tf: Typeface, color: Int, width: Int, maxH: Int, start: Float, min: Float, spacing: Float): StaticLayout {
    var size = start
    while (true) {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, tp(tf, size, color), width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).setIncludePad(false).build()
        if (l.height <= maxH || size <= min) return l
        size -= 3f
    }
}

private fun ellip(s: String, p: TextPaint, max: Float): String {
    if (p.measureText(s) <= max) return s
    var t = s
    while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
    return "$t…"
}

private fun cover(q: QuoteShare): Bitmap? {
    val b = q.bookId?.let { Library.get(it) } ?: Library.books.value.firstOrNull { it.title == q.title }
    if (b == null || !b.hasCover) return null
    val f = Library.coverOf(b.id)
    return if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
}

private fun avgColor(b: Bitmap): Int {
    val s = Bitmap.createScaledBitmap(b, 12, 18, true)
    var r = 0L; var g = 0L; var bl = 0L
    for (y in 0 until s.height) for (x in 0 until s.width) { val c = s.getPixel(x, y); r += (c shr 16) and 255; g += (c shr 8) and 255; bl += c and 255 }
    val n = s.width * s.height
    if (s != b) s.recycle()
    return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
}

private fun shade(c: Int, f: Float) = android.graphics.Color.rgb(
    (android.graphics.Color.red(c) * f).toInt().coerceIn(0, 255),
    (android.graphics.Color.green(c) * f).toInt().coerceIn(0, 255),
    (android.graphics.Color.blue(c) * f).toInt().coerceIn(0, 255),
)

private fun drawCoverAt(c: Canvas, bmp: Bitmap, r: RectF, radius: Float) {
    // Sombra suave
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = android.graphics.BlurMaskFilter(24f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    c.drawRoundRect(RectF(r.left + 6, r.top + 14, r.right + 6, r.bottom + 14), radius, radius, sh)
    c.save()
    c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
    val sr = bmp.width.toFloat() / bmp.height; val dr = r.width() / r.height()
    val src = if (sr > dr) { val w = (bmp.height * dr).toInt(); val x = (bmp.width - w) / 2; Rect(x, 0, x + w, bmp.height) }
    else { val h = (bmp.width / dr).toInt(); val y = (bmp.height - h) / 2; Rect(0, y, bmp.width, y + h) }
    c.drawBitmap(bmp, src, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    c.restore()
}

private fun grain(c: Canvas, w: Int, h: Int, dark: Boolean) {
    val p = Paint().apply { color = if (dark) 0x0FFFFFFF else 0x0D000000 }
    val rnd = Random(7)
    repeat(w * h / 260) {
        val x = rnd.nextInt(w).toFloat(); val y = rnd.nextInt(h).toFloat()
        c.drawRect(x, y, x + 2f, y + 2f, p)
    }
}

fun renderQuoteCard(ctx: Context, q: QuoteShare, design: QuoteDesign, format: QuoteFormat): Bitmap {
    val w = 1080
    val h = format.h
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val f = CardFonts(ctx)
    val story = format == QuoteFormat.Story
    // En las historias, Instagram tapa arriba y abajo: se deja ese margen libre.
    val top = if (story) 260f else 110f
    val bottom = if (story) h - 300f else h - 100f
    val m = 96
    val text = cleanQuote(q.text)
    when (design) {
        QuoteDesign.Cover -> drawCoverCard(c, f, q, text, w, h, m, top, bottom)
        QuoteDesign.Paper -> drawPaperCard(c, f, q, text, w, h, m, top, bottom)
        QuoteDesign.Marker -> drawMarkerCard(c, f, q, text, w, h, m, top, bottom)
        QuoteDesign.Noir -> drawNoirCard(c, f, q, text, w, h, m, top, bottom)
    }
    return bmp
}

/** Portada: fondo con el color del libro (como las tarjetas de letras de Spotify), la portada y la cita en blanco. */
private fun drawCoverCard(c: Canvas, f: CardFonts, q: QuoteShare, text: String, w: Int, h: Int, m: Int, top: Float, bottom: Float) {
    val cov = cover(q)
    val base = cov?.let { avgColor(it) } ?: 0xFF3A3F4B.toInt()
    // Color más vivo y oscuro para que el blanco se lea siempre
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(base, it) }
    hsv[1] = (hsv[1] * 1.25f).coerceIn(0.18f, 0.75f); hsv[2] = hsv[2].coerceIn(0.32f, 0.52f)
    val main = android.graphics.Color.HSVToColor(hsv)
    val bg = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), main, shade(main, 0.45f), Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    grain(c, w, h, dark = true)

    var y = top
    val coverW = 190f; val coverH = 285f
    if (cov != null) {
        drawCoverAt(c, cov, RectF(m.toFloat(), y, m + coverW, y + coverH), 14f)
        cov.recycle()
    }
    val infoX = if (cov != null) m + coverW + 40f else m.toFloat()
    val infoW = w - m - infoX
    val tP = tp(f.sansBold, 40f, 0xFFFFFFFF.toInt())
    val aP = tp(f.sans, 32f, 0xCCFFFFFF.toInt())
    val infoY = if (cov != null) y + coverH / 2f else y + 40f
    c.drawText(ellip(q.title, tP, infoW), infoX, infoY, tP)
    if (q.author.isNotBlank()) c.drawText(ellip(q.author, aP, infoW), infoX, infoY + 48f, aP)
    y += (if (cov != null) coverH else 80f) + 80f

    val layout = fit(text, f.sansBold, 0xFFFFFFFF.toInt(), w - 2 * m, (bottom - y - 120).toInt(), 72f, 34f, 1.22f)
    c.save(); c.translate(m.toFloat(), y); layout.draw(c); c.restore()
    val brand = tp(f.serif, 32f, 0x99FFFFFF.toInt())
    c.drawText("Noir Books", m.toFloat(), bottom, brand)
}

/** Papel: editorial, fondo de papel con grano, comilla grande y la cita en serifa. */
private fun drawPaperCard(c: Canvas, f: CardFonts, q: QuoteShare, text: String, w: Int, h: Int, m: Int, top: Float, bottom: Float) {
    c.drawColor(0xFFF2ECE1.toInt())
    grain(c, w, h, dark = false)
    val accent = 0xFFB4532A.toInt()
    val ink = 0xFF1F1B16.toInt()
    val qm = tp(f.serif, 260f, accent)
    c.drawText("“", m - 12f, top + 190f, qm)
    val y = top + 250f
    val layout = fit(text, f.serif, ink, w - 2 * m, (bottom - y - 230).toInt(), 70f, 34f, 1.28f)
    c.save(); c.translate(m.toFloat(), y); layout.draw(c); c.restore()
    var yy = y + layout.height + 80f
    c.drawRect(m.toFloat(), yy, m + 80f, yy + 4f, Paint().apply { color = accent })
    yy += 70f
    val tP = tp(f.sansBold, 30f, ink, 0.12f)
    c.drawText(ellip(q.title.uppercase(), tP, (w - 2 * m).toFloat()), m.toFloat(), yy, tP)
    if (q.author.isNotBlank()) c.drawText(ellip(q.author, tp(f.serifItalic, 36f, 0xFF5C554B.toInt()), (w - 2 * m).toFloat()), m.toFloat(), yy + 54f, tp(f.serifItalic, 36f, 0xFF5C554B.toInt()))
    val brand = tp(f.serif, 30f, 0xFF8A8276.toInt())
    c.drawText("Noir Books", w - m - brand.measureText("Noir Books"), bottom, brand)
}

/** Subrayado: como una frase marcada con rotulador, del color del subrayado. */
private fun drawMarkerCard(c: Canvas, f: CardFonts, q: QuoteShare, text: String, w: Int, h: Int, m: Int, top: Float, bottom: Float) {
    c.drawColor(0xFFFAF7F0.toInt())
    grain(c, w, h, dark = false)
    val ink = 0xFF181818.toInt()
    val marker = (HighlightColor.of(q.color).argb and 0x00FFFFFF or 0x8C000000).toInt()
    val y0 = top + 60f
    val layout = fit(text, f.serif, ink, w - 2 * m, (bottom - y0 - 220).toInt(), 66f, 32f, 1.42f)
    // Una banda de rotulador detrás de cada línea, un poco irregular
    val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = marker }
    val rnd = Random(text.length)
    // Centrado vertical en el espacio libre
    val y = y0 + ((bottom - y0 - 220 - layout.height) / 2f).coerceAtLeast(0f)
    for (i in 0 until layout.lineCount) {
        val l = layout.getLineLeft(i); val r = layout.getLineRight(i)
        val base = layout.getLineBaseline(i).toFloat()
        val size = layout.paint.textSize
        val rect = RectF(m + l - 10f, y + base - size * 0.42f, m + r + 10f + rnd.nextInt(14), y + base + size * 0.14f)
        c.save(); c.rotate(rnd.nextFloat() * 0.8f - 0.4f, rect.centerX(), rect.centerY())
        c.drawRoundRect(rect, 8f, 8f, mp); c.restore()
    }
    c.save(); c.translate(m.toFloat(), y); layout.draw(c); c.restore()
    var yy = y + layout.height + 90f
    val tP = tp(f.sansBold, 34f, ink)
    c.drawText(ellip(q.title, tP, (w - 2 * m).toFloat()), m.toFloat(), yy, tP)
    if (q.author.isNotBlank()) { yy += 48f; c.drawText(ellip(q.author, tp(f.sans, 30f, 0xFF6B6B6B.toInt()), (w - 2 * m).toFloat()), m.toFloat(), yy, tp(f.sans, 30f, 0xFF6B6B6B.toInt())) }
    val brand = tp(f.serif, 30f, 0xFF9A9A9A.toInt())
    c.drawText("Noir Books", w - m - brand.measureText("Noir Books"), bottom, brand)
}

/** Noir: negro, la cita en cursiva y el sello de Noir Books. */
private fun drawNoirCard(c: Canvas, f: CardFonts, q: QuoteShare, text: String, w: Int, h: Int, m: Int, top: Float, bottom: Float) {
    c.drawColor(0xFF0B0B0C.toInt())
    grain(c, w, h, dark = true)
    val quoted = "“$text”"
    val layout = fit(quoted, f.serifItalic, 0xFFF2F2F2.toInt(), w - 2 * m, (bottom - top - 300).toInt(), 70f, 32f, 1.2f)
    val y = top + ((bottom - top - 260 - layout.height) / 2f).coerceAtLeast(0f)
    c.save(); c.translate(m.toFloat(), y); layout.draw(c); c.restore()
    val y0 = bottom - 170f
    c.drawLine(m.toFloat(), y0, m + 90f, y0, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF96969C.toInt(); strokeWidth = 3f })
    val tP = tp(f.sansBold, 38f, 0xFFF2F2F2.toInt())
    c.drawText(ellip(q.title, tP, (w - 2 * m).toFloat()), m.toFloat(), y0 + 68f, tP)
    if (q.author.isNotBlank()) c.drawText(ellip(q.author, tp(f.sans, 32f, 0xFFB0B0B6.toInt()), (w - 2 * m).toFloat()), m.toFloat(), y0 + 116f, tp(f.sans, 32f, 0xFFB0B0B6.toInt()))
    val brand = tp(f.serif, 30f, 0xFF78787E.toInt())
    c.drawText("Noir Books", w - m - brand.measureText("Noir Books"), bottom, brand)
}
