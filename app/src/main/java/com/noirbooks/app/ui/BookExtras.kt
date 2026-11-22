package com.noirbooks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.LoanReminder
import com.noirbooks.app.data.Prefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val dayFmt get() = SimpleDateFormat("d MMM yyyy", Locale("es", "ES"))
private const val DAY = 86_400_000L

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value, onChange, singleLine = true, modifier = modifier,
        placeholder = { Text(hint, color = NB.text3) },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text,
            focusedTextColor = NB.text, unfocusedTextColor = NB.text),
    )
}

/** Saga: nombre, número y total. Sugiere las sagas que ya tienes para no escribirlas dos veces. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SeriesDialog(b: Book, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(b.series) }
    var no by remember { mutableStateOf(b.seriesNo) }
    var total by remember { mutableStateOf(if (b.seriesTotal > 0) "${b.seriesTotal}" else "") }
    val known = remember { Library.books.value.map { it.series }.filter { it.isNotBlank() }.distinct().sorted() }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text("Saga", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(name, { name = it.take(60) }, "Nombre de la saga")
                val matches = known.filter { it != name && (name.isBlank() || fold(it).contains(fold(name))) }.take(6)
                if (matches.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        matches.forEach { m -> Chip(m, false) { name = m } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(no, { no = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, "N.º", number = true, modifier = Modifier.weight(1f))
                    Text("  de  ", color = NB.text2)
                    Field(total, { total = it.filter(Char::isDigit).take(3) }, "Total", number = true, modifier = Modifier.weight(1f))
                }
                Text("El total es opcional: «3 de 7».", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                scope.launch {
                    Library.update(b.id) {
                        it.copy(series = n, seriesNo = if (n.isBlank()) "" else no.replace(',', '.').trim(),
                            seriesTotal = if (n.isBlank()) 0 else total.toIntOrNull() ?: 0)
                    }
                }
                onDismiss()
            }) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            if (b.series.isNotBlank()) TextButton(onClick = {
                scope.launch { Library.update(b.id) { it.copy(series = "", seriesNo = "", seriesTotal = 0) } }; onDismiss()
            }) { Text("Quitar de la saga", color = NB.text2) }
            else TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) }
        },
    )
}

/** Prestar un libro (o marcarlo como devuelto), con aviso el día de devolución. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LoanDialog(b: Book, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf(b.loanTo) }
    // Plazo: 0 = sin fecha
    var weeks by remember {
        mutableStateOf(if (b.loanDue > 0 && b.loanAt > 0) ((b.loanDue - b.loanAt) / (7 * DAY)).toInt().coerceIn(1, 12) else 0)
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text(if (b.onLoan) "Prestado" else "Prestar", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (b.onLoan) {
                    Text("A ${b.loanTo} desde el ${dayFmt.format(b.loanAt)}" +
                        if (b.loanDue > 0) ". Tenía que volver el ${dayFmt.format(b.loanDue)}." else ".", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                }
                Field(to, { to = it.take(40) }, "¿A quién?")
                Spacer(Modifier.height(12.dp))
                Text("Avisarme para que vuelva", color = NB.text2, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Sin aviso", 2 to "2 semanas", 4 to "1 mes", 8 to "2 meses").forEach { (w, l) -> Chip(l, weeks == w) { weeks = w } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = to.isNotBlank(), onClick = {
                val now = System.currentTimeMillis()
                val since = if (b.onLoan) b.loanAt else now
                scope.launch {
                    Library.update(b.id) { it.copy(loanTo = to.trim(), loanAt = since, loanDue = if (weeks > 0) since + weeks * 7 * DAY else 0L) }
                    Library.get(b.id)?.let { LoanReminder.sync(ctx, it) }
                }
                AppState.toast("Prestado a ${to.trim()}")
                onDismiss()
            }) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            if (b.onLoan) TextButton(onClick = {
                scope.launch {
                    Library.update(b.id) { it.copy(loanTo = "", loanAt = 0L, loanDue = 0L) }
                    Library.get(b.id)?.let { LoanReminder.sync(ctx, it) }
                }
                AppState.toast("Devuelto")
                onDismiss()
            }) { Text("Ya me lo han devuelto", color = NB.text) }
            else TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) }
        },
    )
}

/** Libro abandonado hace más de 3 meses al que proponer volver (uno cada vez). */
fun resurfaceCandidate(books: List<Book>): Book? {
    if (!Prefs.resurface) return null
    val now = System.currentTimeMillis()
    val limit = 90 * DAY
    return books.filter { b ->
        b.status == BookStatus.Abandoned && !b.noResurface && !b.inTrash &&
            now - lastTouched(b) > limit && now - b.resurfaceAskedAt > limit
    }.maxByOrNull { lastTouched(it) }
}

private fun lastTouched(b: Book) = maxOf(b.lastOpened, b.readings.maxOfOrNull { maxOf(it.start, it.end) } ?: 0L, b.addedAt)

/** Tarjeta suave en Inicio: «Hace 4 meses dejaste X. ¿Le das otra oportunidad?». */
@Composable
fun ResurfaceCard(b: Book, onDetails: () -> Unit) {
    val scope = rememberCoroutineScope()
    val months = ((System.currentTimeMillis() - lastTouched(b)) / (30 * DAY)).toInt().coerceAtLeast(3)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface).clickable(onClick = onDetails).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(b, Modifier.width(44.dp), titleSize = 6)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("¿LE DAS OTRA OPORTUNIDAD?", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Hace $months meses dejaste «${b.title}»" + if (b.progress > 0.01f) " al ${(b.progress * 100).toInt()} %." else ".",
                    color = NB.text, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Retomar", primary = true) {
                scope.launch { Library.setStatus(b.id, BookStatus.Reading) }
                AppState.toast("«${b.title}» vuelve a Leyendo")
            }
            PillButton("Ahora no") {
                scope.launch { Library.update(b.id) { it.copy(resurfaceAskedAt = System.currentTimeMillis()) } }
            }
            Text("No preguntar más", color = NB.text3, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).clickable {
                    scope.launch { Library.update(b.id) { it.copy(noResurface = true) } }
                }.padding(8.dp))
        }
    }
}

/** Etiqueta pequeña sobre la portada: «Lo quiero», «Prestado». */
@Composable
fun CoverBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.padding(5.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
