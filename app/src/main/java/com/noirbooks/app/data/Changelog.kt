package com.noirbooks.app.data

/** Novedades de cada versión, para verlas en Ajustes → Actualizaciones (la más reciente primero). */
object Changelog {
    data class Entry(val version: String, val date: String, val items: List<String>)

    val entries = listOf(
        Entry("1.0.16", "20 nov 2026", listOf(
            "Lo que lees en la app solo cuenta en el calendario y las estadísticas si al salir eliges «Guardar lectura»",
            "«No guardar» deja el libro donde estaba y no suma tiempo: para cuando solo estás mirando",
            "Al reiniciar un libro puedes borrar también sus lecturas del calendario",
        )),
        Entry("1.0.15", "20 nov 2026", listOf(
            "Al salir del libro, si te has movido, te pregunta si guardas por dónde vas (página y %) o lo dejas donde estaba",
            "Reiniciar libro: empezarlo desde cero desde su ficha",
            "Ficha del libro más limpia: sin «Leer» (el libro se abre tocándolo)",
        )),
        Entry("1.0.13", "20 nov 2026", listOf("Nuevo icono en claro")),
        Entry("1.0.12", "20 nov 2026", listOf(
            "Apariencia clara u oscura para la app (o como el sistema), en Ajustes → Apariencia",
        )),
        Entry("1.0.11", "20 nov 2026", listOf(
            "Al añadir un libro eliges cuándo lo vas a leer: lo empiezo ahora, será mi próximo libro, para más adelante o ya leído",
            "Nuevo estado «Próximo» en la biblioteca y en la ficha del libro",
        )),
        Entry("1.0.10", "20 nov 2026", listOf(
            "Borrar todas las lecturas de un libro (registro, calendario y estadísticas) desde su ficha o manteniendo pulsada una lectura",
            "En el calendario, mantener pulsado un libro de un día para borrar lo de ese día o todo lo de ese libro",
            "Novedades de cada versión en Ajustes → Actualizaciones, y aviso al actualizar",
        )),
        Entry("1.0.9", "20 nov 2026", listOf(
            "Pestaña Registro: cada lectura por días; mantener pulsado para borrarla",
            "Ajustes de lectura otra vez en secciones plegables",
            "Modo concentración en la barra superior del libro, con aviso corto",
            "Play y pausa en «Seguir leyendo» para contar el tiempo de lectura",
        )),
        Entry("1.0.7", "20 nov 2026", listOf(
            "La actualización se descarga con el navegador (evita el bloqueo de Play Protect)",
        )),
        Entry("1.0.6", "20 nov 2026", listOf(
            "Página del libro en papel en el lector",
            "Porcentaje con un decimal",
            "Historial de lecturas por libro y por día, con opción de borrar",
            "Apuntar lectura desde el calendario, también en días pasados",
            "Biblioteca con 2, 3 o 4 portadas por fila",
        )),
        Entry("1.0.4", "20 nov 2026", listOf(
            "Pie de página que se cambia tocándolo (izquierda y derecha con opciones distintas)",
            "Modo concentración: solo el texto",
        )),
        Entry("1.0.3", "20 nov 2026", listOf(
            "Libros de Kindle, papel y audio: buscador de libros con portada o a mano",
            "Apuntar lecturas (día, de qué hora a qué hora, hasta dónde) y cronómetro",
            "Importar subrayados del Kindle (My Clippings.txt)",
            "Márgenes laterales y verticales, párrafos, sangría y pie de página configurable",
        )),
        Entry("1.0.2", "20 nov 2026", listOf("Arreglo del lector: ya no sale una sola línea por página")),
        Entry("1.0.1", "20 nov 2026", listOf("Primera versión: lector EPUB, calendario, citas y estadísticas")),
    )
}
