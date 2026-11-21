package com.noirbooks.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.doOnLayout
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookServer
import com.noirbooks.app.data.Bookmark
import com.noirbooks.app.data.EpubBook
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Highlight
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Texto seleccionado en el libro. [range] es la posición exacta (JSON del lector). */
data class TextSelection(
    val range: String,
    val text: String,
    val sentence: String,
    val fraction: Float,
    val top: Float,
    val bottom: Float,
)

data class SearchHit(val chapter: Int, val n: Int, val snippet: String, val chapterTitle: String)

/** Un sitio del libro al que se puede volver ("Volver a donde estabas"). */
data class Spot(val chapter: Int, val anchor: String, val fraction: Float)

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
    startAnchor: String,
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
    /** Página de la edición en papel (si el libro trae page-list). */
    var printed by mutableStateOf("")
    val printedTotal: String get() = epub.pageList.lastOrNull()?.label.orEmpty()
    private val marksBySpine: Map<Int, List<com.noirbooks.app.data.PageMark>> by lazy {
        epub.pageList.groupBy { epub.spine.indexOf(it.href) }
    }
    var endReached by mutableStateOf(false)
    /** Se incrementa si el motor del WebView se cae, para crear uno nuevo. */
    var webKey by mutableIntStateOf(0)
        private set
    var selection by mutableStateOf<TextSelection?>(null)
    /** Subrayado tocado (para editarlo). */
    var tappedHighlight by mutableStateOf<Highlight?>(null)
    /** Sitios anteriores a un salto (índice, búsqueda, barra…). */
    var history by mutableStateOf<List<Spot>>(emptyList())
        private set

    /** La interfaz decide qué hacer con un toque (pasar página o mostrar las barras). */
    var onTap: (x: Float, y: Float) -> Unit = { _, _ -> }

    private var web: WebView? = null
    @Volatile private var initJson: String = ""
    private var anchor: String = startAnchor
    private var target: JSONObject =
        if (startAnchor.isNotBlank()) posTarget(startAnchor, startFraction) else fractionTarget(startFraction)

    /** Índice del capítulo de cada entrada del índice, o -1. */
    val tocSpine: List<Int> = epub.toc.map { e -> epub.spine.indexOf(e.href) }

    val book: Book? get() = Library.get(bookId)

    fun chapterTitle(ch: Int = chapter): String {
        val exact = epub.toc.indices.firstOrNull { tocSpine[it] == ch }
        if (exact != null) return epub.toc[exact].title
        val before = epub.toc.indices.lastOrNull { tocSpine[it] in 0 until ch }
        return before?.let { epub.toc[it].title } ?: ""
    }

    val fraction: Float get() = if (count > 0) page.toFloat() / count else 0f

    // ---- Sesión de lectura (tiempo medido solo, como KOReader) ----

    var sessionSeconds by mutableIntStateOf(0)
        private set
    var sessionPages by mutableIntStateOf(0)
        private set
    val startProgress: Float = Library.get(bookId)?.progress ?: 0f
    private var pageSince = 0L
    private var paused = false
    /** Tiempo leído en esta sesión que aún no cuenta: solo se suma si al salir eliges "Guardar lectura". */
    private val pending = ArrayList<Stats.Chunk>()
    val pendingSeconds: Long get() = pending.sumOf { it.seconds }

    /** Páginas del libro en papel: las de la edición que trae el EPUB o las buscadas en el catálogo (0 = no se saben). */
    private fun paperTotal(): Int = printedTotal.toIntOrNull()?.takeIf { it > 0 } ?: (book?.pages ?: 0)

    /** Cuántas páginas en papel equivale una pantalla del capítulo actual (-1 si no se sabe). */
    private fun paperPerScreen(): Float {
        val total = paperTotal()
        val b = book ?: return -1f
        if (total <= 0) return -1f
        val ch = b.chapterLengths.getOrNull(chapter) ?: return -1f
        return ch.toFloat() / b.totalChars / count.coerceAtLeast(1) * total
    }

    private var paperAcc = 0f

    /** Cuenta el tiempo de la página que se deja: menos de 5 s no cuenta, más de 120 s se recorta. */
    private fun closePage(turned: Boolean) {
        val now = System.currentTimeMillis()
        if (pageSince > 0L && !paused) {
            val s = (now - pageSince) / 1000
            if (s >= Stats.MIN_PAGE_SECONDS) {
                val counted = s.coerceAtMost(Stats.MAX_PAGE_SECONDS)
                val pps = if (turned) paperPerScreen() else 0f
                pending += Stats.Chunk(now, counted, turned, if (pps >= 0f) pps else -1f)
                sessionSeconds += counted.toInt()
                if (turned) {
                    if (pps >= 0f) { paperAcc += pps; sessionPages = kotlin.math.round(paperAcc).toInt() } else sessionPages++
                }
            }
        }
        pageSince = now
    }

    fun pause() {
        closePage(turned = false)
        paused = true
        persistPending()
        saveNow()
    }

    /** Se guarda en preferencias por si la app se cierra antes de confirmar. */
    private fun persistPending() {
        if (pending.isEmpty() && !movedSinceOpen()) { Prefs.pendingReading = ""; return }
        val arr = JSONArray()
        pending.forEach { arr.put(JSONArray().put(it.at).put(it.seconds).put(if (it.page) 1 else 0).put(it.paper.toDouble())) }
        Prefs.pendingReading = JSONObject().put("book", bookId).put("chunks", arr)
            .put("ch", openChapter).put("f", openFraction.toDouble()).put("a", openAnchor).toString()
    }

    /** "Guardar lectura": el tiempo cuenta y la posición se queda. */
    fun commitReading(note: String = "") {
        Stats.commitAppReading(bookId, pending.toList(), note)
        pending.clear()
        Prefs.pendingReading = ""
    }

    /** "No guardar": ni el tiempo ni la posición. */
    suspend fun discardReading() {
        pending.clear()
        Prefs.pendingReading = ""
        restoreOpenPosition()
    }

    fun resume() {
        paused = false
        pageSince = System.currentTimeMillis()
    }

    // ---- WebView ----

    /** Oculta el menú de selección del sistema: la app muestra su propia barra. */
    private class QuietSelection(private val inner: ActionMode.Callback?) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            inner?.onCreateActionMode(mode, menu); menu.clear(); return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            inner?.onPrepareActionMode(mode, menu); menu.clear(); return true
        }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
            inner?.onActionItemClicked(mode, item) ?: false
        override fun onDestroyActionMode(mode: ActionMode) { inner?.onDestroyActionMode(mode) }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            val c2 = inner as? ActionMode.Callback2
            if (c2 != null) c2.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(ctx: Context): WebView = object : WebView(ctx) {
        override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
            super.startActionMode(QuietSelection(callback), type)
    }.apply {
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
        setBackgroundColor(Prefs.effectiveReader().palette().bg.toInt())
        addJavascriptInterface(Bridge(), "Android")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                server.handle(request.url)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == BookServer.HOST) {
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
                target = posTarget(anchor, fraction)
                webKey++
                return true
            }
        }
        web = this
        // Cargar solo cuando el WebView ya tiene tamaño (con 0 de alto, cada página sería una línea).
        doOnLayout { loadChapter(chapter, target) }
    }

    // ---- Lectura en voz alta (voz del sistema, frase a frase) ----

    /** Hay lectura en voz alta en curso (sonando o en pausa). */
    var ttsActive by mutableStateOf(false)
        private set
    var ttsPlaying by mutableStateOf(false)
        private set
    var ttsRate by mutableStateOf(Prefs.ttsRate)
        private set
    /** Hora a la que se para sola (0 = sin temporizador). */
    var ttsSleepAt by mutableStateOf(0L)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sentences: List<Pair<String, String>> = emptyList()
    private var sIndex = 0
    private var ttsNextChapter = false

    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) { main.post { ttsAdvance(utteranceId) } }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { main.post { ttsAdvance(utteranceId) } }
    }

    fun ttsStart() {
        ttsActive = true
        ttsEnsure { ttsFetch(fromHere = true) }
    }

    private fun ttsEnsure(then: () -> Unit) {
        if (tts != null && ttsReady) { then(); return }
        tts?.shutdown()
        tts = TextToSpeech(app) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    ttsRestore()
                    tts?.setOnUtteranceProgressListener(ttsListener)
                    then()
                } else {
                    ttsActive = false
                    AppState.toast("No hay ninguna voz instalada en el móvil (Ajustes → Texto a voz)")
                }
            }
        }
    }

    /** Pide al lector las frases del capítulo (desde la página visible o desde el principio). */
    private fun ttsFetch(fromHere: Boolean) {
        val w = web ?: return ttsStop()
        w.evaluateJavascript("window.noir ? noir.ttsSentences($fromHere) : '[]'") { raw ->
            val list = runCatching {
                val arr = JSONArray(org.json.JSONTokener(raw).nextValue() as String)
                (0 until arr.length()).map { i -> arr.getJSONObject(i).let { it.getString("t") to it.getJSONObject("r").toString() } }
            }.getOrDefault(emptyList())
            sentences = list
            sIndex = 0
            if (!ttsActive) return@evaluateJavascript
            if (list.isEmpty()) ttsChapterEnd() else ttsSpeak()
        }
    }

    private fun ttsSpeak() {
        val (text, range) = sentences.getOrNull(sIndex) ?: return ttsChapterEnd()
        js("window.noir && noir.ttsMark(${JSONObject.quote(range)})")
        tts?.setSpeechRate(ttsRate)
        tts?.speak(text.take(3900), TextToSpeech.QUEUE_FLUSH, null, "s$sIndex")
        val wasPlaying = ttsPlaying
        ttsPlaying = true
        if (!wasPlaying) ttsNotify()
    }

    /** Notificación con Pausa/Seguir y Parar; mantiene la voz con la pantalla apagada. */
    private fun ttsNotify() {
        val c = com.noirbooks.app.data.ReadAloudService.Control
        c.title = book?.title ?: epub.title
        c.playing = ttsPlaying
        c.toggle = { main.post { if (ttsPlaying) ttsPause() else ttsResume() } }
        c.stop = { main.post { ttsStop() } }
        com.noirbooks.app.data.ReadAloudService.update(app)
    }

    private fun ttsAdvance(id: String?) {
        if (!ttsActive || !ttsPlaying || id != "s$sIndex") return
        if (ttsSleepAt in 1..System.currentTimeMillis()) { ttsSleepAt = 0L; ttsPause(); return }
        sIndex++
        if (sIndex < sentences.size) ttsSpeak() else ttsChapterEnd()
    }

    private fun ttsChapterEnd() {
        if (chapter < epub.spine.lastIndex) {
            ttsNextChapter = true
            closePage(turned = true)
            loadChapter(chapter + 1, START)
        } else {
            ttsStop()
            endReached = true
        }
    }

    fun ttsPause() {
        ttsPlaying = false
        tts?.stop()
        if (ttsActive) ttsNotify()
    }

    fun ttsResume() {
        if (!ttsActive) return ttsStart()
        if (sentences.isEmpty()) ttsFetch(fromHere = true) else ttsSpeak()
    }

    fun ttsStop() {
        ttsActive = false
        ttsPlaying = false
        ttsNextChapter = false
        ttsSleepAt = 0L
        tts?.stop()
        js("window.noir && noir.ttsClear()")
        com.noirbooks.app.data.ReadAloudService.Control.toggle = null
        com.noirbooks.app.data.ReadAloudService.Control.stop = null
        com.noirbooks.app.data.ReadAloudService.stop(app)
    }

    // ---- Elegir la voz ----

    /** Idioma y voz que se están usando ahora. */
    var ttsLangTag by mutableStateOf("")
        private set
    var ttsVoiceName by mutableStateOf("")
        private set

    /** El idioma del libro; "es" a secas = español de España. */
    private fun bookLangTag(): String = epub.language.trim().let {
        if (it.isBlank() || it.equals("es", true) || it.equals("spa", true)) "es-ES" else it
    }

    /** La voz que toca: la propia del libro, si no la general (si el libro es de ese idioma), si no la del idioma del libro. */
    private fun resolveVoice(): Pair<String, String> {
        Prefs.ttsLangFor(bookId)?.let { return it to Prefs.ttsVoiceFor(bookId) }
        val g = Prefs.ttsLang
        val b = bookLangTag()
        return if (java.util.Locale.forLanguageTag(b).language == java.util.Locale.forLanguageTag(g).language) g to Prefs.ttsVoice
        else b to ""
    }

    private fun ttsApply(tag: String, voice: String) {
        val t = tts ?: return
        val ok = t.setLanguage(java.util.Locale.forLanguageTag(tag))
        if (ok < 0) t.setLanguage(java.util.Locale("es", "ES"))
        if (voice.isNotBlank()) runCatching { t.voices?.firstOrNull { it.name == voice }?.let { t.voice = it } }
    }

    /** Vuelve a poner la voz elegida (tras escuchar una de prueba). */
    fun ttsRestore() {
        val (tag, voice) = resolveVoice()
        ttsApply(tag, voice)
        ttsLangTag = tag
        ttsVoiceName = voice
    }

    /** Prepara el motor de voz sin empezar a leer (para la ventana de elegir voz). */
    fun ttsPrepare(then: () -> Unit) = ttsEnsure(then)

    private fun installed(v: android.speech.tts.Voice) =
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in (v.features ?: emptySet<String>())

    /** Idiomas con alguna voz instalada en el móvil. */
    fun ttsLanguages(): List<String> = runCatching {
        tts?.voices.orEmpty().filter { installed(it) }.map { it.locale.toLanguageTag() }.distinct()
    }.getOrDefault(emptyList())

    /** Voces instaladas de un idioma (primero las que no necesitan conexión). */
    fun ttsVoicesFor(tag: String): List<android.speech.tts.Voice> = runCatching {
        tts?.voices.orEmpty().filter { installed(it) && it.locale.toLanguageTag().equals(tag, true) }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    }.getOrDefault(emptyList())

    fun ttsHasBookVoice() = Prefs.ttsLangFor(bookId) != null

    /** Escuchar una voz antes de elegirla (pausa la lectura si estaba sonando). */
    fun ttsPreview(tag: String, voice: String) {
        if (ttsPlaying) ttsPause()
        ttsEnsure {
            ttsApply(tag, voice)
            tts?.setSpeechRate(ttsRate)
            val sample = when (java.util.Locale.forLanguageTag(tag).language) {
                "en" -> "Hello. This is how this book will sound."
                "ca" -> "Hola. Així sonarà la lectura d'aquest llibre."
                "fr" -> "Bonjour. Voici comment ce livre sera lu."
                else -> "Hola. Así sonará la lectura de este libro."
            }
            tts?.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
    }

    /** Guarda la voz elegida, para este libro o para todos. */
    fun ttsChoose(tag: String, voice: String, onlyThisBook: Boolean) {
        if (onlyThisBook) Prefs.setTtsFor(bookId, tag, voice)
        else { Prefs.ttsLang = tag; Prefs.ttsVoice = voice; Prefs.setTtsFor(bookId, null, null) }
        tts?.stop()
        ttsRestore()
        if (ttsPlaying) ttsSpeak()
    }

    fun ttsSetRate(r: Float) {
        ttsRate = r
        Prefs.ttsRate = r
        if (ttsPlaying) ttsSpeak()
    }

    fun ttsShutdown() {
        ttsStop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    fun release(view: WebView) {
        if (web === view) web = null
        runCatching {
            view.stopLoading()
            view.removeJavascriptInterface("Android")
            view.destroy()
        }
    }

    private fun highlightsJson(ch: Int): JSONArray {
        val arr = JSONArray()
        book?.highlights?.filter { it.chapter == ch }?.forEach { h ->
            runCatching { arr.put(JSONObject().put("id", h.id).put("color", h.color).put("range", JSONObject(h.range))) }
        }
        return arr
    }

    /** Última página impresa que empieza antes de este capítulo (o al principio de él, si la marca no lleva ancla). */
    private fun printedBefore(ch: Int): String {
        marksBySpine[ch]?.firstOrNull { it.anchor.isNullOrEmpty() }?.let { return it.label }
        for (i in ch - 1 downTo 0) marksBySpine[i]?.lastOrNull()?.let { return it.label }
        return ""
    }

    private fun prepareInit() {
        initJson = JSONObject()
            .put("settings", Prefs.effectiveReader().toJson())
            .put("target", target)
            .put("lang", epub.language.ifBlank { "es" })
            .put("highlights", highlightsJson(chapter))
            .put("pageMarks", JSONArray().also { arr ->
                marksBySpine[chapter].orEmpty().forEach { m -> if (!m.anchor.isNullOrEmpty()) arr.put(JSONObject().put("id", m.anchor).put("label", m.label)) }
            })
            .put("pagePrev", printedBefore(chapter))
            .toString()
        if (epub.pageList.isNotEmpty()) printed = printedBefore(chapter).ifEmpty { marksBySpine[chapter]?.firstOrNull()?.label.orEmpty() }
    }

    private fun loadChapter(ch: Int, t: JSONObject) {
        val c = ch.coerceIn(0, epub.spine.lastIndex)
        chapter = c
        target = t
        ready = false
        selection = null
        prepareInit()
        web?.loadUrl(server.urlOf(epub.spine[c]))
    }

    /** Guarda el sitio actual para poder volver tras un salto. */
    private fun remember() {
        if (!ready) return
        history = (history + Spot(chapter, anchor, fraction)).takeLast(20)
    }

    fun goBack() {
        val s = history.lastOrNull() ?: return
        history = history.dropLast(1)
        loadChapter(s.chapter, posTarget(s.anchor, s.fraction))
    }

    fun goTo(ch: Int, fraction: Float) { remember(); loadChapter(ch, fractionTarget(fraction)) }

    fun goToHighlight(h: Highlight) {
        remember()
        loadChapter(h.chapter, runCatching { JSONObject().put("type", "range").put("value", JSONObject(h.range)) }
            .getOrElse { fractionTarget(h.fraction) })
    }

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
        remember()
        loadChapter(ch, if (e.anchor != null) JSONObject().put("type", "anchor").put("value", e.anchor) else START)
    }

    fun goToSearch(hit: SearchHit, query: String) {
        remember()
        loadChapter(hit.chapter, JSONObject().put("type", "search").put("value", query).put("n", hit.n))
    }

    fun next() = js("window.noir && noir.next()")
    fun prev() = js("window.noir && noir.prev()")

    fun nextChapter() { if (chapter < epub.spine.lastIndex) { remember(); loadChapter(chapter + 1, START) } }
    fun prevChapter() { if (chapter > 0) { remember(); loadChapter(chapter - 1, START) } }

    /** Ajustes de lectura cambiados: se vuelve a maquetar conservando la posición exacta. */
    fun applySettings() {
        target = posTarget(anchor, fraction)
        prepareInit()
        val eff = Prefs.effectiveReader()
        web?.setBackgroundColor(eff.palette().bg.toInt())
        js("window.noir && noir.apply(${JSONObject.quote(eff.toJson().toString())})")
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

    fun clearSelection() {
        selection = null
        js("window.noir && noir.clearSelection()")
    }

    private fun repaintHighlights() {
        js("window.noir && noir.setHighlights(${JSONObject.quote(highlightsJson(chapter).toString())})")
    }

    private fun openLink(url: String) {
        val (path, frag) = server.pathOf(url) ?: return
        val ch = epub.spine.indexOf(path)
        if (ch < 0) return
        remember()
        loadChapter(ch, if (frag.isNullOrEmpty()) START else JSONObject().put("type", "anchor").put("value", frag))
    }

    private fun openExternal(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("mailto:")) return
        runCatching {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---- Subrayados ----

    fun addHighlight(color: Int, note: String = "") {
        val s = selection ?: return
        val h = Highlight(
            id = System.currentTimeMillis(), chapter = chapter, fraction = s.fraction, range = s.range,
            text = s.text, color = color, note = note, chapterTitle = chapterTitle(),
        )
        clearSelection()
        scope.launch {
            Library.update(bookId) { b -> b.copy(highlights = (b.highlights + h).sortedWith(compareBy({ it.chapter }, { it.fraction }))) }
            repaintHighlights()
        }
    }

    fun updateHighlight(h: Highlight) {
        scope.launch {
            Library.update(bookId) { b -> b.copy(highlights = b.highlights.map { if (it.id == h.id) h else it }) }
            repaintHighlights()
        }
    }

    fun deleteHighlight(h: Highlight) {
        scope.launch {
            Library.update(bookId) { b -> b.copy(highlights = b.highlights.filterNot { it.id == h.id }) }
            repaintHighlights()
        }
    }

    // ---- Búsqueda en el libro ----

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = fold(query.trim())
        if (q.length < 2) return@withContext emptyList()
        val out = ArrayList<SearchHit>()
        for (i in epub.spine.indices) {
            val text = server.chapterText(epub.spine[i])
            val folded = fold(text)
            var from = 0
            var n = 0
            while (out.size < 300) {
                val at = folded.indexOf(q, from)
                if (at < 0) break
                val a = (at - 50).coerceAtLeast(0)
                val b = (at + q.length + 60).coerceAtMost(text.length)
                val snip = (if (a > 0) "…" else "") + text.substring(a, b).trim() + (if (b < text.length) "…" else "")
                out += SearchHit(i, n, snip, chapterTitle(i))
                n++
                from = at + 1
            }
        }
        out
    }

    private fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val f = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            sb.append(f.lowercaseChar())
        }
        return sb.toString()
    }

    // ---- Progreso ----

    private var saveJob: Job? = null

    // Dónde estaba el libro al abrirlo, por si al salir no se quiere guardar la posición.
    private val openChapter = startChapter
    private val openFraction = startFraction
    private val openAnchor = startAnchor
    val openProgress: Float get() = book?.progressAt(openChapter, openFraction) ?: startProgress

    /** ¿Te has movido de donde abriste el libro? */
    fun movedSinceOpen(): Boolean = chapter != openChapter || kotlin.math.abs(fraction - openFraction) > 0.0005f

    /** No guardar: el libro vuelve a donde estaba al abrirlo (el tiempo leído sí cuenta). */
    suspend fun restoreOpenPosition() {
        saveJob?.cancel()
        Library.update(bookId) { it.copy(chapter = openChapter, fraction = openFraction, anchor = openAnchor) }
    }

    private fun savePosition() {
        val ch = chapter
        val f = fraction
        val a = anchor
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            Library.update(bookId) { it.copy(chapter = ch, fraction = f, anchor = a) }
        }
    }

    private fun saveNow() {
        val ch = chapter
        val f = fraction
        val a = anchor
        saveJob?.cancel()
        scope.launch { Library.update(bookId) { it.copy(chapter = ch, fraction = f, anchor = a) } }
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

    /** Métodos que llama el JavaScript del lector (en un hilo aparte: se pasa todo al hilo principal). */
    private inner class Bridge {
        @JavascriptInterface
        fun init(): String = initJson

        @JavascriptInterface
        fun onReady(p: Int, c: Int, a: String?) = main.post {
            count = c.coerceAtLeast(1); page = p.coerceIn(0, count - 1); ready = true
            if (!a.isNullOrBlank()) anchor = a
            pageSince = System.currentTimeMillis()
            savePosition()
            if (ttsNextChapter && ttsActive) { ttsNextChapter = false; ttsFetch(fromHere = false) }
        }

        @JavascriptInterface
        fun onPage(p: Int, c: Int, a: String?) = main.post {
            val turned = p != page
            if (turned) closePage(turned = true)
            count = c.coerceAtLeast(1); page = p.coerceIn(0, count - 1)
            if (!a.isNullOrBlank()) anchor = a
            selection = null
            savePosition()
        }

        @JavascriptInterface
        fun onPrinted(label: String) = main.post { if (label.isNotBlank()) printed = label }

        @JavascriptInterface
        fun onTap(x: Double, y: Double) = main.post { this@ReaderController.onTap(x.toFloat(), y.toFloat()) }

        @JavascriptInterface
        fun onEnd() = main.post {
            if (chapter < epub.spine.lastIndex) {
                closePage(turned = true)
                loadChapter(chapter + 1, START)
            } else endReached = true
        }

        @JavascriptInterface
        fun onStart() = main.post {
            if (chapter > 0) loadChapter(chapter - 1, END)
        }

        @JavascriptInterface
        fun onLink(url: String) = main.post { openLink(url) }

        @JavascriptInterface
        fun onExternalLink(url: String) = main.post { openExternal(url) }

        @JavascriptInterface
        fun onSelection(json: String?) = main.post {
            selection = if (json.isNullOrBlank()) null else runCatching {
                val o = JSONObject(json)
                TextSelection(
                    range = o.getJSONObject("range").toString(),
                    text = o.optString("text"),
                    sentence = o.optString("sentence"),
                    fraction = o.optDouble("fraction", 0.0).toFloat(),
                    top = o.optDouble("top", 0.0).toFloat(),
                    bottom = o.optDouble("bottom", 0.0).toFloat(),
                )
            }.getOrNull()
        }

        @JavascriptInterface
        fun onHighlightTap(id: String?) = main.post {
            val n = id?.toLongOrNull() ?: return@post
            tappedHighlight = book?.highlights?.firstOrNull { it.id == n }
        }
    }

    companion object {
        private val START get() = JSONObject().put("type", "start")
        private val END get() = JSONObject().put("type", "end")
        fun fractionTarget(f: Float): JSONObject = JSONObject().put("type", "fraction").put("value", f.toDouble())
        fun posTarget(anchor: String, f: Float): JSONObject =
            if (anchor.isBlank()) fractionTarget(f)
            else JSONObject().put("type", "pos").put("value", anchor).put("fraction", f.toDouble())
    }
}
