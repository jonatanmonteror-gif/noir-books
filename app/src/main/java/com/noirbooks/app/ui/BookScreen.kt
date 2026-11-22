package com.noirbooks.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.ProgressUnit
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.durationLabel
import com.noirbooks.app.data.minutesLabel
import com.noirbooks.app.data.percentLabel
import com.noirbooks.app.data.printedPageLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ficha del libro a pantalla completa: portada, sinopsis, progreso y tu lectura. No abre el libro por sí sola. */
@Composable
fun BookScreen(bookId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books by Library.books
    val b = books.firstOrNull { it.id == bookId }
    if (b == null) { LaunchedEffect(Unit) { onBack() }; return }
    val statsVersion by Stats.version
    var menu by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var shelvesOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var loadingDesc by remember { mutableStateOf(b.description.isBlank()) }
    LaunchedEffect(bookId) { Library.ensureDescription(bookId); loadingDesc = false }
    val cpm = remember { Stats.charsPerMinute() }
    val date = remember { SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES")) }

    Box(Modifier.fillMaxSize().background(NB.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = NB.text) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones", tint = NB.text) }
            }
            Column(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BookCover(b, Modifier.width(150.dp).shadow(18.dp, RoundedCornerShape(6.dp)), titleSize = 15)
                Spacer(Modifier.height(20.dp))
                Text(b.title, fontFamily = Playfair, fontSize = 26.sp, lineHeight = 31.sp, color = NB.text, textAlign = TextAlign.Center)
                if (b.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(b.author, color = NB.text2, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                val meta = buildList {
                    if (b.genre.isNotBlank()) add(b.genre)
                    if (!b.isEpub) add(b.format.label)
                    if (b.pages > 0) add(if (b.unit == ProgressUnit.Location && !b.isEpub) "${b.pages} posiciones" else if (b.isEpub) "${b.pages} páginas en papel" else "${b.pages} páginas")
                    else if (b.isEpub) add("unas ${minutesLabel(b.totalChars, cpm)} de lectura")
                    if (b.year.isNotBlank()) add(b.year)
                    if (b.publisher.isNotBlank()) add(b.publisher)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(meta, color = NB.text3, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (b.shelves.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(b.shelves.joinToString(" · "), color = NB.text2, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (b.series.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Saga «${b.series}»" + if (b.seriesLabel.isNotBlank()) " · ${b.seriesLabel}" else "",
                        color = NB.text2, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                val tags = buildList {
                    if (b.wish) add("Lo quiero")
                    if (b.onLoan) add("Prestado a ${b.loanTo} desde el " + SimpleDateFormat("d MMM", Locale("es", "ES")).format(b.loanAt))
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(tags.joinToString(" · "), color = NB.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                BookRating(b.id, size = 26, centered = true)

                // Progreso y acciones
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.status.label.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f))
                        if (b.status == BookStatus.Reading) {
                            Text(
                                (if (b.isEpub) b.printedPageLabel(b.progress) else b.progressLabel()) + " · " + percentLabel(b.progress),
                                color = NB.text2, fontSize = 13.sp,
                            )
                        }
                    }
                    if (b.status == BookStatus.Reading) {
                        Spacer(Modifier.height(10.dp))
                        ThinProgress(b.progress, Modifier.fillMaxWidth())
                    }
                    if (Library.fileMissing(b)) {
                        Spacer(Modifier.height(10.dp))
                        Text("Falta el archivo de este libro (viene de una copia sin los EPUB). Impórtalo otra vez y recuperas todo.",
                            color = NB.text2, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (b.inApp) {
                            val label = when (b.status) {
                                BookStatus.Reading -> "Seguir leyendo"
                                BookStatus.Read -> "Volver a leer"
                                else -> "Empezar a leer"
                            }
                            Chip(label, true) { AppState.openBook(b, from = Screen.Book(b.id)) }
                        } else {
                            Chip("Apuntar lectura", true) { AppState.logSession.value = LogRequest(b.id) }
                        }
                        if (b.status != BookStatus.Read) PlayPauseButton(b.id)
                    }
                }

                // Sinopsis
                Spacer(Modifier.height(26.dp))
                SectionTitle("Sinopsis")
                when {
                    b.description.isNotBlank() -> {
                        Text(
                            b.description, color = NB.text, fontSize = 15.sp, lineHeight = 23.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 7, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        )
                        if (b.description.length > 380) {
                            Text(if (expanded) "Leer menos" else "Leer más", color = NB.text2, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp))
                        }
                    }
                    loadingDesc -> Text("Buscando la sinopsis…", color = NB.text3, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                    else -> Text("Este libro no trae sinopsis y no se ha encontrado en el catálogo.", color = NB.text3, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                }

                // Tu lectura
                Spacer(Modifier.height(26.dp))
                SectionTitle("Tu lectura")
                val secs = remember(statsVersion, bookId) { Stats.secondsForBook(bookId) }
                val nSessions = remember(statsVersion, bookId) { Stats.sessions(bookId).size }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile(if (secs > 0) durationLabel(secs) else "—", "leyendo", Modifier.weight(1f))
                    InfoTile("$nSessions", if (nSessions == 1) "lectura" else "lecturas", Modifier.weight(1f))
                    InfoTile("${b.highlights.size}", if (b.highlights.size == 1) "cita" else "citas", Modifier.weight(1f))
                }
                val lines = buildList {
                    b.readings.firstOrNull()?.let { add("Empezado el ${date.format(Date(it.start))}") }
                    b.readings.lastOrNull { it.end > 0 }?.let { if (b.status == BookStatus.Read) add("Terminado el ${date.format(Date(it.end))}") }
                    if (b.timesRead > 1) add("Leído ${b.timesRead} veces")
                    if (b.status == BookStatus.Abandoned && b.abandonReason.isNotBlank()) add("Abandonado: ${b.abandonReason}")
                }
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    lines.forEach { Text(it, color = NB.text2, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) }
                }
                if (b.review.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Tu opinión", color = NB.text3, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    Text(b.review, color = NB.text, fontSize = 15.sp, fontFamily = Playfair, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Estanterías", false) { shelvesOpen = true }
                    Chip("Historial", false) { history = true }
                    Chip("Más opciones", false) { menu = true }
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
    if (menu) BookMenu(bookId, onDismiss = { menu = false })
    if (history) SessionsDialog(bookId, onDismiss = { history = false })
    if (shelvesOpen) ShelvesDialog(setOf(bookId), onDismiss = { shelvesOpen = false })
}

@Composable
private fun SectionTitle(t: String) {
    Text(t.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun InfoTile(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(NB.surface).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(value, color = NB.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = NB.text3, fontSize = 12.sp)
    }
}
