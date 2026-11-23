package com.noirbooks.app.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.noirbooks.app.MainActivity
import com.noirbooks.app.R

/**
 * Widget «Leyendo ahora»: el libro que estás leyendo, con «Apuntar» (lectura o progreso)
 * y «Cronómetro». Pensado para papel y audiolibros, que se leen fuera de la app.
 * También deja accesos directos al mantener pulsado el icono de la app.
 */
class NowWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(ctx)) }
    }

    companion object {
        const val EXTRA_LOG = "quick_log_book"
        const val EXTRA_TIMER = "quick_timer_book"

        /** El libro de «leyendo ahora»: el último que tocaste de los que estás leyendo. */
        fun current(): Book? = Library.books.value
            .filter { !it.inTrash && it.status == BookStatus.Reading && !it.wish }
            .maxByOrNull { maxOf(it.lastOpened, it.addedAt) }

        fun refresh(ctx: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, NowWidget::class.java))
                if (ids.isNotEmpty()) ids.forEach { m.updateAppWidget(it, views(ctx)) }
            }
            runCatching { shortcuts(ctx) }
        }

        private fun pending(ctx: Context, code: Int, extra: String, id: String) = PendingIntent.getActivity(ctx, code,
            Intent(ctx, MainActivity::class.java).putExtra(extra, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun views(ctx: Context): RemoteViews {
            runCatching { ErrorLog.init(ctx); Prefs.init(ctx); Library.init(ctx) }
            val v = RemoteViews(ctx.packageName, R.layout.widget_now)
            val b = current()
            val openApp = PendingIntent.getActivity(ctx, 80, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.now_root, openApp)
            if (b == null) {
                v.setTextViewText(R.id.now_title, "Añade un libro en Noir Books")
                v.setTextViewText(R.id.now_progress, "")
                v.setOnClickPendingIntent(R.id.now_log, openApp)
                v.setOnClickPendingIntent(R.id.now_timer, openApp)
                return v
            }
            v.setTextViewText(R.id.now_title, b.title)
            v.setTextViewText(R.id.now_progress, listOf(b.format.label, if (b.progress > 0f) b.progressLabel() else "").filter { it.isNotBlank() }.joinToString(" · "))
            val timing = Prefs.timerBook == b.id && Prefs.timerStart > 0
            v.setTextViewText(R.id.now_timer, if (timing) "Parar" else "Cronómetro")
            v.setOnClickPendingIntent(R.id.now_log, pending(ctx, 81, EXTRA_LOG, b.id))
            v.setOnClickPendingIntent(R.id.now_timer, pending(ctx, 82, EXTRA_TIMER, b.id))
            return v
        }

        /** Accesos directos del icono: «Apuntar: título» del libro actual. */
        private fun shortcuts(ctx: Context) {
            val b = current() ?: run { ShortcutManagerCompat.removeAllDynamicShortcuts(ctx); return }
            val s = ShortcutInfoCompat.Builder(ctx, "quick_log")
                .setShortLabel("Apuntar lectura")
                .setLongLabel("Apuntar: ${b.title}".take(60))
                .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_LOG, b.id))
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(ctx, listOf(s))
        }
    }
}
