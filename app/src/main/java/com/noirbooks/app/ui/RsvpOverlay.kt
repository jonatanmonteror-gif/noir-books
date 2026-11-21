package com.noirbooks.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.Prefs
import kotlinx.coroutines.delay

/** Una palabra con la frase a la que pertenece (para volver a esa frase al salir). */
private data class RWord(val text: String, val sentence: Int)

/**
 * Lectura rápida palabra a palabra (RSVP): las palabras salen una a una en el centro, con la letra clave
 * marcada para que el ojo no se mueva. Empieza en la página visible y, al salir, la página queda donde paraste.
 */
@Composable
fun RsvpOverlay(c: ReaderController, onClose: () -> Unit) {
    val pal = Prefs.effectiveReader().palette()
    val bg = Color(pal.bg); val fg = Color(pal.fg)
    val accent = Color(0xFFD9534F)
    var sentences by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var words by remember { mutableStateOf<List<RWord>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var wpm by remember { mutableIntStateOf(Prefs.rsvpWpm.coerceIn(100, 800)) }
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }

    fun load(fromHere: Boolean) {
        loading = true
        c.sentencesFromHere(fromHere) { list ->
            sentences = list
            words = list.flatMapIndexed { i, (t, _) -> t.split(Regex("\\s+")).filter { it.isNotBlank() }.map { RWord(it, i) } }
            index = 0
            loading = false
            finished = words.isEmpty()
        }
    }
    LaunchedEffect(Unit) { load(fromHere = true) }

    fun close() {
        playing = false
        words.getOrNull(index)?.let { w -> sentences.getOrNull(w.sentence)?.let { c.showSentence(it.second) } }
        onClose()
    }
    BackHandler { close() }

    // El reloj: cada palabra dura según su largo y la puntuación (las pausas ayudan a entender)
    LaunchedEffect(playing, words, wpm) {
        while (playing && index < words.size) {
            val w = words[index].text
            val base = 60_000L / wpm
            val factor = when {
                w.endsWith(".") || w.endsWith("!") || w.endsWith("?") || w.endsWith("…") || w.endsWith(":") -> 2.2f
                w.endsWith(",") || w.endsWith(";") || w.endsWith("—") -> 1.5f
                w.length > 10 -> 1.3f
                else -> 1f
            }
            delay((base * factor).toLong())
            if (!playing) break
            if (index < words.lastIndex) index++ else {
                playing = false
                // Fin del capítulo: pasa al siguiente y sigue
                if (!c.nextChapterThen { load(fromHere = false); playing = true }) finished = true
            }
        }
    }

    val navH = activityNavBarHeight()
    val statusH = activityStatusBarHeight()
    androidx.compose.ui.window.Dialog(onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    FullScreenDialogWindow()
    Box(
        Modifier.fillMaxSize().background(bg).padding(top = statusH, bottom = navH)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (words.isNotEmpty()) playing = !playing },
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.Filled.Close, "Cerrar", tint = fg) }
            Text("Lectura rápida", color = fg.copy(alpha = 0.7f), fontSize = 14.sp, fontFamily = Inter, modifier = Modifier.weight(1f))
        }

        // La palabra, con la letra clave siempre en el mismo sitio
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(14.dp).background(fg.copy(alpha = 0.25f)))
            Spacer(Modifier.height(10.dp))
            when {
                loading -> Text("Preparando…", color = fg.copy(alpha = 0.6f), fontSize = 18.sp, fontFamily = Inter)
                finished -> Text("Fin del libro", color = fg.copy(alpha = 0.6f), fontSize = 18.sp, fontFamily = Inter)
                else -> {
                    val w = words.getOrNull(index)?.text.orEmpty()
                    val core = w.trim()
                    val pivot = when { core.length <= 1 -> 0; core.length <= 5 -> 1; core.length <= 9 -> 2; core.length <= 13 -> 3; else -> 4 }
                        .coerceAtMost((core.length - 1).coerceAtLeast(0))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(core.take(pivot), color = fg, fontSize = 38.sp, fontFamily = Playfair, textAlign = TextAlign.End,
                            maxLines = 1, modifier = Modifier.weight(1f))
                        Text(core.drop(pivot).take(1), color = accent, fontSize = 38.sp, fontFamily = Playfair, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(core.drop(pivot + 1), color = fg, fontSize = 38.sp, fontFamily = Playfair, textAlign = TextAlign.Start,
                            maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(2.dp).height(14.dp).background(fg.copy(alpha = 0.25f)))
            if (!loading && words.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    if (playing) "Toca para pausar" else "Toca para empezar",
                    color = fg.copy(alpha = 0.45f), fontSize = 13.sp, fontFamily = Inter,
                )
            }
        }

        // Velocidad y play/pausa
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (words.isNotEmpty()) {
                val pct = ((index + 1) * 100 / words.size.coerceAtLeast(1))
                Text("$pct % del capítulo", color = fg.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = Inter)
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("−", color = fg, fontSize = 26.sp, fontFamily = Inter,
                    modifier = Modifier.clip(CircleShape).clickable { wpm = (wpm - 25).coerceAtLeast(100); Prefs.rsvpWpm = wpm }.padding(horizontal = 14.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$wpm", color = fg, fontSize = 20.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold)
                    Text("palabras/min", color = fg.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = Inter)
                }
                Text("+", color = fg, fontSize = 26.sp, fontFamily = Inter,
                    modifier = Modifier.clip(CircleShape).clickable { wpm = (wpm + 25).coerceAtMost(800); Prefs.rsvpWpm = wpm }.padding(horizontal = 14.dp))
                Box(
                    Modifier.size(54.dp).clip(CircleShape).border(1.5.dp, fg, CircleShape).clickable { if (words.isNotEmpty()) playing = !playing },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(4.dp).height(18.dp).background(fg)); Box(Modifier.width(4.dp).height(18.dp).background(fg))
                    } else Icon(Icons.Filled.PlayArrow, "Empezar", tint = fg, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
}
