package com.noirbooks.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.noirbooks.app.data.Prefs

// ---------------- Bloqueo con huella ----------------

/**
 * Bloqueo de la app con la huella (o el PIN / patrón del móvil). Se pide al abrir la app y al volver
 * después de más de un minuto fuera. Mientras está bloqueada no se pinta nada más.
 */
object AppLock {
    val locked = mutableStateOf(false)
    private var leftAt = 0L
    private var firstStart = true

    fun onStart() {
        if (!Prefs.appLock) { locked.value = false; firstStart = false; return }
        if (firstStart || System.currentTimeMillis() - leftAt > 60_000L) locked.value = true
        firstStart = false
    }

    fun onStop() { leftAt = System.currentTimeMillis() }

    private fun authenticators() =
        if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** null si se puede; si no, el motivo para enseñarlo. */
    fun unavailableReason(ctx: Context): String? = when (BiometricManager.from(ctx).canAuthenticate(authenticators())) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Primero añade una huella o un bloqueo de pantalla en los ajustes del móvil"
        else -> "Este móvil no permite bloquear la app con huella"
    }

    fun prompt(ctx: Context, title: String, onOk: () -> Unit, onFail: (String) -> Unit = {}) {
        val act = ctx.findActivity() ?: return onFail("No se pudo abrir la huella")
        val prompt = BiometricPrompt(act, androidx.core.content.ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onFail(msg.toString())
            })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("Noir Books")
            .setAllowedAuthenticators(authenticators())
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancelar") }
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onFail(it.message ?: "No se pudo abrir la huella") }
    }
}

private fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
    return null
}

@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    fun ask() = AppLock.prompt(ctx, "Desbloquear", onOk = { AppLock.locked.value = false })
    LaunchedEffect(Unit) { ask() }
    Column(
        Modifier.fillMaxSize().background(NB.bg).statusBarsPadding().navigationBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Noir", fontFamily = Playfair, fontSize = 44.sp, color = NB.text)
        Text("Books", fontSize = 13.sp, letterSpacing = 4.sp, color = NB.text3)
        Spacer(Modifier.height(40.dp))
        PillButton("Desbloquear", primary = true) { ask() }
    }
}

/** Ajustes › Privacidad. Para activarlo hay que pasar la huella una vez (así no te quedas fuera). */
@Composable
fun LockSettings() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.appLock) }
    Text("Pide tu huella (o el PIN del móvil) al abrir la app y al volver a ella después de un minuto.",
        color = NB.text2, fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Bloquear con huella", on) {
            if (on) return@Chip
            val why = AppLock.unavailableReason(ctx)
            if (why != null) { AppState.toast(why); return@Chip }
            AppLock.prompt(ctx, "Activar el bloqueo", onOk = { on = true; Prefs.appLock = true; AppState.toast("Bloqueo activado") })
        }
        Chip("Sin bloqueo", !on) {
            if (!on) return@Chip
            AppLock.prompt(ctx, "Quitar el bloqueo", onOk = { on = false; Prefs.appLock = false; AppState.toast("Bloqueo quitado") })
        }
    }
}

// ---------------- Elegir qué se ve en Inicio ----------------

@Composable
fun HomeSectionsSettings() {
    val order by Prefs.homeOrder
    val hidden by Prefs.homeHidden
    Text("Elige qué secciones salen en Inicio y en qué orden.", color = NB.text2, fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    order.forEachIndexed { i, key ->
        val label = Prefs.HOME_SECTIONS.firstOrNull { it.first == key }?.second ?: key
        val shown = key !in hidden
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (shown) NB.text else NB.surface)
                    .border(1.dp, if (shown) NB.text else NB.line, RoundedCornerShape(6.dp))
                    .clickable { Prefs.setHome(order, if (shown) hidden + key else hidden - key) },
                contentAlignment = Alignment.Center,
            ) { if (shown) Text("✓", color = NB.onText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text(label, color = if (shown) NB.text else NB.text3, fontSize = 15.sp, modifier = Modifier.weight(1f))
            ArrowButton("▲", i > 0) {
                val m = order.toMutableList(); m.add(i - 1, m.removeAt(i)); Prefs.setHome(m, hidden)
            }
            Spacer(Modifier.width(6.dp))
            ArrowButton("▼", i < order.lastIndex) {
                val m = order.toMutableList(); m.add(i + 1, m.removeAt(i)); Prefs.setHome(m, hidden)
            }
        }
    }
}

@Composable
private fun ArrowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.dp, NB.line, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) NB.text else NB.text3.copy(alpha = 0.4f), fontSize = 12.sp) }
}

// ---------------- Asistente al instalar ----------------

/**
 * Bienvenida la primera vez: apariencia, letra de lectura, copia de seguridad y traer tus libros.
 * Todo se puede saltar y cambiar luego en Ajustes.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = 5
    val appearance by Prefs.appearanceState
    fun finish(then: () -> Unit = {}) { Prefs.onboarded = true; onDone(); then() }

    Column(Modifier.fillMaxSize().background(NB.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps) { i ->
                    Box(Modifier.height(4.dp).width(if (i == step) 22.dp else 10.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) NB.text else NB.line))
                }
            }
            if (step < steps - 1) Text("Saltar", color = NB.text2, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { finish() }.padding(8.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                when (step) {
                    0 -> {
                        Spacer(Modifier.height(60.dp))
                        Text("Noir", fontFamily = Playfair, fontSize = 56.sp, color = NB.text)
                        Text("BOOKS", fontSize = 13.sp, letterSpacing = 5.sp, color = NB.text3)
                        Spacer(Modifier.height(28.dp))
                        Text("Tu lector de EPUB y PDF y tu diario de lectura, en uno.", color = NB.text, fontSize = 20.sp, lineHeight = 27.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("Lee, subraya, apunta lo que lees en papel, y mira tus rachas y estadísticas. " +
                            "Vamos a dejarlo a tu gusto en un minuto.", color = NB.text2, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    1 -> {
                        StepTitle("Apariencia", "Los colores de la app. Los libros tienen sus propios temas.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("dark" to "Oscura", "light" to "Clara", "system" to "Como el móvil").forEach { (k, l) ->
                                Chip(l, appearance == k) { Prefs.setAppearance(k) }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        val uiScale by Prefs.uiScale
                        Text("Tamaño de la app", color = NB.text2, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.9f to "Pequeño", 0.96f to "Medio", 1f to "Normal").forEach { (v, l) ->
                                Chip(l, kotlin.math.abs(uiScale - v) < 0.01f) { Prefs.setUiScale(v) }
                            }
                        }
                    }
                    2 -> {
                        StepTitle("Letra para leer", "Tema, tipo y tamaño de letra. Lo verás en la muestra.")
                        ReadingControls(showPreview = true)
                    }
                    3 -> {
                        StepTitle("Copia de seguridad", "Tus libros, lecturas y citas viven en este móvil.")
                        Text("Para no perderlos si cambias de móvil, puedes guardar una copia en tu Google Drive " +
                            "o en un archivo. Se configura en Ajustes › Copia de seguridad.", color = NB.text2, fontSize = 15.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(16.dp))
                        PillButton("Ir a Copia de seguridad") { finish { AppState.screen.value = Screen.Settings } }
                    }
                    else -> {
                        StepTitle("Trae tus libros", "Añade un EPUB o PDF, o un libro de papel buscándolo.")
                        Text("¿Vienes de otra app? Puedes importar tu biblioteca de Goodreads o de Bookmory, " +
                            "y tus subrayados de Kindle.", color = NB.text2, fontSize = 15.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(18.dp))
                        PillButton("Añadir o importar libros", primary = true) { finish { AppState.addBook.value = true } }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) Text("Atrás", color = NB.text2, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { step-- }.padding(10.dp))
            Spacer(Modifier.weight(1f))
            PillButton(when (step) { 0 -> "Empezar"; steps - 1 -> "Ir a mi biblioteca"; else -> "Siguiente" }, primary = true) {
                if (step < steps - 1) step++ else finish()
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, fontFamily = Playfair, fontSize = 30.sp, color = NB.text)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, color = NB.text2, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Start)
    Spacer(Modifier.height(20.dp))
}
