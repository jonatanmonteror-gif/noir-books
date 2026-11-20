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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                FoldSection("Lectura", "Tema, letra, márgenes y brillo para todos los libros") {
                    ReadingControls(showPreview = true)
                }

                FoldSection("Actualizaciones", "Versión ${BuildConfig.VERSION_NAME}") {
                    val status = when (val u = update) {
                        UpdateState.Idle -> "Se comprueba sola al abrir la app."
                        UpdateState.Checking -> "Buscando…"
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
                    if (ready != null) {
                        PillButton("Descargar ${ready.rel.name}", primary = true) {
                            if (!Updater.openDownload(ctx, ready.rel)) AppState.toast("No se pudo abrir el navegador")
                        }
                    } else {
                        PillButton("Buscar actualizaciones", primary = true) { scope.launch { AppState.checkUpdate(ctx, manual = true) } }
                    }
                }

                FoldSection("Registro de errores", run { errVersion; val n = ErrorLog.count(); if (n == 0) "Sin errores" else "$n anotados" }) {
                    Text(
                        "Si algo falla (un libro que no abre, un cierre inesperado), aquí queda anotado sin datos personales. " +
                            "Cópialo o compártelo para arreglarlo.",
                        color = NB.text2, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                FoldSection("Acerca de", "${books.count { !it.inTrash }} libros en la biblioteca") {
                    Text(
                        "Noir Books abre libros EPUB sin protección DRM. Los libros de Kindle y muchos de Kobo llevan DRM y no se pueden abrir; " +
                            "Kobo indica en cada libro si lo lleva.\n\n" +
                            "Los libros se guardan dentro de la app: puedes borrar el archivo original sin perderlos.\n\n" +
                            "Tipografías con licencia SIL Open Font License: Literata, Inter, Atkinson Hyperlegible y Playfair Display.",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun PillButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) NB.text else NB.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (primary) Color.Black else NB.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
