package com.noirbooks.app.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.noirbooks.app.MainActivity
import com.noirbooks.app.R
import com.noirbooks.app.data.BookServer
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.Epub
import com.noirbooks.app.data.EpubBook
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.HighlightColor
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.durationLabel
import com.noirbooks.app.data.minutesLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Sheet { None, Toc, Style, Search }

@Composable
fun ReaderScreen(bookId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var opened by remember(bookId) { mutableStateOf<Pair<EpubBook, BookServer>?>(null) }
    var error by remember(bookId) { mutableStateOf<String?>(null) }
    val settings by Prefs.reader
    val theme = settings.theme

    LaunchedEffect(bookId) {
        val b = Library.get(bookId)
        if (b == null) { onClose(); return@LaunchedEffect }
        try {
            opened = withContext(Dispatchers.IO) {
                val file = Library.epubOf(bookId)
                val epub = Epub.open(file, b.title)
                epub to BookServer(ctx, file, epub)
            }
            Library.markOpened(bookId)
        } catch (e: Exception) {
            ErrorLog.note("Abrir libro", e)
            error = e.message ?: "No se pudo abrir el libro."
        }
    }
    DisposableEffect(bookId) {
        onDispose { opened?.second?.close() }
    }

    Box(Modifier.fillMaxSize().background(Color(theme.bg))) {
        val o = opened
        when {
            error != null -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "", color = Color(theme.fg), textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(20.dp))
                Text("Volver", color = Color(theme.fg), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onClose).padding(12.dp))
            }
            o == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color(theme.muted), strokeWidth = 2.dp)
            else -> {
                val controller = remember(bookId) {
                    val b = Library.get(bookId)
                    val restart = b?.status == BookStatus.Read
                    ReaderController(
                        ctx, bookId, o.first, o.second, scope,
                        if (restart) 0 else b?.chapter ?: 0,
                        if (restart) 0f else b?.fraction ?: 0f,
                        if (restart) "" else b?.anchor.orEmpty(),
                    )
                }
                Reader(controller, onClose)
            }
        }
    }
}

@Composable
private fun Reader(c: ReaderController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    val settings by Prefs.reader
    val theme = settings.theme
    val fg = Color(theme.fg)
    val muted = Color(theme.muted)
    val bg = Color(theme.bg)
    val books by Library.books
    val book = books.firstOrNull { it.id == c.bookId }
    var bars by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var showHint by remember { mutableStateOf(!Prefs.gestureHintShown) }
    var summary by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<Int?>(null) } // color del subrayado nuevo con nota
    var dictionaryWord by remember { mutableStateOf<Pair<String, String>?>(null) }
    val cpm = remember { Stats.charsPerMinute() }

    fun requestClose() {
        c.pause()
        if (c.sessionSeconds >= 60 && !summary) summary = true else onClose()
    }
    BackHandler {
        when {
            c.selection != null -> c.clearSelection()
            bars -> bars = false
            else -> requestClose()
        }
    }

    c.onTap = { x ->
        when {
            showHint -> { showHint = false; Prefs.gestureHintShown = true }
            bars -> bars = false
            x < 0.28f -> c.prev()
            x > 0.72f -> c.next()
            else -> bars = true
        }
    }

    // Tiempo de lectura: se pausa al salir de la app o apagar la pantalla.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> c.pause()
                Lifecycle.Event.ON_START -> c.resume()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            c.pause()
        }
    }

    // Pantalla completa mientras se lee; las barras del sistema vuelven con el menú.
    LaunchedEffect(bars, sheet, theme) {
        val w = activity?.window ?: return@LaunchedEffect
        val ctl = WindowCompat.getInsetsController(w, w.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (bars || sheet != Sheet.None) ctl.show(WindowInsetsCompat.Type.systemBars())
        else ctl.hide(WindowInsetsCompat.Type.systemBars())
        ctl.isAppearanceLightStatusBars = !theme.dark
        ctl.isAppearanceLightNavigationBars = !theme.dark
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
    LaunchedEffect(settings.brightness, settings.keepScreenOn) {
        activity?.window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                else settings.brightness.coerceIn(0.02f, 1f)
            }
            if (settings.keepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(Unit) {
        MainActivity.keyHandler = handler@{ e ->
            if (sheet != Sheet.None) return@handler false
            val vol = Prefs.reader.value.volumeKeys
            val next = e.keyCode == KeyEvent.KEYCODE_PAGE_DOWN || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                e.keyCode == KeyEvent.KEYCODE_SPACE || (vol && e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            val prev = e.keyCode == KeyEvent.KEYCODE_PAGE_UP || e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                (vol && e.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            if (!next && !prev) return@handler false
            if (e.action == KeyEvent.ACTION_DOWN) { if (next) c.next() else c.prev() }
            true
        }
        onDispose { MainActivity.keyHandler = null }
    }
    var firstSettings by remember { mutableStateOf(true) }
    val layoutKey = settings.copy(volumeKeys = false, brightness = 0f, keepScreenOn = false)
    LaunchedEffect(layoutKey) {
        if (firstSettings) firstSettings = false else c.applySettings()
    }
    // Abrir el libro desde una cita: ir a esa cita en cuanto el capítulo esté listo.
    LaunchedEffect(c.ready) {
        val j = AppState.jumpTo.value
        if (c.ready && j != null && j.first == c.bookId) {
            AppState.jumpTo.value = null
            c.goToHighlight(j.second)
        }
    }
    // "Volver a donde estabas": visible unos segundos tras un salto.
    var showBack by remember { mutableStateOf(false) }
    LaunchedEffect(c.history.size) {
        if (c.history.isNotEmpty()) { showBack = true; delay(12_000); showBack = false } else showBack = false
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            key(c.webKey) {
                AndroidView(
                    factory = { c.createWebView(it) },
                    onRelease = { c.release(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            val chLen = book?.chapterLengths?.getOrNull(c.chapter) ?: 0
            val leftInChapter = (chLen * (1f - (c.page + 1f) / c.count.coerceAtLeast(1))).toInt().coerceAtLeast(0)
            val pct = ((book?.progressAt(c.chapter, (c.page + 1f) / c.count.coerceAtLeast(1)) ?: 0f) * 100).toInt()
            Row(
                Modifier.fillMaxWidth().height(28.dp).padding(horizontal = settings.margins.px.dp.coerceAtLeast(16.dp)),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    if (c.ready) (if (c.page >= c.count - 1) "Último de este capítulo" else "Quedan ${minutesLabel(leftInChapter, cpm)} en el capítulo") else "",
                    color = muted, fontSize = 11.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(if (c.ready) "$pct %" else "", color = muted, fontSize = 11.sp, fontFamily = Inter)
            }
        }

        if (c.ready && c.currentBookmark() != null && !bars) {
            Icon(
                painterResource(R.drawable.ic_bookmark_filled), null, tint = muted,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp).size(18.dp),
            )
        }

        // Volver a donde estabas
        AnimatedVisibility(showBack && !bars && c.selection == null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = 36.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.92f)).clickable { c.goBack() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = bg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Volver a donde estabas", color = bg, fontSize = 13.sp, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        }

        // Barra superior
        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(bg.copy(alpha = 0.97f)).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Biblioteca", tint = fg) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(book?.title ?: c.epub.title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val chTitle = c.chapterTitle()
                    if (chTitle.isNotBlank()) {
                        Text(chTitle, color = muted, fontSize = 12.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { sheet = Sheet.Search; bars = false }) { Icon(Icons.Filled.Search, "Buscar en el libro", tint = fg) }
                val marked = c.currentBookmark() != null
                IconButton(onClick = { c.toggleBookmark() }) {
                    Icon(
                        painterResource(if (marked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
                        if (marked) "Quitar marcador" else "Añadir marcador", tint = fg,
                    )
                }
            }
        }

        // Barra inferior
        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(c, fg, muted, bg,
                onToc = { sheet = Sheet.Toc; bars = false },
                onStyle = { sheet = Sheet.Style; bars = false })
        }

        // Barra de selección propia (subrayar, nota, copiar, compartir, diccionario)
        c.selection?.let { sel ->
            SelectionBar(
                sel = sel, fg = fg, bg = bg,
                onColor = { color -> c.addHighlight(color) },
                onNote = { noteFor = 0 },
                onCopy = { copyText(ctx, "Cita", sel.text); AppState.toast("Copiado"); c.clearSelection() },
                onShare = {
                    shareQuoteImage(ctx, sel.text, book?.title.orEmpty(), book?.author.orEmpty())
                    c.clearSelection()
                },
                onDefine = {
                    val word = sel.text.trim().split(Regex("\\s+")).firstOrNull()?.trim { !it.isLetterOrDigit() }.orEmpty()
                    if (word.isNotEmpty()) {
                        dictionaryWord = word to sel.sentence
                        scope.launch {
                            Library.addWord(com.noirbooks.app.data.VocabWord(word, sel.sentence, c.bookId, book?.title.orEmpty(), System.currentTimeMillis()))
                        }
                    }
                    c.clearSelection()
                },
                modifier = Modifier.align(if (sel.top > 0.5f) Alignment.TopCenter else Alignment.BottomCenter),
            )
        }

        if (showHint && c.ready) GestureHint(fg, bg) { showHint = false; Prefs.gestureHintShown = true }

        if (c.endReached) {
            EndOfBook(c, fg, muted, bg, onClose = { c.endReached = false; requestClose() })
        }
    }

    when (sheet) {
        Sheet.Toc -> TocSheet(c, onDismiss = { sheet = Sheet.None })
        Sheet.Style -> StyleSheet(onDismiss = { sheet = Sheet.None })
        Sheet.Search -> SearchSheet(c, onDismiss = { sheet = Sheet.None })
        Sheet.None -> {}
    }
    noteFor?.let {
        NoteDialog(
            title = "Nota", initial = "",
            onSave = { text -> c.addHighlight(HighlightColor.Yellow.ordinal, text); noteFor = null },
            onDismiss = { noteFor = null },
        )
    }
    c.tappedHighlight?.let { h ->
        HighlightSheet(
            h = h, bookTitle = book?.title.orEmpty(), author = book?.author.orEmpty(),
            onChange = { c.updateHighlight(it); c.tappedHighlight = null },
            onDelete = { c.deleteHighlight(h); c.tappedHighlight = null },
            onDismiss = { c.tappedHighlight = null },
        )
    }
    dictionaryWord?.let { (w, _) -> DictionarySheet(w, onDismiss = { dictionaryWord = null }) }
    if (summary) {
        SessionSummary(
            seconds = c.sessionSeconds.toLong(), pages = c.sessionPages,
            from = c.startProgress, to = book?.progress ?: c.startProgress,
            onDone = { summary = false; onClose() },
        )
    }
}

@Composable
private fun BottomBar(c: ReaderController, fg: Color, muted: Color, bg: Color, onToc: () -> Unit, onStyle: () -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val book = c.book
    Column(
        Modifier.fillMaxWidth().background(bg.copy(alpha = 0.97f)).navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    ) {
        val shown = dragging ?: book?.progressAt(c.chapter, c.fraction) ?: 0f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (dragging != null) chapterAt(c, shown) else "Página ${c.page + 1} de ${c.count} · ${c.chapterTitle()}",
                color = muted, fontSize = 12.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${(shown * 100).toInt()} %", color = muted, fontSize = 12.sp, fontFamily = Inter)
        }
        Box {
            // Mapa del libro: capítulos, subrayados y marcadores sobre la barra
            if (book != null && book.chapterLengths.size > 1) {
                val total = book.totalChars.toFloat()
                val starts = remember(book.id, book.chapterLengths) {
                    book.chapterLengths.runningFold(0) { a, b -> a + b }.dropLast(1).drop(1).map { it / total }
                }
                val marks = book.highlights.map { book.progressAt(it.chapter, it.fraction) } +
                    book.bookmarks.map { book.progressAt(it.chapter, it.fraction) }
                Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
                    val y = size.height / 2
                    starts.forEach { f ->
                        drawLine(muted.copy(alpha = 0.5f), Offset(f * size.width, y - 7f), Offset(f * size.width, y + 7f), strokeWidth = 2f)
                    }
                    marks.forEach { f -> drawCircle(fg.copy(alpha = 0.55f), radius = 3.5f, center = Offset(f * size.width, y - 16f)) }
                }
            }
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { c.goToBookFraction(it) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = muted.copy(alpha = 0.3f)),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { c.prevChapter() }, enabled = c.chapter > 0) {
                Icon(Icons.Filled.KeyboardArrowLeft, "Capítulo anterior", tint = if (c.chapter > 0) fg else muted.copy(alpha = 0.4f))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onToc).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_toc), null, tint = fg, modifier = Modifier.size(22.dp))
                    Text("Índice", color = fg, fontSize = 11.sp, fontFamily = Inter)
                }
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onStyle).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Aa", color = fg, fontSize = 18.sp, fontFamily = Playfair, modifier = Modifier.height(22.dp))
                    Text("Lectura", color = fg, fontSize = 11.sp, fontFamily = Inter)
                }
            }
            Spacer(Modifier.weight(1f))
            val canNext = c.chapter < c.epub.spine.lastIndex
            IconButton(onClick = { c.nextChapter() }, enabled = canNext) {
                Icon(Icons.Filled.KeyboardArrowRight, "Capítulo siguiente", tint = if (canNext) fg else muted.copy(alpha = 0.4f))
            }
        }
    }
}

private fun chapterAt(c: ReaderController, f: Float): String {
    val b = c.book ?: return ""
    var pos = f * b.totalChars
    for (i in b.chapterLengths.indices) {
        if (pos <= b.chapterLengths[i] || i == b.chapterLengths.lastIndex) return c.chapterTitle(i).ifBlank { "Capítulo ${i + 1}" }
        pos -= b.chapterLengths[i]
    }
    return ""
}

@Composable
private fun SelectionBar(
    sel: TextSelection, fg: Color, bg: Color,
    onColor: (Int) -> Unit, onNote: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onDefine: () -> Unit,
    modifier: Modifier,
) {
    val single = !sel.text.trim().contains(' ')
    Column(
        modifier.padding(horizontal = 12.dp, vertical = 28.dp).statusBarsPadding().navigationBarsPadding()
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFF151517)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightColor.entries.forEach { hc ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(hc.argb)).clickable { onColor(hc.ordinal) },
                )
            }
            Spacer(Modifier.width(2.dp))
            BarAction("Nota", onNote)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BarAction("Copiar", onCopy)
            BarAction("Compartir", onShare)
            if (single) BarAction("Definir", onDefine)
        }
    }
}

@Composable
private fun BarAction(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 14.sp, fontFamily = Inter, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun GestureHint(fg: Color, bg: Color, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(onClick = onDone)) {
        Row(Modifier.fillMaxSize()) {
            HintZone("Página anterior", "Toca aquí o desliza →", Modifier.weight(0.28f))
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.25f)))
            HintZone("Menú", "Toca el centro\nMantén pulsado para subrayar", Modifier.weight(0.44f))
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.25f)))
            HintZone("Página siguiente", "Toca aquí o desliza ←", Modifier.weight(0.28f))
        }
        Text(
            "Toca para empezar", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun HintZone(title: String, sub: String, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = Color.White.copy(alpha = 0.75f), fontFamily = Inter, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EndOfBook(c: ReaderController, fg: Color, muted: Color, bg: Color, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(c.book?.rating ?: 0f) }
    Box(Modifier.fillMaxSize().background(bg.copy(alpha = 0.97f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Fin", fontFamily = Playfair, fontSize = 44.sp, color = fg)
            Spacer(Modifier.height(8.dp))
            val secs = Stats.secondsForBook(c.bookId)
            Text(
                if (secs > 0) "Has terminado el libro en ${durationLabel(secs)} de lectura." else "Has terminado el libro.",
                color = muted, fontSize = 15.sp, fontFamily = Inter, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Text("¿Qué te ha parecido?", color = fg, fontFamily = Inter, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            StarRating(rating, color = fg, size = 34) { rating = it }
            Spacer(Modifier.height(26.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(fg).clickable {
                    scope.launch {
                        Library.setStatus(c.bookId, BookStatus.Read)
                        Library.update(c.bookId) { it.copy(rating = rating) }
                    }
                    onClose()
                }.padding(horizontal = 24.dp, vertical = 12.dp),
            ) { Text("Marcar como leído", color = bg, fontWeight = FontWeight.SemiBold, fontFamily = Inter) }
            Spacer(Modifier.height(10.dp))
            Text("Seguir aquí", color = muted, fontFamily = Inter,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { c.endReached = false }.padding(12.dp))
        }
    }
}

@Composable
private fun SessionSummary(seconds: Long, pages: Int, from: Float, to: Float, onDone: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = NB.surface2,
        title = { Text("Sesión de lectura", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SummaryFigure(durationLabel(seconds), "leyendo")
                    SummaryFigure("$pages", if (pages == 1) "página" else "páginas")
                    val gained = ((to - from) * 100).toInt().coerceAtLeast(0)
                    SummaryFigure("+$gained %", "del libro")
                }
                Spacer(Modifier.height(12.dp))
                Text("Llevas el ${(to * 100).toInt()} % leído.", color = NB.text2, fontFamily = Inter, fontSize = 14.sp)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Listo", color = NB.text, fontWeight = FontWeight.SemiBold) } },
    )
}

@Composable
private fun SummaryFigure(value: String, label: String) {
    Column {
        Text(value, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = NB.text)
        Text(label, fontFamily = Inter, fontSize = 12.sp, color = NB.text3)
    }
}
