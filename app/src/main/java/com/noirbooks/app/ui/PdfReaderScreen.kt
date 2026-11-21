package com.noirbooks.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.percentLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Abre un PDF con el lector de Android. Las páginas se pintan de una en una (PdfRenderer solo admite una a la vez). */
private class PdfDoc(file: java.io.File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val count: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                renderer.openPage(index).use { p ->
                    val w = widthPx.coerceIn(300, 2200)
                    val h = (w.toLong() * p.height / p.width.coerceAtLeast(1)).toInt().coerceIn(100, 4000)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.getOrNull()
        }
    }

    fun close() { runCatching { renderer.close() }; runCatching { pfd.close() } }
}

@Composable
fun PdfReaderScreen(bookId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = remember { Library.get(bookId) }
    val doc = remember {
        runCatching { PdfDoc(Library.pdfOf(bookId)) }.onFailure { ErrorLog.note("Abrir PDF", it) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { doc?.close() } }
    if (book == null || doc == null || doc.count == 0) {
        LaunchedEffect(Unit) { AppState.toast("No se pudo abrir este PDF"); onClose() }
        return
    }
    // Pantalla siempre encendida mientras se lee
    DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(Unit) { Library.markOpened(bookId) }

    val start = ((book.manualProgress * doc.count).toInt() - 1).coerceIn(0, doc.count - 1)
        .let { if (book.manualProgress <= 0f) 0 else it }
    val pager = rememberPagerState(initialPage = start) { doc.count }
    var bars by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }

    // Tiempo por página: cuenta entre 5 s y 2 min, como en los EPUB; se guarda solo si al salir dices que sí.
    val chunks = remember { mutableListOf<Stats.Chunk>() }
    var pageSince by remember { mutableStateOf(System.currentTimeMillis()) }
    fun closePage(turned: Boolean) {
        val now = System.currentTimeMillis()
        val secs = ((now - pageSince) / 1000).coerceAtMost(120)
        if (secs >= 5) chunks += Stats.Chunk(now, secs, turned)
        pageSince = now
    }
    LaunchedEffect(pager.currentPage) {
        val p = pager.currentPage
        Library.update(bookId) { it.copy(manualProgress = (p + 1f) / doc.count, lastOpened = System.currentTimeMillis()) }
    }
    var lastPage by remember { mutableStateOf(start) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != lastPage) { closePage(turned = true); lastPage = pager.settledPage }
    }

    fun finish() {
        closePage(turned = false)
        if (chunks.isNotEmpty()) askSave = true else onClose()
    }
    BackHandler { if (bars) bars = false else finish() }

    Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            HorizontalPager(
                state = pager, userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
                key = { it }, modifier = Modifier.fillMaxSize(),
            ) { index ->
                PdfPage(doc, index, (widthPx * 1.6f).toInt(),
                    onZoom = { zoomed = it },
                    onTap = { x ->
                        val third = widthPx / 3f
                        when {
                            x < third -> scope.launch { pager.animateScrollToPage((index - 1).coerceAtLeast(0)) }
                            x > widthPx - third -> scope.launch { pager.animateScrollToPage((index + 1).coerceAtMost(doc.count - 1)) }
                            else -> bars = !bars
                        }
                    })
            }
        }

        // Número de página discreto abajo
        if (!bars) Text(
            "${pager.currentPage + 1} / ${doc.count}", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontFamily = Inter,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
        )

        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xF0151517)).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White) }
                Text(book.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Color(0xF0151517)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                var drag by remember { mutableFloatStateOf(-1f) }
                val shown = if (drag >= 0f) drag.toInt() else pager.currentPage
                Text("Página ${shown + 1} de ${doc.count} · ${percentLabel((shown + 1f) / doc.count)}",
                    color = Color.White, fontSize = 13.sp, fontFamily = Inter)
                Slider(
                    value = shown.toFloat(), valueRange = 0f..(doc.count - 1).coerceAtLeast(1).toFloat(),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { val t = drag.toInt(); drag = -1f; scope.launch { pager.scrollToPage(t) } },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                )
                Text("Toca los lados para pasar página. Pellizca o toca dos veces para ampliar.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }

    if (askSave) {
        val mins = (chunks.sumOf { it.seconds } / 60).coerceAtLeast(1)
        val pages = chunks.count { it.page }
        AlertDialog(
            onDismissRequest = {}, containerColor = NB.surface2,
            title = { Text("¿Guardar esta lectura?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = { Text("$mins min · ${if (pages == 1) "1 página" else "$pages páginas"}. Si la guardas, cuenta en tu diario y estadísticas.",
                color = NB.text2, fontFamily = Inter) },
            confirmButton = {
                TextButton(onClick = {
                    Stats.commitAppReading(bookId, chunks.toList())
                    askSave = false; onClose()
                }) { Text("Guardar lectura", color = NB.text, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { askSave = false; onClose() }) { Text("No guardar", color = NB.text2) } },
        )
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int, onZoom: (Boolean) -> Unit, onTap: (Float) -> Unit) {
    val bmp by produceState<Bitmap?>(null, index, widthPx) { value = doc.render(index, widthPx) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) { onDispose { bmp?.recycle() } }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero; onZoom(false) } else { scale = 2.5f; onZoom(true) }
                    },
                    onTap = { onTap(it.x) },
                )
            }
            // Pellizcar para ampliar; con la página ampliada, arrastrar la mueve. Sin ampliar, el deslizar pasa página.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val s = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            scale = s
                            offset = if (s == 1f) Offset.Zero else offset + event.calculatePan()
                            onZoom(s > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
        else Image(
            b.asImageBitmap(), contentDescription = "Página ${index + 1}", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
        )
    }
}
