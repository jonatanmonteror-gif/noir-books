package com.noirbooks.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noirbooks.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Actualizaciones desde GitHub Releases, con comprobación de huella SHA-256 antes de instalar. */
object Updater {
    private const val REPO = "jonatanmonteror-gif/noir-books"

    data class Release(val versionCode: Int, val name: String, val apkUrl: String, val shaUrl: String?)

    val currentCode: Int get() = BuildConfig.VERSION_CODE

    /**
     * Lee la versión más reciente siguiendo la redirección de github.com/…/releases/latest
     * (lección de Noir+: la API de GitHub limita a 60 consultas por hora).
     */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val client = Http.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            val req = Request.Builder().url("https://github.com/$REPO/releases/latest").head().header("User-Agent", Http.UA).build()
            val location = client.newCall(req).execute().use { it.header("Location") } ?: return@runCatching null
            val tag = location.substringAfterLast("/tag/", "").ifEmpty { return@runCatching null }
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return@runCatching null
            val base = "https://github.com/$REPO/releases/download/$tag"
            Release(code, "Noir Books 1.0.$code", "$base/NoirBooks.apk", "$base/NoirBooks.apk.sha256")
        }.getOrNull()
    }

    /** Descarga el APK a la caché y verifica la huella. Devuelve el archivo listo para instalar. */
    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "NoirBooks-${rel.versionCode}.apk")
        val req = Request.Builder().url(rel.apkUrl).header("User-Agent", Http.UA).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Error ${r.code} al descargar")
            val body = r.body ?: throw IOException("Descarga vacía")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        rel.shaUrl?.let { url ->
            val expected = Http.get(url).trim().take(64).lowercase()
            val actual = sha256(file)
            if (expected.length == 64 && expected != actual) {
                file.delete()
                throw IOException("La huella del archivo no coincide. Descarga cancelada por seguridad.")
            }
        }
        file
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Abre la descarga del APK en el navegador (y al terminar se toca el archivo para instalar).
     * Así la app no necesita el permiso de instalar apps, que hace saltar a Play Protect.
     */
    fun openDownload(ctx: Context, rel: Release): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
