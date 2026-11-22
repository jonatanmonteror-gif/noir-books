package com.noirbooks.app.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.noirbooks.app.MainActivity
import com.noirbooks.app.R

/**
 * Widget «Cita del día»: una de tus citas, la misma todo el día (cambia a medianoche).
 * Tocarlo abre la app en Citas.
 */
class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(ctx)) }
    }

    companion object {
        const val EXTRA_QUOTES = "open_quotes"

        /** Vuelve a pintar los widgets (al cerrar la app, por si hay citas nuevas). */
        fun refresh(ctx: Context) = runCatching {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, QuoteWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { m.updateAppWidget(it, views(ctx)) }
        }

        private fun views(ctx: Context): RemoteViews {
            runCatching { ErrorLog.init(ctx); Prefs.init(ctx); Library.init(ctx) }
            val all = Library.books.value.filter { !it.inTrash }.flatMap { b -> b.highlights.map { b to it } }.sortedByDescending { it.second.id }
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val v = RemoteViews(ctx.packageName, R.layout.widget_quote)
            if (all.isNotEmpty()) {
                val (b, h) = all[(day.hashCode() and 0x7fffffff) % all.size]
                val text = h.text.replace(Regex("\\s+"), " ").trim().let { if (it.length > 280) it.take(277) + "…" else it }
                v.setTextViewText(R.id.widget_quote, "“$text”")
                v.setTextViewText(R.id.widget_source, b.title + if (b.author.isNotBlank()) " · ${b.author}" else "")
            }
            val open = PendingIntent.getActivity(ctx, 77,
                Intent(ctx, MainActivity::class.java).putExtra(EXTRA_QUOTES, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
