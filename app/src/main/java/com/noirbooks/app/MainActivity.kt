package com.noirbooks.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.ui.App
import com.noirbooks.app.ui.AppState
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLog.init(this)
        Prefs.init(this)
        Library.init(this)
        com.noirbooks.app.data.Stats.init(this)
        com.noirbooks.app.data.TimerNotification.init(this)
        enableEdgeToEdge()
        setContent { App() }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        com.noirbooks.app.ui.AppLock.onStop()
        com.noirbooks.app.data.QuoteWidget.refresh(this)
        com.noirbooks.app.data.Stats.flush()
        com.noirbooks.app.data.DriveBackup.autoBackup(this)
    }

    override fun onStart() {
        super.onStart()
        com.noirbooks.app.ui.AppLock.onStart()
        AppState.resumeCount.intValue++
    }

    /** "Abrir con" o "Compartir" un .epub desde otra app: se importa y, si es uno solo, se abre. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // Desde el widget: compartir la cita del día con el diseño para redes
        if (intent.getBooleanExtra(com.noirbooks.app.data.QuoteWidget.EXTRA_SHARE, false)) {
            AppState.screen.value = com.noirbooks.app.ui.Screen.Library
            AppState.tab.intValue = 2
            com.noirbooks.app.data.QuoteWidget.daily()?.let { (b, h) ->
                AppState.shareQuote.value = com.noirbooks.app.ui.QuoteShare(h.text, b.title, b.author, b.id, h.color)
            }
            return
        }
        // Desde el widget de la cita del día: abrir Citas
        if (intent.getBooleanExtra(com.noirbooks.app.data.QuoteWidget.EXTRA_QUOTES, false)) {
            AppState.screen.value = com.noirbooks.app.ui.Screen.Library
            AppState.tab.intValue = 2
            return
        }
        if (intent.action == com.noirbooks.app.data.TimerNotification.ACTION_STOP) {
            AppState.screen.value = com.noirbooks.app.ui.Screen.Library
            AppState.stopTimer()
            return
        }
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uris += it }
            Intent.ACTION_SEND -> stream(intent)?.let { uris += it }
            Intent.ACTION_SEND_MULTIPLE -> uris += streams(intent)
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch { AppState.importAll(this@MainActivity, uris, openIfSingle = true) }
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = keyHandler
        if (handler != null && handler(event)) return true
        return super.dispatchKeyEvent(event)
    }

    companion object {
        /** El lector lo usa para pasar página con los botones de volumen o un teclado. */
        var keyHandler: ((KeyEvent) -> Boolean)? = null
    }
}
