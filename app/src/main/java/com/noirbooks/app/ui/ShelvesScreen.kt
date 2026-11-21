package com.noirbooks.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import kotlinx.coroutines.launch

/** Filtro especial: libros que no están en ninguna estantería. */
const val NO_SHELF = "\u0001sin"

fun shelfLabel(s: String) = if (s == NO_SHELF) "Sin estantería" else s

fun inShelf(b: Book, s: String?) = when (s) {
    null -> true
    NO_SHELF -> b.shelves.isEmpty()
    else -> s in b.shelves
}

/**
 * Pestaña Estanterías: cada estantería es un hueco de librería con sus libros de lado (lomos) o de frente (portadas).
 * Tocar un libro abre su ficha; tocar el nombre muestra la estantería en la biblioteca.
 */
@Composable
fun ShelvesPane(books: List<Book>, current: String?, onPick: (String?) -> Unit, onBook: (Book) -> Unit) {
    val shelves by Library.shelves
    var newShelf by remember { mutableStateOf(false) }
    var editShelf by remember { mutableStateOf<String?>(null) }
    var addTo by remember { mutableStateOf<String?>(null) }
    var design by remember { mutableStateOf(false) }
    var styleKey by remember { mutableStateOf(Prefs.shelfStyle) }
    var view by remember { mutableStateOf(Prefs.shelfView) }
    var size by remember { mutableStateOf(Prefs.shelfSize) }
    val scale = when (size) { "s" -> 0.72f; "l" -> 1.32f; else -> 1f }
    val style = shelfStyle(styleKey)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "tools") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("Toda la biblioteca", books.size, false, Modifier.weight(1f)) { onPick(null) }
                    QuickTile("Sin estantería", books.count { it.shelves.isEmpty() }, false, Modifier.weight(1f)) { onPick(NO_SHELF) }
                }
                Spacer(Modifier.height(12.dp))
                ButtonRow {
                    PillButton("+ Nueva estantería", primary = true) { newShelf = true }
                    PillButton("Diseño") { design = true }
                }
            }
        }
        if (shelves.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, NB.line), RoundedCornerShape(16.dp))
                        .clickable { newShelf = true }.padding(22.dp),
                ) {
                    Text("Ordena tus libros a tu manera", color = NB.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Crea estanterías como «Clásicos», «Para el verano» o «Regalos». Un libro puede estar en varias.",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
            }
        }
        items(shelves, key = { it }) { s ->
            val inside = books.filter { s in it.shelves }.sortedBy { fold(it.author.ifBlank { "~" }) + fold(it.title) }
            ShelfRow(s, inside, current == s, style, view, scale, onOpen = { onPick(s) }, onAdd = { addTo = s }, onEdit = { editShelf = s }, onBook = onBook)
        }
    }

    if (newShelf) NewShelfDialog { n -> newShelf = false; if (n != null) addTo = n }
    editShelf?.let { s -> EditShelfDialog(s) { editShelf = null } }
    addTo?.let { s -> AddToShelfDialog(s, books) { addTo = null } }
    if (design) ShelfDesignDialog(styleKey, view, size,
        onStyle = { styleKey = it; Prefs.shelfStyle = it },
        onView = { view = it; Prefs.shelfView = it },
        onSize = { size = it; Prefs.shelfSize = it },
        onDismiss = { design = false })
}

@Composable
private fun QuickTile(label: String, n: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) NB.text else NB.surface)
            .border(1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("$n", fontFamily = Playfair, fontSize = 24.sp, color = if (selected) NB.onText else NB.text)
        Text(label, fontSize = 13.sp, color = if (selected) NB.onText else NB.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Un hueco de la librería: nombre arriba y los libros apoyados en la balda. */
@Composable
private fun ShelfRow(
    name: String, books: List<Book>, selected: Boolean, style: ShelfStyle, view: String, scale: Float,
    onOpen: () -> Unit, onAdd: () -> Unit, onEdit: () -> Unit, onBook: (Book) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen)) {
                Text(name, fontFamily = Playfair, fontSize = 21.sp, color = NB.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (books.size == 1) "1 libro" else "${books.size} libros") + if (selected) " · viendo ahora" else "",
                    fontSize = 13.sp, color = NB.text3,
                )
            }
            TextButton(onClick = onAdd) { Text("Añadir", color = NB.text2, fontSize = 14.sp) }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.MoreVert, "Renombrar o borrar", tint = NB.text2) }
        }
        Spacer(Modifier.height(8.dp))
        val h = (if (view == "covers") 128.dp else 150.dp) * scale
        ShelfCompartment(style, h) {
            if (books.isEmpty()) {
                Text("Vacía. Pulsa Añadir para poner libros.", color = style.onBack?.copy(alpha = 0.7f) ?: NB.text3, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 14.dp, bottom = 14.dp).clickable(onClick = onAdd))
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (view == "covers") 6.dp else 1.5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    books.forEach { b ->
                        if (view == "covers") {
                            Box(Modifier.width(74.dp * scale).clickable { onBook(b) }) { BookCover(b, Modifier.fillMaxWidth(), titleSize = 10) }
                        } else {
                            BookSpine(b, h - 14.dp, onClick = { onBook(b) }, onLongClick = { AppState.bookMenu.value = b.id }, widthScale = scale)
                        }
                    }
                }
            }
        }
    }
}

/** Poner o quitar libros de una estantería desde la propia estantería. */
@Composable
fun AddToShelfDialog(shelf: String, books: List<Book>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val live by Library.books
    val list = remember(books) { books.sortedBy { fold(it.title) } }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = {
            Column {
                Text(shelf, fontFamily = Inter, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Marca los libros que van en esta estantería", fontSize = 14.sp, color = NB.text2)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEach { b ->
                    val on = live.firstOrNull { it.id == b.id }?.shelves?.contains(shelf) == true
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { scope.launch { Library.setShelf(setOf(b.id), shelf, !on) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(on, { v -> scope.launch { Library.setShelf(setOf(b.id), shelf, v) } },
                            colors = CheckboxDefaults.colors(checkedColor = NB.text, checkmarkColor = NB.onText, uncheckedColor = NB.text3))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, color = NB.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (b.author.isNotBlank()) Text(b.author, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = NB.text, fontWeight = FontWeight.SemiBold) } },
    )
}
