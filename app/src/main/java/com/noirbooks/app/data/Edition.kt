package com.noirbooks.app.data

import androidx.compose.runtime.mutableStateOf

/**
 * Qué «vista» tiene la app: gratuita, Pro o desarrollador.
 *
 * TEMPORAL (fase de pruebas): el selector de vistas está en Ajustes para ver la app como la verá cada tipo
 * de usuario. ANTES DE PUBLICAR hay que quitar el selector y que Pro dependa solo de la compra en Google Play.
 *
 * - free: usuario nuevo sin Pro. Las funciones Pro salen con candado y llevan a la pantalla de Pro.
 * - pro: usuario que ha pagado Pro. Todo desbloqueado, sin ajustes de desarrollador.
 * - dev: todo desbloqueado y además los ajustes de desarrollador (registro de errores, ver el asistente…).
 */
object Edition {
    val mode = mutableStateOf("dev")

    fun init() { mode.value = Prefs.viewMode }
    fun set(m: String) { mode.value = m; Prefs.viewMode = m }

    val isPro: Boolean get() = mode.value == "pro" || mode.value == "dev"
    val isDev: Boolean get() = mode.value == "dev"

    /** Lo que trae Pro (se enseña en la pantalla de Pro). Añadir aquí cada función nueva que sea Pro. */
    val FEATURES = listOf(
        "Más temas de lectura: Verde, Negro y tu tema propio",
        "Tema automático: claro de día y oscuro de noche",
        "Pie de página a tu gusto: tiempo del capítulo, %, páginas, hora y batería",
        "Perfil de lectura «Verde», listo para usar",
        "10 recomendaciones personalizadas al mes (sin Pro, 1)",
    )

    /** Temas de lectura gratis; el resto son Pro. */
    val FREE_THEMES = setOf("light", "sepia", "dark")
}
