package com.noirbooks.app.data

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Copia de seguridad en Google Drive.
 *
 * Seguridad:
 *  - Solo se pide el permiso "drive.appdata": la carpeta privada de la app dentro del Drive del usuario.
 *    La app no puede ver ni tocar ningún otro archivo del usuario, y esa carpeta no aparece en su Drive.
 *  - No hay servidor de Noir Books: la copia va directamente del móvil al Drive de cada persona.
 *  - El inicio de sesión lo hace Google; la app solo recibe un permiso temporal (token) que no se guarda.
 */
object DriveBackup {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_NAME = "noirbooks-backup.zip"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    data class Remote(val id: String, val modified: String, val size: Long)

    sealed class Auth {
        data class Token(val token: String) : Auth()
        /** Hay que mostrar la pantalla de Google para elegir cuenta o aceptar el permiso. */
        data class NeedsUser(val intentSender: IntentSender) : Auth()
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Pide el permiso. Si ya se concedió antes, no muestra nada y devuelve el token. */
    suspend fun authorize(activity: Activity): Auth = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { r -> cont.resume(fromResult(r)) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /** Sin pantalla: solo funciona si el usuario ya vinculó la cuenta. */
    private suspend fun silentToken(ctx: Context): String? = runCatching {
        suspendCancellableCoroutine<String?> { cont ->
            Identity.getAuthorizationClient(ctx).authorize(request())
                .addOnSuccessListener { r -> cont.resume(if (r.hasResolution()) null else r.accessToken) }
                .addOnFailureListener { cont.resume(null) }
        }
    }.getOrNull()

    fun fromResult(r: AuthorizationResult): Auth {
        val pi = r.pendingIntent
        return if (r.hasResolution() && pi != null) Auth.NeedsUser(pi.intentSender)
        else Auth.Token(r.accessToken ?: throw IOException("Google no ha dado permiso"))
    }

    /** Resultado de la pantalla de Google. */
    fun tokenFromIntent(activity: Activity, data: android.content.Intent?): String? = runCatching {
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
    }.getOrNull()

    // ---- API de Drive (REST) ----

    private fun call(req: Request): okhttp3.Response {
        val r = Http.client.newCall(req).execute()
        if (!r.isSuccessful) {
            val code = r.code
            r.close()
            throw IOException(
                when (code) {
                    401, 403 -> "Google no ha dado permiso. Vuelve a vincular la cuenta"
                    404 -> "No se ha encontrado la copia en Drive"
                    507 -> "Tu Google Drive está lleno"
                    else -> "Error de Google Drive ($code)"
                }
            )
        }
        return r
    }

    suspend fun find(token: String): Remote? = withContext(Dispatchers.IO) {
        val url = "$API/files?spaces=appDataFolder&q=" + java.net.URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8") +
            "&fields=files(id,modifiedTime,size)&orderBy=modifiedTime%20desc"
        call(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).use { r ->
            val files = JSONObject(r.body?.string().orEmpty()).optJSONArray("files") ?: return@withContext null
            val f = files.optJSONObject(0) ?: return@withContext null
            Remote(f.optString("id"), f.optString("modifiedTime"), f.optString("size").toLongOrNull() ?: 0L)
        }
    }

    /** Sube [file] a la carpeta privada (subida reanudable, vale para copias grandes con los EPUB). */
    suspend fun upload(token: String, file: File, existingId: String?) = withContext(Dispatchers.IO) {
        val json = "application/json; charset=UTF-8".toMediaType()
        val meta = if (existingId == null) JSONObject().put("name", FILE_NAME).put("parents", org.json.JSONArray().put("appDataFolder"))
        else JSONObject()
        val start = Request.Builder()
            .url(if (existingId == null) "$UPLOAD/files?uploadType=resumable" else "$UPLOAD/files/$existingId?uploadType=resumable")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", "application/zip")
            .header("X-Upload-Content-Length", file.length().toString())
            .method(if (existingId == null) "POST" else "PATCH", meta.toString().toRequestBody(json))
            .build()
        val location = call(start).use { it.header("Location") } ?: throw IOException("Google Drive no aceptó la subida")
        val body = object : RequestBody() {
            override fun contentType() = "application/zip".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) { file.source().use { sink.writeAll(it) } }
        }
        call(Request.Builder().url(location).put(body).build()).close()
    }

    suspend fun download(token: String, id: String, out: File) = withContext(Dispatchers.IO) {
        call(Request.Builder().url("$API/files/$id?alt=media").header("Authorization", "Bearer $token").build()).use { r ->
            out.outputStream().use { o -> r.body?.byteStream()?.copyTo(o, 64 * 1024) ?: throw IOException("Copia vacía") }
        }
    }

    /** Hace la copia y la sube. Devuelve la hora de la copia. */
    suspend fun backupNow(ctx: Context, token: String): Long = lock.withLock {
        val tmp = File(ctx.cacheDir, "drive-backup.zip")
        try {
            tmp.outputStream().use { Backup.write(ctx, it, withBooks = Prefs.driveWithBooks) }
            upload(token, tmp, find(token)?.id)
            val now = System.currentTimeMillis()
            Prefs.driveLastBackup = now
            now
        } finally {
            tmp.delete()
        }
    }

    /**
     * Copia automática: se llama al salir de la app. Solo sube si hay cambios desde la última copia
     * y han pasado al menos 15 minutos. Sin pantallas: si el permiso caducó, no hace nada.
     */
    fun autoBackup(ctx: Context) {
        if (!Prefs.driveLinked) return
        if (Prefs.dataChangedAt <= Prefs.driveLastBackup) return
        if (System.currentTimeMillis() - Prefs.driveLastBackup < 15 * 60_000L) return
        val app = ctx.applicationContext
        io.launch {
            val token = silentToken(app) ?: return@launch
            runCatching { backupNow(app, token) }.onFailure { ErrorLog.note("Copia en Google Drive", it) }
        }
    }
}
