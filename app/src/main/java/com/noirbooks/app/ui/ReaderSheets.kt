package com.noirbooks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
        Text(label, color = if (selected) NB.onText else NB.text2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Ajustes rápidos de lectura (se aplican al momento y se guardan para todos los libros). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSheet(onDismiss: () -> Unit, scroll: Boolean? = null, onScroll: ((Boolean) -> Unit)? = null) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = NB.surface, contentColor = NB.text) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            ReadingControls(scroll = scroll, onScroll = onScroll)
        }
    }
}

/** Controles de lectura compartidos entre el lector y la pantalla de Ajustes. */
@Composable
fun ReadingControls(showPreview: Boolean = false, scroll: Boolean? = null, onScroll: ((Boolean) -> Unit)? = null) {
    val s by Prefs.reader
    fun set(n: ReaderSettings) = Prefs.setReader(n)

    if (showPreview) {
        Preview(s)
        Spacer(Modifier.height(16.dp))
    }

    // Perfiles nada más entrar: cambiar de golpe tema, letra, interlineado, márgenes…
    Label("Perfiles")
    ProfilesBlock(s) { set(it) }

    Label("Tema")
    if (s.autoTheme != "off") {
        Text(if (s.autoTheme == "system") "Automático según el modo oscuro del móvil: ${s.dayTheme.label} / ${s.nightTheme.label}"
            else "Automático por horas: ${s.dayTheme.label} de 7 a 21 h, ${s.nightTheme.label} de noche",
            color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val free = com.noirbooks.app.data.Edition.FREE_THEMES
        ReaderTheme.entries.sortedBy { if (it.key in free) 0 else 1 }.forEach { t ->
            val sel = s.theme == t && s.autoTheme == "off"
            val tbg = if (t == ReaderTheme.Custom) s.customBg else t.bg
            val tfg = if (t == ReaderTheme.Custom) s.customFg else t.fg
            val locked = !com.noirbooks.app.data.Edition.isPro && t.key !in com.noirbooks.app.data.Edition.FREE_THEMES
            Column(
                Modifier.weight(1f).alpha(if (locked) 0.35f else 1f).clip(RoundedCornerShape(12.dp)).clickable {
                    if (locked) AppState.showPro.value = true else set(s.copy(theme = t, autoTheme = "off"))
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(tbg))
                        .border(if (sel) 2.dp else 1.dp, if (sel) NB.text else NB.line, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = Color(tfg), fontFamily = Playfair, fontSize = 17.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(t.label, fontSize = 12.sp, color = if (sel) NB.text else NB.text3)
            }
        }
    }

    Label("Tamaño de letra")
    StepControl(s.fontSize.toFloat(), 12f, 40f, 1f, 0, "Tamaño de letra", minus = "A−", plus = "A+") { set(s.copy(fontSize = it.toInt())) }

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
        FontDropdown(s.font) { set(s.copy(font = it)) }

        Label("Interlineado")
        StepControl(s.lineHeight, 1.0f, 2.6f, 0.05f, 2, "Interlineado") { set(s.copy(lineHeight = it)) }

        Label("Márgenes laterales")
        StepControl(s.marginH.toFloat(), 0f, 80f, 1f, 0, "Márgenes laterales") { set(s.copy(marginH = it.toInt())) }
        Label("Márgenes arriba y abajo")
        StepControl(s.marginV.toFloat(), 0f, 80f, 1f, 0, "Márgenes arriba y abajo") { set(s.copy(marginV = it.toInt())) }

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
    ProFoldSection("Tema propio y automático", "Tus colores, y claro de día y oscuro de noche") {
        ThemeExtras(s) { set(it) }
    }
    ProFoldSection("Pie de página", "Tiempo del capítulo, %, hora, batería") {
        Label("A la izquierda")
        FooterChoices(FooterInfo.LEFT, s.footerLeft) { set(s.copy(footerLeft = it)) }
        Label("A la derecha")
        FooterChoices(FooterInfo.RIGHT, s.footerRight) { set(s.copy(footerRight = it)) }
        Label("Arriba")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Hora y batería", s.headerClock, Modifier.weight(1f)) { set(s.copy(headerClock = true)) }
            Choice("Nada", !s.headerClock, Modifier.weight(1f)) { set(s.copy(headerClock = false)) }
        }
        if (s.headerClock) Toggle("Mostrarlas también en modo concentración", s.headerInFocus) { set(s.copy(headerInFocus = it)) }
        Spacer(Modifier.height(6.dp))
        Text("También puedes tocar cada lado del pie mientras lees para cambiarlo.", color = NB.text3, fontSize = 12.sp)
    }
    FoldSection("Avanzado", "Concentración, tablet, botones y pantalla") {
        Toggle("Modo concentración (solo el texto)", s.focus) { set(s.copy(focus = it)) }
        var edge by remember { mutableStateOf(Prefs.edgeFontSize) }
        Toggle("Cambiar el tamaño de letra deslizando por el borde derecho", edge) { edge = it; Prefs.edgeFontSize = it; set(s.copy()) }
        var rapid by remember { mutableStateOf(Prefs.rsvpEnabled) }
        Toggle("Lectura rápida palabra a palabra (botón «Rápida» en el lector)", rapid) { rapid = it; Prefs.rsvpEnabled = it }
        if (scroll != null && onScroll != null) {
            var sc by remember { mutableStateOf(scroll) }
            Toggle("Desplazamiento continuo en este libro (sin páginas)", sc) { sc = it; onScroll(it) }
        }
        Label("Zonas al tocar la página")
        TapZones(s.tapLayout) { set(s.copy(tapLayout = it)) }
        Spacer(Modifier.height(10.dp))
        ResetReading()
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(s.palette().bg))
            .border(1.dp, NB.line, RoundedCornerShape(14.dp)).padding(horizontal = (s.marginH * 0.7f).dp, vertical = 16.dp),
    ) {
        Text(
            "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero.",
            color = Color(s.palette().fg), fontFamily = family, fontSize = (s.fontSize * 0.85f).sp,
            lineHeight = (s.fontSize * 0.85f * s.lineHeight).sp,
        )
    }
}

/** Deslizador que solo aplica el cambio al soltar (cada cambio vuelve a maquetar el capítulo). */
/**
 * − valor + : cambia de [step] en [step]. Tocar el número del medio permite escribirlo.
 * [decimals] es cuántos decimales se muestran (0 para enteros).
 */
@Composable
private fun StepControl(
    value: Float, min: Float, max: Float, step: Float, decimals: Int, title: String,
    minus: String = "−", plus: String = "+", onChange: (Float) -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    fun fmt(v: Float) = if (decimals == 0) "${kotlin.math.round(v).toInt()}"
        else String.format(java.util.Locale("es", "ES"), "%.${decimals}f", v)
    fun snap(v: Float) = (kotlin.math.round(v / step) * step).coerceIn(min, max)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice(minus, false, Modifier.width(56.dp)) { onChange(snap(value - step)) }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, NB.line, RoundedCornerShape(10.dp))
                .clickable { typing = true }.padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(fmt(value), color = NB.text, fontSize = 15.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
        }
        Choice(plus, false, Modifier.width(56.dp)) { onChange(snap(value + step)) }
    }
    if (typing) {
        var text by remember { mutableStateOf(fmt(value)) }
        val parsed = text.replace(',', '.').toFloatOrNull()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { typing = false }, containerColor = NB.surface2,
            title = { Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        text, { text = it.take(6) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (decimals == 0) androidx.compose.ui.text.input.KeyboardType.Number
                            else androidx.compose.ui.text.input.KeyboardType.Decimal),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text,
                            focusedTextColor = NB.text, unfocusedTextColor = NB.text),
                    )
                    Text("Entre ${fmt(min)} y ${fmt(max)}", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = parsed != null, onClick = {
                    parsed?.let { onChange(it.coerceIn(min, max)) }; typing = false
                }) { Text("Aplicar", color = NB.text, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { typing = false }) { Text("Cancelar", color = NB.text2) } },
        )
    }
}

/** Tipo de letra en un desplegable: cada opción se ve con su propia letra. */
@Composable
private fun FontDropdown(value: ReaderFont, onPick: (ReaderFont) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun familyOf(f: ReaderFont) = when (f) {
        ReaderFont.Inter, ReaderFont.Original -> Inter
        ReaderFont.Atkinson -> FontFamily.SansSerif
        else -> FontFamily.Serif
    }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NB.surface2).clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.label, color = NB.text, fontSize = 15.sp, fontFamily = familyOf(value), modifier = Modifier.weight(1f))
            Text("▾", color = NB.text2, fontSize = 14.sp)
        }
        androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }, containerColor = NB.surface2) {
            ReaderFont.entries.forEach { f ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(f.label + if (f == value) "  ✓" else "", color = NB.text, fontSize = 15.sp, fontFamily = familyOf(f))
                    },
                    onClick = { onPick(f); open = false },
                )
            }
        }
    }
}

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
private fun FooterChoices(items: List<FooterInfo>, value: FooterInfo, onPick: (FooterInfo) -> Unit) {
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
private fun Choice(label: String, selected: Boolean, modifier: Modifier, family: FontFamily = Inter, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) NB.text else NB.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) NB.onText else NB.text2, fontSize = 13.sp, fontFamily = family, maxLines = 1)
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
                checkedThumbColor = NB.onText, checkedTrackColor = NB.text,
                uncheckedThumbColor = NB.text3, uncheckedTrackColor = NB.surface2, uncheckedBorderColor = NB.line,
            ),
        )
    }
}

/** Plantillas de zonas táctiles con un mapa de la pantalla. */
@Composable
private fun TapZones(value: com.noirbooks.app.data.TapLayout, onPick: (com.noirbooks.app.data.TapLayout) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val back = NB.surface2
        val fwd = NB.text.copy(alpha = 0.55f)
        val menu = NB.text3.copy(alpha = 0.6f)
        androidx.compose.foundation.Canvas(
            Modifier.size(width = 74.dp, height = 120.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, NB.line, RoundedCornerShape(10.dp)),
        ) {
            val cols = 20; val rows = 32
            val cw = size.width / cols; val ch = size.height / rows
            for (i in 0 until cols) for (j in 0 until rows) {
                val a = value.action((i + 0.5f) / cols, (j + 0.5f) / rows)
                drawRect(
                    when (a) { com.noirbooks.app.data.TapAction.Prev -> back; com.noirbooks.app.data.TapAction.Next -> fwd; else -> menu },
                    topLeft = androidx.compose.ui.geometry.Offset(i * cw, j * ch), size = androidx.compose.ui.geometry.Size(cw + 0.5f, ch + 0.5f),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            com.noirbooks.app.data.TapLayout.entries.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(t) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (t == value) NB.text else Color.Transparent).border(1.5.dp, NB.text2, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(t.label, color = NB.text, fontSize = 14.sp, fontWeight = if (t == value) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(value.hint, color = NB.text3, fontSize = 12.sp)
    Text("En el mapa: blanco adelante · oscuro atrás · gris menú", color = NB.text3, fontSize = 11.sp)
}

/** Perfiles de lectura: aplicar con un toque, guardar los ajustes actuales, borrar manteniendo pulsado. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ProfileChip(name: String, selected: Boolean, onClick: () -> Unit, onLong: (() -> Unit)?) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) NB.text else NB.surface2)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(name, color = if (selected) NB.onText else NB.text, fontSize = 13.sp, maxLines = 1) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfilesBlock(current: ReaderSettings, apply: (ReaderSettings) -> Unit) {
    val saved by Prefs.profiles
    // «Usuario nuevo» se ve como una instalación limpia: sin perfiles guardados
    val profiles = if (com.noirbooks.app.data.Edition.mode.value == "free") emptyList() else saved
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Perfil listo «Verde» (letra Literata, tema verde): usa un tema Pro
        if (com.noirbooks.app.data.Edition.isPro)
            ProfileChip("Verde", current == current.kindle(), onClick = { apply(current.kindle()) }, onLong = null)
        profiles.forEach { (name, ps) ->
            ProfileChip(name, ps == current, onClick = { apply(ps) }, onLong = { deleting = name })
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).border(1.dp, NB.line, RoundedCornerShape(50)).clickable { naming = true }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text("+ Guardar", color = NB.text2, fontSize = 13.sp) }
    }
    Text(if (profiles.isEmpty()) "Ajusta la lectura a tu gusto y pulsa «+ Guardar» para crear un perfil (Cama, Tren…)."
        else "Mantén pulsado un perfil para borrarlo o actualizarlo.", color = NB.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    if (naming) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { naming = false }, containerColor = NB.surface2,
            title = { Text("Nombre del perfil", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                androidx.compose.material3.OutlinedTextField(name, { name = it.take(24) }, singleLine = true,
                    placeholder = { Text("Cama, Tren, Tablet…", color = NB.text3) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = {
                    Prefs.saveProfile(name.trim(), current); naming = false; AppState.toast("Perfil «${name.trim()}» guardado")
                }) { Text("Guardar", color = NB.text) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { naming = false }) { Text("Cancelar", color = NB.text2) } },
        )
    }
    deleting?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = NB.surface2,
            title = { Text("Perfil «$n»", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = { Text("Puedes guardar en él los ajustes que tienes ahora o borrarlo.", color = NB.text2) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { Prefs.saveProfile(n, current); deleting = null; AppState.toast("Perfil «$n» actualizado") }) {
                    Text("Actualizar", color = NB.text, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { Prefs.deleteProfile(n); deleting = null }) { Text("Borrar", color = Color(0xFFFF6B6B)) } },
        )
    }
}

private val BG_SWATCHES = listOf(0xFFFFFFFFL, 0xFFF7F3EAL, 0xFFF4ECD8L, 0xFFE9E4D8L, 0xFFDDE5D6L, 0xFFDCE4EEL,
    0xFF2A2A2CL, 0xFF1C1C1EL, 0xFF14181FL, 0xFF1F1B16L, 0xFF0F1A14L, 0xFF000000L)
private val FG_SWATCHES = listOf(0xFF000000L, 0xFF1A1A1AL, 0xFF3B2F22L, 0xFF2F3A46L, 0xFF5A4A3AL,
    0xFFF2F2F2L, 0xFFD6D6D6L, 0xFFD9D4C7L, 0xFFE8DCC4L, 0xFFB8C4CFL, 0xFFC9B79CL)

/** Tema propio (fondo y letra) y tema automático de día y de noche. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ThemeExtras(s: ReaderSettings, set: (ReaderSettings) -> Unit) {
    Label("Tu tema: fondo")
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BG_SWATCHES.forEach { c -> Swatch(c, s.customBg == c && s.theme == ReaderTheme.Custom) { set(s.copy(theme = ReaderTheme.Custom, customBg = c, autoTheme = "off")) } }
    }
    Label("Tu tema: letra")
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FG_SWATCHES.forEach { c -> Swatch(c, s.customFg == c && s.theme == ReaderTheme.Custom) { set(s.copy(theme = ReaderTheme.Custom, customFg = c, autoTheme = "off")) } }
    }
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(s.customBg)).padding(12.dp),
    ) { Text("Así se ve tu tema propio", color = Color(s.customFg), fontFamily = FontFamily.Serif, fontSize = 15.sp) }

    Label("Cambio automático")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("off" to "No", "hours" to "Por horas", "system" to "Como el móvil").forEach { (k, l) ->
            Choice(l, s.autoTheme == k, Modifier.weight(1f)) { set(s.copy(autoTheme = k)) }
        }
    }
    if (s.autoTheme != "off") {
        Text(if (s.autoTheme == "hours") "De día es de 7 a 21 h." else "Sigue el modo oscuro de tu móvil.", color = NB.text3, fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp))
        Label("De día")
        ReaderTheme.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t -> Choice(t.label, s.dayTheme == t, Modifier.weight(1f)) { set(s.copy(dayTheme = t)) } }
            }
        }
        Label("De noche")
        ReaderTheme.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t -> Choice(t.label, s.nightTheme == t, Modifier.weight(1f)) { set(s.copy(nightTheme = t)) } }
            }
        }
    }
}

@Composable
private fun Swatch(c: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) NB.text else NB.line, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Volver a los ajustes de lectura de fábrica (los perfiles guardados se quedan). */
@Composable
private fun ResetReading() {
    var ask by remember { mutableStateOf(false) }
    Text("Restablecer los ajustes de lectura", color = NB.text2, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ask = true }.padding(vertical = 8.dp))
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false }, containerColor = NB.surface2,
            title = { Text("¿Restablecer la lectura?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = { Text("Tema, letra, márgenes, pie, zonas y demás vuelven a como venían. Tus perfiles guardados se quedan.", color = NB.text2) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { Prefs.setReader(ReaderSettings()); ask = false }) { Text("Restablecer", color = NB.text) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { ask = false }) { Text("Cancelar", color = NB.text2) } },
        )
    }
}
