package com.noirbooks.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.noirbooks.app.R
import com.noirbooks.app.data.ErrorLog
import java.io.File

/**
 * Cita como imagen para compartir (4:5, para redes): fondo negro, la frase en Playfair cursiva,
 * título y autor abajo, con el sello de Noir Books.
 */
fun renderQuote(ctx: Context, quote: String, title: String, author: String): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(11, 11, 12))

    val serif = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair_italic) }.getOrNull() ?: Typeface.SERIF
    val sans = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_regular) }.getOrNull() ?: Typeface.SANS_SERIF
    val sansBold = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_semi_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    val margin = 110
    val textW = w - margin * 2
    val text = "“" + quote.trim().trim('"', '“', '”', '«', '»') + "”"

    // La letra se reduce hasta que la cita cabe.
    var size = 66f
    var layout: StaticLayout
    while (true) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = size; color = Color.rgb(242, 242, 242) }
        layout = StaticLayout.Builder.obtain(text, 0, text.length, p, textW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()
        if (layout.height <= 860 || size <= 30f) break
        size -= 4f
    }
    val top = ((h - 260 - layout.height) / 2f).coerceAtLeast(150f)
    c.save()
    c.translate(margin.toFloat(), top)
    layout.draw(c)
    c.restore()

    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 150, 156); strokeWidth = 3f }
    val y0 = h - 250f
    c.drawLine(margin.toFloat(), y0, margin + 90f, y0, line)

    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sansBold; textSize = 38f; color = Color.rgb(242, 242, 242) }
    val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 32f; color = Color.rgb(176, 176, 182) }
    c.drawText(ellipsize(title, tp, textW.toFloat()), margin.toFloat(), y0 + 70f, tp)
    if (author.isNotBlank()) c.drawText(ellipsize(author, ap, textW.toFloat()), margin.toFloat(), y0 + 118f, ap)

    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair_regular) }.getOrNull() ?: Typeface.SERIF
        textSize = 30f; color = Color.rgb(120, 120, 126)
    }
    val label = "Noir Books"
    c.drawText(label, w - margin - brand.measureText(label), h - 70f, brand)
    return bmp
}

private fun ellipsize(s: String, p: TextPaint, max: Float): String {
    if (p.measureText(s) <= max) return s
    var t = s
    while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
    return "$t…"
}

/** Crea la imagen y abre "Compartir" (Instagram, WhatsApp…). */
fun shareQuoteImage(ctx: Context, quote: String, title: String, author: String) {
    runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "cita-${System.currentTimeMillis()}.png")
        val bmp = renderQuote(ctx, quote, title, author)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "“$quote” — $title${if (author.isNotBlank()) ", $author" else ""}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Compartir cita").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { ErrorLog.note("Compartir cita", it) }
}
