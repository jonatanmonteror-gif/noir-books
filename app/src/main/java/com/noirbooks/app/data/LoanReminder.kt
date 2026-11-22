package com.noirbooks.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
 * Recordatorio de préstamo: el día que un libro prestado debería volver, a las 10:00, llega un aviso.
 * Usa una alarma no exacta (no pide permisos especiales); puede llegar con unos minutos de margen.
 * El título y el nombre van dentro del aviso, así funciona aunque la app esté cerrada.
 */
object LoanReminder {
    private const val CHANNEL = "loans"

    private fun intent(ctx: Context, b: Book) = PendingIntent.getBroadcast(
        ctx, b.id.hashCode(),
        Intent(ctx, LoanReceiver::class.java).putExtra("id", b.id).putExtra("title", b.title).putExtra("to", b.loanTo),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Programa (o quita) el aviso según el estado del préstamo del libro. */
    fun sync(ctx: Context, b: Book) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(ctx, b)
        am.cancel(pi)
        if (!b.onLoan || b.loanDue <= 0L || b.inTrash) return
        val at = java.util.Calendar.getInstance().apply {
            timeInMillis = b.loanDue
            set(java.util.Calendar.HOUR_OF_DAY, 10); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        if (at < System.currentTimeMillis()) return
        runCatching { am.set(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    /** Al abrir la app se vuelven a programar todos (las alarmas se pierden al reiniciar el móvil). */
    fun syncAll(ctx: Context) = Library.books.value.forEach { sync(ctx, it) }

    fun show(ctx: Context, id: String, title: String, to: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Préstamos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso el día que un libro prestado debería volver"
            })
        }
        val open = PendingIntent.getActivity(ctx, id.hashCode(), Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_timer)
            .setContentTitle("¿Te han devuelto «$title»?")
            .setContentText(if (to.isNotBlank()) "Se lo prestaste a $to y hoy era el día de devolverlo." else "Hoy era el día de devolverlo.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(("loan" + id).hashCode(), n) }
    }
}

class LoanReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        LoanReminder.show(ctx, intent.getStringExtra("id").orEmpty(), intent.getStringExtra("title").orEmpty(), intent.getStringExtra("to").orEmpty())
    }
}
