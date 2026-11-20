package com.noirbooks.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.noirbooks.app.data.Prefs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noirbooks.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val InterDisplay = FontFamily(Font(R.font.inter_display_semi_bold, FontWeight.SemiBold))
val Playfair = FontFamily(
    Font(R.font.playfair_regular, FontWeight.Normal),
    Font(R.font.playfair_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Colores de la app. Cambian solos con la apariencia elegida (oscura o clara). */
object NB {
    /** true = apariencia clara. La fija [NoirTheme]. */
    var light by mutableStateOf(false)
    val bg: Color get() = if (light) Color(0xFFF6F5F2) else Color(0xFF000000)
    val surface: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF111113)
    val surface2: Color get() = if (light) Color(0xFFECEBE7) else Color(0xFF1C1C1F)
    val line: Color get() = if (light) Color(0xFFD8D6D1) else Color(0xFF2A2A2E)
    val text: Color get() = if (light) Color(0xFF151515) else Color(0xFFF2F2F2)
    val text2: Color get() = if (light) Color(0xFF55555A) else Color(0xFFA1A1A6)
    val text3: Color get() = if (light) Color(0xFF8A8A8F) else Color(0xFF6C6C70)
    val silver: Color get() = if (light) Color(0xFF6A6A70) else Color(0xFFB0B0B6)
    /** Texto sobre un fondo [text] (botones principales, chips elegidos). */
    val onText: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF000000)
}

@Composable
fun NoirTheme(content: @Composable () -> Unit) {
    val mode by Prefs.appearanceState
    val systemDark = isSystemInDarkTheme()
    NB.light = when (mode) { "light" -> true; "dark" -> false; else -> !systemDark }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = NB.light
            isAppearanceLightNavigationBars = NB.light
        }
    }
    val base0 = if (NB.light) lightColorScheme() else darkColorScheme()
    val scheme = base0.copy(
        primary = NB.text,
        onPrimary = NB.onText,
        secondary = NB.silver,
        background = NB.bg,
        onBackground = NB.text,
        surface = NB.surface,
        onSurface = NB.text,
        surfaceVariant = NB.surface2,
        onSurfaceVariant = NB.text2,
        surfaceContainer = NB.surface,
        surfaceContainerHigh = NB.surface2,
        surfaceContainerLow = NB.surface,
        outline = NB.line,
        outlineVariant = NB.line,
    )
    val base = TextStyle(fontFamily = Inter)
    val t = MaterialTheme.typography
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            displaySmall = t.displaySmall.merge(base).copy(fontFamily = InterDisplay),
            headlineSmall = t.headlineSmall.merge(base).copy(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold),
            titleLarge = t.titleLarge.merge(base).copy(fontWeight = FontWeight.SemiBold),
            titleMedium = t.titleMedium.merge(base).copy(fontWeight = FontWeight.SemiBold),
            titleSmall = t.titleSmall.merge(base).copy(fontWeight = FontWeight.Medium),
            bodyLarge = t.bodyLarge.merge(base),
            bodyMedium = t.bodyMedium.merge(base),
            bodySmall = t.bodySmall.merge(base),
            labelLarge = t.labelLarge.merge(base).copy(fontWeight = FontWeight.Medium),
            labelMedium = t.labelMedium.merge(base),
            labelSmall = t.labelSmall.merge(base).copy(fontSize = 11.sp),
        ),
        content = content,
    )
}
