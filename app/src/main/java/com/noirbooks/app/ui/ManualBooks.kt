package com.noirbooks.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookFormat
import com.noirbooks.app.data.BookSearch
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.Clippings
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.FoundBook
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.ProgressUnit
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.durationLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ES = Locale("es", "ES")

@Composable
private fun field(): androidx.compose.material3.TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line,
    focusedContainerColor = NB.surface, unfocusedContainerColor = NB.surface, cursorColor = NB.text,
    focusedLabelColor = NB.text2, unfocusedLabelColor = NB.text3,
)

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (enabled) NB.text else NB.line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) NB.onText else NB.text3, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

// ---------------------------------------------------------------------------------------------
// Añadir libro: buscar en el catálogo, escribirlo a mano, importar EPUB o subrayados del Kindle
// ---------------------------------------------------------------------------------------------

@Composable
fun AddBookScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoundBook>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<FoundBook?>(null) }

    val epubPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch { AppState.importAll(ctx, uris, openIfSingle = false) }
            onClose()
        }
    }
    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { Clippings.import(ctx, uri) }.onSuccess { r ->
                AppState.toast(
                    if (r.quotes == 0) "No había subrayados nuevos en ese archivo"
                    else "${r.quotes} citas en ${r.books} libros" + if (r.newBooks > 0) " (${r.newBooks} libros nuevos)" else ""
                )
                if (r.quotes > 0) { AppState.tab.intValue = 2; onClose() }
            }.onFailure { ErrorLog.note("Importar My Clippings", it); AppState.toast("No se pudo leer ese archivo") }
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); error = null; loading = false; return@LaunchedEffect }
        delay(550)
        loading = true
        error = null
        runCatching { BookSearch.search(q) }
            .onSuccess { results = it; if (it.isEmpty()) error = "Sin resultados. Prueba con otras palabras o escríbelo a mano." }
            .onFailure { results = emptyList(); error = "No hay conexión con el catálogo. Puedes escribirlo a mano." }
        loading = false
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (picked != null) picked = null else onClose() }
        Column(Modifier.fillMaxSize().background(NB.bg).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (picked == null) "Añadir libro" else "Revisa los datos", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = NB.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (picked != null) picked = null else onClose() }) { Icon(Icons.Filled.Close, "Cerrar", tint = NB.text) }
            }
            val p = picked
            if (p != null) {
                ConfirmBook(p, onSaved = { b -> onClose(); AppState.bookMenu.value = b.id })
                return@Column
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Título, autor o ISBN", color = NB.text3) },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = field(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (query.isBlank()) {
                Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    Text("Busca el libro que lees en Kindle, en papel o en audio y apunta tus lecturas aunque no lo leas en el móvil.",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(18.dp))
                    MenuRow("Escribirlo a mano") { picked = FoundBook("", "", null, 0, "", "", "", "", "") }
                    MenuRow("Importar EPUB desde el móvil") { epubPicker.launch(arrayOf("application/epub+zip", "application/octet-stream", "application/zip")) }
                    MenuRow("Importar subrayados del Kindle (My Clippings.txt)") { clipPicker.launch(arrayOf("text/plain", "*/*")) }
                    Spacer(Modifier.height(6.dp))
                    Text("Conecta el Kindle por cable: el archivo está en la carpeta documents.", color = NB.text3, fontSize = 12.sp)
                }
                return@Column
            }
            if (loading) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = NB.text, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Buscando…", color = NB.text2, fontSize = 14.sp)
                }
            }
            error?.let { Text(it, color = NB.text2, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            LazyColumn(Modifier.weight(1f)) {
                items(results) { r -> ResultRow(r) { picked = r } }
                item {
                    MenuRow("No está: escribirlo a mano", color = NB.text2) {
                        picked = FoundBook(query.trim(), "", null, 0, "", "", "", "", "")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: FoundBook, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(46.dp).height(69.dp).clip(RoundedCornerShape(4.dp)).background(NB.surface2)) {
            if (r.coverUrl != null) AsyncImage(r.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (r.author.isNotBlank()) Text(r.author, color = NB.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = listOfNotNull(r.year.ifBlank { null }, if (r.pages > 0) "${r.pages} págs." else null, r.publisher.ifBlank { null }).joinToString(" · ")
            if (info.isNotBlank()) Text(info, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConfirmBook(r: FoundBook, onSaved: (Book) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(r.title) }
    var author by remember { mutableStateOf(r.author) }
    var format by remember { mutableStateOf(BookFormat.Kindle) }
    var unit by remember { mutableStateOf(ProgressUnit.Percent) }
    var pages by remember { mutableStateOf(if (r.pages > 0) r.pages.toString() else "") }
    var status by remember { mutableStateOf(BookStatus.Reading) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row {
            Box(Modifier.width(84.dp).height(126.dp).clip(RoundedCornerShape(6.dp)).background(NB.surface2)) {
                if (r.coverUrl != null) AsyncImage(r.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, colors = field(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, colors = field(), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(18.dp))
        Label("¿Dónde lo lees?")
        ChipRow(listOf(BookFormat.Kindle, BookFormat.Paper, BookFormat.Audio, BookFormat.Other), format, { it.label }) { f ->
            format = f
            unit = if (f == BookFormat.Paper) ProgressUnit.Page else ProgressUnit.Percent
        }
        Spacer(Modifier.height(16.dp))
        Label("Cómo apuntas el progreso")
        ChipRow(ProgressUnit.entries.toList(), unit, { it.label }) { unit = it }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            pages, { pages = it.filter(Char::isDigit).take(6) },
            label = { Text(if (unit == ProgressUnit.Location) "Posiciones totales (opcional)" else "Páginas (opcional)") },
            singleLine = true, colors = field(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (unit == ProgressUnit.Location) Text("En el Kindle: Ir a → Posición muestra el total.", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Label("¿Cuándo lo vas a leer?")
        WHEN_OPTIONS.chunked(2).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (st, label) -> Chip(label, status == st) { status = st } }
            }
        }
        Spacer(Modifier.height(26.dp))
        PrimaryButton(if (saving) "Añadiendo…" else "Añadir a la biblioteca", Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !saving) {
            saving = true
            scope.launch {
                val b = runCatching {
                    Library.addManual(title, author, format, pages.toIntOrNull() ?: 0, unit, r.coverUrl, r.isbn, r.publisher, r.year, status, r.description)
                }.getOrElse { ErrorLog.note("Añadir libro", it); null }
                saving = false
                if (b != null) { AppState.toast("Añadido: ${b.title}"); onSaved(b) } else AppState.toast("No se pudo añadir")
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Label(t: String) {
    Text(t.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Chip(label(it), it == selected) { onPick(it) } }
    }
}

// ---------------------------------------------------------------------------------------------
// Apuntar una lectura hecha fuera de la app: de tal hora a tal hora, hasta tal punto
// ---------------------------------------------------------------------------------------------

/** Opciones al añadir un libro: lo encasilla directamente. */
val WHEN_OPTIONS = listOf(
    BookStatus.Reading to "Lo empiezo ahora",
    BookStatus.Next to "Será mi próximo libro",
    BookStatus.ToRead to "Para más adelante",
    BookStatus.Read to "Ya lo he leído",
)

/** Tras importar un EPUB: preguntar cuándo se va a leer. */
@Composable
fun WhenToReadDialog(bookId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val b = Library.get(bookId)
    if (b == null) { onDismiss(); return }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = {
            Column {
                Text("¿Cuándo lo vas a leer?", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                Text(b.title, fontSize = 14.sp, color = NB.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column {
                WHEN_OPTIONS.forEach { (st, label) ->
                    MenuRow(label) {
                        onDismiss()
                        when (st) {
                            BookStatus.Reading -> AppState.openBook(b)
                            BookStatus.ToRead -> AppState.toast("Añadido a pendientes")
                            else -> scope.launch { Library.setStatus(b.id, st) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Luego", color = NB.text2) } },
    )
}

private fun roundTo5(t: Long): Long = t - t % (5 * 60_000L)

@Composable
fun LogSessionDialog(r: LogRequest, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = Library.get(r.bookId)
    if (b == null) { onDismiss(); return }
    val hm = remember { SimpleDateFormat("HH:mm", ES) }
    val dayFmt = remember { SimpleDateFormat("EEEE d 'de' MMMM", ES) }

    var end by remember { mutableLongStateOf(if (r.end > 0) r.end else roundTo5(System.currentTimeMillis())) }
    var start by remember { mutableLongStateOf(if (r.start > 0) r.start else end - 30 * 60_000L) }
    val unit0 = if (b.isEpub) ProgressUnit.Percent else b.unit
    var unit by remember { mutableStateOf(unit0) }
    var total by remember { mutableStateOf(if (b.pages > 0) b.pages.toString() else "") }
    fun valueOf(p: Float, u: ProgressUnit, t: Int): String = when {
        p <= 0f -> ""
        u == ProgressUnit.Percent -> ((p * 100).toInt()).toString()
        t > 0 -> ((p * t).toInt()).toString()
        else -> ""
    }
    var value by remember { mutableStateOf(valueOf(b.progress, unit0, b.pages)) }
    var finished by remember { mutableStateOf(false) }

    // Si la hora de fin es anterior a la de inicio, se entiende que empezó el día antes (leer de noche).
    val effStart = if (start > end) start - 24 * 3600_000L else start
    val seconds = ((end - effStart) / 1000).coerceIn(0, 16 * 3600)
    val totalN = total.toIntOrNull() ?: 0
    val newP: Float? = when {
        finished -> 1f
        value.isBlank() -> null
        unit == ProgressUnit.Percent -> (value.toFloatOrNull() ?: -1f) / 100f
        totalN > 0 -> (value.toFloatOrNull() ?: -1f) / totalN
        else -> null
    }?.takeIf { it in 0f..1f }
    val needTotal = unit != ProgressUnit.Percent && totalN <= 0 && value.isNotBlank() && !finished

    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = end }
        DatePickerDialog(ctx, { _, y, m, d ->
            fun move(t: Long) = Calendar.getInstance().apply { timeInMillis = t; set(y, m, d) }.timeInMillis
            val dur = end - start
            end = move(end); start = end - dur
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }
    fun pickTime(t: Long, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        TimePickerDialog(ctx, { _, h, m ->
            onPick(Calendar.getInstance().apply { timeInMillis = end; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NB.surface2,
        title = {
            Column {
                Text(when {
                    r.onlyProgress -> "Actualizar progreso"
                    r.start > 0 && r.end > 0 && r.end - r.start < 24 * 3600_000L && r.end >= System.currentTimeMillis() - 60_000L -> "¿Guardar esta lectura?"
                    else -> "Apuntar lectura"
                }, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                Text(b.title, fontSize = 14.sp, color = NB.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!r.onlyProgress) {
                    TapField("Día", dayFmt.format(Date(end)).replaceFirstChar { it.uppercase() }) { pickDate() }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TapField("De", hm.format(Date(start)), Modifier.weight(1f)) { pickTime(start) { start = it } }
                        Spacer(Modifier.width(10.dp))
                        TapField("A", hm.format(Date(end)), Modifier.weight(1f)) { pickTime(end) { end = it } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (seconds >= 60) "${durationLabel(seconds)} de lectura" else "Elige la hora de inicio y de fin",
                        color = NB.text2, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                }
                Text("HASTA DÓNDE", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                if (!b.isEpub) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProgressUnit.entries.forEach { u ->
                            Chip(when (u) { ProgressUnit.Percent -> "%"; ProgressUnit.Page -> "Página"; ProgressUnit.Location -> "Posición" }, unit == u) {
                                if (unit != u) { unit = u; value = valueOf(b.progress, u, totalN) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value, { value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.').take(7) },
                        label = { Text(when (unit) { ProgressUnit.Percent -> "%"; ProgressUnit.Page -> "Página"; ProgressUnit.Location -> "Posición" }) },
                        singleLine = true, enabled = !finished, colors = field(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions.Default,
                        modifier = Modifier.weight(1f),
                    )
                    if (unit != ProgressUnit.Percent) {
                        Text("  de  ", color = NB.text3)
                        OutlinedTextField(
                            total, { total = it.filter(Char::isDigit).take(6) },
                            label = { Text("Total") }, singleLine = true, enabled = !finished, colors = field(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (needTotal) Text("Pon el total para calcular el porcentaje.", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().clickable { finished = !finished }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(finished, { finished = it }, colors = CheckboxDefaults.colors(checkedColor = NB.text, checkmarkColor = NB.onText, uncheckedColor = NB.text3))
                    Text("Lo he terminado", color = NB.text, fontSize = 15.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (r.onlyProgress && newP != null) || (!r.onlyProgress && (seconds >= 60 || newP != null)),
                onClick = {
                    scope.launch {
                        val oldP = b.progress
                        if (!b.isEpub) Library.update(b.id) { it.copy(unit = unit, pages = if (unit != ProgressUnit.Percent && totalN > 0) totalN else it.pages) }
                        val pagesKnown = if (unit == ProgressUnit.Page && totalN > 0) totalN else if (unit != ProgressUnit.Location && b.pages > 0 && b.unit == ProgressUnit.Page) b.pages else 0
                        val pagesRead = if (newP != null && pagesKnown > 0) ((newP - oldP) * pagesKnown).toInt().coerceAtLeast(0) else 0
                        if (!r.onlyProgress && seconds >= 60) Stats.addSession(b.id, effStart, seconds, pagesRead)
                        if (newP != null) Library.setProgress(b.id, newP)
                        val msg = buildList {
                            if (!r.onlyProgress && seconds >= 60) add(durationLabel(seconds))
                            if (newP != null) add(if (newP >= 0.999f) "terminado" else "${(newP * 100).toInt()} %")
                        }.joinToString(" · ")
                        AppState.toast("Apuntado: $msg")
                        onDismiss()
                    }
                },
            ) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
    )
}

@Composable
private fun TapField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NB.surface)
            .border(1.dp, NB.line, RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = NB.text3, fontSize = 12.sp)
        Text(value, color = NB.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------------------------
// Cronómetro para leer fuera del móvil
// ---------------------------------------------------------------------------------------------

@Composable
fun TimerBar() {
    val t = AppState.timer.value ?: return
    val b = Library.get(t.first) ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(t) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val s = ((now - t.second) / 1000).coerceAtLeast(0)
    val clock = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    Row(
        Modifier.fillMaxWidth().background(NB.surface2).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("LEYENDO EN ${b.format.label.uppercase()}", fontSize = 10.sp, letterSpacing = 1.2.sp, color = NB.silver)
            Text(b.title, color = NB.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(clock, color = NB.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)
        Spacer(Modifier.width(12.dp))
        PlayPauseButton(b.id, size = 34)
        IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Filled.Close, "Descartar", tint = NB.text3) }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false }, containerColor = NB.surface2,
            title = { Text("¿Descartar esta lectura?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = { Text("No se apuntará el tiempo.", color = NB.text2) },
            confirmButton = { TextButton(onClick = { AppState.discardTimer(); confirmDiscard = false }) { Text("Descartar", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir", color = NB.text2) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Historial de lecturas (para revisar o borrar una mal apuntada) y elegir libro
// ---------------------------------------------------------------------------------------------

@Composable
fun SessionRows(list: List<com.noirbooks.app.data.Session>, showBook: Boolean) {
    val books by Library.books
    val byId = books.associateBy { it.id }
    val hm = remember { SimpleDateFormat("HH:mm", ES) }
    val day = remember { SimpleDateFormat("EEE d MMM", ES) }
    var confirm by remember { mutableStateOf<com.noirbooks.app.data.Session?>(null) }
    if (list.isEmpty()) {
        Text("Todavía no hay lecturas apuntadas.", color = NB.text3, fontSize = 13.sp)
    }
    list.forEach { s ->
        val b = byId[s.bookId]
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBook && b != null) { BookCover(b, Modifier.width(30.dp), titleSize = 6); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                if (showBook) Text(b?.title ?: "Libro eliminado", color = NB.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (showBook) "" else day.format(Date(s.start)).replaceFirstChar { it.uppercase() } + " · ") +
                        "${hm.format(Date(s.start))}–${hm.format(Date(s.end))} · ${durationLabel(s.seconds)}",
                    color = if (showBook) NB.text2 else NB.text, fontSize = 13.sp,
                )
                Text(if (s.manual) "Apuntada a mano" else "Leída en la app" + if (s.pages > 0) " · ${s.pages} páginas" else "",
                    color = NB.text3, fontSize = 12.sp)
            }
            Text("Borrar", color = NB.text3, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirm = s }.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
    confirm?.let { s -> DeleteReadingDialog(s.bookId, session = s, onDismiss = { confirm = null }) }
}

@Composable
fun SessionsDialog(bookId: String, onDismiss: () -> Unit) {
    val v by Stats.version
    val list = remember(v) { Stats.sessions(bookId) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text("Lecturas", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { SessionRows(list, showBook = false) } },
        confirmButton = { TextButton(onClick = { onDismiss(); AppState.logSession.value = LogRequest(bookId) }) { Text("Apuntar lectura", color = NB.text) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = NB.text2) } },
    )
}

/** Elegir libro para apuntar una lectura (primero los que estás leyendo). */
@Composable
fun BookPickerDialog(onPick: (Book) -> Unit, onDismiss: () -> Unit) {
    val books by Library.books
    val list = books.filter { !it.inTrash }.sortedWith(
        compareBy<Book>({ when (it.status) { BookStatus.Reading -> 0; BookStatus.Next -> 1; BookStatus.ToRead -> 2; else -> 3 } }, { -maxOf(it.lastOpened, it.addedAt) })
    )
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text("¿Qué has leído?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) Text("Añade antes un libro a tu biblioteca.", color = NB.text2)
                list.forEach { b ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(b) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BookCover(b, Modifier.width(30.dp), titleSize = 6)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, color = NB.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.status.label + if (!b.isEpub) " · ${b.format.label}" else "", color = NB.text3, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Play / pausa para contar el tiempo cuando lees fuera de la app
// ---------------------------------------------------------------------------------------------

/** Tiempo transcurrido desde [start], actualizado cada segundo. */
@Composable
fun TimerText(start: Long, prefix: String = "", color: Color = NB.text, size: Int = 12) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val s = ((now - start) / 1000).coerceAtLeast(0)
    val clock = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    Text(prefix + clock, color = color, fontSize = size.sp, fontFamily = Inter, fontWeight = FontWeight.Medium)
}

/**
 * Botón redondo: play empieza a contar el tiempo de lectura de ese libro; pausa lo para y pide confirmar
 * la lectura (hora de inicio y fin ya puestas, solo falta hasta dónde has llegado).
 */
@Composable
fun PlayPauseButton(bookId: String, size: Int = 36) {
    val running = AppState.timer.value?.first == bookId
    val ctx = LocalContext.current
    // Android 13+: permiso para la notificación del cronómetro (si se niega, el cronómetro funciona igual dentro de la app).
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) AppState.timer.value?.let { com.noirbooks.app.data.TimerNotification.show(it.first, it.second) }
    }
    Box(
        Modifier.size(size.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (running) NB.text else Color.Transparent)
            .border(1.dp, if (running) NB.text else NB.text3, androidx.compose.foundation.shape.CircleShape)
            .clickable {
                if (running) AppState.stopTimer()
                else {
                    if (AppState.timer.value != null) AppState.stopTimer()
                    AppState.startTimer(bookId)
                    AppState.toast("Contando tu tiempo de lectura. Pulsa pausa al terminar.")
                    if (!com.noirbooks.app.data.TimerNotification.canNotify(ctx) && android.os.Build.VERSION.SDK_INT >= 33) {
                        askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (running) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Box(Modifier.width(3.dp).height((size * 0.36f).dp).clip(RoundedCornerShape(1.dp)).background(NB.onText)) }
            }
        } else {
            Icon(Icons.Filled.PlayArrow, "Empezar a contar el tiempo de lectura", tint = NB.text, modifier = Modifier.size((size * 0.55f).dp))
        }
    }
}

/**
 * Borrar lecturas: solo esta (una lectura del registro), solo lo de un día, o todo lo de ese libro.
 * Nunca cambia el progreso del libro.
 */
@Composable
fun DeleteReadingDialog(bookId: String, session: com.noirbooks.app.data.Session? = null, date: String? = null, onDismiss: () -> Unit) {
    val title = Library.get(bookId)?.title ?: "este libro"
    val total = Stats.secondsForBook(bookId)
    var confirmAll by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    if (editing && session != null) { EditSessionDialog(session, onDismiss = onDismiss); return }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text(if (confirmAll) "¿Borrar todo lo leído?" else "Lectura", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (confirmAll) {
                    Text("Se borran todas las lecturas de «$title» (${durationLabel(total)}) del registro, el calendario y las estadísticas. " +
                        "El libro, su progreso y sus citas se quedan.", color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
                } else {
                    Text("El progreso del libro no cambia.", color = NB.text3, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (session != null) MenuRow("Editar esta lectura") { editing = true }
                    if (session != null) MenuRow("Borrar esta lectura (${durationLabel(session.seconds)})") {
                        Stats.deleteSession(session.id); AppState.toast("Lectura borrada"); onDismiss()
                    }
                    if (date != null) MenuRow("Borrar lo leído este día") {
                        Stats.deleteDay(date, bookId); AppState.toast("Borrado lo de ese día"); onDismiss()
                    }
                    MenuRow("Borrar todas las lecturas de este libro", color = Color(0xFFFF6B6B)) { confirmAll = true }
                }
            }
        },
        confirmButton = {
            if (confirmAll) TextButton(onClick = { Stats.deleteBook(bookId); AppState.toast("Lecturas de «$title» borradas"); onDismiss() }) {
                Text("Borrar todo", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
    )
}

/**
 * Si la app se cerró con el libro abierto sin confirmar la lectura, se pregunta al volver a abrirla.
 */
@Composable
fun PendingReadingPrompt() {
    val scope = rememberCoroutineScope()
    var data by remember {
        mutableStateOf(
            if (AppState.pendingChecked) null
            else { AppState.pendingChecked = true; com.noirbooks.app.data.Prefs.pendingReading.takeIf { it.isNotBlank() } }
        )
    }
    val raw = data ?: return
    val o = runCatching { org.json.JSONObject(raw) }.getOrNull()
    val b = o?.optString("book")?.let { Library.get(it) }
    if (o == null || b == null) { com.noirbooks.app.data.Prefs.pendingReading = ""; data = null; return }
    val arr = o.optJSONArray("chunks") ?: org.json.JSONArray()
    val chunks = (0 until arr.length()).mapNotNull { i ->
        arr.optJSONArray(i)?.let { Stats.Chunk(it.optLong(0), it.optLong(1), it.optInt(2) == 1) }
    }
    val secs = chunks.sumOf { it.seconds }
    fun done() { com.noirbooks.app.data.Prefs.pendingReading = ""; data = null }
    AlertDialog(
        onDismissRequest = {}, containerColor = NB.surface2,
        title = { Text("Lectura sin guardar", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("«${b.title}»" + if (secs > 0) " · ${durationLabel(secs)}" else "", color = NB.text, fontSize = 15.sp)
                Text("La app se cerró antes de confirmar. ¿La guardas?", color = NB.text2, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                MenuRow("Guardar lectura") { Stats.commitAppReading(b.id, chunks); done() }
                MenuRow("No guardar") {
                    val ch = o.optInt("ch"); val f = o.optDouble("f", 0.0).toFloat(); val a = o.optString("a")
                    scope.launch { Library.update(b.id) { it.copy(chapter = ch, fraction = f, anchor = a) } }
                    done()
                }
            }
        },
        confirmButton = {},
    )
}

/** Corregir una lectura: día, de qué hora a qué hora y páginas. */
@Composable
fun EditSessionDialog(s: com.noirbooks.app.data.Session, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val hm = remember { SimpleDateFormat("HH:mm", ES) }
    val dayFmt = remember { SimpleDateFormat("EEEE d 'de' MMMM", ES) }
    var end by remember { mutableLongStateOf(s.end) }
    var start by remember { mutableLongStateOf(s.start) }
    var pages by remember { mutableStateOf(if (s.pages > 0) s.pages.toString() else "") }
    val effStart = if (start > end) start - 24 * 3600_000L else start
    val seconds = ((end - effStart) / 1000).coerceIn(0, 16 * 3600)

    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = end }
        DatePickerDialog(ctx, { _, y, m, d ->
            val dur = end - start
            end = Calendar.getInstance().apply { timeInMillis = end; set(y, m, d) }.timeInMillis
            start = end - dur
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }
    fun pickTime(t: Long, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        TimePickerDialog(ctx, { _, h, m ->
            onPick(Calendar.getInstance().apply { timeInMillis = end; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = {
            Column {
                Text("Editar lectura", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                Text(Library.get(s.bookId)?.title.orEmpty(), fontSize = 14.sp, color = NB.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column {
                TapField("Día", dayFmt.format(Date(end)).replaceFirstChar { it.uppercase() }) { pickDate() }
                Spacer(Modifier.height(8.dp))
                Row {
                    TapField("De", hm.format(Date(start)), Modifier.weight(1f)) { pickTime(start) { start = it } }
                    Spacer(Modifier.width(10.dp))
                    TapField("A", hm.format(Date(end)), Modifier.weight(1f)) { pickTime(end) { end = it } }
                }
                Spacer(Modifier.height(6.dp))
                Text(if (seconds >= 60) "${durationLabel(seconds)} de lectura" else "Elige la hora de inicio y de fin", color = NB.text2, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    pages, { pages = it.filter(Char::isDigit).take(5) }, label = { Text("Páginas leídas") },
                    singleLine = true, colors = field(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("El progreso del libro no cambia.", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = seconds >= 60, onClick = {
                Stats.editSession(s.id, effStart, seconds, pages.toIntOrNull() ?: 0)
                AppState.toast("Lectura corregida")
                onDismiss()
            }) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
    )
}
