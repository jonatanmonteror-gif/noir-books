package com.noirbooks.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.BuildConfig
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by AppState.update
    val errVersion by ErrorLog.version
    val books by Library.books

    Column(Modifier.fillMaxSize().background(NB.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = NB.text) }
            Text("Ajustes", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = NB.text)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                ProUpsellCard()
                val appearance by com.noirbooks.app.data.Prefs.appearanceState
                FoldSection("Apariencia", when (appearance) { "light" -> "Clara"; "system" -> "Como el sistema"; else -> "Oscura" } + " · biblioteca y estanterías") {
                    Text("Colores de la app (el libro tiene sus propios temas en Lectura).", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark" to "Oscura", "light" to "Clara", "system" to "Sistema").forEach { (k, l) ->
                            Chip(l, appearance == k) { com.noirbooks.app.data.Prefs.setAppearance(k) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    val uiScale by com.noirbooks.app.data.Prefs.uiScale
                    Text("Tamaño de la app", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.9f to "Pequeño", 0.96f to "Medio", 1f to "Normal").forEach { (v, l) ->
                            Chip(l, kotlin.math.abs(uiScale - v) < 0.01f) { com.noirbooks.app.data.Prefs.setUiScale(v) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    LibraryLookSettings()

                }

                FoldSection("Inicio", "Qué secciones se ven y en qué orden") { HomeSectionsSettings() }

                FoldSection("Privacidad", if (com.noirbooks.app.data.Prefs.appLock) "Bloqueo con huella activado" else "Bloqueo con huella") { LockSettings() }

                FoldSection("Lectura", "Tema, letra, márgenes y brillo para todos los libros") {
                    ReadingControls(showPreview = true)
                }

                val ratingMode by com.noirbooks.app.data.Prefs.ratingMode
                val praise by com.noirbooks.app.data.Prefs.praise
                FoldSection("Diario", (if (ratingMode == "aspects") "Valoración por aspectos" else "Valoración con estrellas") +
                    if (praise) " · con elogios" else "") {
                    Text("Valoraciones", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Básica", ratingMode != "aspects") { com.noirbooks.app.data.Prefs.setRatingMode("basic") }
                        Chip("Por aspectos", ratingMode == "aspects") { com.noirbooks.app.data.Prefs.setRatingMode("aspects") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (ratingMode == "aspects") "Valoras trama, personajes y prosa; la nota del libro es la media."
                        else "Una nota de media estrella a cinco estrellas.", color = NB.text3, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Elogios", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Activados", praise) { com.noirbooks.app.data.Prefs.setPraise(true) }
                        Chip("Desactivados", !praise) { com.noirbooks.app.data.Prefs.setPraise(false) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Un aviso discreto al terminar un libro o un mes, solo cuando hay algo que destacar.", color = NB.text3, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    var resurface by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.noirbooks.app.data.Prefs.resurface) }
                    Text("Retomar abandonados", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Proponer", resurface) { resurface = true; com.noirbooks.app.data.Prefs.resurface = true }
                        Chip("No", !resurface) { resurface = false; com.noirbooks.app.data.Prefs.resurface = false }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Pasados 3 meses, una tarjeta discreta en Inicio te pregunta si quieres volver a un libro que dejaste.", color = NB.text3, fontSize = 12.sp)
                }

                if (BuildConfig.SELF_UPDATE) {
                FoldSection("Actualizaciones", "Versión ${BuildConfig.VERSION_NAME}") {
                        val status = when (val u = update) {
                            UpdateState.Idle -> "Se comprueba sola al abrir la app."
                            UpdateState.Checking -> "Buscando…"
                            is UpdateState.Available -> "Hay una versión nueva: ${u.rel.name}."
                            is UpdateState.Downloading -> "Descargando ${u.name}… ${(u.progress * 100).toInt()} %"
                            is UpdateState.Ready -> "${u.rel.name} descargada y verificada (SHA-256)."
                            is UpdateState.UpToDate -> "Tienes la última versión."
                            is UpdateState.Failed -> u.reason
                        }
                        Text(status, color = NB.text2, fontSize = 14.sp)
                        (update as? UpdateState.Downloading)?.let {
                            Spacer(Modifier.height(10.dp))
                            DownloadBar(it.progress)
                        }
                        Spacer(Modifier.height(12.dp))
                        val ready = update as? UpdateState.Ready
                        val avail = update as? UpdateState.Available
                        if (ready != null) {
                            PillButton("Instalar ${ready.rel.name}", primary = true) {
                                if (!Updater.install(ctx, ready.file)) AppState.toast("Permite instalar apps desde Noir Books y vuelve a pulsar Instalar")
                            }
                        } else if (avail != null) {
                            PillButton("Descargar e instalar ${avail.rel.name}", primary = true) { AppState.downloadUpdate(ctx) }
                        } else if (update !is UpdateState.Downloading) {
                            PillButton("Buscar actualizaciones", primary = true) { scope.launch { AppState.checkUpdate(ctx, manual = true) } }
                        }
                    }
                }

                FoldSection("Copia de seguridad", "Guardar, restaurar y exportar tus datos") {
                    BackupSection()
                }

                val noCover = books.count { !it.hasCover && !it.inTrash }
                FoldSection("Portadas", if (noCover == 0) "Todos tus libros tienen portada" else "$noCover libros sin portada") {
                    Text("Busca en Google Books y Open Library la portada de los libros que no tienen: primero por ISBN y, si no, por título y autor. " +
                        "Las que ya tienes no se tocan.", color = NB.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    ButtonRow { PillButton("Buscar portadas que faltan", primary = true) { AppState.fetchCovers() } }
                }

                FoldSection("Novedades", "Qué ha cambiado en cada versión") {
                    ChangelogList()
                }

                if (com.noirbooks.app.data.Edition.isDev) FoldSection("Registro de errores", run { errVersion; val n = ErrorLog.count(); if (n == 0) "Sin errores" else "$n anotados" }) {
                    Text(
                        "Si algo falla (un libro que no abre, un cierre inesperado), aquí queda anotado sin datos personales. " +
                            "Cópialo o compártelo para arreglarlo.",
                        color = NB.text2, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    ButtonRow {
                        PillButton("Copiar") {
                            copyText(ctx, "Registro Noir Books", ErrorLog.read().ifEmpty { "Sin errores" }); AppState.toast("Registro copiado")
                        }
                        PillButton("Compartir") { shareText(ctx, "Registro Noir Books", ErrorLog.read().ifEmpty { "Sin errores" }) }
                        PillButton("Borrar") { ErrorLog.clear() }
                    }
                }

                val trash = books.filter { it.inTrash }
                FoldSection("Papelera", if (trash.isEmpty()) "Vacía" else "${trash.size} libros · se vacía sola a los 30 días") {
                    if (trash.isEmpty()) {
                        Text("Los libros que quites de la biblioteca se guardan aquí 30 días, con su progreso y sus citas.", color = NB.text2, fontSize = 14.sp)
                    }
                    trash.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BookCover(b, Modifier.width(34.dp), titleSize = 7)
                            Spacer(Modifier.width(12.dp))
                            Text(b.title, color = NB.text, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { com.noirbooks.app.data.Library.restore(b.id) } }) { Text("Recuperar", color = NB.text) }
                            TextButton(onClick = { scope.launch { com.noirbooks.app.data.Library.deleteForever(b.id) } }) { Text("Borrar", color = Color(0xFFFF6B6B)) }
                        }
                    }
                }

                // TEMPORAL (pruebas): elegir cómo se ve la app. Se quita antes de publicar.
                FoldSection("Vista de la app · temporal", when (com.noirbooks.app.data.Edition.mode.value) {
                    "free" -> "Usuario nuevo (gratis)"; "pro" -> "Usuario Pro"; else -> "Desarrollador"
                }) {
                    Text("Solo para probar: ve la app como la verá cada tipo de usuario. Tus libros y datos no cambian. " +
                        "Esta sección se quitará antes de publicar.", color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    val mode by com.noirbooks.app.data.Edition.mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ViewOption("Usuario nuevo", "Primera instalación, sin Pro: sale el asistente y las funciones Pro con candado", mode == "free") {
                            com.noirbooks.app.data.Edition.set("free"); AppState.welcome.value = true
                        }
                        ViewOption("Usuario Pro", "Todo desbloqueado, sin ajustes de desarrollador", mode == "pro") {
                            com.noirbooks.app.data.Edition.set("pro")
                        }
                        ViewOption("Desarrollador", "Todo, más registro de errores y herramientas de prueba", mode == "dev") {
                            com.noirbooks.app.data.Edition.set("dev")
                        }
                    }
                    if (mode == "dev") {
                        Spacer(Modifier.height(14.dp))
                        PillButton("Ver el asistente de bienvenida") { AppState.welcome.value = true }
                        Spacer(Modifier.height(18.dp))
                        AiTestSettings()
                    }
                }

                FoldSection("Acerca de", "${books.count { !it.inTrash }} libros en la biblioteca") {
                    Text(
                        "Noir Books abre libros EPUB sin protección DRM. Los libros de Kindle y muchos de Kobo llevan DRM y no se pueden abrir; " +
                            "Kobo indica en cada libro si lo lleva.\n\n" +
                            "Los libros se guardan dentro de la app: puedes borrar el archivo original sin perderlos.\n\n" +
                            "Tipografías con licencia SIL Open Font License: Literata, Inter, Atkinson Hyperlegible y Playfair Display.",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sin cuenta, sin publicidad y sin rastreadores: tus datos se quedan en el móvil (y en tu Drive si lo activas).",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    val docs = "https://github.com/jonatanmonteror-gif/noir-books/blob/main/docs/"
                    fun openUrl(u: String) = runCatching {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)))
                    }.onFailure { AppState.toast("No se pudo abrir el navegador") }
                    ButtonRow {
                        PillButton("Privacidad") { openUrl(docs + "privacidad.md") }
                        PillButton("Condiciones de uso") { openUrl(docs + "condiciones.md") }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** Fila de botones que pasa a la línea siguiente si no caben (nunca parte un botón en dos). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun PillButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) NB.text else NB.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (primary) NB.onText else NB.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** Lista de novedades por versión; la instalada aparece marcada. */
@Composable
fun ChangelogList() {
    com.noirbooks.app.data.Changelog.entries.forEachIndexed { i, e ->
        if (i > 0) Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Versión ${e.version}", color = NB.text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            if (e.version == BuildConfig.VERSION_NAME) {
                Spacer(Modifier.width(8.dp))
                Text("la tuya", color = NB.text3, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(e.date, color = NB.text3, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        e.items.forEach { t ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("·  ", color = NB.text3, fontSize = 14.sp)
                Text(t, color = NB.text2, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}

/** Al abrir una versión nueva por primera vez, se enseñan sus novedades una sola vez. */
@Composable
fun WhatsNewPrompt() {
    var show by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.noirbooks.app.data.Prefs.seenVersionCode in 1 until BuildConfig.VERSION_CODE
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (com.noirbooks.app.data.Prefs.seenVersionCode != BuildConfig.VERSION_CODE && !show)
            com.noirbooks.app.data.Prefs.seenVersionCode = BuildConfig.VERSION_CODE
    }
    if (!show) return
    val latest = com.noirbooks.app.data.Changelog.entries.firstOrNull()
    fun close() { com.noirbooks.app.data.Prefs.seenVersionCode = BuildConfig.VERSION_CODE; show = false }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { close() }, containerColor = NB.surface2,
        title = { Text("Actualizada a la ${BuildConfig.VERSION_NAME}", fontFamily = Inter, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
        text = {
            Column {
                latest?.items?.forEach { t ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("·  ", color = NB.text3, fontSize = 14.sp)
                        Text(t, color = NB.text2, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Todas las novedades: Ajustes → Novedades.", color = NB.text3, fontSize = 12.sp)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { close() }) { Text("Vale", color = NB.text) } },
    )
}

@Composable
private fun ViewOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) NB.text else NB.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(title, color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = NB.text3, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** Pruebas de la IA: clave de OpenRouter, modelo, internet y lo gastado (para calcular el precio). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AiTestSettings() {
    val P = com.noirbooks.app.data.Prefs
    var key by remember { mutableStateOf(P.aiKey) }
    var model by remember { mutableStateOf(P.aiModel) }
    var web by remember { mutableStateOf(P.aiWeb) }
    var calls by remember { mutableStateOf(P.aiCalls) }
    var spent by remember { mutableStateOf(P.aiSpentUsd) }
    Text("IA · pruebas de coste", color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    androidx.compose.material3.OutlinedTextField(
        key, { key = it.trim(); P.aiKey = key }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Clave de OpenRouter (sk-or-…)") },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text,
            focusedTextColor = NB.text, unfocusedTextColor = NB.text, focusedLabelColor = NB.text2, unfocusedLabelColor = NB.text3),
    )
    Text("Solo se guarda en este móvil y no va en las copias de seguridad.", color = NB.text3, fontSize = 11.sp)
    Spacer(Modifier.height(10.dp))
    Text("Modelo", color = NB.text2, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        com.noirbooks.app.data.AiRecommender.MODELS.forEach { (id, name) -> Chip(name, model == id) { model = id; P.aiModel = id } }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Con internet", web) { web = true; P.aiWeb = true }
        Chip("Sin internet", !web) { web = false; P.aiWeb = false }
    }
    Text("Con internet conoce lo recién salido, pero cada consulta cuesta algo más.", color = NB.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(10.dp))
    Text(if (calls == 0) "Aún no has hecho consultas." else
        "$calls consultas · \$${"%.4f".format(spent)} en total · \$${"%.4f".format(spent / calls)} de media",
        color = NB.text2, fontSize = 13.sp)
    if (calls > 0) Text("Poner a cero", color = NB.text3, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { P.aiCalls = 0; P.aiSpentUsd = 0.0; calls = 0; spent = 0.0 }.padding(vertical = 6.dp))
}
