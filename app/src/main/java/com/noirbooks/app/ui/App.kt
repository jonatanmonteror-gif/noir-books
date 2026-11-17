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
    data class Reader(val bookId: String) : Screen()
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

object AppState {
    val screen = mutableStateOf<Screen>(Screen.Library)
    val resumeCount = mutableIntStateOf(0)
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
            added == 1 && !openIfSingle -> toast("Libro añadido")
        }
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
        try {
            update.value = UpdateState.Downloading(rel.name, 0f)
            val file = Updater.download(ctx, rel) { p -> update.value = UpdateState.Downloading(rel.name, p) }
            update.value = UpdateState.Ready(rel, file)
        } catch (e: Exception) {
            ErrorLog.note("Descargar actualización", e)
            update.value = UpdateState.Failed(e.message ?: "Error al descargar")
        }
    }
}

@Composable
fun App() {
    NoirTheme {
        val ctx = LocalContext.current
        val screen by AppState.screen
        val snack = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            for (m in AppState.messages) snack.showSnackbar(m)
        }
        val resume by AppState.resumeCount
        LaunchedEffect(resume) {
            if (resume > 0) AppState.checkUpdate(ctx, manual = false)
        }

        Box(Modifier.fillMaxSize().background(NB.bg)) {
            when (val s = screen) {
                Screen.Library -> LibraryScreen()
                Screen.Settings -> {
                    BackHandler { AppState.screen.value = Screen.Library }
                    SettingsScreen(onBack = { AppState.screen.value = Screen.Library })
                }
                is Screen.Reader -> {
                    BackHandler { AppState.screen.value = Screen.Library }
                    ReaderScreen(s.bookId, onClose = { AppState.screen.value = Screen.Library })
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            UpdatePrompt()
            CrashReportPrompt()
        }
    }
}
