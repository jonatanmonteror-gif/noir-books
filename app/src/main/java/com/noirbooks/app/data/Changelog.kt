package com.noirbooks.app.data

/** Novedades de cada versión, para verlas en Ajustes → Actualizaciones (la más reciente primero). */
object Changelog {
    data class Entry(val version: String, val date: String, val items: List<String>)

    val entries = listOf(
        Entry("1.0.86", "23 nov 2026", listOf(
            "Audiolibros por tiempo: apunta «voy por 4:20 de 11:30», como en Audible. Al añadir un audiolibro se elige solo",
            "Widget «Leyendo ahora»: el libro que lees con «Apuntar» y «Cronómetro», sin abrir la app",
            "Mantén pulsado el icono de la app: «Apuntar lectura» del libro actual",
            "Apuntar lectura más rápido: botones de 15 min, 30 min, 1 h… sin tocar relojes",
        )),
        Entry("1.0.85", "22 nov 2026", listOf(
            "Ediciones agrupadas: al buscar un libro sale una sola vez, con «N ediciones» para elegir la tuya (editorial, año, páginas, idioma e ISBN)",
            "La cita del día se actualiza al momento si le cambias el color, la nota o las etiquetas (también en el widget)",
        )),
        Entry("1.0.84", "22 nov 2026", listOf(
            "Pantalla de Pro más compacta: se ve entera sin desplazar",
        )),
        Entry("1.0.83", "22 nov 2026", listOf(
            "Pantalla de Noir Books Pro con precio de lanzamiento y tarjeta «¿Te pasas a Pro?» arriba en Ajustes",
            "Sin Pro, los temas Pro salen al final y apagados, y las secciones Pro se pueden desplegar para verlas, pero no tocar",
            "El perfil listo «Kindle» se llama ahora «Verde». En la vista de usuario nuevo no salen perfiles, como en una instalación limpia",
        )),
        Entry("1.0.82", "22 nov 2026", listOf(
            "Preparando Noir Books Pro: los temas Verde, Negro y Propio, el tema automático y el pie de página a tu gusto serán Pro",
            "Ajustes › Vista de la app (temporal, para pruebas): ver la app como usuario nuevo, usuario Pro o desarrollador",
        )),
        Entry("1.0.81", "22 nov 2026", listOf(
            "Cita del día: botón «Compartir» en Citas y en el widget, que abre directamente la imagen para Instagram y otras redes",
        )),
        Entry("1.0.80", "22 nov 2026", listOf(
            "Etiquetas en las citas: añádelas desde la cita (#amor, #ideas…) y filtra por ellas en Citas. También salen al exportar",
            "Widget «Cita del día» para la pantalla de inicio del móvil: una de tus citas cada día; al tocarlo abre Citas",
            "Ajustes › Apariencia › «Ver el asistente de bienvenida», para ver cómo lo ve quien instala la app",
        )),
        Entry("1.0.79", "22 nov 2026", listOf(
            "Sagas automáticas: se leen del propio EPUB (los preparados con Calibre o EPUB 3 las traen) o del título, p. ej. «Alas de sangre (Empíreo 1)». También en los libros que ya tenías",
        )),
        Entry("1.0.78", "22 nov 2026", listOf(
            "Asistente de bienvenida al instalar: apariencia, letra de lectura, copia de seguridad y traer tus libros",
            "Ajustes › Privacidad: bloquear la app con la huella (o el PIN del móvil) al abrirla y al volver después de un minuto",
            "Ajustes › Inicio: elige qué secciones ves en Inicio y en qué orden",
        )),
        Entry("1.0.77", "22 nov 2026", listOf(
            "La ficha ya no muestra como editorial las firmas o alias que traen algunos EPUB (por ejemplo «lineasdesaparecidas»)",
        )),
        Entry("1.0.76", "22 nov 2026", listOf(
            "Lista de deseos: marca un libro como «Lo quiero» (aún no lo tienes). Filtro propio en Biblioteca y etiqueta en la portada",
            "Sagas: nombre y número («3 de 7»). En Estanterías › Sagas cada saga sale ordenada",
            "Préstamos de libros de papel: a quién, desde cuándo y aviso el día que debería volver. Filtro «Prestados»",
            "Retomar abandonados: pasados 3 meses, una tarjeta en Inicio te propone volver (se desactiva en Ajustes › Diario)",
        )),
        Entry("1.0.74", "22 nov 2026", listOf(
            "Selección de varios en Biblioteca: nuevo botón «Género» para ponérselo a todos a la vez (y así se forman sus estanterías de género)",
        )),
        Entry("1.0.73", "22 nov 2026", listOf(
            "«Sin estantería» ahora son solo los libros que no están en ninguna: ni tuya ni de género",
            "Biblioteca: mantén pulsado un libro para seleccionar varios y meterlos en una estantería o cambiarles el estado. Un toque abre la ficha (y desde sus tres puntos, el resto de opciones)",
            "El menú del libro muestra también su estantería de género",
        )),
        Entry("1.0.72", "22 nov 2026", listOf(
            "Ajustes › Portadas: busca sola la portada de los libros que no tienen (por ISBN y, si no, por título y autor)",
            "Después de importar de Bookmory o Goodreads, las portadas que falten se buscan solas",
        )),
        Entry("1.0.71", "22 nov 2026", listOf(
            "Menú del libro más compacto: portada pequeña y X arriba, acciones en una fila, estrellas más pequeñas y filas más juntas",
            "Si queda algo por debajo, un degradado avisa de que puedes deslizar",
        )),
        Entry("1.0.70", "22 nov 2026", listOf(
            "La app pesa unos 30 MB menos: ya no lleva las piezas para emuladores de ordenador",
        )),
        Entry("1.0.69", "22 nov 2026", listOf(
            "Actualizar: la comprobación de seguridad se hace antes de descargar, así no falla si sales de la app mientras baja",
            "Si no hay internet, el aviso lo dice claro y ya no llena el registro de errores",
        )),
        Entry("1.0.68", "22 nov 2026", listOf(
            "Importar de Bookmory ahora lee su exportación real: el .zip con un .txt por libro. Trae estado, fechas de lectura, estrellas, comentario, ISBN, editorial, páginas, género y el tiempo leído",
            "Si el libro ya estaba (por ejemplo, el EPUB que lees aquí), solo se completan la valoración y la reseña",
        )),
        Entry("1.0.67", "21 nov 2026", listOf(
            "Importar tu biblioteca de Bookmory: el Excel (.xlsx) que exporta la app, o ese Excel guardado como CSV. Trae título, autor, ISBN, páginas, estado, valoración, fechas y reseña",
        )),
        Entry("1.0.66", "21 nov 2026", listOf(
            "Seguir leyendo: la portada se ajusta al alto de la tarjeta",
            "Ajustes › Apariencia: tres tamaños para las portadas de Recientes (pequeñas, medianas, grandes)",
        )),
        Entry("1.0.65", "21 nov 2026", listOf(
            "Compartir cita: la barra Historia / Publicación baja un poco y la vista previa es más pequeña para que todo quepa con aire",
        )),
        Entry("1.0.64", "21 nov 2026", listOf(
            "El botón Compartir de las citas ya cabe entero: la pantalla ocupa todo el móvil y respeta la barra de abajo",
            "Lo mismo en el diccionario y en la lectura rápida",
        )),
        Entry("1.0.63", "21 nov 2026", listOf(
            "Perfiles de lectura nada más abrir los ajustes: Kindle, los tuyos y «+ Guardar». Mantén pulsado para actualizar o borrar",
            "Tamaño de letra, interlineado y márgenes van de 1 en 1 (interlineado de 0,05 en 0,05) y puedes tocar el número para escribirlo",
            "El tipo de letra se elige en un desplegable, cada opción con su propia letra",
        )),
        Entry("1.0.62", "21 nov 2026", listOf(
            "Hora y batería más arriba, en el borde superior de la pantalla; la batería ya no se elige en el pie",
            "Tamaño de la app intermedio (Medio = 96 %)",
            "Compartir cita, diccionario y lectura rápida: ya no se cortan por abajo",
        )),
        Entry("1.0.61", "21 nov 2026", listOf(
            "Hora y batería fijas arriba del todo en el lector, con margen por las esquinas del móvil",
            "Elige si se ven también en modo concentración (Lectura → Pie de página → Arriba)",
            "Márgenes con − y + de 4 en 4",
            "Toda la app un poco más pequeña; el tamaño se cambia en Ajustes → Apariencia (Pequeño, Medio, Normal)",
        )),
        Entry("1.0.60", "21 nov 2026", listOf(
            "Diccionario: cambiar entre RAE, Wikipedia y Traducir ya no hace un pantallazo negro ni recarga la página",
        )),
        Entry("1.0.59", "21 nov 2026", listOf(
            "Compartir cita: el botón Compartir ya no queda debajo de los botones del móvil",
            "Subrayado: menos interlineado",
            "Diccionario a pantalla completa: ya se puede bajar por la página de la RAE y de Wikipedia",
            "Lectura rápida: desactivada de serie; se activa en Ajustes → Lectura → Avanzado. Controles bien colocados",
        )),
        Entry("1.0.58", "21 nov 2026", listOf(
            "Tamaño de letra deslizando arriba o abajo por el borde derecho (se ve el número en grande); se puede desactivar en Lectura → Avanzado",
            "Desplazamiento continuo por libro, sin páginas (Lectura → Avanzado); al final, «Siguiente capítulo»",
            "Citas que siguen en la página siguiente: selecciona, pulsa «Sigue ›» y elige dónde termina",
            "Lectura rápida palabra a palabra (botón Rápida abajo): de 100 a 800 palabras por minuto",
        )),
        Entry("1.0.56", "21 nov 2026", listOf(
            "Subrayar más fino: la selección ya no mueve la página ni se escapa a la página siguiente",
            "Android ya no amplía la selección por su cuenta («selección inteligente»)",
        )),
        Entry("1.0.55", "21 nov 2026", listOf(
            "Compartir cita rediseñado: vista previa grande, miniaturas de cada diseño, formato arriba y un solo botón Compartir",
            "Diseño Papel más compacto (menos interlineado y separaciones); se quita el diseño Portada",
        )),
        Entry("1.0.53", "21 nov 2026", listOf(
            "Estanterías: pestañas más pequeñas (Mis estanterías, Por género, Todas) y el «+» suelto a la derecha, en una sola fila",
        )),
        Entry("1.0.52", "21 nov 2026", listOf(
            "Actualizaciones más fiables: se descargan con el gestor de descargas de Android y ya no se cortan si cambia la red o sales de la app",
            "Escáner de ISBN: si Google aún lo está preparando, avisa y se puede volver a probar; zoom automático",
        )),
        Entry("1.0.51", "21 nov 2026", listOf(
            "Tocar una portada abre siempre la ficha del libro, también en «Seguir leyendo»; desde la ficha, Seguir leyendo o Empezar a leer",
            "La tarjeta de «Seguir leyendo» ya no hace nada al tocarla fuera de la portada y los botones",
        )),
        Entry("1.0.49", "21 nov 2026", listOf(
            "RAE y Wikipedia: ya se puede bajar por la página dentro del diccionario (botón ✕ para cerrar)",
            "Estadísticas: arreglado el texto de «Hoy» y las cifras del informe; «Compartir» solo sale si hay lecturas",
        )),
        Entry("1.0.48", "21 nov 2026", listOf(
            "Tocar un libro de «Seguir leyendo» lo abre para seguir leyendo; mantener pulsado, su menú",
            "Estanterías por género que se forman solas, además de las tuyas: elige Mis estanterías, Por género o Todas",
            "El aspecto de la biblioteca y de las estanterías (libros por fila, madera, tamaño) se cambia en Ajustes → Apariencia",
        )),
        Entry("1.0.47", "21 nov 2026", listOf(
            "Valorar ya no mueve nada: sin botón «Quitar» junto a las estrellas; la nota media (por aspectos) siempre visible",
            "Para quitar una nota: menú del libro → Más opciones → Reiniciar valoración",
            "Menú del libro más ordenado: acciones principales arriba, estado con un toque y el resto en «Más opciones»",
        )),
        Entry("1.0.46", "21 nov 2026", listOf(
            "Compartir una cita como imagen para Instagram: cuatro diseños (Portada, Papel, Subrayado y Noir)",
            "Formato historia (9:16) o publicación (4:5), con vista previa antes de compartir",
            "Guardar la imagen en la galería (Imágenes › Noir Books)",
        )),
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
