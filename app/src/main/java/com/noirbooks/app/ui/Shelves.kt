package com.noirbooks.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.Library
import kotlinx.coroutines.launch

/** Chip de estantería: tocar filtra, mantener pulsado para renombrar o borrar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) NB.text else Color.Transparent)
            .border(1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(50))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) NB.onText else NB.text2, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value, { onChange(it.take(32)) }, singleLine = true,
        placeholder = { Text(placeholder, color = NB.text3) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Crear una estantería nueva (opcionalmente con libros dentro). */
@Composable
fun NewShelfDialog(ids: Set<String> = emptySet(), onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = NB.surface2,
        title = { Text("Nueva estantería", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = { NameField(name, { name = it }, "Verano, Regalos, Clásicos…") },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val n = name.trim()
                scope.launch {
                    Library.createShelf(n)
                    if (ids.isNotEmpty()) Library.setShelf(ids, n, true)
                }
                onDone(n)
            }) { Text("Crear", color = NB.text, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancelar", color = NB.text2) } },
    )
}

/** Renombrar o borrar una estantería. */
@Composable
fun EditShelfDialog(name: String, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var n by remember { mutableStateOf(name) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(name) }, containerColor = NB.surface2,
        title = { Text(if (confirmDelete) "¿Borrar «$name»?" else "Estantería", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            if (confirmDelete) Text("Se borra la estantería; los libros se quedan en tu biblioteca.", color = NB.text2)
            else Column {
                NameField(n, { n = it }, "Nombre")
                Spacer(Modifier.height(8.dp))
                MenuRow("Borrar estantería", color = Color(0xFFFF6B6B)) { confirmDelete = true }
            }
        },
        confirmButton = {
            if (confirmDelete) TextButton(onClick = { scope.launch { Library.deleteShelf(name) }; onDone(null) }) {
                Text("Borrar", color = Color(0xFFFF6B6B))
            } else TextButton(enabled = n.isNotBlank(), onClick = { scope.launch { Library.renameShelf(name, n) }; onDone(n.trim()) }) {
                Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = { onDone(name) }) { Text("Cancelar", color = NB.text2) } },
    )
}

/** Elegir en qué estanterías están uno o varios libros. */
@Composable
fun ShelvesDialog(ids: Set<String>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val shelves by Library.shelves
    val books by Library.books
    val chosen = books.filter { it.id in ids }
    var creating by remember { mutableStateOf(false) }
    if (creating) { NewShelfDialog(ids) { creating = false }; return }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = {
            Column {
                Text("Estanterías", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                Text(if (chosen.size == 1) chosen.first().title else "${chosen.size} libros", fontSize = 14.sp, color = NB.text2, maxLines = 1)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (shelves.isEmpty()) Text("Aún no tienes estanterías. Crea una para ordenar tus libros a tu manera.", color = NB.text3, fontSize = 14.sp)
                shelves.forEach { s ->
                    val inAll = chosen.isNotEmpty() && chosen.all { s in it.shelves }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { scope.launch { Library.setShelf(ids, s, !inAll) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(inAll, { v -> scope.launch { Library.setShelf(ids, s, v) } },
                            colors = CheckboxDefaults.colors(checkedColor = NB.text, checkmarkColor = NB.onText, uncheckedColor = NB.text3))
                        Text(s, color = NB.text, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                MenuRow("+ Nueva estantería") { creating = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = NB.text, fontWeight = FontWeight.SemiBold) } },
    )
}

/** Cambiar el estado de varios libros a la vez. */
@Composable
fun StatusManyDialog(ids: Set<String>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NB.surface2,
        title = { Text("Cambiar estado de ${ids.size} libros", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                listOf(BookStatus.Reading, BookStatus.Next, BookStatus.ToRead, BookStatus.Read).forEach { st ->
                    MenuRow(st.label) { scope.launch { Library.setStatusMany(ids, st) }; onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
    )
}
