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
/** Una lectura del historial: en la app o apuntada a mano. */
data class Session(val id: Long, val bookId: String, val start: Long, val seconds: Long, val pages: Int, val manual: Boolean) {
    val end: Long get() = start + seconds * 1000
}

data class DayRead(val date: String, val bookId: String, val seconds: Long, val pages: Int, val hourSeconds: LongArray = LongArray(24))

/**
 * Estadísticas de lectura medidas solas mientras se lee (como KOReader):
 * una página cuenta si estuviste al menos 5 s, y como mucho 120 s (si dejaste el móvil).
 */
object Stats {
    const val MIN_PAGE_SECONDS = 5L
    const val MAX_PAGE_SECONDS = 120L

    private lateinit var file: File
    private lateinit var sessionsFile: File
    private val sessions = ArrayList<Session>()
    private val data = LinkedHashMap<String, DayRead>() // clave: fecha|libro
    /** Cambia cada vez que hay datos nuevos: las pantallas lo leen para recalcular. */
    val version = mutableIntStateOf(0)
    private var dirty = false

    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "stats.json")
        sessionsFile = File(ctx.filesDir, "sessions.json")
        runCatching { load() }.onFailure { ErrorLog.note("Leer estadísticas", it) }
        runCatching { loadSessions() }.onFailure { ErrorLog.note("Leer historial de lecturas", it) }
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
        spread(bookId, startMillis, seconds, pages, 1)
        sessions.add(Session(System.currentTimeMillis(), bookId, startMillis, seconds, pages, manual = true))
        saveSessions()
        flush()
    }

    /** Historial de lecturas (apuntadas a mano o leídas en la app), de la más reciente a la más antigua. */
    @Synchronized
    fun sessions(bookId: String? = null): List<Session> =
        sessions.filter { bookId == null || it.bookId == bookId }.sortedByDescending { it.start }

    /** Tramo leído en la app (se une al anterior si fue hace menos de 10 minutos). */
    @Synchronized
    fun logAppSession(bookId: String, start: Long, seconds: Long, pages: Int) {
        if (seconds <= 0) return
        val last = sessions.lastOrNull()
        if (last != null && !last.manual && last.bookId == bookId && start - last.end < 10 * 60_000L && start >= last.start) {
            sessions[sessions.lastIndex] = last.copy(seconds = last.seconds + seconds, pages = last.pages + pages)
        } else {
            sessions.add(Session(System.currentTimeMillis(), bookId, start, seconds, pages, manual = false))
        }
        saveSessions()
    }

    /** Borra una lectura y resta su tiempo y sus páginas de las estadísticas. */
    @Synchronized
    fun deleteSession(id: Long) {
        val s = sessions.firstOrNull { it.id == id } ?: return
        sessions.remove(s)
        spread(s.bookId, s.start, s.seconds, s.pages, -1)
        // Quita los días que se quedan vacíos.
        data.entries.removeAll { it.value.seconds <= 0 && it.value.pages <= 0 }
        saveSessions()
        flush()
    }

    /** Borra todo lo leído de un libro: registro, calendario y estadísticas. */
    @Synchronized
    fun deleteBook(bookId: String) {
        sessions.removeAll { it.bookId == bookId }
        data.entries.removeAll { it.value.bookId == bookId }
        dirty = true
        saveSessions()
        flush()
    }

    /** Borra lo leído de un libro en un día concreto (también sus lecturas del registro de ese día). */
    @Synchronized
    fun deleteDay(date: String, bookId: String) {
        sessions.removeAll { it.bookId == bookId && dayOf(it.start) == date }
        data.remove("$date|$bookId")
        dirty = true
        saveSessions()
        flush()
    }

    /** Reparte (o resta, con sign = -1) un tiempo por las horas y días en que ocurrió. */
    private fun spread(bookId: String, startMillis: Long, seconds: Long, pages: Int, sign: Int) {
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
            if (sign < 0 && data[key] == null) { remaining -= chunk; cal.add(Calendar.SECOND, chunk.toInt().coerceAtLeast(1)); first = false; if (chunk <= 0) break; continue }
            hours[h] = (hours[h] + sign * chunk).coerceAtLeast(0)
            data[key] = old.copy(
                seconds = (old.seconds + sign * chunk).coerceAtLeast(0),
                pages = (old.pages + if (first) sign * pages else 0).coerceAtLeast(0),
                hourSeconds = hours,
            )
            remaining -= chunk
            cal.add(Calendar.SECOND, chunk.toInt().coerceAtLeast(1))
            first = false
            if (chunk <= 0) break
        }
        dirty = true
        version.intValue++
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

    private fun saveSessions() {
        if (!::sessionsFile.isInitialized) return
        runCatching {
            val arr = org.json.JSONArray()
            sessions.forEach { x ->
                arr.put(JSONObject().put("id", x.id).put("b", x.bookId).put("st", x.start).put("s", x.seconds).put("p", x.pages).put("m", x.manual))
            }
            val tmp = File(sessionsFile.parentFile, "sessions.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(sessionsFile)) { sessionsFile.writeText(tmp.readText()); tmp.delete() }
        }.onFailure { ErrorLog.note("Guardar historial de lecturas", it) }
        version.intValue++
    }

    private fun loadSessions() {
        if (!sessionsFile.exists()) return
        val arr = org.json.JSONArray(sessionsFile.readText())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            sessions += Session(o.optLong("id"), o.optString("b"), o.optLong("st"), o.optLong("s"), o.optInt("p"), o.optBoolean("m"))
        }
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
