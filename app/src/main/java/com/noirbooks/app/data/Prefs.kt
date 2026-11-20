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

/** Lo que se puede mostrar en el pie de página del lector. */
enum class FooterInfo(val key: String, val label: String) {
    ChapterTime("chapter_time", "Queda del capítulo"),
    BookTime("book_time", "Queda del libro"),
    ChapterPages("chapter_pages", "Página del capítulo"),
    BookPage("book_page", "Página del libro"),
    BookPercent("book_percent", "% del libro"),
    Clock("clock", "Hora"),
    Battery("battery", "Batería"),
    None("none", "Nada");

    companion object {
        /** Cada lado tiene sus propias opciones, para que no se repita lo mismo a los dos lados. */
        val LEFT = listOf(ChapterTime, BookTime, ChapterPages, None)
        val RIGHT = listOf(BookPercent, BookPage, Clock, Battery, None)
        fun of(k: String?, d: FooterInfo, allowed: List<FooterInfo>) =
            entries.firstOrNull { it.key == k }?.takeIf { it in allowed } ?: d
        fun next(f: FooterInfo, list: List<FooterInfo>) = list[(list.indexOf(f) + 1).mod(list.size)]
    }
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Dark,
    val font: ReaderFont = ReaderFont.Literata,
    val fontSize: Int = 19,          // px CSS
    val lineHeight: Float = 1.55f,
    val marginH: Int = 24,            // px a cada lado
    val marginV: Int = 22,            // px arriba (abajo, un poco menos: ya está el pie)
    /** Espacio entre párrafos en em (-1 = el del libro). */
    val paraSpacing: Float = -1f,
    /** Sangría de la primera línea en em (-1 = la del libro). */
    val indent: Float = -1f,
    /** Respetar el diseño del libro: no cambiar letra, interlineado, justificado ni párrafos. */
    val publisher: Boolean = false,
    val footerLeft: FooterInfo = FooterInfo.ChapterTime,
    val footerRight: FooterInfo = FooterInfo.BookPercent,
    /** Modo concentración: solo el texto, sin pie de página ni avisos. */
    val focus: Boolean = false,
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
        put("margin", marginH); put("mtop", marginV); put("mbottom", (marginV * 0.6f).toInt().coerceAtLeast(6))
        put("para", paraSpacing.toDouble()); put("indent", indent.toDouble()); put("publisher", publisher)
        put("justify", justify); put("hyphens", hyphens)
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
    /** Apariencia de la app: "system", "dark" o "light". */
    val appearanceState = mutableStateOf("dark")

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("noirbooks", Context.MODE_PRIVATE)
        reader.value = runCatching { loadReader() }.getOrDefault(ReaderSettings())
        appearanceState.value = sp.getString("app_appearance", "dark") ?: "dark"
    }

    fun setReader(s: ReaderSettings) {
        reader.value = s
        sp.edit()
            .putString("reader_theme", s.theme.key)
            .putString("reader_font", s.font.key)
            .putInt("reader_font_size", s.fontSize)
            .putFloat("reader_line_height", s.lineHeight)
            .putInt("reader_margin_h", s.marginH)
            .putInt("reader_margin_v", s.marginV)
            .putFloat("reader_para_spacing", s.paraSpacing)
            .putFloat("reader_indent", s.indent)
            .putBoolean("reader_publisher", s.publisher)
            .putString("reader_footer_left", s.footerLeft.key)
            .putString("reader_footer_right", s.footerRight.key)
            .putBoolean("reader_focus", s.focus)
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
            // Antes los márgenes eran tres opciones fijas: se respeta la que hubiera elegida.
            marginH = sp.getInt("reader_margin_h", Margins.of(sp.getString("reader_margins", "normal") ?: "normal").px).coerceIn(4, 80),
            marginV = sp.getInt("reader_margin_v", d.marginV).coerceIn(4, 80),
            paraSpacing = sp.getFloat("reader_para_spacing", d.paraSpacing),
            indent = sp.getFloat("reader_indent", d.indent),
            publisher = sp.getBoolean("reader_publisher", d.publisher),
            footerLeft = FooterInfo.of(sp.getString("reader_footer_left", null), d.footerLeft, FooterInfo.LEFT),
            footerRight = FooterInfo.of(sp.getString("reader_footer_right", null), d.footerRight, FooterInfo.RIGHT),
            focus = sp.getBoolean("reader_focus", d.focus),
            justify = sp.getBoolean("reader_justify", d.justify),
            hyphens = sp.getBoolean("reader_hyphens", d.hyphens),
            twoPages = sp.getBoolean("reader_two_pages", d.twoPages),
            volumeKeys = sp.getBoolean("reader_volume_keys", d.volumeKeys),
            brightness = sp.getFloat("reader_brightness", d.brightness),
            keepScreenOn = sp.getBoolean("reader_keep_screen_on", d.keepScreenOn),
        )
    }

    /** Todos los ajustes, para las copias de seguridad (salvo lo propio de este aparato). */
    fun exportJson(): JSONObject {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            if (k in LOCAL_ONLY) return@forEach
            val t = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; else -> return@forEach }
            o.put(k, JSONObject().put("t", t).put("v", v))
        }
        return o
    }

    /** Sustituye los ajustes por los de una copia (se guarda al momento: la app se reinicia después). */
    fun importJson(o: JSONObject) {
        val e = sp.edit()
        sp.all.keys.filter { it !in LOCAL_ONLY }.forEach { e.remove(it) }
        o.keys().forEach { k ->
            if (k in LOCAL_ONLY) return@forEach
            val x = o.optJSONObject(k) ?: return@forEach
            when (x.optString("t")) {
                "b" -> e.putBoolean(k, x.optBoolean("v"))
                "i" -> e.putInt(k, x.optInt("v"))
                "l" -> e.putLong(k, x.optLong("v"))
                "f" -> e.putFloat(k, x.optDouble("v").toFloat())
                "s" -> e.putString(k, x.optString("v"))
            }
        }
        e.commit()
    }

    /** Claves que son de este aparato y no viajan en las copias. */
    private val LOCAL_ONLY = setOf("timer_book", "timer_start", "reader_pending_reading", "updates_last_check",
        "errors_crash_seen_at", "updates_seen_version_code", "backup_last_at")

    fun setAppearance(v: String) {
        appearanceState.value = v
        sp.edit().putString("app_appearance", v).apply()
    }

    // Meta anual de libros (0 = sin meta)
    var yearGoal: Int
        get() = sp.getInt("goal_books_year", 12)
        set(v) { sp.edit().putInt("goal_books_year", v).apply() }

    /** Tipo de meta anual: "books", "hours" o "pages". */
    var goalKind: String
        get() = sp.getString("goal_kind", "books") ?: "books"
        set(v) { sp.edit().putString("goal_kind", v).apply() }
    var goalHours: Int
        get() = sp.getInt("goal_hours_year", 100)
        set(v) { sp.edit().putInt("goal_hours_year", v).apply() }
    var goalPages: Int
        get() = sp.getInt("goal_pages_year", 5000)
        set(v) { sp.edit().putInt("goal_pages_year", v).apply() }

    // Pista de gestos del lector: se muestra solo la primera vez
    var gestureHintShown: Boolean
        get() = sp.getBoolean("reader_gesture_hint_shown", false)
        set(v) { sp.edit().putBoolean("reader_gesture_hint_shown", v).apply() }

    // Biblioteca
    var sortOrder: String
        get() = sp.getString("library_sort", "recent") ?: "recent"
        set(v) { sp.edit().putString("library_sort", v).apply() }

    // Registro de errores: momento en que se mostró el último aviso de cierre inesperado.
    var crashSeenAt: Long
        get() = sp.getLong("errors_crash_seen_at", 0L)
        set(v) { sp.edit().putLong("errors_crash_seen_at", v).apply() }

    // Cronómetro de lectura fuera de la app (Kindle, papel): sobrevive a cerrar la app.
    var timerBook: String
        get() = sp.getString("timer_book", "") ?: ""
        set(v) { sp.edit().putString("timer_book", v).apply() }
    var timerStart: Long
        get() = sp.getLong("timer_start", 0L)
        set(v) { sp.edit().putLong("timer_start", v).apply() }

    // Libros cuyas páginas en papel ya se buscaron en el catálogo (para no repetir).
    fun descLookedUp(id: String) = sp.getBoolean("desc_lookup_$id", false)
    fun markDescLookedUp(id: String) { sp.edit().putBoolean("desc_lookup_$id", true).apply() }
    fun pagesLookedUp(id: String) = sp.getBoolean("pages_lookup_$id", false)
    fun markPagesLookedUp(id: String) { sp.edit().putBoolean("pages_lookup_$id", true).apply() }

    // Biblioteca: portadas por fila (0 = automático según la pantalla)
    var libraryColumns: Int
        get() = sp.getInt("library_columns", 3)
        set(v) { sp.edit().putInt("library_columns", v).apply() }

    /** Última copia de seguridad hecha en este aparato. */
    var lastBackupAt: Long
        get() = sp.getLong("backup_last_at", 0L)
        set(v) { sp.edit().putLong("backup_last_at", v).apply() }

    // Última versión de la que se mostraron las novedades.
    var seenVersionCode: Int
        get() = sp.getInt("updates_seen_version_code", 0)
        set(v) { sp.edit().putInt("updates_seen_version_code", v).apply() }

    /** Lectura en la app aún sin confirmar (JSON), por si la app se cierra antes de guardarla. */
    var pendingReading: String
        get() = sp.getString("reader_pending_reading", "") ?: ""
        set(v) { sp.edit().putString("reader_pending_reading", v).apply() }

    var lastUpdateCheck: Long
        get() = sp.getLong("updates_last_check", 0L)
        set(v) { sp.edit().putLong("updates_last_check", v).apply() }
}
