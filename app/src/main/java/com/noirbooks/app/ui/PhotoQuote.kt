package com.noirbooks.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Highlight
import com.noirbooks.app.data.HighlightColor
import com.noirbooks.app.data.Library
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Cita desde una foto: eliges el libro, haces una foto a la página (o la eliges de la galería),
 * el texto se reconoce en el propio móvil y dejas solo la frase que quieres guardar.
 */
@Composable
fun PhotoQuoteFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<Book?>(null) }
    var reading by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    val photoFile = remember { File(ctx.cacheDir, "camera/page.jpg").apply { parentFile?.mkdirs() } }
    val photoUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photoFile) }

    fun recognize(uri: Uri) {
        reading = true
        scope.launch {
            val result = runCatching {
                val image = InputImage.fromFilePath(ctx, uri)
                suspendCancellableCoroutine<String> { cont ->
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                        .addOnSuccessListener { cont.resume(it.text) }
                        .addOnFailureListener { cont.resume("") }
                }
            }.onFailure { ErrorLog.note("Leer texto de una foto", it) }.getOrDefault("")
            reading = false
            // Une las líneas partidas de la página en un solo párrafo (y quita los guiones de fin de línea).
            val clean = result.replace(Regex("-\\n(?=\\p{Ll})"), "").replace(Regex("(?<![.!?:…])\\n(?!\\n)"), " ").trim()
            if (clean.isBlank()) { AppState.toast("No se ha podido leer texto en la foto. Prueba con más luz y la página recta"); onDone() }
            else text = clean
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) recognize(photoUri) else onDone() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) recognize(uri) else onDone() }

    val b = book
    when {
        b == null -> BookPickerDialog(onPick = { book = it }, onDismiss = onDone)
        reading -> AlertDialog(
            onDismissRequest = {}, containerColor = NB.surface2,
            title = { Text("Leyendo la foto…", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = { CircularProgressIndicator(color = NB.text) },
            confirmButton = {},
        )
        text == null -> AlertDialog(
            onDismissRequest = onDone, containerColor = NB.surface2,
            title = {
                Column {
                    Text("Cita desde una foto", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                    Text(b.title, fontSize = 14.sp, color = NB.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column {
                    Text("Haz la foto con la página recta y buena luz. El texto se lee en tu móvil: la foto no se envía a ningún sitio.",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(10.dp))
                    MenuRow("Hacer una foto") { camera.launch(photoUri) }
                    MenuRow("Elegir una foto de la galería") { gallery.launch("image/*") }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Cancelar", color = NB.text2) } },
        )
        else -> SaveQuoteDialog(b, text!!, onDone)
    }
}

@Composable
private fun SaveQuoteDialog(b: Book, initial: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf(initial) }
    var page by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDone, containerColor = NB.surface2,
        title = { Text("Deja solo tu frase", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Borra lo que sobre y corrige si alguna palabra salió mal.", color = NB.text3, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    t, { t = it }, minLines = 5, maxLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    page, { page = it.filter(Char::isDigit).take(5) }, label = { Text("Página (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HighlightColor.entries.forEach { hc ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(hc.argb))
                                .border(if (color == hc.ordinal) 2.dp else 0.dp, NB.text, CircleShape)
                                .clickable { color = hc.ordinal },
                        )
                    }
                }
                Text(HighlightColor.of(color).meaning, color = NB.text3, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = t.isNotBlank(), onClick = {
                val p = page.toIntOrNull()
                val h = Highlight(
                    id = System.currentTimeMillis(), chapter = -1,
                    fraction = if (p != null && b.pages > 0) (p.toFloat() / b.pages).coerceIn(0f, 1f) else 0f,
                    range = "", text = t.trim(), color = color,
                    chapterTitle = if (p != null) "Página $p" else "Desde una foto",
                )
                scope.launch { Library.update(b.id) { it.copy(highlights = it.highlights + h) } }
                AppState.toast("Cita guardada")
                onDone()
            }) { Text("Guardar cita", color = NB.text, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar", color = NB.text2) } },
    )
}
