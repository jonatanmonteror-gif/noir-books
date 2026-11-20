package com.noirbooks.app.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lectura de un día con un libro: segundos y páginas. */
data class DayRead(val date: String, val bookId: String, val seconds: Long, val pages: Int, val hourSeconds: LongArray = LongArray(24))

/**
 * Estadísticas de lectura medidas solas mientras se lee (como KOReader):
 * una página cuenta si estuviste al menos 5 s, y como mucho 120 s (si dejaste el móvil).
 */
object Stats {
    const val MIN_PAGE_SECONDS = 5L
    const val MAX_PAGE_SECONDS = 120L

    private lateinit var file: File
    private val data = LinkedHashMap<String, DayRead>() // clave: fecha|libro
    /** Cambia cada vez que hay datos nuevos: las pantallas lo leen para recalcular. */
    val version = mutableIntStateOf(0)
    private var dirty = false

    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "stats.json")
        runCatching { load() }.onFailure { ErrorLog.note("Leer estadísticas", it) }
    }

    fun today(): String = dayFmt.format(Date())
    fun dayOf(millis: Long): String = dayFmt.format(Date(millis))

    /** Suma el tiempo de una página ya filtrado (5–120 s). Se guarda con [flush]. */
    @Synchronized
    fun addPage(bookId: String, seconds: Long, countPage: Boolean) {
        if (seconds <= 0) return
        val day = today()
        val key = "$day|$bookId"
        val old = data[key] ?: DayRead(day, bookId, 0, 0)
        val hours = old.hourSeconds.copyOf()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        hours[h] = hours[h] + seconds
        data[key] = old.copy(seconds = old.seconds + seconds, pages = old.pages + if (countPage) 1 else 0, hourSeconds = hours)
        dirty = true
        version.intValue++
    }

    /**
     * Sesión apuntada a mano (Kindle, papel…): se reparte por las horas en que ocurrió.
     */
    @Synchronized
    fun addSession(bookId: String, startMillis: Long, seconds: Long, pages: Int) {
        if (seconds <= 0 && pages <= 0) return
        var remaining = seconds
        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
        var first = true
        while (remaining > 0 || first) {
            val day = dayOf(cal.timeInMillis)
            val key = "$day|$bookId"
            val old = data[key] ?: DayRead(day, bookId, 0, 0)
            val hours = old.hourSeconds.copyOf()
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val inHour = (3600 - cal.get(Calendar.MINUTE) * 60 - cal.get(Calendar.SECOND)).toLong()
            val chunk = minOf(remaining, inHour)
            hours[h] = hours[h] + chunk
            data[key] = old.copy(seconds = old.seconds + chunk, pages = old.pages + if (first) pages else 0, hourSeconds = hours)
            remaining -= chunk
            cal.add(Calendar.SECOND, chunk.toInt().coerceAtLeast(1))
            first = false
            if (chunk <= 0) break
        }
        dirty = true
        version.intValue++
        flush()
    }

    @Synchronized
    fun flush() {
        if (!dirty || !::file.isInitialized) return
        runCatching {
            val o = JSONObject()
            data.values.forEach { d ->
                o.put("${d.date}|${d.bookId}", JSONObject().put("s", d.seconds).put("p", d.pages)
                    .put("h", org.json.JSONArray(d.hourSeconds.toList())))
            }
            val tmp = File(file.parentFile, "stats.json.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
            dirty = false
        }.onFailure { ErrorLog.note("Guardar estadísticas", it) }
    }

    private fun load() {
        if (!file.exists()) return
        val o = JSONObject(file.readText())
        o.keys().forEach { k ->
            val v = o.optJSONObject(k) ?: return@forEach
            val date = k.substringBefore('|')
            val book = k.substringAfter('|')
            val ha = v.optJSONArray("h")
            val hours = LongArray(24) { i -> ha?.optLong(i) ?: 0L }
            data[k] = DayRead(date, book, v.optLong("s"), v.optInt("p"), hours)
        }
    }

    @Synchronized
    fun all(): List<DayRead> = data.values.toList()

    fun byDay(): Map<String, List<DayRead>> = all().groupBy { it.date }

    fun secondsForBook(bookId: String): Long = all().filter { it.bookId == bookId }.sumOf { it.seconds }

    /** Velocidad personal (letras por minuto) a partir de lo leído, si hay datos suficientes. */
    fun charsPerMinute(): Int {
        val books = Library.books.value.associateBy { it.id }
        var chars = 0.0
        var seconds = 0L
        all().groupBy { it.bookId }.forEach { (id, days) ->
            val b = books[id] ?: return@forEach
            if (!b.isEpub) return@forEach
            val s = days.sumOf { it.seconds }
            if (s < 600) return@forEach
            chars += b.progress * b.totalChars
            seconds += s
        }
        if (seconds < 1800) return CHARS_PER_MINUTE
        return (chars / (seconds / 60.0)).toInt().coerceIn(400, 4000)
    }

    /** Días seguidos leyendo, hasta hoy (o ayer, si hoy aún no has leído). */
    fun streak(): Int {
        val days = byDay().filterValues { l -> l.sumOf { it.seconds } >= 60 }.keys
        val cal = Calendar.getInstance()
        if (dayOf(cal.timeInMillis) !in days) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (dayOf(cal.timeInMillis) in days) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }
}
