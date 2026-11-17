package com.noirbooks.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

enum class ReaderTheme(val key: String, val label: String, val bg: Long, val fg: Long, val muted: Long, val dark: Boolean) {
    Light("light", "Claro", 0xFFFFFFFF, 0xFF1A1A1A, 0xFF8A8A8A, false),
    Sepia("sepia", "Sepia", 0xFFF4ECD8, 0xFF3B2F22, 0xFF9A8B74, false),
    Dark("dark", "Oscuro", 0xFF1C1C1E, 0xFFD6D6D6, 0xFF7C7C80, true),
    Black("black", "Negro", 0xFF000000, 0xFFC8C8C8, 0xFF6E6E72, true);

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k } ?: Dark
    }
}

enum class ReaderFont(val key: String, val label: String, val css: String?) {
    Original("original", "Original", null),
    Literata("literata", "Literata", "'NB Literata', Georgia, serif"),
    Serif("serif", "Clásica", "Georgia, 'Noto Serif', serif"),
    Inter("inter", "Inter", "'NB Inter', sans-serif"),
    Atkinson("atkinson", "Atkinson", "'NB Atkinson', sans-serif");

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k } ?: Literata
    }
}

enum class Margins(val key: String, val label: String, val px: Int) {
    Narrow("narrow", "Estrechos", 14),
    Normal("normal", "Normales", 24),
    Wide("wide", "Amplios", 40);

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k } ?: Normal
    }
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Dark,
    val font: ReaderFont = ReaderFont.Literata,
    val fontSize: Int = 19,          // px CSS
    val lineHeight: Float = 1.55f,
    val margins: Margins = Margins.Normal,
    val justify: Boolean = true,
    val hyphens: Boolean = true,
    val twoPages: Boolean = true,    // dos páginas en horizontal en pantallas anchas
    val volumeKeys: Boolean = false, // pasar página con los botones de volumen
    val brightness: Float = -1f,     // -1 = la del sistema
    val keepScreenOn: Boolean = true,
) {
    /** Lo que necesita el lector (JavaScript) para maquetar. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("bg", css(theme.bg)); put("fg", css(theme.fg)); put("dark", theme.dark)
        put("font", font.css ?: JSONObject.NULL)
        put("size", fontSize); put("lh", lineHeight.toDouble())
        put("margin", margins.px); put("justify", justify); put("hyphens", hyphens)
        put("twoPages", twoPages)
    }

    private fun css(c: Long) = "#%06X".format(c and 0xFFFFFF)
}

/**
 * Preferencias. Cada ajuste tiene su propia clave con prefijo "reader_" (lección de Noir+:
 * nunca reutilizar una clave para dos cosas distintas).
 */
object Prefs {
    private lateinit var sp: SharedPreferences
    val reader = mutableStateOf(ReaderSettings())

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("noirbooks", Context.MODE_PRIVATE)
        reader.value = runCatching { loadReader() }.getOrDefault(ReaderSettings())
    }

    fun setReader(s: ReaderSettings) {
        reader.value = s
        sp.edit()
            .putString("reader_theme", s.theme.key)
            .putString("reader_font", s.font.key)
            .putInt("reader_font_size", s.fontSize)
            .putFloat("reader_line_height", s.lineHeight)
            .putString("reader_margins", s.margins.key)
            .putBoolean("reader_justify", s.justify)
            .putBoolean("reader_hyphens", s.hyphens)
            .putBoolean("reader_two_pages", s.twoPages)
            .putBoolean("reader_volume_keys", s.volumeKeys)
            .putFloat("reader_brightness", s.brightness)
            .putBoolean("reader_keep_screen_on", s.keepScreenOn)
            .apply()
    }

    private fun loadReader(): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            theme = ReaderTheme.of(sp.getString("reader_theme", d.theme.key) ?: d.theme.key),
            font = ReaderFont.of(sp.getString("reader_font", d.font.key) ?: d.font.key),
            fontSize = sp.getInt("reader_font_size", d.fontSize).coerceIn(12, 40),
            lineHeight = sp.getFloat("reader_line_height", d.lineHeight).coerceIn(1.1f, 2.4f),
            margins = Margins.of(sp.getString("reader_margins", d.margins.key) ?: d.margins.key),
            justify = sp.getBoolean("reader_justify", d.justify),
            hyphens = sp.getBoolean("reader_hyphens", d.hyphens),
            twoPages = sp.getBoolean("reader_two_pages", d.twoPages),
            volumeKeys = sp.getBoolean("reader_volume_keys", d.volumeKeys),
            brightness = sp.getFloat("reader_brightness", d.brightness),
            keepScreenOn = sp.getBoolean("reader_keep_screen_on", d.keepScreenOn),
        )
    }

    // Biblioteca
    var sortOrder: String
        get() = sp.getString("library_sort", "recent") ?: "recent"
        set(v) { sp.edit().putString("library_sort", v).apply() }

    // Registro de errores: momento en que se mostró el último aviso de cierre inesperado.
    var crashSeenAt: Long
        get() = sp.getLong("errors_crash_seen_at", 0L)
        set(v) { sp.edit().putLong("errors_crash_seen_at", v).apply() }

    var lastUpdateCheck: Long
        get() = sp.getLong("updates_last_check", 0L)
        set(v) { sp.edit().putLong("updates_last_check", v).apply() }
}
