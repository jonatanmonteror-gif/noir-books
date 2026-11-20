package com.noirbooks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.FooterInfo
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.ReaderFont
import com.noirbooks.app.data.ReaderSettings
import com.noirbooks.app.data.ReaderTheme
import com.noirbooks.app.data.minutesLabel

/** Índice y marcadores. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(c: ReaderController, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    val book = c.book
    val tocCpm = remember { com.noirbooks.app.data.Stats.charsPerMinute() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = NB.surface, contentColor = NB.text) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab("Índice", tab == 0) { tab = 0 }
                Tab("Marcadores${book?.bookmarks?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: ""}", tab == 1) { tab = 1 }
                Tab("Citas${book?.highlights?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: ""}", tab == 2) { tab = 2 }
            }
            Spacer(Modifier.height(10.dp))
            if (tab == 0) {
                // Entrada que corresponde a la posición actual
                val current = c.epub.toc.indices.lastOrNull { i -> c.tocSpine[i] in 0..c.chapter } ?: -1
                val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
                LazyColumn(state = list, modifier = Modifier.navigationBarsPadding()) {
                    itemsIndexed(c.epub.toc) { i, e ->
                        val ch = c.tocSpine[i]
                        val here = i == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = ch >= 0) { c.goToToc(i); onDismiss() }
                                .background(if (here) NB.surface2 else Color.Transparent)
                                .padding(start = (20 + 16 * e.level.coerceAtMost(4)).dp, end = 20.dp, top = 13.dp, bottom = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                e.title, modifier = Modifier.weight(1f),
                                color = if (ch < 0) NB.text3 else if (here) NB.text else NB.text2,
                                fontSize = if (e.level == 0) 15.sp else 14.sp,
                                fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            if (e.level == 0 && ch >= 0 && book != null && e.anchor == null) {
                                val len = book.chapterLengths.getOrNull(ch) ?: 0
                                if (len > 0) Text(minutesLabel(len, tocCpm), color = NB.text3, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else if (tab == 2) {
                val hs = book?.highlights.orEmpty()
                if (hs.isEmpty()) {
                    Text(
                        "Mantén pulsada una palabra y arrastra para seleccionar. Luego elige un color o añade una nota.",
                        color = NB.text3, fontSize = 14.sp, modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.navigationBarsPadding()) {
                        items(hs, key = { it.id }) { h ->
                            QuoteRow(h, onClick = { c.goToHighlight(h); onDismiss() })
                        }
                    }
                }
            } else {
                val marks = book?.bookmarks.orEmpty()
                if (marks.isEmpty()) {
                    Text(
                        "Aún no tienes marcadores. Toca el centro de la página y pulsa el icono del marcador arriba a la derecha.",
                        color = NB.text3, fontSize = 14.sp, modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.navigationBarsPadding()) {
                        items(marks, key = { it.createdAt }) { m ->
                            Row(
                                Modifier.fillMaxWidth().clickable { c.goTo(m.chapter, m.fraction); onDismiss() }
                                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val pct = ((book?.progressAt(m.chapter, m.fraction) ?: 0f) * 100).toInt()
                                    Text("${m.label.ifBlank { "Capítulo ${m.chapter + 1}" }} · $pct %", color = NB.text3, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (m.snippet.isNotBlank()) {
                                        Text(m.snippet, color = NB.text, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                            fontFamily = Playfair)
                                    }
                                }
                                IconButton(onClick = { c.deleteBookmark(m) }) {
                                    Icon(Icons.Filled.Close, "Borrar marcador", tint = NB.text3, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) NB.text else Color.Transparent)
            .border(1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Color.Black else NB.text2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Ajustes rápidos de lectura (se aplican al momento y se guardan para todos los libros). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSheet(onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = NB.surface, contentColor = NB.text) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            ReadingControls()
        }
    }
}

/** Controles de lectura compartidos entre el lector y la pantalla de Ajustes. */
@Composable
fun ReadingControls(showPreview: Boolean = false) {
    val s by Prefs.reader
    fun set(n: ReaderSettings) = Prefs.setReader(n)

    if (showPreview) {
        Preview(s)
        Spacer(Modifier.height(16.dp))
    }

    Label("Tema")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReaderTheme.entries.forEach { t ->
            val sel = s.theme == t
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { set(s.copy(theme = t)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(t.bg))
                        .border(if (sel) 2.dp else 1.dp, if (sel) NB.text else NB.line, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("Aa", color = Color(t.fg), fontFamily = Playfair, fontSize = 17.sp) }
                Spacer(Modifier.height(5.dp))
                Text(t.label, fontSize = 12.sp, color = if (sel) NB.text else NB.text3)
            }
        }
    }

    Label("Tamaño de letra")
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("A−", 14) { set(s.copy(fontSize = (s.fontSize - 1).coerceAtLeast(12))) }
        Text("${s.fontSize}", modifier = Modifier.weight(1f), color = NB.text, fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("A+", 19) { set(s.copy(fontSize = (s.fontSize + 1).coerceAtMost(40))) }
    }

    Label("Brillo")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = if (s.brightness < 0) 0.5f else s.brightness,
            onValueChange = { set(s.copy(brightness = it.coerceIn(0.02f, 1f))) },
            enabled = s.brightness >= 0,
            colors = SliderDefaults.colors(thumbColor = NB.text, activeTrackColor = NB.text, inactiveTrackColor = NB.line),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Choice("Sistema", s.brightness < 0, Modifier) {
            set(s.copy(brightness = if (s.brightness < 0) 0.5f else -1f))
        }
    }


    // Segundo y tercer nivel, plegados: lo rápido arriba, lo demás a un toque.
    Spacer(Modifier.height(14.dp))
    FoldSection("Letra y página", "Tipo de letra, interlineado, márgenes, párrafos") {
        Toggle("Respetar el diseño del libro", s.publisher) { set(s.copy(publisher = it)) }
        if (s.publisher) Text("Se usa la letra, el interlineado y los párrafos del propio libro. Solo cambian el tema, el tamaño y los márgenes.",
            color = NB.text3, fontSize = 12.sp, lineHeight = 17.sp)
        Label("Tipo de letra")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderFont.entries.take(3).forEach { f -> FontChip(f, s.font == f, Modifier.weight(1f)) { set(s.copy(font = f)) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderFont.entries.drop(3).forEach { f -> FontChip(f, s.font == f, Modifier.weight(1f)) { set(s.copy(font = f)) } }
            Spacer(Modifier.weight(1f))
        }

        Label("Interlineado")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.3f to "Junto", 1.55f to "Normal", 1.8f to "Amplio", 2.1f to "Muy amplio").forEach { (v, l) ->
                Choice(l, kotlin.math.abs(s.lineHeight - v) < 0.05f, Modifier.weight(1f)) { set(s.copy(lineHeight = v)) }
            }
        }

        Label("Márgenes laterales")
        PxSlider(s.marginH, 4, 72) { set(s.copy(marginH = it)) }
        Label("Márgenes arriba y abajo")
        PxSlider(s.marginV, 6, 72) { set(s.copy(marginV = it)) }

        Label("Espacio entre párrafos")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1f to "Del libro", 0f to "Ninguno", 0.5f to "Poco", 1f to "Mucho").forEach { (v, l) ->
                Choice(l, kotlin.math.abs(s.paraSpacing - v) < 0.01f, Modifier.weight(1f)) { set(s.copy(paraSpacing = v)) }
            }
        }
        Label("Sangría")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1f to "Del libro", 0f to "Sin sangría", 1.5f to "Con sangría").forEach { (v, l) ->
                Choice(l, kotlin.math.abs(s.indent - v) < 0.01f, Modifier.weight(1f)) { set(s.copy(indent = v)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Toggle("Justificar texto", s.justify) { set(s.copy(justify = it)) }
        Toggle("Dividir palabras al final de línea", s.hyphens) { set(s.copy(hyphens = it)) }
    }
    FoldSection("Pie de página", "Tiempo del capítulo, %, hora, batería") {
        Label("A la izquierda")
        FooterChoices(s.footerLeft) { set(s.copy(footerLeft = it)) }
        Label("A la derecha")
        FooterChoices(s.footerRight) { set(s.copy(footerRight = it)) }
        Spacer(Modifier.height(6.dp))
        Text("Con \"Nada\" en los dos lados, el texto ocupa también esa línea.", color = NB.text3, fontSize = 12.sp)
    }
    FoldSection("Avanzado", "Tablet, botones y pantalla") {
        Toggle("Dos páginas en horizontal (tablet)", s.twoPages) { set(s.copy(twoPages = it)) }
        Toggle("Pasar página con el volumen", s.volumeKeys) { set(s.copy(volumeKeys = it)) }
        Toggle("Mantener la pantalla encendida", s.keepScreenOn) { set(s.copy(keepScreenOn = it)) }
    }
}

@Composable
private fun Preview(s: ReaderSettings) {
    val family = when (s.font) {
        ReaderFont.Inter -> Inter
        ReaderFont.Serif, ReaderFont.Literata, ReaderFont.Original -> FontFamily.Serif
        ReaderFont.Atkinson -> FontFamily.SansSerif
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(s.theme.bg))
            .border(1.dp, NB.line, RoundedCornerShape(14.dp)).padding(horizontal = (s.marginH * 0.7f).dp, vertical = 16.dp),
    ) {
        Text(
            "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero.",
            color = Color(s.theme.fg), fontFamily = family, fontSize = (s.fontSize * 0.85f).sp,
            lineHeight = (s.fontSize * 0.85f * s.lineHeight).sp,
        )
    }
}

/** Deslizador que solo aplica el cambio al soltar (cada cambio vuelve a maquetar el capítulo). */
@Composable
private fun PxSlider(value: Int, min: Int, max: Int, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = v, onValueChange = { v = it }, valueRange = min.toFloat()..max.toFloat(),
            onValueChangeFinished = { onDone(v.toInt()) },
            colors = SliderDefaults.colors(thumbColor = NB.text, activeTrackColor = NB.text, inactiveTrackColor = NB.line),
            modifier = Modifier.weight(1f),
        )
        Text("${v.toInt()}", color = NB.text2, fontSize = 13.sp, modifier = Modifier.width(34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun FooterChoices(value: FooterInfo, onPick: (FooterInfo) -> Unit) {
    val items = FooterInfo.entries
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { f -> Choice(f.label, f == value, Modifier.weight(1f)) { onPick(f) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StepButton(label: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(12.dp)).background(NB.surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = NB.text, fontSize = size.sp, fontFamily = Playfair) }
}

@Composable
private fun FontChip(f: ReaderFont, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val family = when (f) {
        ReaderFont.Inter -> Inter
        ReaderFont.Atkinson -> FontFamily.SansSerif
        ReaderFont.Original -> Inter
        else -> FontFamily.Serif
    }
    Choice(f.label, selected, modifier, family, onClick)
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, family: FontFamily = Inter, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) NB.text else NB.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.Black else NB.text2, fontSize = 13.sp, fontFamily = family, maxLines = 1)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!value) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NB.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = NB.text,
                uncheckedThumbColor = NB.text3, uncheckedTrackColor = NB.surface2, uncheckedBorderColor = NB.line,
            ),
        )
    }
}
