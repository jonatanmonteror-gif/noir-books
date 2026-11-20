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
import com.noirbooks.app.data.DriveBackup
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

    Spacer(Modifier.height(8.dp))
    DriveBlock(onRestoreFile = { file, info -> pending = android.net.Uri.fromFile(file) to info })

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

/** Copia automática en Google Drive (carpeta privada de la app). */
@Composable
private fun DriveBlock(onRestoreFile: (java.io.File, Backup.Info) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? android.app.Activity
    val scope = rememberCoroutineScope()
    var linked by remember { mutableStateOf(Prefs.driveLinked) }
    var last by remember { mutableStateOf(Prefs.driveLastBackup) }
    var withBooks by remember { mutableStateOf(Prefs.driveWithBooks) }
    var busy by remember { mutableStateOf<String?>(null) }
    var after by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "ES")) }

    suspend fun doBackup(token: String) {
        busy = "Subiendo la copia a Google Drive…"
        runCatching { DriveBackup.backupNow(ctx, token) }
            .onSuccess { last = it; AppState.toast("Copia guardada en Google Drive") }
            .onFailure { ErrorLog.note("Copia en Google Drive", it); AppState.toast(it.message ?: "No se pudo subir la copia") }
        busy = null
    }
    suspend fun doRestore(token: String) {
        busy = "Buscando tu copia en Google Drive…"
        runCatching {
            val remote = DriveBackup.find(token) ?: error("No hay ninguna copia de Noir Books en esta cuenta de Google")
            busy = "Descargando la copia…"
            val f = java.io.File(ctx.cacheDir, "drive-restore.zip")
            DriveBackup.download(token, remote.id, f)
            val info = f.inputStream().use { Backup.info(it) } ?: error("La copia de Drive no se puede leer")
            f to info
        }.onSuccess { (f, info) -> onRestoreFile(f, info) }
            .onFailure { ErrorLog.note("Restaurar desde Google Drive", it); AppState.toast(it.message ?: "No se pudo restaurar") }
        busy = null
    }

    val consent = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = activity?.let { DriveBackup.tokenFromIntent(it, r.data) }
        val next = after
        after = null
        if (token == null) { AppState.toast("No se ha vinculado Google Drive"); return@rememberLauncherForActivityResult }
        Prefs.driveLinked = true; linked = true
        if (next != null) scope.launch { next(token) }
    }

    /** Consigue el permiso (con la pantalla de Google si hace falta) y sigue con [next]. */
    fun withToken(next: suspend (String) -> Unit) {
        val act = activity ?: return
        scope.launch {
            runCatching { DriveBackup.authorize(act) }
                .onSuccess { a ->
                    when (a) {
                        is DriveBackup.Auth.Token -> { Prefs.driveLinked = true; linked = true; next(a.token) }
                        is DriveBackup.Auth.NeedsUser -> {
                            after = next
                            consent.launch(androidx.activity.result.IntentSenderRequest.Builder(a.intentSender).build())
                        }
                    }
                }
                .onFailure {
                    ErrorLog.note("Vincular Google Drive", it)
                    AppState.toast("Google Drive todavía no está disponible en esta versión de la app")
                }
        }
    }

    Text("Google Drive", color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Text(
        "Copia automática en la carpeta privada de Noir Books dentro de tu Drive. La app no puede ver el resto de tus archivos " +
            "y la copia no pasa por ningún servidor nuestro.",
        color = NB.text3, fontSize = 12.sp, lineHeight = 16.sp,
    )
    Spacer(Modifier.height(8.dp))
    busy?.let {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            CircularProgressIndicator(Modifier.height(16.dp), color = NB.text, strokeWidth = 2.dp)
            Text("  $it", color = NB.text2, fontSize = 14.sp)
        }
    }
    if (!linked) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Vincular Google Drive", primary = true) { if (busy == null) withToken { doBackup(it) } }
            PillButton("Restaurar de Drive") { if (busy == null) withToken { doRestore(it) } }
        }
    } else {
        Text(if (last > 0) "Última copia en Drive: ${fmt.format(Date(last))}" else "Vinculado. Aún no hay copia en Drive",
            color = NB.text2, fontSize = 13.sp)
        Text("Se copia sola al salir de la app si ha habido cambios (como mucho cada 15 minutos).", color = NB.text3, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().clickable { withBooks = !withBooks; Prefs.driveWithBooks = withBooks }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Incluir los EPUB en Drive", color = NB.text, fontSize = 15.sp)
                Text("Ocupan espacio de tu Drive; sin ellos, cada libro se recupera al volver a importarlo.",
                    color = NB.text3, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Switch(withBooks, { withBooks = it; Prefs.driveWithBooks = it }, colors = SwitchDefaults.colors(
                checkedThumbColor = NB.onText, checkedTrackColor = NB.text,
                uncheckedThumbColor = NB.text3, uncheckedTrackColor = NB.surface2, uncheckedBorderColor = NB.line,
            ))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Copiar ahora", primary = true) { if (busy == null) withToken { doBackup(it) } }
            PillButton("Restaurar de Drive") { if (busy == null) withToken { doRestore(it) } }
        }
        Spacer(Modifier.height(6.dp))
        Text("Desvincular", color = NB.text3, fontSize = 13.sp,
            modifier = Modifier.clickable { Prefs.driveLinked = false; linked = false
                AppState.toast("Desvinculado. Puedes quitar el permiso en tu cuenta de Google → Seguridad → Apps") }
                .padding(vertical = 6.dp))
    }
}
