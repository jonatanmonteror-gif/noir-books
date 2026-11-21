package com.noirbooks.app.data

import java.util.Calendar

/**
 * Elogios con sentido: un aviso discreto solo cuando hay algo de verdad que destacar
 * (al terminar un libro o al empezar un mes). Se desactivan en Ajustes → Diario.
 */
object Praise {
    /** Dónde se muestra el aviso (la app pone aquí su mensaje emergente). */
    var sink: ((String) -> Unit)? = null

    private val MILESTONES = setOf(1, 5, 10, 15, 20, 25, 30, 40, 50, 60, 75, 100)
    private val MONTHS = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    private fun yearOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
    private fun endOf(b: Book) = b.readings.lastOrNull { it.end > 0 }?.end ?: 0L
    private fun startOf(b: Book) = b.readings.lastOrNull { it.end > 0 }?.start ?: 0L

    fun onFinished(id: String) {
        if (!Prefs.praise.value) return
        val book = Library.get(id) ?: return
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val finished = Library.books.value.filter { !it.inTrash && it.status == BookStatus.Read && yearOf(endOf(it)) == year }
        val n = finished.size
        val days = ((endOf(book) - startOf(book)) / 86_400_000L).coerceAtLeast(0) + 1
        val secs = Stats.secondsForBook(id)
        val msg = when {
            n in MILESTONES && n > 1 -> "Tu libro número $n de $year"
            n == 1 -> "Tu primer libro de $year"
            finished.size >= 3 && startOf(book) > 0 && finished.filter { it.id != id && startOf(it) > 0 }
                .all { ((endOf(it) - startOf(it)) / 86_400_000L) + 1 > days } -> "El libro que más rápido has leído este año: ${if (days == 1L) "1 día" else "$days días"}"
            finished.size >= 3 && secs >= 3600 && finished.filter { it.id != id }.all { Stats.secondsForBook(it.id) < secs } ->
                "Tu libro con más horas de lectura este año: ${durationLabel(secs)}"
            else -> null
        } ?: return
        sink?.invoke(msg)
    }

    /** Al abrir la app en los primeros días del mes: un resumen del mes anterior si destacó. Una vez por mes. */
    fun checkMonth() {
        if (!Prefs.praise.value) return
        val now = Calendar.getInstance()
        val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val key = "%04d-%02d".format(prev.get(Calendar.YEAR), prev.get(Calendar.MONTH) + 1)
        if (Prefs.praisedMonth == key || now.get(Calendar.DAY_OF_MONTH) > 7) return
        Prefs.praisedMonth = key
        val byMonth = Stats.all().groupBy { it.date.take(7) }.mapValues { (_, l) -> l.sumOf { it.seconds } }
        val secs = byMonth[key] ?: 0L
        val name = MONTHS[prev.get(Calendar.MONTH)].replaceFirstChar { it.uppercase() }
        val finished = Library.books.value.count { b ->
            b.status == BookStatus.Read && endOf(b) > 0 &&
                Calendar.getInstance().apply { timeInMillis = endOf(b) }.let { it.get(Calendar.YEAR) == prev.get(Calendar.YEAR) && it.get(Calendar.MONTH) == prev.get(Calendar.MONTH) }
        }
        val msg = when {
            secs >= 3600 && byMonth.size >= 2 && byMonth.filterKeys { it != key }.values.all { it < secs } ->
                "$name ha sido tu mes con más horas de lectura: ${durationLabel(secs)}"
            finished >= 2 -> "En $name terminaste $finished libros"
            else -> null
        } ?: return
        sink?.invoke(msg)
    }
}
