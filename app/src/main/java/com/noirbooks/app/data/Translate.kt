package com.noirbooks.app.data

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Traducción en el propio móvil (Google ML Kit). Cada idioma se descarga una vez (unos 30 MB). */
object Translate {
    suspend fun translate(text: String, from: String, to: String, onStatus: (String) -> Unit): String {
        val src = TranslateLanguage.fromLanguageTag(from) ?: throw IllegalArgumentException("Este idioma no se puede traducir")
        val dst = TranslateLanguage.fromLanguageTag(to) ?: throw IllegalArgumentException("Ese idioma no se puede traducir")
        val t = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build())
        try {
            onStatus("Preparando el idioma (la primera vez se descarga, unos 30 MB)…")
            suspendCancellableCoroutine<Unit> { cont ->
                t.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(Exception("No se pudo descargar el idioma. Revisa la conexión.")) }
            }
            onStatus("Traduciendo…")
            return suspendCancellableCoroutine { cont ->
                t.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(Exception("No se pudo traducir")) }
            }
        } finally {
            t.close()
        }
    }
}
