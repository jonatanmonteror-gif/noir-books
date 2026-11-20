package com.noirbooks.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.noirbooks.app.MainActivity
import com.noirbooks.app.R

/**
 * Notificación del cronómetro de lectura: el tiempo corre en la notificación (y en la pantalla de bloqueo)
 * y "Terminar" abre la app para confirmar la lectura.
 */
object TimerNotification {
    const val ACTION_STOP = "com.noirbooks.app.STOP_TIMER"
    private const val CHANNEL = "reading_timer"
    private const val ID = 4201
    private var app: Context? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Cronómetro de lectura", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "El tiempo que llevas leyendo fuera de la app"
                    setShowBadge(false)
                })
            }
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(bookId: String, start: Long) {
        val ctx = app ?: return
        if (!canNotify(ctx)) return
        val title = Library.get(bookId)?.title ?: "Leyendo"
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val stop = PendingIntent.getActivity(ctx, 2,
            Intent(ctx, MainActivity::class.java).setAction(ACTION_STOP).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_timer)
            .setContentTitle(title)
            .setContentText("Leyendo · toca Terminar al acabar")
            .setWhen(start).setShowWhen(true).setUsesChronometer(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, "Terminar", stop)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }

    fun cancel() {
        val ctx = app ?: return
        runCatching { NotificationManagerCompat.from(ctx).cancel(ID) }
    }
}
