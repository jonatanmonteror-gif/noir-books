package com.noirbooks.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El servidor respondió con error ${r.code}")
            r.body?.string() ?: ""
        }
    }

    /** POST con JSON y cabeceras extra. Devuelve el cuerpo; si el servidor da error, lanza con su mensaje. */
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), body))
            .build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("Error ${r.code}: " + text.take(300))
            text
        }
    }
}
