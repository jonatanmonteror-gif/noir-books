package com.noirbooks.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.layout.onSizeChanged
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
import com.noirbooks.app.data.BookFormat
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

private val EPUB_TYPES = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream", "application/zip")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by Library.books
    val books = all.filter { !it.inTrash }
    val importing by AppState.importing
    var sort by remember { mutableStateOf(Prefs.sortOrder) }
    // Libros por fila: se elige en Ajustes → Apariencia
    val columns = remember { Prefs.libraryColumns.coerceIn(2, 4) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val cpm = remember(all.size) { Stats.charsPerMinute() }
    val shelves by Library.shelves
    var shelf by rememberSaveable { mutableStateOf<String?>(null) }
    if (shelf != null && shelf != NO_SHELF && !isGenreShelf(shelf) && !isSeriesShelf(shelf) && shelf !in shelves) shelf = null
    // Tres vistas: Inicio (lo que lees y lo reciente), Biblioteca (todos) y Estanterías
    var mode by rememberSaveable { mutableStateOf("home") }
    var addToShelf by remember { mutableStateOf<String?>(null) }
    // Selección de varios libros para moverlos en grupo
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var shelvesFor by remember { mutableStateOf<Set<String>?>(null) }
    var statusFor by remember { mutableStateOf<Set<String>?>(null) }
    var genreFor by remember { mutableStateOf<Set<String>?>(null) }
    var newShelf by remember { mutableStateOf(false) }
    var editShelf by remember { mutableStateOf<String?>(null) }
    fun endSelection() { selecting = false; selected = emptySet() }
    BackHandler(enabled = selecting) { endSelection() }

    fun open(b: Book) = AppState.openBook(b)
    /** Tocar un libro de la biblioteca abre su ficha, no el libro. */
    fun details(b: Book) { AppState.screen.value = Screen.Book(b.id) }

    val reading = books.filter { it.status == BookStatus.Reading }.sortedByDescending { maxOf(it.lastOpened, it.addedAt) }
    val q = fold(query.trim())
    val shown = books
        .filter { q.isEmpty() || fold(it.title).contains(q) || fold(it.author).contains(q) }
        .filter {
            when (filter) {
                "all" -> true
                "wish" -> it.wish
                "loan" -> it.onLoan
                else -> it.status.key == filter && !it.wish
            }
        }
        .filter { inShelf(it, shelf) }
        .let { list ->
            if (isSeriesShelf(shelf)) list.sortedWith(compareBy({ seriesOrder(it) }, { fold(it.title) }))
            else when (sort) {
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
                IconButton(onClick = { searching = !searching; if (!searching) query = "" else mode = "all" }) {
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

        // Pestañas de la biblioteca
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("home" to "Inicio", "all" to "Biblioteca", "shelves" to "Estanterías").forEach { (k, label) ->
                val on = mode == k
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) NB.text else NB.surface)
                        .clickable { mode = k; if (k != "all") endSelection() }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) NB.onText else NB.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        BackHandler(enabled = mode != "home" && !selecting) { mode = "home" }
        if (mode == "home" && !searching) {
            HomePane(books, reading, cpm,
                onOpen = { open(it) }, onDetails = { details(it) },
                onSeeAll = { mode = "all" },
                onStatus = { filter = it; shelf = null; mode = "all" },
                onShelf = { shelf = it; mode = "all" },
                onShelves = { mode = "shelves" })
            return@Column
        }
        if (mode == "shelves" && !searching) {
            ShelvesPane(books, shelf, onPick = { shelf = it; mode = "all" }, onBook = { details(it) })
            return@Column
        }

        if (selecting) {
            Row(
                Modifier.fillMaxWidth().background(NB.surface2).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (selected.isEmpty()) "Toca libros" else "✓ ${selected.size}", color = NB.text, fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.weight(1f))
                TextButton(enabled = selected.isNotEmpty(), onClick = { genreFor = selected }) { Text("Género", color = NB.text) }
                TextButton(enabled = selected.isNotEmpty(), onClick = { shelvesFor = selected }) { Text("Estantería", color = NB.text) }
                TextButton(enabled = selected.isNotEmpty(), onClick = { statusFor = selected }) { Text("Estado", color = NB.text) }
                IconButton(onClick = { endSelection() }) { Icon(Icons.Filled.Close, "Terminar selección", tint = NB.text2) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (columns >= 4) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (columns >= 4) 14.dp else 18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Biblioteca", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = NB.text)
                        Spacer(Modifier.width(10.dp))
                        Text("${shown.size}", color = NB.text3, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text("Seleccionar", color = NB.text2, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selecting = true }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Todos", filter == "all") { filter = "all" }
                        BookStatus.entries.forEach { st ->
                            val n = books.count { it.status == st && !it.wish }
                            if (n > 0) Chip("${st.label} · $n", filter == st.key) { filter = st.key }
                        }
                        books.count { it.wish }.let { n -> if (n > 0) Chip("Lo quiero · $n", filter == "wish") { filter = "wish" } }
                        books.count { it.onLoan }.let { n -> if (n > 0) Chip("Prestados · $n", filter == "loan") { filter = "loan" } }
                    }
                    // Estantería elegida (desde el icono de arriba): una sola franja, no una fila de etiquetas
                    shelf?.let { s ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NB.surface2)
                                .clickable { mode = "shelves" }.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(androidx.compose.ui.res.painterResource(com.noirbooks.app.R.drawable.ic_shelves), null, tint = NB.text, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(shelfLabel(s), color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (s != NO_SHELF && !isGenreShelf(s)) TextButton(onClick = { addToShelf = s }) { Text("Añadir", color = NB.text2, fontSize = 14.sp) }
                            IconButton(onClick = { shelf = null }) { Icon(Icons.Filled.Close, "Quitar filtro", tint = NB.text2, modifier = Modifier.size(20.dp)) }
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
                    }
                }
            }
            items(shown, key = { it.id }) { b ->
                val isSel = b.id in selected
                Column(Modifier.combinedClickable(
                    onClick = { if (selecting) selected = if (isSel) selected - b.id else selected + b.id else details(b) },
                    // Mantener pulsado = seleccionar varios (para meterlos en estanterías o cambiar su estado)
                    onLongClick = { selecting = true; selected = selected + b.id },
                )) {
                    Box {
                        BookCover(b, Modifier.fillMaxWidth(), titleSize = if (columns >= 4) 11 else if (columns == 3) 13 else 16)
                        if (b.onLoan) CoverBadge("Prestado", Modifier.align(Alignment.BottomStart))
                        else if (b.wish) CoverBadge("Lo quiero", Modifier.align(Alignment.BottomStart))
                        if (selecting) {
                            Box(Modifier.matchParentSize().background(if (isSel) Color.Black.copy(alpha = 0.35f) else Color.Transparent))
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                                    .background(if (isSel) NB.text else Color.Black.copy(alpha = 0.35f))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (isSel) Text("✓", color = NB.onText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
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

    shelvesFor?.let { ids -> ShelvesDialog(ids, onDismiss = { shelvesFor = null; endSelection() }) }
    statusFor?.let { ids -> StatusManyDialog(ids, onDismiss = { statusFor = null; endSelection() }) }
    // Género para varios a la vez: con él se forman solas las estanterías de género
    genreFor?.let { ids ->
        val scope = rememberCoroutineScope()
        val common = books.filter { it.id in ids }.map { it.genre }.distinct().singleOrNull().orEmpty()
        GenreDialog(common, onPick = { g ->
            scope.launch { ids.forEach { id -> Library.update(id) { it.copy(genre = g) } } }
            AppState.toast(if (g.isBlank()) "Género quitado de ${ids.size} libros" else "${ids.size} libros en «$g»")
            genreFor = null; endSelection()
        }, onDismiss = { genreFor = null })
    }
    if (newShelf) NewShelfDialog { n -> newShelf = false; if (n != null) shelf = n }
    editShelf?.let { s -> EditShelfDialog(s) { n -> editShelf = null; if (shelf == s) shelf = n } }
    addToShelf?.let { s -> AddToShelfDialog(s, books) { addToShelf = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(b: Book, cpm: Int, onOpen: () -> Unit, fixedHeight: Boolean = false) {
    val left = (b.totalChars * (1f - b.progress)).toInt()
    // La portada se ajusta al alto del texto de al lado: ni se queda pequeña ni sobresale
    val density = androidx.compose.ui.platform.LocalDensity.current
    var textH by remember { mutableStateOf(0) }
    val coverH = with(density) { textH.toDp() }.coerceIn(120.dp, 200.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))

            .background(Brush.horizontalGradient(listOf(NB.surface2, NB.surface)))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La portada abre la ficha (como en toda la app); «Seguir leyendo» abre el libro
        BookCover(b, Modifier.height(coverH).width(coverH * 2f / 3f).combinedClickable(
            onClick = { AppState.screen.value = Screen.Book(b.id) },
            onLongClick = { AppState.bookMenu.value = b.id },
        ), titleSize = 11)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).onSizeChanged { textH = it.height }) {
            Text(if (b.inApp) "SEGUIR LEYENDO" else "LEYENDO EN ${b.format.label.uppercase()}", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            // En el carrusel todas las tarjetas miden lo mismo (título en una línea y autor siempre),
            // para que la pantalla no salte al deslizar.
            Text(b.title, fontFamily = Playfair, fontSize = 21.sp, lineHeight = 25.sp, color = NB.text,
                maxLines = if (fixedHeight) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank() || fixedHeight) Text(b.author.ifBlank { " " }, color = NB.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            ThinProgress(b.progress, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            val running = AppState.timer.value?.takeIf { it.first == b.id }
            if (running != null) {
                TimerText(running.second, prefix = "Leyendo · ")
            } else if (b.isEpub) {
                Text("${percentLabel(b.progress)} · quedan ${minutesLabel(left, cpm)}", color = NB.text3, fontSize = 12.sp)
            } else if (b.isPdf) {
                Text("PDF · ${b.progressLabel()}", color = NB.text3, fontSize = 12.sp)
            } else {
                Text("${b.format.label} · ${b.progressLabel()}", color = NB.text3, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (b.inApp) Chip("Seguir leyendo", true, onOpen)
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
        Spacer(Modifier.height(14.dp))
        Text(
            "¿Vienes de otro móvil? Restaura tu copia", color = NB.text2, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { AppState.screen.value = Screen.Settings }.padding(8.dp),
        )
        Spacer(Modifier.height(20.dp))
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
    var shelvesOpen by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    var editPages by remember { mutableStateOf(false) }
    var editGenre by remember { mutableStateOf(false) }
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
    if (shelvesOpen) { ShelvesDialog(setOf(b.id), onDismiss = { shelvesOpen = false }); return }
    if (restarting) {
        AlertDialog(
            onDismissRequest = { restarting = false }, containerColor = NB.surface2,
            title = { Text("¿Reiniciar el libro?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Vuelve al principio (0 %). Tus citas y marcadores se quedan.", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    SheetRow("Reiniciar y borrar sus lecturas del calendario") {
                        Stats.deleteBook(b.id)
                        scope.launch { Library.restart(b.id) }; restarting = false; AppState.toast("Libro reiniciado y lecturas borradas"); onDismiss()
                    }
                    SheetRow("Reiniciar y conservar sus lecturas") {
                        scope.launch { Library.restart(b.id) }; restarting = false; AppState.toast("Libro reiniciado"); onDismiss()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { restarting = false }) { Text("Cancelar", color = NB.text2) } },
        )
        return
    }
    if (editGenre) GenreDialog(b.genre, onPick = { g -> scope.launch { Library.update(b.id) { it.copy(genre = g) } }; editGenre = false },
        onDismiss = { editGenre = false })
    if (editPages) {
        var t by remember { mutableStateOf(if (b.pages > 0) b.pages.toString() else "") }
        AlertDialog(
            onDismissRequest = { editPages = false }, containerColor = NB.surface2,
            title = { Text("Páginas en papel", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        if (b.pagesAuto) "Se han buscado solas en el catálogo. Si tu edición tiene otras, cámbialas aquí."
                        else "Con las páginas de tu edición, el lector te dice por qué página del libro irías.",
                        color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(t, { t = it.filter(Char::isDigit).take(5) }, singleLine = true, label = { Text("Páginas") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text))
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { Library.update(b.id) { it.copy(pages = t.toIntOrNull() ?: 0, pagesAuto = false) } }; editPages = false }) { Text("Guardar", color = NB.text) } },
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
    var seriesOpen by remember { mutableStateOf(false) }
    var loanOpen by remember { mutableStateOf(false) }
    if (seriesOpen) { SeriesDialog(b) { seriesOpen = false }; return }
    if (loanOpen) { LoanDialog(b) { loanOpen = false }; return }
    val menuScroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NB.surface2,
        // Cabecera compacta: portada pequeña, título y autor, y la X para cerrar (sin botón «Cerrar» abajo)
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(b, Modifier.width(40.dp), titleSize = 6)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontFamily = Playfair, fontSize = 18.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.author.isNotBlank()) Text(b.author, fontSize = 13.sp, color = NB.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = NB.text2, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
          Box {
            Column(Modifier.verticalScroll(menuScroll)) {
                BookRating(b.id, size = 22)
                Spacer(Modifier.height(8.dp))
                // Resumen corto: estado y progreso, tiempo y citas
                val secs = Stats.secondsForBook(b.id)
                val line1 = buildList {
                    add(b.status.label)
                    if (b.status == BookStatus.Reading) add(if (b.isEpub) b.printedPageLabel(b.progress) else b.progressLabel())
                    if (b.status == BookStatus.Abandoned && b.abandonReason.isNotBlank()) add(b.abandonReason)
                    b.readings.lastOrNull { it.end > 0 }?.let { if (b.status == BookStatus.Read) add("terminado el ${date.format(Date(it.end))}") }
                }.joinToString(" · ")
                val line2 = buildList {
                    if (secs > 0) add("${durationLabel(secs)} leyendo")
                    if (b.highlights.isNotEmpty()) add("${b.highlights.size} citas")
                    if (b.timesRead > 1) add("leído ${b.timesRead} veces")
                }.joinToString(" · ")
                Text(line1, color = NB.text2, fontSize = 14.sp)
                if (line2.isNotBlank()) Text(line2, color = NB.text3, fontSize = 13.sp)
                if (b.review.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(b.review, color = NB.text, fontSize = 14.sp, fontFamily = Playfair, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }

                // Lo principal
                // Acciones en una sola fila, del mismo ancho
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (b.inApp) MenuAction(if (b.status == BookStatus.Reading) "Seguir leyendo" else "Leer", true, Modifier.weight(1.4f)) {
                        onDismiss(); AppState.openBook(b)
                    }
                    if (!b.inApp && b.status != BookStatus.Read) {
                        if (AppState.timer.value?.first == b.id) MenuAction("Parar", true, Modifier.weight(1f)) { onDismiss(); AppState.stopTimer() }
                        else MenuAction("Cronómetro", true, Modifier.weight(1f)) { onDismiss(); AppState.startTimer(b.id) }
                    }
                    MenuAction("Apuntar", false, Modifier.weight(1f)) { onDismiss(); AppState.logSession.value = LogRequest(b.id) }
                    if (!b.inApp) MenuAction("Progreso", false, Modifier.weight(1f)) { onDismiss(); AppState.logSession.value = LogRequest(b.id, onlyProgress = true) }
                }

                // Estado con un toque
                MenuGroup("Estado")
                ButtonRow {
                    Chip("Próximo", b.status == BookStatus.Next) { scope.launch { Library.setStatus(b.id, BookStatus.Next) } }
                    Chip("Pendiente", b.status == BookStatus.ToRead) { scope.launch { Library.setStatus(b.id, BookStatus.ToRead) } }
                    Chip("Leído", b.status == BookStatus.Read) { if (b.status != BookStatus.Read) scope.launch { Library.setStatus(b.id, BookStatus.Read) } }
                    if (b.status == BookStatus.Reading || b.status == BookStatus.Abandoned)
                        Chip("Abandonado", b.status == BookStatus.Abandoned) { if (b.status != BookStatus.Abandoned) abandoning = true }
                }

                // Organizar y datos del libro
                MenuGroup("Libro")
                val shelfNames = (if (b.genre.isNotBlank()) listOf(b.genre) else emptyList()) + b.shelves
                SheetRow(if (shelfNames.isEmpty()) "Añadir a una estantería" else "Estanterías: ${shelfNames.joinToString(", ")}") { shelvesOpen = true }
                SheetRow(if (b.series.isBlank()) "Saga" else "Saga: ${b.series}" + if (b.seriesLabel.isNotBlank()) " (${b.seriesLabel})" else "") { seriesOpen = true }
                if (b.format == BookFormat.Paper) SheetRow(if (b.onLoan) "Prestado a ${b.loanTo}" else "Prestar") { loanOpen = true }
                SheetRow(if (b.wish) "Ya lo tengo (quitar de «Lo quiero»)" else "Lo quiero (aún no lo tengo)") {
                    scope.launch { Library.update(b.id) { it.copy(wish = !it.wish) } }
                    AppState.toast(if (b.wish) "Quitado de «Lo quiero»" else "Añadido a «Lo quiero»")
                }
                SheetRow("Ver ficha del libro") { onDismiss(); AppState.screen.value = Screen.Book(b.id) }
                SheetRow("Historial de lecturas (${Stats.sessions(b.id).size})") { history = true }
                SheetRow(if (b.review.isBlank()) "Escribir tu opinión" else "Editar tu opinión") { reviewing = true }
                var more by remember { mutableStateOf(false) }
                SheetRow(if (more) "Menos opciones ▴" else "Más opciones ▾", color = NB.text2) { more = !more }
                if (more) {
                    SheetRow(if (b.genre.isNotBlank()) "Género: ${b.genre}" else "Género") { editGenre = true }
                    if (b.isEpub) SheetRow(if (b.pages > 0) "Páginas en papel: ${b.pages}" else "Páginas en papel") { editPages = true }
                    SheetRow(if (b.hasCover) "Cambiar portada" else "Poner portada") { coverPicker.launch("image/*") }
                    if (b.rating > 0f || b.ratingPlot > 0f || b.ratingChars > 0f || b.ratingProse > 0f) SheetRow("Reiniciar valoración") {
                        scope.launch { Library.update(b.id) { it.copy(rating = 0f, ratingPlot = 0f, ratingChars = 0f, ratingProse = 0f) } }
                        AppState.toast("Valoración quitada")
                    }
                    if (b.progress > 0f || b.status == BookStatus.Read) SheetRow("Reiniciar libro (empezar desde cero)") { restarting = true }
                    if (Stats.secondsForBook(b.id) > 0 || Stats.sessions(b.id).isNotEmpty()) SheetRow("Borrar lecturas…", color = Color(0xFFFF6B6B)) { deleteAll = true }
                    SheetRow("Mover a la papelera", color = Color(0xFFFF6B6B)) {
                        scope.launch { Library.moveToTrash(b.id) }
                        AppState.toast("Movido a la papelera (Ajustes → Papelera)")
                        onDismiss()
                    }
                }
            }
            // Degradado abajo cuando queda más por ver: así se nota que se puede deslizar
            if (menuScroll.canScrollForward) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, NB.surface2)))
            )
          }
        },
        confirmButton = {},
    )
}

@Composable
private fun MenuAction(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (primary) NB.text else NB.surface)
            .border(1.dp, if (primary) NB.text else NB.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) NB.onText else NB.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Fila del menú del libro: más compacta que MenuRow para que quepa todo sin perderse abajo. */
@Composable
private fun SheetRow(label: String, color: Color = NB.text, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
    )
}

@Composable
private fun MenuGroup(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
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

/**
 * Inicio de la biblioteca, al estilo de Kindle: lo que estás leyendo, lo último que has añadido o abierto,
 * tus estados y tus estanterías. Tamaños fijos, sin elegir columnas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePane(
    books: List<Book>, reading: List<Book>, cpm: Int,
    onOpen: (Book) -> Unit, onDetails: (Book) -> Unit, onSeeAll: () -> Unit,
    onStatus: (String) -> Unit, onShelf: (String) -> Unit, onShelves: () -> Unit,
) {
    val shelves by Library.shelves
    val recent = remember(books) { books.sortedByDescending { maxOf(it.lastOpened, it.addedAt) }.take(8) }
    val style = shelfStyle(Prefs.shelfStyle)
    val recentW = when (Prefs.recentSize) { "s" -> 84.dp; "l" -> 132.dp; else -> 108.dp }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        if (reading.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (reading.size == 1) ContinueCard(reading[0], cpm, onOpen = { onOpen(reading[0]) })
                else {
                    val pager = androidx.compose.foundation.pager.rememberPagerState { reading.size }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pager, contentPadding = PaddingValues(horizontal = 2.dp), pageSpacing = 8.dp,
                        key = { reading[it].id }, modifier = Modifier.fillMaxWidth(),
                    ) { i -> ContinueCard(reading[i], cpm, onOpen = { onOpen(reading[i]) }, fixedHeight = true) }
                }
            }
        }

        resurfaceCandidate(books)?.let { r ->
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) { ResurfaceCard(r) { onDetails(r) } }
        }

        HomeTitle("Recientes", "Ver todos", onSeeAll)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            recent.forEach { b ->
                Column(Modifier.width(recentW).combinedClickable(
                    onClick = { onDetails(b) },
                    onLongClick = { AppState.bookMenu.value = b.id })) {
                    BookCover(b, Modifier.fillMaxWidth(), titleSize = 12)
                    if (b.status == BookStatus.Reading) {
                        Spacer(Modifier.height(5.dp))
                        ThinProgress(b.progress, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(b.title, color = NB.text, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.author.isNotBlank()) Text(b.author, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Atajo a todos los libros al final del carrusel
            Box(
                Modifier.width(recentW).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))
                    .border(1.dp, NB.line, RoundedCornerShape(6.dp)).clickable(onClick = onSeeAll),
                contentAlignment = Alignment.Center,
            ) { Text("Ver los\n${books.size} libros", color = NB.text2, fontSize = 14.sp, textAlign = TextAlign.Center) }
        }

        HomeTitle("Por estado", null, null)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BookStatus.entries.forEach { st ->
                val n = books.count { it.status == st }
                Column(
                    Modifier.width(104.dp).clip(RoundedCornerShape(14.dp)).background(NB.surface)
                        .border(1.dp, NB.line, RoundedCornerShape(14.dp)).clickable { onStatus(st.key) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text("$n", fontFamily = Playfair, fontSize = 22.sp, color = if (n > 0) NB.text else NB.text3)
                    Text(st.label, fontSize = 13.sp, color = NB.text2, maxLines = 1)
                }
            }
        }

        HomeTitle("Tus estanterías", "Ver todas", onShelves)
        if (shelves.isEmpty()) {
            Text(
                "Aún no tienes estanterías. Créalas en la pestaña Estanterías.",
                color = NB.text3, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onShelves).padding(vertical = 6.dp),
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                shelves.forEach { s ->
                    val inside = books.filter { s in it.shelves }
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(8.dp)).clickable { onShelf(s) }) {
                        ShelfCompartment(style, 86.dp) {
                            Row(Modifier.padding(start = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom) {
                                inside.take(3).forEach { b ->
                                    Box(Modifier.width(44.dp)) { BookCover(b, Modifier.fillMaxWidth(), titleSize = 7) }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(s, color = NB.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (inside.size == 1) "1 libro" else "${inside.size} libros", color = NB.text3, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTitle(title: String, action: String?, onAction: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = NB.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(
            "$action ›", color = NB.text2, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
