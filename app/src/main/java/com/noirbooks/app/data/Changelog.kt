package com.noirbooks.app.data

/** Novedades de cada versión, para verlas en Ajustes → Actualizaciones (la más reciente primero). */
object Changelog {
    data class Entry(val version: String, val date: String, val items: List<String>)

    val entries = listOf(
        Entry("1.0.45", "21 nov 2026", listOf(
            "Las páginas leídas cuentan como páginas del libro en papel cuando se conocen (no como pantallas del móvil)",
            "Así cuadran las estadísticas, la meta por páginas y el registro con tu edición en papel",
        )),
        Entry("1.0.44", "21 nov 2026", listOf(
            "Gráficos del tiempo de lectura por semana, mes y año (Estadísticas)",
            "Género de cada libro: se busca solo y se puede cambiar desde el menú del libro; estadísticas por género",
            "Nota opcional al guardar una lectura («¿qué te ha parecido?»), visible en el registro",
            "Elogios: un aviso discreto al terminar un libro o un mes cuando hay algo que destacar (Ajustes → Diario)",
            "Valoración básica o por aspectos (trama, personajes y prosa), a elegir en Ajustes → Diario",
        )),
        Entry("1.0.43", "21 nov 2026", listOf(
            "Meta diaria en Estadísticas: «Hoy: 18 de 30 min»",
            "Calendario del año entero: un cuadrito por día, más oscuro cuanto más leíste",
            "Compartir el mes del calendario como imagen, con las portadas",
            "Informe de la semana y del mes, comparado con el anterior y para compartir como imagen",
        )),
        Entry("1.0.42", "21 nov 2026", listOf(
            "Diccionario con pestañas: RAE, Wikipedia y Traducir",
            "Traducir una palabra o frase en el propio móvil (Google ML Kit): selecciona el texto y pulsa Traducir",
            "Lector de PDF: importa PDF como los EPUB; páginas a pantalla completa, ampliar con los dedos y guardar la lectura",
        )),
        Entry("1.0.40", "21 nov 2026", listOf(
            "Las estanterías muestran siempre las portadas de los libros (sin lomos)",
        )),
        Entry("1.0.39", "21 nov 2026", listOf(
            "Elegir la voz de la lectura en voz alta: idioma (Español de España, de México…), voz y botón para escucharla antes",
            "La voz se guarda para todos los libros o solo para el que estás leyendo",
            "Estanterías en tres tamaños: Pequeña, Mediana y Grande (Estanterías → Diseño)",
        )),
        Entry("1.0.38", "21 nov 2026", listOf(
            "Velocidad de la lectura en voz alta de 0,8 a 1,5, de 0,1 en 0,1, con los botones − y +",
        )),
        Entry("1.0.37", "21 nov 2026", listOf(
            "Páginas de la edición en papel automáticas: se buscan solas por ISBN o por título y autor",
            "También para los libros que ya tenías, en segundo plano",
            "En la ficha: «412 páginas en papel». Si tu edición tiene otras, cámbialas en el menú del libro",
            "Estilo Kindle (Lectura → Perfiles): tema verde, letra con serifa, sangría, hora arriba y tiempo del libro abajo",
            "Tema Verde y opción de ver la hora arriba (Lectura → Pie de página)",
            "Lectura en voz alta en español de España cuando el libro solo dice «español»",
        )),
        Entry("1.0.36", "21 nov 2026", listOf(
            "Quitar la valoración de un libro: botón Quitar junto a las estrellas (o tocar otra vez la misma nota)",
        )),
        Entry("1.0.35", "20 nov 2026", listOf(
            "Biblioteca en tres pestañas: Inicio, Biblioteca y Estanterías",
            "Inicio al estilo Kindle: lo que lees, tus 8 libros más recientes, tus estados y tus estanterías",
            "Estanterías con los libros de lado (lomos con el color de su portada) o de frente",
            "Diseño de las estanterías: Noir, Nogal, Roble o Blanca",
        )),
        Entry("1.0.34", "20 nov 2026", listOf(
            "Política de privacidad y condiciones de uso (Ajustes → Acerca de)",
            "Preparada la firma con claves guardadas como secretos (necesario para Google Play)",
        )),
        Entry("1.0.33", "20 nov 2026", listOf(
            "Estanterías con su propia pantalla: toca el icono de la estantería arriba en la biblioteca",
            "Cada estantería se ve como un estante con sus portadas; tócala para ver solo esos libros",
            "Añadir libros a una estantería desde la propia estantería, y filtro «Sin estantería»",
            "Ya no aparecen etiquetas de estanterías ocupando la biblioteca",
        )),
        Entry("1.0.32", "20 nov 2026", listOf(
            "Los botones que no caben pasan a la línea de abajo en vez de partirse en dos (p. ej. Restaurar de Drive)",
        )),
        Entry("1.0.31", "20 nov 2026", listOf(
            "Las actualizaciones se descargan e instalan desde la propia app, sin pasar por el navegador",
            "Se comprueba la huella del archivo antes de instalarlo",
        )),
        Entry("1.0.29", "20 nov 2026", listOf(
            "Estanterías propias: créalas en la biblioteca (+ Nueva estantería), filtra tocándolas y mantenlas pulsadas para renombrar o borrar",
            "Seleccionar varios libros para ponerlos en una estantería o cambiarles el estado de una vez",
            "Estanterías también en la ficha del libro y en su menú",
        )),
        Entry("1.0.28", "20 nov 2026", listOf(
            "Tema propio: elige el color del fondo y de la letra (Lectura → Tema propio y automático)",
            "Tema automático: claro de día y oscuro de noche, por horas o como el modo oscuro del móvil",
            "Restablecer los ajustes de lectura (Lectura → Avanzado)",
        )),
        Entry("1.0.27", "20 nov 2026", listOf(
            "La lectura en voz alta sigue con la pantalla apagada, con Pausa y Parar en la notificación",
            "Escanear varios libros seguidos: «Añadir y escanear otro»",
            "Perfiles de lectura: guarda tus ajustes con nombre y cambia con un toque (Lectura → Perfiles)",
            "Zonas táctiles: Normal, Zurdo, Una mano o Todo avanza, con mapa (Lectura → Avanzado)",
        )),
        Entry("1.0.25", "20 nov 2026", listOf(
            "Lectura en voz alta: botón ▶ arriba en el libro; frase resaltada, pasa de página y de capítulo sola, velocidad y temporizador para dormir",
            "Escáner del código de barras (ISBN) al añadir un libro",
            "Importar tu biblioteca de Goodreads (CSV): estados, valoraciones, fechas, opinión y portadas",
            "Cita desde una foto de una página de papel (Citas → + Foto); el texto se lee en el móvil",
        )),
        Entry("1.0.24", "20 nov 2026", listOf(
            "Copia automática en Google Drive (carpeta privada de la app) y restaurar desde Drive en otro móvil. Se activará cuando esté configurada la cuenta de Google del proyecto",
            "En una biblioteca vacía, acceso directo a restaurar tu copia",
        )),
        Entry("1.0.22", "20 nov 2026", listOf(
            "Copia de seguridad en un archivo (con o sin los EPUB) y restaurarla en este u otro móvil: Ajustes → Copia de seguridad",
            "Exportar tus datos en tablas CSV: libros, lecturas, lectura por día, citas y palabras",
            "Editar una lectura del registro (día, horas y páginas) manteniéndola pulsada",
            "El cronómetro de lectura aparece en una notificación, con «Terminar»",
            "Meta anual por libros, horas o páginas",
            "Buscador en Citas",
        )),
        Entry("1.0.20", "20 nov 2026", listOf("Valoración en medias estrellas: 0,5, 1, 1,5… hasta 5")),
        Entry("1.0.19", "20 nov 2026", listOf(
            "Tocar un libro abre su ficha a pantalla completa: portada, sinopsis, progreso y tu lectura (el libro ya no se abre solo)",
            "Sinopsis del propio EPUB o de Google Books",
            "La tarjeta «Seguir leyendo» ya no abre el libro al tocarla: solo el botón",
        )),
        Entry("1.0.18", "20 nov 2026", listOf(
            "Carrusel de «Seguir leyendo»: asoman la tarjeta anterior y la siguiente, y ya no salta al deslizar",
        )),
        Entry("1.0.17", "20 nov 2026", listOf(
            "Si lees varios libros a la vez, «Seguir leyendo» es un carrusel: desliza y la siguiente tarjeta asoma por la derecha",
            "La meta anual solo cuenta los libros marcados como leídos, una vez cada uno",
        )),
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
