package com.noirbooks.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val ES = Locale("es", "ES")

/** "es-ES" → "Español (España)". */
fun languageLabel(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayName(ES).replaceFirstChar { it.titlecase(ES) }

/**
 * Elegir idioma y voz de la lectura en voz alta, con prueba de cada voz.
 * Se puede guardar para todos los libros o solo para el que estás leyendo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceDialog(c: ReaderController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { c.ttsPrepare { ready = true } }
    var lang by remember { mutableStateOf(c.ttsLangTag.ifBlank { "es-ES" }) }
    var voice by remember { mutableStateOf(c.ttsVoiceName) }
    var onlyBook by remember { mutableStateOf(c.ttsHasBookVoice()) }
    var allLangs by remember { mutableStateOf(false) }

    val langs = remember(ready) {
        val base = Locale.forLanguageTag(lang).language
        c.ttsLanguages().sortedWith(compareBy(
            { if (it.equals("es-ES", true)) 0 else if (Locale.forLanguageTag(it).language == "es") 1 else if (Locale.forLanguageTag(it).language == base) 2 else 3 },
            { languageLabel(it) },
        ))
    }
    val shown = if (allLangs) langs else langs.filter {
        val l = Locale.forLanguageTag(it).language
        l == "es" || l == Locale.forLanguageTag(lang).language || it.equals(lang, true)
    }.ifEmpty { langs.take(6) }
    val voices = remember(ready, lang) { c.ttsVoicesFor(lang) }

    fun close() { c.ttsRestore(); onDismiss() }

    AlertDialog(
        onDismissRequest = { close() }, containerColor = NB.surface2,
        title = { Text("Voz", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (!ready) {
                    Text("Preparando las voces del móvil…", color = NB.text2, fontSize = 14.sp)
                } else {
                    Text("IDIOMA", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { t -> Chip(languageLabel(t), t.equals(lang, true)) { lang = t; voice = "" } }
                        if (!allLangs && langs.size > shown.size) Chip("Más idiomas…", false) { allLangs = true }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("VOZ", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    VoiceRow("Automática", "La que elija el móvil para este idioma", voice.isBlank(),
                        onPick = { voice = "" }, onPlay = { c.ttsPreview(lang, "") })
                    if (voices.isEmpty()) {
                        Text("No hay voces instaladas de este idioma. Puedes descargarlas abajo.", color = NB.text3, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 6.dp))
                    }
                    voices.forEachIndexed { i, v ->
                        val notes = buildList {
                            add(if (v.isNetworkConnectionRequired) "Necesita conexión" else "Sin conexión")
                            if (v.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH) add("muy natural")
                            else if (v.quality >= android.speech.tts.Voice.QUALITY_HIGH) add("natural")
                        }.joinToString(" · ")
                        VoiceRow("Voz ${i + 1}", notes, voice == v.name, onPick = { voice = v.name }, onPlay = { c.ttsPreview(lang, v.name) })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("USAR PARA", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Todos los libros", !onlyBook) { onlyBook = false }
                        Chip("Solo este libro", onlyBook) { onlyBook = true }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Descargar más voces (ajustes del móvil)", color = NB.text2, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            runCatching {
                                ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { AppState.toast("Búscalo en Ajustes del móvil: «Texto a voz»") }
                        }.padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { c.ttsChoose(lang, voice, onlyBook); onDismiss() }, enabled = ready) {
                Text("Usar esta voz", color = NB.text, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Cancelar", color = NB.text2) } },
    )
}

@Composable
private fun VoiceRow(title: String, sub: String, selected: Boolean, onPick: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) NB.text.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onPick).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NB.text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (sub.isNotBlank()) Text(sub, color = NB.text3, fontSize = 12.sp)
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.PlayArrow, "Escuchar", tint = NB.text) }
    }
}
