package com.noirbooks.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Translate
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.noirbooks.app.data.Highlight
import com.noirbooks.app.data.HighlightColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Valoración en medias estrellas: tocar la mitad izquierda de una estrella da media; la derecha, la entera. */
@Composable
fun StarRating(value: Float, color: Color = NB.text, size: Int = 28, onChange: ((Float) -> Unit)? = null) {
    // Valores siempre al día dentro del gesto (antes se quedaba con el primero y no dejaba quitar la nota)
    val current by androidx.compose.runtime.rememberUpdatedState(value)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    Row(verticalAlignment = Alignment.CenterVertically) {
    Row(
        Modifier
            .then(if (onChange != null) Modifier.pointerInput(Unit) {
                detectTapGestures { o ->
                    val raw = (o.x / this.size.width.coerceAtLeast(1)) * 5f
                    val q = (kotlin.math.ceil(raw * 2f) / 2f).coerceIn(0.5f, 5f)
                    // Para quitar la nota: menú del libro → Reiniciar valoración (así no se borra sin querer)
                    if (q != current) change?.invoke(q)
                }
            } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val shown = kotlin.math.round(value * 2f) / 2f
        for (i in 0 until 5) {
            val fill = (shown - i).coerceIn(0f, 1f)
            Box(Modifier.size(size.dp)) {
                Text("★", color = color.copy(alpha = 0.22f), fontSize = (size * 0.9f).sp, modifier = Modifier.align(Alignment.Center))
                if (fill > 0f) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clipToBounds()) {
                        Box(Modifier.size(size.dp)) {
                            Text("★", color = color, fontSize = (size * 0.9f).sp, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
    }
}

fun ratingLabel(v: Float): String =
    if (v <= 0f) "" else (if (v % 1f == 0f) "${v.toInt()}" else "%.2f".format(v).trimEnd('0').replace('.', ','))

@Composable
fun NoteDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NB.surface2,
        title = { Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("Escribe tu nota", color = NB.text3) },
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NB.text2) } },
    )
}

/** Fila de una cita: barra de color, texto en Playfair y nota. */
@Composable
fun QuoteRow(h: Highlight, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.width(3.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(Color(HighlightColor.of(h.color).argb)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.text, fontFamily = Playfair, fontSize = 15.sp, lineHeight = 21.sp, color = NB.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
            if (h.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(h.note, fontFamily = Inter, fontSize = 13.sp, color = NB.text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle ?: h.chapterTitle, fontFamily = Inter, fontSize = 12.sp, color = NB.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Editar un subrayado: color, nota, compartir como imagen o borrar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightSheet(
    h: Highlight, bookTitle: String, author: String,
    onChange: (Highlight) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = NB.surface, contentColor = NB.text) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text(h.text, fontFamily = Playfair, fontSize = 17.sp, lineHeight = 24.sp, color = NB.text, maxLines = 8, overflow = TextOverflow.Ellipsis)
            if (h.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(h.note, fontFamily = Inter, fontSize = 14.sp, color = NB.text2)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HighlightColor.entries.forEach { hc ->
                    val sel = hc.ordinal == h.color
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(hc.argb))
                            .border(if (sel) 3.dp else 0.dp, NB.text, CircleShape)
                            .clickable { onChange(h.copy(color = hc.ordinal)) },
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(HighlightColor.of(h.color).meaning, fontFamily = Inter, fontSize = 12.sp, color = NB.text3)
            }
            Spacer(Modifier.height(14.dp))
            ButtonRow {
                PillButton(if (h.note.isBlank()) "Añadir nota" else "Editar nota") { editing = true }
                PillButton("Compartir") { AppState.shareQuote.value = QuoteShare(h.text, bookTitle, author, color = h.color) }
                if (onOpen != null) PillButton("Ir al libro", primary = true) { onOpen() }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Borrar subrayado", color = Color(0xFFFF6B6B), fontFamily = Inter, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(vertical = 10.dp, horizontal = 4.dp),
            )
        }
    }
    if (editing) {
        NoteDialog("Nota", h.note, onSave = { onChange(h.copy(note = it)); editing = false }, onDismiss = { editing = false })
    }
}

/**
 * Diccionario: la palabra en la RAE, en Wikipedia, o traducida en el propio móvil.
 * Con una frase (varias palabras) solo salen Wikipedia y Traducir.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(word: String, onDismiss: () -> Unit, language: String = "es", startTab: Int = 0) {
    val single = !word.trim().contains(' ')
    val lang = java.util.Locale.forLanguageTag(language.ifBlank { "es" }).language.ifBlank { "es" }
    val tabs = buildList {
        if (single && lang == "es") add("RAE")
        add("Wikipedia")
        add("Traducir")
    }
    // startTab < 0 = la última pestaña (Traducir)
    var tab by remember { mutableStateOf(if (startTab < 0) tabs.lastIndex else startTab.coerceIn(0, tabs.lastIndex)) }
    // Pantalla completa (no hoja deslizable): así la página de la RAE o de Wikipedia se desplaza sin pelearse con la hoja
    val navH = activityNavBarHeight()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(NB.surface).statusBarsPadding().padding(top = 8.dp, bottom = navH)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(word, fontFamily = Playfair, fontSize = if (single) 24.sp else 18.sp, color = NB.text, modifier = Modifier.weight(1f),
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (single) Text("Guardada en Mis palabras", fontFamily = Inter, fontSize = 12.sp, color = NB.text3)
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "Cerrar", tint = NB.text2)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, t -> Chip(t, tab == i) { tab = i } }
            }
            Spacer(Modifier.height(10.dp))
            // Las páginas se quedan cargadas al cambiar de pestaña: sin recargar ni pantallazo negro
            val current = tabs[tab.coerceIn(0, tabs.lastIndex)]
            var opened by remember { mutableStateOf(setOf(current)) }
            LaunchedEffect(current) { opened = opened + current }
            Box(Modifier.weight(1f).fillMaxWidth().background(if (current == "Traducir") NB.surface else Color.White)) {
                if ("RAE" in opened) WebPane("https://dle.rae.es/" + Uri.encode(word.lowercase()), current == "RAE")
                if ("Wikipedia" in opened) WebPane(
                    "https://$lang.m.wikipedia.org/w/index.php?search=" + Uri.encode(word.trim()) + "&go=Go", current == "Wikipedia",
                )
                if (current == "Traducir") TranslatePane(word, lang, Modifier.fillMaxSize().background(NB.surface).zIndex(2f))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPane(url: String, visible: Boolean) {
    var loading by remember(url) { mutableStateOf(true) }
    Box(
        Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f)
            .graphicsLayer { alpha = if (visible) 1f else 0f }.background(Color.White),
    ) {
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    isNestedScrollingEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                    }
                    loadUrl(url)
                }
            },
            update = { it.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading && visible) CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = Color.Black, strokeWidth = 2.dp)
    }
}

/** Idiomas a los que se puede traducir (los más útiles primero). */
private val TARGETS = listOf("es" to "Español", "en" to "Inglés", "ca" to "Catalán", "fr" to "Francés", "it" to "Italiano", "de" to "Alemán", "pt" to "Portugués")

/** Traducción con Google ML Kit en el propio móvil: el idioma se descarga una vez y el texto no sale del móvil. */
@Composable
private fun TranslatePane(text: String, source: String, modifier: Modifier) {
    var target by remember { mutableStateOf(Prefs.translateTarget.takeIf { it != source } ?: if (source == "es") "en" else "es") }
    var result by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(target) {
        result = null
        status = "Traduciendo…"
        val r = runCatching { Translate.translate(text, source, target) { status = it } }
        r.onSuccess { result = it; status = "" }
        r.onFailure { status = it.message ?: "No se pudo traducir" }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text("TRADUCIR AL", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TARGETS.filter { it.first != source }.forEach { (k, label) ->
                Chip(label, target == k) { target = k; Prefs.translateTarget = k }
            }
        }
        Spacer(Modifier.height(18.dp))
        result?.let {
            Text(it, fontFamily = Playfair, fontSize = 20.sp, lineHeight = 28.sp, color = NB.text)
            Spacer(Modifier.height(12.dp))
            Text("Traducido en tu móvil con Google ML Kit", color = NB.text3, fontSize = 12.sp)
        }
        if (status.isNotBlank()) Text(status, color = NB.text2, fontSize = 14.sp)
    }
}

/** Buscar en todo el libro (sin distinguir mayúsculas ni tildes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(c: ReaderController, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { hits = null; return@LaunchedEffect }
        delay(350)
        searching = true
        hits = c.search(query)
        searching = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = NB.surface, contentColor = NB.text) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Buscar en el libro", color = NB.text3) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            val h = hits
            when {
                searching && h == null -> CircularProgressIndicator(Modifier.padding(24.dp).size(22.dp), color = NB.text, strokeWidth = 2.dp)
                h != null && h.isEmpty() -> Text("Sin resultados", color = NB.text3, fontFamily = Inter, modifier = Modifier.padding(20.dp))
                h != null -> {
                    Text(
                        if (h.size >= 300) "Más de 300 resultados" else "${h.size} resultados",
                        color = NB.text3, fontSize = 12.sp, fontFamily = Inter, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    LazyColumn(Modifier.navigationBarsPadding()) {
                        items(h) { hit ->
                            Column(
                                Modifier.fillMaxWidth().clickable { c.goToSearch(hit, query); onDismiss() }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text(hit.chapterTitle.ifBlank { "Capítulo ${hit.chapter + 1}" }, color = NB.text3, fontSize = 12.sp, fontFamily = Inter, maxLines = 1)
                                Text(hit.snippet, color = NB.text, fontSize = 14.sp, fontFamily = Inter, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Valoración de un libro según el modo elegido en Ajustes: estrellas, o por aspectos (trama, personajes y prosa).
 * Por aspectos, la nota general es la media de los aspectos valorados (en medias estrellas).
 */
@Composable
fun BookRating(bookId: String, size: Int = 28, color: Color = NB.text, centered: Boolean = false) {
    val scope = rememberCoroutineScope()
    val books by com.noirbooks.app.data.Library.books
    val b = books.firstOrNull { it.id == bookId } ?: return
    val mode by com.noirbooks.app.data.Prefs.ratingMode
    fun save(t: (com.noirbooks.app.data.Book) -> com.noirbooks.app.data.Book) = scope.launch { com.noirbooks.app.data.Library.update(bookId, t) }
    if (mode != "aspects") {
        StarRating(b.rating, color = color, size = size) { v -> save { it.copy(rating = v) } }
        return
    }
    fun avg(x: com.noirbooks.app.data.Book): Float {
        val l = listOf(x.ratingPlot, x.ratingChars, x.ratingProse).filter { it > 0f }
        return if (l.isEmpty()) 0f else (kotlin.math.round(l.average() * 2) / 2).toFloat()
    }
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        listOf(
            Triple("Trama", b.ratingPlot) { v: Float -> save { val n = it.copy(ratingPlot = v); n.copy(rating = avg(n)) } },
            Triple("Personajes", b.ratingChars) { v: Float -> save { val n = it.copy(ratingChars = v); n.copy(rating = avg(n)) } },
            Triple("Prosa", b.ratingProse) { v: Float -> save { val n = it.copy(ratingProse = v); n.copy(rating = avg(n)) } },
        ).forEach { (label, value, set) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = color.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = Inter, modifier = Modifier.width(84.dp))
                StarRating(value, color = color, size = (size * 0.75f).toInt()) { v -> set(v) }
            }
        }
        // Siempre visible (con guion si aún no hay nota), para que nada se mueva al valorar
        Text(
            "Nota media: " + (if (b.rating > 0f) String.format(java.util.Locale("es", "ES"), "%.1f", b.rating) + " ★" else "—"),
            color = color.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = Inter, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
