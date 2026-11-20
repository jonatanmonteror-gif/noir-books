package com.noirbooks.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.durationLabel
import com.noirbooks.app.data.minutesLabel
import com.noirbooks.app.data.percentLabel
import com.noirbooks.app.data.printedPageLabel
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EPUB_TYPES = arrayOf("application/epub+zip", "application/octet-stream", "application/zip")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by Library.books
    val books = all.filter { !it.inTrash }
    val importing by AppState.importing
    var sort by remember { mutableStateOf(Prefs.sortOrder) }
    var columns by remember { mutableStateOf(Prefs.libraryColumns.coerceIn(2, 4)) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val cpm = remember(all.size) { Stats.charsPerMinute() }

    fun open(b: Book) = AppState.openBook(b)

    val current = books.filter { it.status == BookStatus.Reading }.maxByOrNull { it.lastOpened }
    val q = fold(query.trim())
    val shown = books
        .filter { q.isEmpty() || fold(it.title).contains(q) || fold(it.author).contains(q) }
        .filter { filter == "all" || it.status.key == filter }
        .let { list ->
            when (sort) {
                "title" -> list.sortedBy { fold(it.title) }
                "author" -> list.sortedWith(compareBy({ fold(it.author.ifBlank { "~" }) }, { fold(it.title) }))
                "rating" -> list.sortedByDescending { it.rating }
                else -> list.sortedByDescending { maxOf(it.lastOpened, it.addedAt) }
            }
        }

    Column(Modifier.fillMaxSize().background(NB.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text("Noir", fontFamily = Playfair, fontSize = 30.sp, color = NB.text)
                Spacer(Modifier.width(8.dp))
                Text(
                    "BOOKS", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                    letterSpacing = 3.sp, color = NB.silver, modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            if (books.isNotEmpty()) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Buscar", tint = NB.text)
                }
            }
            IconButton(onClick = { AppState.screen.value = Screen.Settings }) {
                Icon(Icons.Filled.Settings, "Ajustes", tint = NB.text)
            }
            IconButton(onClick = { AppState.addBook.value = true }) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(NB.text), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Añadir libros", tint = NB.onText, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Título o autor", color = NB.text3) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line,
                    focusedContainerColor = NB.surface, unfocusedContainerColor = NB.surface, cursorColor = NB.text,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (importing > 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = NB.text, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (importing == 1) "Añadiendo libro…" else "Añadiendo $importing libros…", color = NB.text2, fontSize = 14.sp)
            }
        }

        if (books.isEmpty()) {
            EmptyLibrary(onAdd = { AppState.addBook.value = true })
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (columns >= 4) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (columns >= 4) 14.dp else 18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (current != null && q.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                    ContinueCard(current, cpm, onOpen = { open(current) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Biblioteca", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = NB.text)
                        Spacer(Modifier.width(10.dp))
                        Text("${shown.size}", color = NB.text3, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Todos", filter == "all") { filter = "all" }
                        BookStatus.entries.forEach { st ->
                            val n = books.count { it.status == st }
                            if (n > 0) Chip("${st.label} · $n", filter == st.key) { filter = st.key }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("recent" to "Recientes", "title" to "Título", "author" to "Autor", "rating" to "Valoración").forEach { (k, label) ->
                            Text(
                                label, fontSize = 13.sp, fontFamily = Inter,
                                fontWeight = if (sort == k) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sort == k) NB.text else NB.text3,
                                modifier = Modifier.clickable { sort = k; Prefs.sortOrder = k }.padding(vertical = 4.dp),
                            )
                        }
                    }
                    // Portadas por fila: 2, 3 o 4
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (2..4).forEach { n ->
                            Text(
                                "$n", fontSize = 13.sp, fontFamily = Inter,
                                fontWeight = if (columns == n) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (columns == n) NB.onText else NB.text3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(26.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (columns == n) NB.text else Color.Transparent)
                                    .clickable { columns = n; Prefs.libraryColumns = n }.padding(vertical = 3.dp),
                            )
                        }
                    }
                    }
                }
            }
            items(shown, key = { it.id }) { b ->
                Column(Modifier.combinedClickable(onClick = { open(b) }, onLongClick = { AppState.bookMenu.value = b.id })) {
                    BookCover(b, Modifier.fillMaxWidth(), titleSize = if (columns >= 4) 11 else if (columns == 3) 13 else 16)
                    if (b.status == BookStatus.Reading) {
                        Spacer(Modifier.height(6.dp))
                        ThinProgress(b.progress, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        b.title, color = NB.text, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = when {
                        b.rating > 0f -> "★ ${ratingLabel(b.rating)}" + if (b.author.isNotBlank()) " · ${b.author}" else ""
                        b.status == BookStatus.Read -> "Leído" + if (b.author.isNotBlank()) " · ${b.author}" else ""
                        b.status == BookStatus.Abandoned -> "Abandonado"
                        b.status == BookStatus.Next -> "Próximo" + if (b.author.isNotBlank()) " · ${b.author}" else ""
                        !b.isEpub && b.status == BookStatus.Reading -> b.format.label + " · " + b.progressLabel()
                        else -> b.author
                    }
                    if (sub.isNotBlank()) Text(sub, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

}

@Composable
private fun ContinueCard(b: Book, cpm: Int, onOpen: () -> Unit) {
    val left = (b.totalChars * (1f - b.progress)).toInt()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(NB.surface2, NB.surface)))
            .clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(b, Modifier.width(92.dp), titleSize = 11)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(if (b.isEpub) "SEGUIR LEYENDO" else "LEYENDO EN ${b.format.label.uppercase()}", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(b.title, fontFamily = Playfair, fontSize = 21.sp, lineHeight = 25.sp, color = NB.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank()) Text(b.author, color = NB.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            ThinProgress(b.progress, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            val running = AppState.timer.value?.takeIf { it.first == b.id }
            if (running != null) {
                TimerText(running.second, prefix = "Leyendo · ")
            } else if (b.isEpub) {
                Text("${percentLabel(b.progress)} · quedan ${minutesLabel(left, cpm)}", color = NB.text3, fontSize = 12.sp)
            } else {
                Text("${b.format.label} · ${b.progressLabel()}", color = NB.text3, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (b.isEpub) Chip("Seguir leyendo", true, onOpen)
                else Chip("Apuntar lectura", true) { AppState.logSession.value = LogRequest(b.id) }
                PlayPauseButton(b.id)
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) NB.text else Color.Transparent)
            .border(1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) NB.onText else NB.text2, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tu biblioteca está vacía", fontFamily = Playfair, fontSize = 26.sp, color = NB.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Busca el libro que lees (en Kindle, en papel o en audio) o importa un EPUB para leerlo aquí. Lo que leas aparecerá en tu calendario y tus estadísticas.",
            color = NB.text2, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(NB.text).clickable(onClick = onAdd)
                .padding(horizontal = 26.dp, vertical = 13.dp),
        ) {
            Text("Añadir un libro", color = NB.onText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Los libros con protección DRM (la mayoría de Kindle y algunos de Kobo) no se pueden abrir.",
            color = NB.text3, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

private val ABANDON_REASONS = listOf("Lento", "No era el momento", "No me engancha", "No me gusta cómo está escrito", "Otro")

/** Ficha del libro (pulsación larga): estado, valoración, relecturas, abandonar o mover a la papelera. */
@Composable
fun BookMenu(bookId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { Library.setCover(ctx, bookId, uri) }
    }
    val all by Library.books
    val b = all.firstOrNull { it.id == bookId }
    if (b == null) { onDismiss(); return }
    var abandoning by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var deleteAll by remember { mutableStateOf(false) }
    var editPages by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("d MMM yyyy", Locale("es", "ES"))

    if (abandoning) {
        AlertDialog(
            onDismissRequest = { abandoning = false },
            containerColor = NB.surface2,
            title = { Text("¿Por qué lo dejas?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Se guarda dónde lo dejaste, por si vuelves.", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    ABANDON_REASONS.forEach { r ->
                        MenuRow(r) {
                            scope.launch { Library.setStatus(b.id, BookStatus.Abandoned, r) }
                            abandoning = false; onDismiss()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { abandoning = false }) { Text("Cancelar", color = NB.text2) } },
        )
        return
    }
    if (history) { SessionsDialog(b.id, onDismiss = { history = false }); return }
    if (deleteAll) { DeleteReadingDialog(b.id, onDismiss = { deleteAll = false }); return }
    if (editPages) {
        var t by remember { mutableStateOf(if (b.pages > 0) b.pages.toString() else "") }
        AlertDialog(
            onDismissRequest = { editPages = false }, containerColor = NB.surface2,
            title = { Text("Páginas en papel", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Con las páginas de tu edición, el lector te dice por qué página del libro irías.", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(t, { t = it.filter(Char::isDigit).take(5) }, singleLine = true, label = { Text("Páginas") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text))
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { Library.update(b.id) { it.copy(pages = t.toIntOrNull() ?: 0) } }; editPages = false }) { Text("Guardar", color = NB.text) } },
            dismissButton = { TextButton(onClick = { editPages = false }) { Text("Cancelar", color = NB.text2) } },
        )
        return
    }
    if (reviewing) {
        NoteDialog("Tu opinión (privada)", b.review,
            onSave = { t -> scope.launch { Library.update(b.id) { it.copy(review = t) } }; reviewing = false },
            onDismiss = { reviewing = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NB.surface2,
        title = {
            Column {
                Text(b.title, fontFamily = Playfair, fontSize = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotBlank()) Text(b.author, fontSize = 14.sp, color = NB.text2)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StarRating(b.rating, size = 30) { v -> scope.launch { Library.update(b.id) { it.copy(rating = v) } } }
                Spacer(Modifier.height(10.dp))
                val secs = Stats.secondsForBook(b.id)
                val info = buildString {
                    append(b.status.label)
                    if (b.status == BookStatus.Reading) append(" · ${b.progressLabel()}")
                    if (b.status == BookStatus.Abandoned && b.abandonReason.isNotBlank()) append(" · ${b.abandonReason}")
                    if (b.isEpub) append("\n${b.chapterLengths.size} capítulos · unas ${minutesLabel(b.totalChars)} de lectura")
                    if (b.isEpub && b.status == BookStatus.Reading) append("\nVas por la ${b.printedPageLabel(b.progress).replace("p. ", "página ")}")
                    else append("\n${b.format.label}" + if (b.pages > 0) " · ${b.pages} ${if (b.unit == com.noirbooks.app.data.ProgressUnit.Location) "posiciones" else "páginas"}" else "")
                    if (!b.isEpub && (b.publisher.isNotBlank() || b.year.isNotBlank())) append("\n" + listOf(b.publisher, b.year).filter { it.isNotBlank() }.joinToString(", "))
                    if (secs > 0) append("\nLlevas ${durationLabel(secs)} leyendo este libro")
                    if (b.timesRead > 1) append("\nLeído ${b.timesRead} veces")
                    b.readings.lastOrNull { it.end > 0 }?.let { append("\nTerminado el ${date.format(Date(it.end))}") }
                    if (b.highlights.isNotEmpty()) append("\n${b.highlights.size} citas")
                }
                Text(info, color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
                if (b.review.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(b.review, color = NB.text, fontSize = 14.sp, fontFamily = Playfair)
                }
                Spacer(Modifier.height(10.dp))
                if (b.isEpub) MenuRow(if (b.status == BookStatus.Read) "Volver a leer" else "Leer") { onDismiss(); AppState.screen.value = Screen.Reader(b.id) }
                if (!b.isEpub && b.status != BookStatus.Read) {
                    if (AppState.timer.value?.first == b.id) MenuRow("Terminar cronómetro") { onDismiss(); AppState.stopTimer() }
                    else MenuRow("Empiezo a leer (cronómetro)") { onDismiss(); AppState.startTimer(b.id) }
                }
                MenuRow(if (b.isEpub) "Apuntar lectura hecha fuera de la app" else "Apuntar lectura") { onDismiss(); AppState.logSession.value = LogRequest(b.id) }
                if (!b.isEpub) MenuRow("Actualizar progreso") { onDismiss(); AppState.logSession.value = LogRequest(b.id, onlyProgress = true) }
                MenuRow("Historial de lecturas (${Stats.sessions(b.id).size})") { history = true }
                if (b.isEpub) MenuRow(if (b.pages > 0) "Páginas en papel: ${b.pages}" else "Páginas en papel…") { editPages = true }
                MenuRow(if (b.hasCover) "Cambiar portada" else "Poner portada") { coverPicker.launch("image/*") }
                if (b.status != BookStatus.Read) MenuRow("Marcar como leído") {
                    scope.launch { Library.setStatus(b.id, BookStatus.Read) }; onDismiss()
                }
                if (b.status == BookStatus.ToRead || b.status == BookStatus.Abandoned) MenuRow("Será mi próximo libro") {
                    scope.launch { Library.setStatus(b.id, BookStatus.Next) }; onDismiss()
                }
                if (b.status != BookStatus.ToRead) MenuRow("Pasar a pendientes") {
                    scope.launch { Library.setStatus(b.id, BookStatus.ToRead) }; onDismiss()
                }
                if (b.status == BookStatus.Reading) MenuRow("Abandonar…") { abandoning = true }
                MenuRow(if (b.review.isBlank()) "Escribir tu opinión" else "Editar tu opinión") { reviewing = true }
                if (Stats.secondsForBook(b.id) > 0 || Stats.sessions(b.id).isNotEmpty()) MenuRow("Borrar lecturas…", color = Color(0xFFFF6B6B)) { deleteAll = true }
                MenuRow("Mover a la papelera", color = Color(0xFFFF6B6B)) {
                    scope.launch { Library.moveToTrash(b.id) }
                    AppState.toast("Movido a la papelera (Ajustes → Papelera)")
                    onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = NB.text2) } },
    )
}

@Composable
fun MenuRow(label: String, color: Color = NB.text, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** Para buscar y ordenar sin distinguir mayúsculas ni tildes. */
fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
