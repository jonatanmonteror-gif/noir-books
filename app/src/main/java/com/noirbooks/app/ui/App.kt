package com.noirbooks.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.navigationBarsPadding
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Updater
import kotlinx.coroutines.channels.Channel
import java.io.File

sealed class Screen {
    data object Library : Screen()
    /** [from]: pantalla a la que volver al cerrar el libro (la biblioteca si no se indica). */
    data class Reader(val bookId: String, val from: Screen? = null) : Screen()
    /** Ficha del libro a pantalla completa. */
    data class Book(val bookId: String) : Screen()
    data object Settings : Screen()
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Downloading(val name: String, val progress: Float) : UpdateState()
    data class Ready(val rel: Updater.Release, val file: File) : UpdateState()
    data class UpToDate(val at: Long) : UpdateState()
    data class Failed(val reason: String) : UpdateState()
}

data class LogRequest(val bookId: String, val start: Long = 0L, val end: Long = 0L, val onlyProgress: Boolean = false)

object AppState {
    val screen = mutableStateOf<Screen>(Screen.Library)
    /** Pestaña de la pantalla principal: 0 Biblioteca, 1 Calendario, 2 Citas, 3 Estadísticas. */
    val tab = mutableIntStateOf(0)
    /** Al abrir un libro desde una cita: ir directamente a esa cita. */
    val jumpTo = mutableStateOf<Pair<String, com.noirbooks.app.data.Highlight>?>(null)
    val resumeCount = mutableIntStateOf(0)
    /** Ficha del libro abierta (pulsación larga o libro que no se lee en la app). */
    val bookMenu = mutableStateOf<String?>(null)
    val addBook = mutableStateOf(false)
    /** Libro recién añadido al que preguntar cuándo se va a leer. */
    val askStatus = mutableStateOf<String?>(null)
    /** Cronómetro de lectura fuera de la app: libro y hora de inicio (0 = parado). */
    val timer = mutableStateOf<Pair<String, Long>?>(null)
    /** Lectura que se está apuntando: libro y, si viene del cronómetro, inicio y fin. */
    val logSession = mutableStateOf<LogRequest?>(null)

    /** La lectura sin guardar de una sesión anterior solo se pregunta una vez al arrancar la app. */
    var pendingChecked = false

    fun openBook(b: com.noirbooks.app.data.Book, from: Screen? = null) {
        when {
            !b.isEpub -> bookMenu.value = b.id
            com.noirbooks.app.data.Library.fileMissing(b) ->
                toast("Falta el archivo de este libro. Impórtalo otra vez (el mismo EPUB) y recuperas todo")
            else -> screen.value = Screen.Reader(b.id, from)
        }
    }

    fun startTimer(bookId: String) {
        val now = System.currentTimeMillis()
        com.noirbooks.app.data.Prefs.timerBook = bookId
        com.noirbooks.app.data.Prefs.timerStart = now
        timer.value = bookId to now
        com.noirbooks.app.data.TimerNotification.show(bookId, now)
    }

    fun stopTimer() {
        // Si la app se cerró, el cronómetro sigue guardado en preferencias.
        val t = timer.value ?: com.noirbooks.app.data.Prefs.timerStart.takeIf { it > 0L }
            ?.let { com.noirbooks.app.data.Prefs.timerBook to it } ?: return
        com.noirbooks.app.data.Prefs.timerStart = 0L
        timer.value = null
        com.noirbooks.app.data.TimerNotification.cancel()
        logSession.value = LogRequest(t.first, t.second, System.currentTimeMillis())
    }

    fun discardTimer() {
        com.noirbooks.app.data.Prefs.timerStart = 0L
        timer.value = null
        com.noirbooks.app.data.TimerNotification.cancel()
    }
    val importing = mutableIntStateOf(0)
    val update = mutableStateOf<UpdateState>(UpdateState.Idle)
    /** El aviso de "Nueva versión lista" solo se muestra una vez por versión. */
    val updateDismissed = mutableIntStateOf(0)
    val messages = Channel<String>(Channel.BUFFERED)

    fun toast(msg: String) {
        messages.trySend(msg)
    }

    suspend fun importAll(ctx: Context, uris: List<Uri>, openIfSingle: Boolean) {
        importing.intValue += uris.size
        var added = 0
        var last: String? = null
        val errors = mutableListOf<String>()
        uris.forEach { uri ->
            when (val r = Library.import(ctx, uri)) {
                is Library.ImportResult.Added -> { added++; last = r.book.id }
                is Library.ImportResult.Existing -> { last = r.book.id; if (uris.size == 1) toast("Ya estaba en tu biblioteca") }
                is Library.ImportResult.Failed -> errors += "${r.name}: ${r.reason}"
            }
            importing.intValue--
        }
        when {
            errors.size == 1 && uris.size == 1 -> toast(errors.first().substringAfter(": "))
            errors.isNotEmpty() -> toast("No se pudieron añadir ${errors.size} de ${uris.size} archivos")
            added > 1 -> toast("$added libros añadidos")
        }
        if (added == 1 && !openIfSingle) last?.let { askStatus.value = it }
        if (openIfSingle && uris.size == 1) last?.let { screen.value = Screen.Reader(it) }
    }

    /** Busca una versión nueva y la descarga en segundo plano; deja el aviso listo para instalar. */
    suspend fun checkUpdate(ctx: Context, manual: Boolean) {
        val st = update.value
        if (st is UpdateState.Checking || st is UpdateState.Downloading || st is UpdateState.Ready) return
        if (!manual && System.currentTimeMillis() - Prefs.lastUpdateCheck < 10 * 60_000L) return
        update.value = UpdateState.Checking
        Prefs.lastUpdateCheck = System.currentTimeMillis()
        val rel = Updater.latest()
        if (rel == null) {
            update.value = if (manual) UpdateState.Failed("No se pudo consultar. Revisa la conexión.") else UpdateState.Idle
            return
        }
        if (rel.versionCode <= Updater.currentCode) {
            update.value = UpdateState.UpToDate(System.currentTimeMillis())
            return
        }
        // La descarga la hace el navegador (sin permiso de instalar apps en Noir Books)
        update.value = UpdateState.Ready(rel, File(""))
    }
}

@Composable
fun App() {
    NoirTheme {
        val ctx = LocalContext.current
        val screen by AppState.screen
        val snack = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            val ts = com.noirbooks.app.data.Prefs.timerStart
            val tb = com.noirbooks.app.data.Prefs.timerBook
            if (ts > 0L && com.noirbooks.app.data.Library.get(tb) != null) {
                AppState.timer.value = tb to ts
                com.noirbooks.app.data.TimerNotification.show(tb, ts)
            }
            for (m in AppState.messages) snack.showSnackbar(m)
        }
        val resume by AppState.resumeCount
        LaunchedEffect(resume) {
            if (resume > 0) AppState.checkUpdate(ctx, manual = false)
        }

        Box(Modifier.fillMaxSize().background(NB.bg)) {
            when (val s = screen) {
                Screen.Library -> MainScreen()
                Screen.Settings -> {
                    BackHandler { AppState.screen.value = Screen.Library }
                    SettingsScreen(onBack = { AppState.screen.value = Screen.Library })
                }
                is Screen.Book -> {
                    BackHandler { AppState.screen.value = Screen.Library }
                    BookScreen(s.bookId, onBack = { AppState.screen.value = Screen.Library })
                }
                is Screen.Reader -> {
                    ReaderScreen(s.bookId, onClose = { AppState.screen.value = s.from ?: Screen.Library })
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            if (screen !is Screen.Reader) {
                AppState.bookMenu.value?.let { id -> BookMenu(id, onDismiss = { AppState.bookMenu.value = null }) }
                if (AppState.addBook.value) AddBookScreen(onClose = { AppState.addBook.value = false })
                AppState.askStatus.value?.let { id -> WhenToReadDialog(id, onDismiss = { AppState.askStatus.value = null }) }
            }
            AppState.logSession.value?.let { r -> LogSessionDialog(r, onDismiss = { AppState.logSession.value = null }) }
            UpdatePrompt()
            CrashReportPrompt()
            if (screen !is Screen.Reader) WhatsNewPrompt()
            if (screen !is Screen.Reader) PendingReadingPrompt()
        }
    }
}
