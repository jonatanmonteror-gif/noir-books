package com.noirbooks.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.minutesLabel
import kotlinx.coroutines.launch
import java.text.Normalizer

private val EPUB_TYPES = arrayOf("application/epub+zip", "application/octet-stream", "application/zip")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by Library.books
    val importing by AppState.importing
    var sort by remember { mutableStateOf(Prefs.sortOrder) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { AppState.importAll(ctx, uris, openIfSingle = false) }
    }
    fun open(b: Book) { AppState.screen.value = Screen.Reader(b.id) }

    val current = books.filter { it.started && !it.finished }.maxByOrNull { it.lastOpened }
    val q = fold(query.trim())
    val shown = books
        .filter { q.isEmpty() || fold(it.title).contains(q) || fold(it.author).contains(q) }
        .let { list ->
            when (sort) {
                "title" -> list.sortedBy { fold(it.title) }
                "author" -> list.sortedWith(compareBy({ fold(it.author.ifBlank { "~" }) }, { fold(it.title) }))
                else -> list.sortedByDescending { maxOf(it.lastOpened, it.addedAt) }
            }
        }

    Column(Modifier.fillMaxSize().background(NB.bg).statusBarsPadding()) {
        // Cabecera
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
            IconButton(onClick = { picker.launch(EPUB_TYPES) }) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(NB.text), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Añadir libros", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Título o autor", color = NB.text3) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
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
            EmptyLibrary(onAdd = { picker.launch(EPUB_TYPES) })
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (current != null && q.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                    ContinueCard(current, onOpen = { open(current) })
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("recent" to "Recientes", "title" to "Título", "author" to "Autor").forEach { (k, label) ->
                            Chip(label, selected = sort == k) { sort = k; Prefs.sortOrder = k }
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { b ->
                Column(
                    Modifier.combinedClickable(onClick = { open(b) }, onLongClick = { menuFor = b }),
                ) {
                    BookCover(b, Modifier.fillMaxWidth())
                    if (b.started || b.finished) {
                        Spacer(Modifier.height(6.dp))
                        ThinProgress(b.progress, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        b.title, color = NB.text, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (b.author.isNotBlank()) {
                        Text(b.author, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }

    menuFor?.let { b -> BookMenu(b, onDismiss = { menuFor = null }) }
}

@Composable
private fun ContinueCard(b: Book, onOpen: () -> Unit) {
    val left = (b.totalChars * (1f - b.progress)).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(NB.surface2, NB.surface)))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(b, Modifier.width(92.dp), titleSize = 11)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("SEGUIR LEYENDO", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(b.title, fontFamily = Playfair, fontSize = 21.sp, lineHeight = 25.sp, color = NB.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank()) {
                Text(b.author, color = NB.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            ThinProgress(b.progress, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("${(b.progress * 100).toInt()} % · quedan ${minutesLabel(left)}", color = NB.text3, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) NB.text else Color.Transparent)
            .border(1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Color.Black else NB.text2, fontWeight = FontWeight.Medium)
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
            "Añade libros en formato EPUB desde tu móvil, o ábrelos con Noir Books desde otra app (Archivos, Drive, correo…).",
            color = NB.text2, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(NB.text).clickable(onClick = onAdd)
                .padding(horizontal = 26.dp, vertical = 13.dp),
        ) {
            Text("Añadir libros", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Los libros con protección DRM (la mayoría de Kindle y algunos de Kobo) no se pueden abrir.",
            color = NB.text3, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookMenu(b: Book, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = NB.surface2,
            title = { Text("¿Eliminar este libro?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = { Text("Se borra de Noir Books con su progreso y marcadores. El archivo original no se toca.", color = NB.text2) },
            confirmButton = {
                TextButton(onClick = { scope.launch { Library.delete(b.id) }; onDismiss() }) {
                    Text("Eliminar", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
        )
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
            Column {
                val info = buildString {
                    append("${b.chapterLengths.size} capítulos · unas ${minutesLabel(b.totalChars)} de lectura")
                    if (b.started) append("\nLeído: ${(b.progress * 100).toInt()} %")
                    if (b.bookmarks.isNotEmpty()) append(" · ${b.bookmarks.size} marcadores")
                }
                Text(info, color = NB.text2, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                MenuRow("Abrir") { onDismiss(); AppState.screen.value = Screen.Reader(b.id) }
                MenuRow(if (b.finished) "Marcar como no leído" else "Marcar como leído") {
                    scope.launch { Library.update(b.id) { it.copy(finished = !it.finished) } }
                    onDismiss()
                }
                if (b.started) {
                    MenuRow("Volver al principio") {
                        scope.launch { Library.update(b.id) { it.copy(chapter = 0, fraction = 0f, finished = false) } }
                        onDismiss()
                    }
                }
                MenuRow("Eliminar", color = Color(0xFFFF6B6B)) { confirmDelete = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = NB.text2) } },
    )
}

@Composable
private fun MenuRow(label: String, color: Color = NB.text, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** Para buscar y ordenar sin distinguir mayúsculas ni tildes. */
private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
