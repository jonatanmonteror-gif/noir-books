package com.noirbooks.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.noirbooks.app.MainActivity
import com.noirbooks.app.R

/**
 * Mantiene viva la lectura en voz alta con la pantalla apagada o la app en segundo plano,
 * con una notificación para pausar, seguir o parar. La voz la lleva el lector; esto solo la "sostiene".
 */
class ReadAloudService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Control.toggle?.invoke()
            ACTION_STOP -> { Control.stop?.invoke(); stopSelf(); return START_NOT_STICKY }
        }
        val n = build(this)
        runCatching {
            ServiceCompat.startForeground(this, ID, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        }.onFailure { ErrorLog.note("Voz alta en segundo plano", it) }
        return START_NOT_STICKY
    }

    /** Lo que el lector deja para que la notificación lo controle. */
    object Control {
        var toggle: (() -> Unit)? = null
        var stop: (() -> Unit)? = null
        var title: String = ""
        var playing: Boolean = false
    }

    companion object {
        private const val CHANNEL = "read_aloud"
        private const val ID = 4202
        private const val ACTION_TOGGLE = "com.noirbooks.app.TTS_TOGGLE"
        private const val ACTION_STOP = "com.noirbooks.app.TTS_STOP"

        private fun channel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lectura en voz alta", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    })
                }
            }
        }

        private fun build(ctx: Context): Notification {
            channel(ctx)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            fun svc(action: String, code: Int) = PendingIntent.getService(ctx, code, Intent(ctx, ReadAloudService::class.java).setAction(action), flags)
            val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify_timer)
                .setContentTitle(Control.title.ifBlank { "Noir Books" })
                .setContentText(if (Control.playing) "Leyendo en voz alta" else "En pausa")
                .setOngoing(Control.playing).setOnlyAlertOnce(true).setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .addAction(0, if (Control.playing) "Pausa" else "Seguir", svc(ACTION_TOGGLE, 4))
                .addAction(0, "Parar", svc(ACTION_STOP, 5))
                .build()
        }

        /** Arranca o actualiza la notificación (la primera vez, desde la app en primer plano). */
        fun update(ctx: Context) {
            if (!TimerNotification.canNotify(ctx)) return
            runCatching {
                if (running) NotificationManagerCompat.from(ctx).notify(ID, build(ctx))
                else { ContextCompat.startForegroundService(ctx, Intent(ctx, ReadAloudService::class.java)); running = true }
            }.onFailure { ErrorLog.note("Voz alta en segundo plano", it) }
        }

        fun stop(ctx: Context) {
            if (!running) return
            running = false
            runCatching { ctx.stopService(Intent(ctx, ReadAloudService::class.java)) }
            runCatching { NotificationManagerCompat.from(ctx).cancel(ID) }
        }

        @Volatile private var running = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Companion.stop(this)
    }
}
