package com.noirbooks.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookServer
import com.noirbooks.app.data.Bookmark
import com.noirbooks.app.data.EpubBook
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Estado del lector y puente con el WebView. Un WebView por libro abierto; cada capítulo se carga
 * de uno en uno (lección de Noir+: nada de cargas grandes en paralelo).
 */
class ReaderController(
    ctx: Context,
    val bookId: String,
    val epub: EpubBook,
    val server: BookServer,
    private val scope: CoroutineScope,
    startChapter: Int,
    startFraction: Float,
) {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())

    var chapter by mutableIntStateOf(startChapter.coerceIn(0, epub.spine.lastIndex))
        private set
    var page by mutableIntStateOf(0)
        private set
    var count by mutableIntStateOf(1)
        private set
    var ready by mutableStateOf(false)
        private set
    var endReached by mutableStateOf(false)
    /** Se incrementa si el motor del WebView se cae, para crear uno nuevo. */
    var webKey by mutableIntStateOf(0)
        private set
    var lastTap by mutableFloatStateOf(0f)

    /** La interfaz decide qué hacer con un toque (pasar página o mostrar las barras). */
    var onTap: (x: Float) -> Unit = {}

    private var web: WebView? = null
    @Volatile private var initJson: String = ""
    private var target: JSONObject = fractionTarget(startFraction)

    /** Índice del capítulo (posición en el orden de lectura) de cada entrada del índice, o -1. */
    val tocSpine: List<Int> = epub.toc.map { e -> epub.spine.indexOf(e.href) }

    val book: Book? get() = Library.get(bookId)

    fun chapterTitle(ch: Int = chapter): String {
        val exact = epub.toc.indices.firstOrNull { tocSpine[it] == ch }
        if (exact != null) return epub.toc[exact].title
        val before = epub.toc.indices.lastOrNull { tocSpine[it] in 0 until ch }
        return before?.let { epub.toc[it].title } ?: ""
    }

    val fraction: Float get() = if (count > 0) page.toFloat() / count else 0f

    // ---- WebView ----

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(ctx: Context): WebView = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.textZoom = 100
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.loadWithOverviewMode = false
        settings.useWideViewPort = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isHapticFeedbackEnabled = false
        setBackgroundColor(Prefs.reader.value.theme.bg.toInt())
        addJavascriptInterface(Bridge(), "Android")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                server.handle(request.url)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == BookServer.HOST) {
                    // Navegación dentro del libro que no pasó por el lector (p. ej. un formulario o un script).
                    openLink(u.toString())
                    return true
                }
                openExternal(u.toString())
                return true
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                ErrorLog.note("Motor de lectura cerrado (crash=${detail?.didCrash()})")
                // Se crea un WebView nuevo en la misma posición (el viejo se destruye al retirarlo de la pantalla).
                web = null
                target = fractionTarget(fraction)
                webKey++
                return true
            }
        }
        web = this
        loadChapter(chapter, target)
    }

    fun release(view: WebView) {
        if (web === view) web = null
        runCatching {
            view.stopLoading()
            view.removeJavascriptInterface("Android")
            view.destroy()
        }
    }

    private fun prepareInit() {
        initJson = JSONObject()
            .put("settings", Prefs.reader.value.toJson())
            .put("target", target)
            .put("lang", epub.language.ifBlank { "es" })
            .toString()
    }

    private fun loadChapter(ch: Int, t: JSONObject) {
        val c = ch.coerceIn(0, epub.spine.lastIndex)
        chapter = c
        target = t
        ready = false
        prepareInit()
        web?.loadUrl(server.urlOf(epub.spine[c]))
    }

    fun goTo(ch: Int, fraction: Float) = loadChapter(ch, fractionTarget(fraction))

    fun goToBookFraction(f: Float) {
        val b = book ?: return
        val lengths = b.chapterLengths
        if (lengths.isEmpty()) return
        var pos = (f.coerceIn(0f, 1f) * b.totalChars)
        for (i in lengths.indices) {
            val len = lengths[i]
            if (pos <= len || i == lengths.lastIndex) {
                goTo(i, if (len > 0) (pos / len).coerceIn(0f, 0.999f) else 0f)
                return
            }
            pos -= len
        }
    }

    fun goToToc(index: Int) {
        val e = epub.toc.getOrNull(index) ?: return
        val ch = tocSpine.getOrNull(index)?.takeIf { it >= 0 } ?: return
        loadChapter(ch, if (e.anchor != null) JSONObject().put("type", "anchor").put("value", e.anchor) else START)
    }

    fun next() = js("window.noir && noir.next()")
    fun prev() = js("window.noir && noir.prev()")

    fun nextChapter() { if (chapter < epub.spine.lastIndex) loadChapter(chapter + 1, START) }
    fun prevChapter() { if (chapter > 0) loadChapter(chapter - 1, START) }

    /** Ajustes de lectura cambiados: se vuelve a maquetar conservando la posición. */
    fun applySettings() {
        target = fractionTarget(fraction)
        prepareInit()
        web?.setBackgroundColor(Prefs.reader.value.theme.bg.toInt())
        js("window.noir && noir.apply(${JSONObject.quote(Prefs.reader.value.toJson().toString())})")
    }

    fun snippet(cb: (String) -> Unit) {
        val w = web ?: return cb("")
        w.evaluateJavascript("window.noir ? noir.snippet() : ''") { raw ->
            val s = runCatching { JSONArray("[$raw]").optString(0) }.getOrDefault("")
            cb(if (s == "null") "" else s)
        }
    }

    private fun js(code: String) {
        web?.evaluateJavascript(code, null)
    }

    private fun openLink(url: String) {
        val (path, frag) = server.pathOf(url) ?: return
        val ch = epub.spine.indexOf(path)
        if (ch < 0) return
        loadChapter(ch, if (frag.isNullOrEmpty()) START else JSONObject().put("type", "anchor").put("value", frag))
    }

    private fun openExternal(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("mailto:")) return
        runCatching {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---- Progreso ----

    private var saveJob: Job? = null

    private fun savePosition() {
        val ch = chapter
        val f = fraction
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            Library.update(bookId) { it.copy(chapter = ch, fraction = f, lastOpened = System.currentTimeMillis()) }
        }
    }

    fun currentBookmark(): Bookmark? {
        val b = book ?: return null
        return b.bookmarks.firstOrNull { m ->
            m.chapter == chapter && (m.fraction * count).let { kotlin.math.round(it).toInt() } == page
        }
    }

    fun toggleBookmark() {
        val existing = currentBookmark()
        if (existing != null) {
            scope.launch { Library.update(bookId) { b -> b.copy(bookmarks = b.bookmarks - existing) } }
            return
        }
        val ch = chapter
        val f = fraction
        val label = chapterTitle()
        snippet { text ->
            scope.launch {
                Library.update(bookId) { b ->
                    b.copy(bookmarks = (b.bookmarks + Bookmark(ch, f, label, text, System.currentTimeMillis()))
                        .sortedWith(compareBy({ it.chapter }, { it.fraction })))
                }
            }
        }
    }

    fun deleteBookmark(m: Bookmark) {
        scope.launch { Library.update(bookId) { b -> b.copy(bookmarks = b.bookmarks - m) } }
    }

    fun markFinished() {
        scope.launch { Library.update(bookId) { it.copy(finished = true, lastOpened = System.currentTimeMillis()) } }
    }

    /** Métodos que llama el JavaScript del lector (en un hilo aparte: se pasa todo al hilo principal). */
    private inner class Bridge {
        @JavascriptInterface
        fun init(): String = initJson

        @JavascriptInterface
        fun onReady(p: Int, c: Int) = main.post {
            count = c.coerceAtLeast(1); page = p.coerceIn(0, count - 1); ready = true
            savePosition()
        }

        @JavascriptInterface
        fun onPage(p: Int, c: Int) = main.post {
            count = c.coerceAtLeast(1); page = p.coerceIn(0, count - 1)
            savePosition()
        }

        @JavascriptInterface
        fun onTap(x: Double, y: Double) = main.post { lastTap = x.toFloat(); onTap(x.toFloat()) }

        @JavascriptInterface
        fun onEnd() = main.post {
            if (chapter < epub.spine.lastIndex) loadChapter(chapter + 1, START)
            else endReached = true
        }

        @JavascriptInterface
        fun onStart() = main.post {
            if (chapter > 0) loadChapter(chapter - 1, END)
        }

        @JavascriptInterface
        fun onLink(url: String) = main.post { openLink(url) }

        @JavascriptInterface
        fun onExternalLink(url: String) = main.post { openExternal(url) }
    }

    companion object {
        private val START get() = JSONObject().put("type", "start")
        private val END get() = JSONObject().put("type", "end")
        fun fractionTarget(f: Float): JSONObject = JSONObject().put("type", "fraction").put("value", f.toDouble())
    }
}
