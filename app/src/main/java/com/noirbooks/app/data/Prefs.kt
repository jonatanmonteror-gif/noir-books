package com.noirbooks.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

enum class ReaderTheme(val key: String, val label: String, val bg: Long, val fg: Long, val muted: Long, val dark: Boolean) {
    Light("light", "Claro", 0xFFFFFFFF, 0xFF1A1A1A, 0xFF8A8A8A, false),
    Sepia("sepia", "Sepia", 0xFFF4ECD8, 0xFF3B2F22, 0xFF9A8B74, false),
    /** Verde suave, como el de Kindle: cansa poco la vista. */
    Green("green", "Verde", 0xFFC5E7CE, 0xFF2A3A31, 0xFF4A5F53, false),
    Dark("dark", "Oscuro", 0xFF1C1C1E, 0xFFD6D6D6, 0xFF7C7C80, true),
    Black("black", "Negro", 0xFF000000, 0xFFC8C8C8, 0xFF6E6E72, true),
    /** Colores elegidos por ti (fondo y letra en los ajustes). */
    Custom("custom", "Propio", 0xFF14181F, 0xFFD9D4C7, 0xFF7D7A72, true);

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k } ?: Dark
    }
}

/** Colores con los que se pinta la página. */
data class Palette(val bg: Long, val fg: Long, val muted: Long, val dark: Boolean)

private fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()
private fun mix(a: Long, b: Long, t: Float): Long {
    fun m(sh: Int) = (channel(a, sh) * (1 - t) + channel(b, sh) * t).toInt().coerceIn(0, 255).toLong()
    return (0xFFL shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
}
private fun luminance(c: Long) = (0.299 * channel(c, 16) + 0.587 * channel(c, 8) + 0.114 * channel(c, 0)) / 255.0

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

enum class TapAction { Prev, Next, Menu }

/** Qué hace cada zona de la pantalla al tocar (plantillas). */
enum class TapLayout(val key: String, val label: String, val hint: String) {
    Normal("normal", "Normal", "Izquierda atrás · centro menú · derecha adelante"),
    Lefty("lefty", "Zurdo", "Izquierda adelante · centro menú · derecha atrás"),
    OneHand("onehand", "Una mano", "Arriba menú · abajo: izquierda atrás, el resto adelante"),
    Forward("forward", "Todo avanza", "Casi toda la pantalla adelante · borde izquierdo atrás · centro menú");

    fun action(x: Float, y: Float): TapAction = when (this) {
        Normal -> if (x < 0.28f) TapAction.Prev else if (x > 0.72f) TapAction.Next else TapAction.Menu
        Lefty -> if (x < 0.28f) TapAction.Next else if (x > 0.72f) TapAction.Prev else TapAction.Menu
        OneHand -> if (y < 0.25f) TapAction.Menu else if (x < 0.3f) TapAction.Prev else TapAction.Next
        Forward -> if (x in 0.35f..0.65f && y in 0.35f..0.65f) TapAction.Menu else if (x < 0.15f) TapAction.Prev else TapAction.Next
    }

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: Normal
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
    /** La hora arriba a la izquierda, discreta (como en Kindle). */
    val headerClock: Boolean = false,
    val justify: Boolean = true,
    val hyphens: Boolean = true,
    val twoPages: Boolean = true,    // dos páginas en horizontal en pantallas anchas
    val volumeKeys: Boolean = false, // pasar página con los botones de volumen
    val brightness: Float = -1f,     // -1 = la del sistema
    val keepScreenOn: Boolean = true,
    val tapLayout: TapLayout = TapLayout.Normal,
    /** Colores del tema propio. */
    val customBg: Long = 0xFF14181F,
    val customFg: Long = 0xFFD9D4C7,
    /** Tema automático: "off", "system" (según el modo oscuro del móvil) o "hours" (de 7 a 21 h, día). */
    val autoTheme: String = "off",
    val dayTheme: ReaderTheme = ReaderTheme.Sepia,
    val nightTheme: ReaderTheme = ReaderTheme.Black,
) {
    fun palette(): Palette =
        if (theme == ReaderTheme.Custom) Palette(customBg, customFg, mix(customFg, customBg, 0.5f), luminance(customBg) < 0.5)
        else Palette(theme.bg, theme.fg, theme.muted, theme.dark)

    /** Estilo Kindle: verde, letra con serifa, justificado con guiones, sangría, hora arriba y tiempo del libro abajo. */
    fun kindle(): ReaderSettings = copy(
        theme = ReaderTheme.Green, autoTheme = "off", font = ReaderFont.Literata, publisher = false,
        lineHeight = 1.8f, marginH = 22, marginV = 20, paraSpacing = 0f, indent = 1.5f,
        justify = true, hyphens = true, focus = false, headerClock = true,
        footerLeft = FooterInfo.BookTime, footerRight = FooterInfo.BookPercent,
    )

    /** El tema que toca ahora, si el automático está activado. */
    fun effective(systemDark: Boolean, hour: Int): ReaderSettings = when (autoTheme) {
        "system" -> copy(theme = if (systemDark) nightTheme else dayTheme)
        "hours" -> copy(theme = if (hour in 7..20) dayTheme else nightTheme)
        else -> this
    }

    /** Lo que necesita el lector (JavaScript) para maquetar. */
    fun toJson(): JSONObject = JSONObject().apply {
        val p = palette()
        put("bg", css(p.bg)); put("fg", css(p.fg)); put("dark", p.dark)
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

    private var appCtx: Context? = null

    /** Los ajustes de lectura con el tema automático ya aplicado (según la hora o el modo oscuro del móvil). */
    fun effectiveReader(): ReaderSettings {
        val s = reader.value
        if (s.autoTheme == "off") return s
        val night = appCtx?.resources?.configuration?.uiMode?.and(android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return s.effective(night, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("noirbooks", Context.MODE_PRIVATE)
        reader.value = runCatching { loadReader() }.getOrDefault(ReaderSettings())
        appearanceState.value = sp.getString("app_appearance", "dark") ?: "dark"
        loadProfiles()
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
            .putBoolean("reader_header_clock", s.headerClock)
            .putBoolean("reader_justify", s.justify)
            .putBoolean("reader_hyphens", s.hyphens)
            .putBoolean("reader_two_pages", s.twoPages)
            .putBoolean("reader_volume_keys", s.volumeKeys)
            .putFloat("reader_brightness", s.brightness)
            .putBoolean("reader_keep_screen_on", s.keepScreenOn)
            .putString("reader_tap_layout", s.tapLayout.key)
            .putLong("reader_custom_bg", s.customBg)
            .putLong("reader_custom_fg", s.customFg)
            .putString("reader_auto_theme", s.autoTheme)
            .putString("reader_day_theme", s.dayTheme.key)
            .putString("reader_night_theme", s.nightTheme.key)
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
            headerClock = sp.getBoolean("reader_header_clock", d.headerClock),
            justify = sp.getBoolean("reader_justify", d.justify),
            hyphens = sp.getBoolean("reader_hyphens", d.hyphens),
            twoPages = sp.getBoolean("reader_two_pages", d.twoPages),
            volumeKeys = sp.getBoolean("reader_volume_keys", d.volumeKeys),
            brightness = sp.getFloat("reader_brightness", d.brightness),
            keepScreenOn = sp.getBoolean("reader_keep_screen_on", d.keepScreenOn),
            tapLayout = TapLayout.of(sp.getString("reader_tap_layout", null)),
            customBg = sp.getLong("reader_custom_bg", d.customBg),
            customFg = sp.getLong("reader_custom_fg", d.customFg),
            autoTheme = sp.getString("reader_auto_theme", d.autoTheme) ?: d.autoTheme,
            dayTheme = ReaderTheme.of(sp.getString("reader_day_theme", d.dayTheme.key) ?: d.dayTheme.key),
            nightTheme = ReaderTheme.of(sp.getString("reader_night_theme", d.nightTheme.key) ?: d.nightTheme.key),
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
        "errors_crash_seen_at", "updates_seen_version_code", "backup_last_at",
        "drive_linked", "drive_last_backup", "data_changed_at")

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

    // ---- Perfiles de lectura: ajustes guardados con nombre ----

    fun settingsToJson(s: ReaderSettings): JSONObject = JSONObject()
        .put("theme", s.theme.key).put("font", s.font.key).put("size", s.fontSize).put("lh", s.lineHeight.toDouble())
        .put("mh", s.marginH).put("mv", s.marginV).put("para", s.paraSpacing.toDouble()).put("indent", s.indent.toDouble())
        .put("publisher", s.publisher).put("fl", s.footerLeft.key).put("fr", s.footerRight.key).put("focus", s.focus).put("hclock", s.headerClock)
        .put("justify", s.justify).put("hyphens", s.hyphens).put("two", s.twoPages).put("vol", s.volumeKeys)
        .put("bright", s.brightness.toDouble()).put("awake", s.keepScreenOn).put("tap", s.tapLayout.key)
        .put("cbg", s.customBg).put("cfg", s.customFg).put("auto", s.autoTheme).put("day", s.dayTheme.key).put("night", s.nightTheme.key)

    fun settingsFromJson(o: JSONObject): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            theme = ReaderTheme.of(o.optString("theme", d.theme.key)), font = ReaderFont.of(o.optString("font", d.font.key)),
            fontSize = o.optInt("size", d.fontSize).coerceIn(12, 40), lineHeight = o.optDouble("lh", d.lineHeight.toDouble()).toFloat(),
            marginH = o.optInt("mh", d.marginH), marginV = o.optInt("mv", d.marginV),
            paraSpacing = o.optDouble("para", -1.0).toFloat(), indent = o.optDouble("indent", -1.0).toFloat(),
            publisher = o.optBoolean("publisher"), footerLeft = FooterInfo.of(o.optString("fl"), d.footerLeft, FooterInfo.LEFT),
            footerRight = FooterInfo.of(o.optString("fr"), d.footerRight, FooterInfo.RIGHT), focus = o.optBoolean("focus"), headerClock = o.optBoolean("hclock"),
            justify = o.optBoolean("justify", d.justify), hyphens = o.optBoolean("hyphens", d.hyphens), twoPages = o.optBoolean("two", d.twoPages),
            volumeKeys = o.optBoolean("vol"), brightness = o.optDouble("bright", -1.0).toFloat(), keepScreenOn = o.optBoolean("awake", true),
            tapLayout = TapLayout.of(o.optString("tap")),
            customBg = o.optLong("cbg", d.customBg), customFg = o.optLong("cfg", d.customFg),
            autoTheme = o.optString("auto", "off"), dayTheme = ReaderTheme.of(o.optString("day", d.dayTheme.key)),
            nightTheme = ReaderTheme.of(o.optString("night", d.nightTheme.key)),
        )
    }

    val profiles = mutableStateOf<List<Pair<String, ReaderSettings>>>(emptyList())

    private fun loadProfiles() {
        profiles.value = runCatching {
            val a = org.json.JSONArray(sp.getString("reader_profiles", "[]") ?: "[]")
            (0 until a.length()).map { i -> a.getJSONObject(i).let { it.getString("name") to settingsFromJson(it.getJSONObject("s")) } }
        }.getOrDefault(emptyList())
    }

    private fun saveProfiles(list: List<Pair<String, ReaderSettings>>) {
        profiles.value = list
        val a = org.json.JSONArray()
        list.forEach { (n, s) -> a.put(JSONObject().put("name", n).put("s", settingsToJson(s))) }
        sp.edit().putString("reader_profiles", a.toString()).apply()
    }

    fun saveProfile(name: String, s: ReaderSettings) = saveProfiles(profiles.value.filter { it.first != name } + (name to s))
    fun deleteProfile(name: String) = saveProfiles(profiles.value.filter { it.first != name })

    /** Velocidad de la lectura en voz alta (1 = normal). */
    var ttsRate: Float
        get() = (kotlin.math.round(sp.getFloat("reader_tts_rate", 1f) * 10f) / 10f).coerceIn(0.8f, 1.5f)
        set(v) { sp.edit().putFloat("reader_tts_rate", v).apply() }

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
    /** Búsqueda mejorada de páginas en papel (por ISBN, título y autor); se repite para los libros ya mirados antes. */
    fun paperLookedUp(id: String) = sp.getBoolean("paper_lookup_$id", false)
    fun markPaperLookedUp(id: String) { sp.edit().putBoolean("paper_lookup_$id", true).apply() }
    fun pagesLookedUp(id: String) = sp.getBoolean("pages_lookup_$id", false)
    fun markPagesLookedUp(id: String) { sp.edit().putBoolean("pages_lookup_$id", true).apply() }

    // Biblioteca: portadas por fila (0 = automático según la pantalla)
    /** Diseño de las estanterías: noir, oak (madera clara), walnut (madera oscura), white. */
    var shelfStyle: String
        get() = sp.getString("shelf_style", "walnut") ?: "walnut"
        set(v) { sp.edit().putString("shelf_style", v).apply() }

    /** Cómo se ven los libros en las estanterías: spines (lomos) o covers (portadas). */
    var shelfView: String
        get() = sp.getString("shelf_view", "spines") ?: "spines"
        set(v) { sp.edit().putString("shelf_view", v).apply() }

    /** Tamaño de las estanterías: s (pequeña), m (mediana), l (grande). */
    var shelfSize: String
        get() = sp.getString("shelf_size", "m") ?: "m"
        set(v) { sp.edit().putString("shelf_size", v).apply() }

    // ---- Voz de la lectura en voz alta ----
    /** Idioma y voz para todos los libros (la voz vacía = la que elija el móvil para ese idioma). */
    var ttsLang: String
        get() = sp.getString("tts_lang", "es-ES") ?: "es-ES"
        set(v) { sp.edit().putString("tts_lang", v).apply() }
    var ttsVoice: String
        get() = sp.getString("tts_voice", "") ?: ""
        set(v) { sp.edit().putString("tts_voice", v).apply() }
    /** Voz propia de un libro (null = la general). */
    fun ttsLangFor(id: String): String? = sp.getString("tts_lang_$id", null)
    fun ttsVoiceFor(id: String): String = sp.getString("tts_voice_$id", "") ?: ""
    fun setTtsFor(id: String, lang: String?, voice: String?) {
        val e = sp.edit()
        if (lang == null) e.remove("tts_lang_$id").remove("tts_voice_$id")
        else e.putString("tts_lang_$id", lang).putString("tts_voice_$id", voice.orEmpty())
        e.apply()
    }

    /** Idioma al que se traduce por defecto. */
    var translateTarget: String
        get() = sp.getString("translate_target", "en") ?: "en"
        set(v) { sp.edit().putString("translate_target", v).apply() }

    var libraryColumns: Int
        get() = sp.getInt("library_columns", 3)
        set(v) { sp.edit().putInt("library_columns", v).apply() }

    // Google Drive (propio de este aparato)
    var driveLinked: Boolean
        get() = sp.getBoolean("drive_linked", false)
        set(v) { sp.edit().putBoolean("drive_linked", v).apply() }
    var driveWithBooks: Boolean
        get() = sp.getBoolean("drive_with_books", false)
        set(v) { sp.edit().putBoolean("drive_with_books", v).apply() }
    var driveLastBackup: Long
        get() = sp.getLong("drive_last_backup", 0L)
        set(v) { sp.edit().putLong("drive_last_backup", v).apply() }
    /** Último cambio en los datos (para saber si hace falta otra copia). */
    var dataChangedAt: Long
        get() = if (::sp.isInitialized) sp.getLong("data_changed_at", 0L) else 0L
        set(v) { if (::sp.isInitialized) sp.edit().putLong("data_changed_at", v).apply() }

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
