package com.noirbooks.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.Backup
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Prefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ajustes → Copia de seguridad: guardar, restaurar y exportar. */
@Composable
fun BackupSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var withBooks by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<android.net.Uri, Backup.Info>?>(null) }
    var lastBackup by remember { mutableStateOf(Prefs.lastBackupAt) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "ES")) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Guardando la copia…"
            runCatching { Backup.writeTo(ctx, uri) { Backup.write(ctx, it, withBooks) } }
                .onSuccess { Prefs.lastBackupAt = System.currentTimeMillis(); lastBackup = Prefs.lastBackupAt; AppState.toast("Copia guardada") }
                .onFailure { ErrorLog.note("Guardar copia", it); AppState.toast("No se pudo guardar la copia") }
            busy = null
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Exportando…"
            runCatching { Backup.writeTo(ctx, uri) { Backup.exportData(it) } }
                .onSuccess { AppState.toast("Datos exportados") }
                .onFailure { ErrorLog.note("Exportar datos", it); AppState.toast("No se pudo exportar") }
            busy = null
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val info = runCatching { ctx.contentResolver.openInputStream(uri)?.use { Backup.info(it) } }.getOrNull()
            if (info == null) AppState.toast("Ese archivo no es una copia de Noir Books") else pending = uri to info
        }
    }

    Text(
        "Guarda todo (biblioteca, lecturas, estadísticas, citas, palabras y ajustes) en un archivo. " +
            "Guárdalo en Drive, en el ordenador o donde quieras: con él lo recuperas en este u otro móvil.",
        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp,
    )
    Spacer(Modifier.height(8.dp))
    Text(if (lastBackup > 0) "Última copia: ${fmt.format(Date(lastBackup))}" else "Todavía no has hecho ninguna copia",
        color = NB.text3, fontSize = 13.sp)
    Row(
        Modifier.fillMaxWidth().clickable { withBooks = !withBooks }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Incluir los libros (EPUB)", color = NB.text, fontSize = 15.sp)
            Text("Sin ellos la copia ocupa muy poco; al restaurar, cada libro se recupera al volver a importarlo.",
                color = NB.text3, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(withBooks, { withBooks = it }, colors = SwitchDefaults.colors(
            checkedThumbColor = NB.onText, checkedTrackColor = NB.text,
            uncheckedThumbColor = NB.text3, uncheckedTrackColor = NB.surface2, uncheckedBorderColor = NB.line,
        ))
    }
    busy?.let {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            CircularProgressIndicator(Modifier.height(16.dp), color = NB.text, strokeWidth = 2.dp)
            Text("  $it", color = NB.text2, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Guardar copia", primary = true) { if (busy == null) saveLauncher.launch(Backup.suggestedName()) }
        PillButton("Restaurar") { if (busy == null) openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    }
    Spacer(Modifier.height(16.dp))
    Text("Exportar tus datos", color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Text("Libros, lecturas, citas y palabras en tablas (CSV) que se abren con Excel o Google Sheets.",
        color = NB.text3, fontSize = 12.sp, lineHeight = 16.sp)
    Spacer(Modifier.height(8.dp))
    PillButton("Exportar datos") { if (busy == null) exportLauncher.launch(Backup.suggestedName("NoirBooks-datos")) }
    Spacer(Modifier.height(12.dp))
    Text("Copia automática en Google Drive: en la próxima versión.", color = NB.text3, fontSize = 12.sp)

    pending?.let { (uri, info) ->
        AlertDialog(
            onDismissRequest = { pending = null }, containerColor = NB.surface2,
            title = { Text("¿Restaurar esta copia?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Copia del ${fmt.format(Date(info.createdAt))} · ${info.books} libros" +
                        if (info.withBooks) " con sus EPUB" else " (sin los EPUB)", color = NB.text, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Sustituye todo lo que hay ahora en este móvil por lo de la copia. La app se reiniciará.",
                        color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = "Restaurando…"
                        runCatching { ctx.contentResolver.openInputStream(uri)?.use { Backup.restore(ctx, it) } ?: error("No se pudo abrir") }
                            .onSuccess { Backup.restart(ctx) }
                            .onFailure { ErrorLog.note("Restaurar copia", it); AppState.toast(it.message ?: "No se pudo restaurar"); busy = null }
                    }
                }) { Text("Restaurar", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar", color = NB.text2) } },
        )
    }
}
