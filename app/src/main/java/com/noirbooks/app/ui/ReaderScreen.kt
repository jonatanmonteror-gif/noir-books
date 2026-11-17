package com.noirbooks.app.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.noirbooks.app.MainActivity
import com.noirbooks.app.R
import com.noirbooks.app.data.BookServer
import com.noirbooks.app.data.Epub
import com.noirbooks.app.data.EpubBook
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.minutesLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Sheet { None, Toc, Style }

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
            Library.update(bookId) { it.copy(lastOpened = System.currentTimeMillis()) }
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
                val b = Library.get(bookId)
                val controller = remember(bookId) {
                    ReaderController(ctx, bookId, o.first, o.second, scope, b?.chapter ?: 0, if (b?.finished == true) 0f else b?.fraction ?: 0f)
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
    val settings by Prefs.reader
    val theme = settings.theme
    val fg = Color(theme.fg)
    val muted = Color(theme.muted)
    val books by Library.books
    val book = books.firstOrNull { it.id == c.bookId }
    var bars by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }

    c.onTap = { x ->
        when {
            bars -> bars = false
            x < 0.28f -> c.prev()
            x > 0.72f -> c.next()
            else -> bars = true
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
    // Brillo y pantalla siempre encendida
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
    // Botones de volumen y teclado
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
    // Ajustes cambiados → volver a maquetar
    var firstSettings by remember { mutableStateOf(true) }
    val layoutKey = settings.copy(volumeKeys = false, brightness = 0f, keepScreenOn = false)
    LaunchedEffect(layoutKey) {
        if (firstSettings) firstSettings = false else c.applySettings()
    }

    Box(Modifier.fillMaxSize().background(Color(theme.bg))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            key(c.webKey) {
                AndroidView(
                    factory = { c.createWebView(it) },
                    onRelease = { c.release(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            // Pie discreto: capítulo y tiempo restante
            val chLen = book?.chapterLengths?.getOrNull(c.chapter) ?: 0
            val leftInChapter = (chLen * (1f - (c.page + 1f) / c.count.coerceAtLeast(1))).toInt().coerceAtLeast(0)
            val pct = ((book?.progressAt(c.chapter, (c.page + 1f) / c.count.coerceAtLeast(1)) ?: 0f) * 100).toInt()
            Row(
                Modifier.fillMaxWidth().height(28.dp).padding(horizontal = settings.margins.px.dp.coerceAtLeast(16.dp)),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    if (c.ready) (if (c.page >= c.count - 1) "Último de este capítulo" else "Quedan ${minutesLabel(leftInChapter)} en el capítulo") else "",
                    color = muted, fontSize = 11.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(if (c.ready) "$pct %" else "", color = muted, fontSize = 11.sp, fontFamily = Inter)
            }
        }

        // Marca de marcador en la esquina
        if (c.ready && c.currentBookmark() != null && !bars) {
            Icon(
                painterResource(R.drawable.ic_bookmark_filled), null, tint = muted,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp).size(18.dp),
            )
        }

        // Barra superior
        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(theme.bg).copy(alpha = 0.97f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Biblioteca", tint = fg) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(book?.title ?: c.epub.title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val chTitle = c.chapterTitle()
                    if (chTitle.isNotBlank()) {
                        Text(chTitle, color = muted, fontSize = 12.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
            BottomBar(c, book?.progress ?: 0f, fg, muted, Color(theme.bg),
                onToc = { sheet = Sheet.Toc; bars = false },
                onStyle = { sheet = Sheet.Style; bars = false })
        }

        if (c.endReached) {
            EndOfBook(fg, muted, Color(theme.bg),
                onFinish = { c.markFinished(); c.endReached = false; onClose() },
                onStay = { c.endReached = false })
        }
    }

    when (sheet) {
        Sheet.Toc -> TocSheet(c, onDismiss = { sheet = Sheet.None })
        Sheet.Style -> StyleSheet(onDismiss = { sheet = Sheet.None })
        Sheet.None -> {}
    }
}

@Composable
private fun BottomBar(c: ReaderController, progress: Float, fg: Color, muted: Color, bg: Color, onToc: () -> Unit, onStyle: () -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val book = c.book
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    ) {
        val shown = dragging ?: book?.progressAt(c.chapter, c.fraction) ?: progress
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (dragging != null) chapterAt(c, shown) else "Página ${c.page + 1} de ${c.count} · ${c.chapterTitle()}",
                color = muted, fontSize = 12.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${(shown * 100).toInt()} %", color = muted, fontSize = 12.sp, fontFamily = Inter)
        }
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { c.goToBookFraction(it) }; dragging = null },
            colors = SliderDefaults.colors(
                thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = muted.copy(alpha = 0.3f),
            ),
        )
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
private fun EndOfBook(fg: Color, muted: Color, bg: Color, onFinish: () -> Unit, onStay: () -> Unit) {
    Box(Modifier.fillMaxSize().background(bg.copy(alpha = 0.96f)).clickable(onClick = onStay), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Fin", fontFamily = Playfair, fontSize = 44.sp, color = fg)
            Spacer(Modifier.height(8.dp))
            Text("Has terminado el libro.", color = muted, fontSize = 15.sp, fontFamily = Inter)
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(fg).clickable(onClick = onFinish)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text("Marcar como leído", color = bg, fontWeight = FontWeight.SemiBold, fontFamily = Inter)
            }
            Spacer(Modifier.height(10.dp))
            Text("Seguir aquí", color = muted, fontFamily = Inter,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onStay).padding(12.dp))
        }
    }
}
