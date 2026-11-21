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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    Paper("paper", "Papel"),
    Marker("marker", "Subrayado"),
    Noir("noir", "Noir");
    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: Paper }
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
    // Miniaturas de cada diseño (se dibujan una vez por formato)
    val thumbs by produceState<Map<QuoteDesign, Bitmap>>(emptyMap(), format) {
        value = withContext(Dispatchers.Default) {
            QuoteDesign.entries.associateWith { d ->
                val full = renderQuoteCard(ctx, q, d, format)
                Bitmap.createScaledBitmap(full, 216, (216f * full.height / full.width).toInt(), true).also { if (it != full) full.recycle() }
            }
        }
    }
    val navH = activityNavBarHeight()
    val statusH = activityStatusBarHeight()
    // Ventana a pantalla completa y márgenes medidos en la ventana principal: así nada queda bajo las barras del móvil
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullScreenDialogWindow()
        Column(Modifier.fillMaxSize().background(NB.bg).padding(top = statusH.coerceAtLeast(28.dp) + 14.dp, bottom = navH.coerceAtLeast(12.dp) + 4.dp)) {
            // Cabecera: cerrar · formato (Historia | Publicación) · guardar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cerrar", tint = NB.text) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(NB.surface2).padding(3.dp),
                ) {
                    QuoteFormat.entries.forEach { f ->
                        val on = f == format
                        Text(
                            f.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (on) NB.onText else NB.text2,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) NB.text else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { format = f; Prefs.quoteFormat = f.key }.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) IconButton(onClick = {
                    preview?.let { AppState.toast(if (saveToGallery(ctx, it)) "Guardada en Imágenes › Noir Books" else "No se pudo guardar") }
                }) { Icon(painterResource(R.drawable.ic_download), "Guardar en la galería", tint = NB.text) }
                else Spacer(Modifier.size(48.dp))
            }
            // Vista previa grande, con sombra suave
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 64.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
                val b = preview
                if (b == null) CircularProgressIndicator(Modifier.size(26.dp), color = NB.text, strokeWidth = 2.dp)
                else Image(b.asImageBitmap(), "Vista previa", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)))
            }
            // Diseños como miniaturas
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                QuoteDesign.entries.forEach { d ->
                    val on = d == design
                    Column(
                        Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(10.dp)).clickable { design = d; Prefs.quoteDesign = d.key },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 58.dp, height = if (format == QuoteFormat.Story) 103.dp else 72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(if (on) 2.dp else 1.dp, if (on) NB.text else NB.line, RoundedCornerShape(8.dp)),
                        ) {
                            thumbs[d]?.let { Image(it.asImageBitmap(), d.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(d.label, fontSize = 12.sp, color = if (on) NB.text else NB.text3, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            // Una sola acción principal
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(50)).background(NB.text)
                    .clickable { preview?.let { shareBitmap(ctx, it.copy(Bitmap.Config.ARGB_8888, false), "cita", "“${q.text}” — ${q.title}") } },
                contentAlignment = Alignment.Center,
            ) { Text("Compartir", color = NB.onText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
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
        QuoteDesign.Paper -> drawPaperCard(c, f, q, text, w, h, m, top, bottom)
        QuoteDesign.Marker -> drawMarkerCard(c, f, q, text, w, h, m, top, bottom)
        QuoteDesign.Noir -> drawNoirCard(c, f, q, text, w, h, m, top, bottom)
    }
    return bmp
}

/** Papel: editorial, fondo de papel con grano, comilla grande y la cita en serifa. */
private fun drawPaperCard(c: Canvas, f: CardFonts, q: QuoteShare, text: String, w: Int, h: Int, m: Int, top: Float, bottom: Float) {
    c.drawColor(0xFFF2ECE1.toInt())
    grain(c, w, h, dark = false)
    val accent = 0xFFB4532A.toInt()
    val ink = 0xFF1F1B16.toInt()
    val qm = tp(f.serif, 200f, accent)
    c.drawText("“", m - 10f, top + 150f, qm)
    val y = top + 185f
    val layout = fit(text, f.serif, ink, w - 2 * m, (bottom - y - 190).toInt(), 68f, 32f, 1.1f)
    c.save(); c.translate(m.toFloat(), y); layout.draw(c); c.restore()
    var yy = y + layout.height + 48f
    c.drawRect(m.toFloat(), yy, m + 80f, yy + 4f, Paint().apply { color = accent })
    yy += 58f
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
    val layout = fit(text, f.serif, ink, w - 2 * m, (bottom - y0 - 220).toInt(), 66f, 32f, 1.2f)
    // Una banda de rotulador detrás de cada línea, un poco irregular
    val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = marker }
    val rnd = Random(text.length)
    // Centrado vertical en el espacio libre
    val y = y0 + ((bottom - y0 - 220 - layout.height) / 2f).coerceAtLeast(0f)
    for (i in 0 until layout.lineCount) {
        val l = layout.getLineLeft(i); val r = layout.getLineRight(i)
        val base = layout.getLineBaseline(i).toFloat()
        val size = layout.paint.textSize
        val rect = RectF(m + l - 8f, y + base - size * 0.36f, m + r + 8f + rnd.nextInt(12), y + base + size * 0.12f)
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
