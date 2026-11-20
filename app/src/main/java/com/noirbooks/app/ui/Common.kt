package com.noirbooks.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import coil.compose.AsyncImage
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.ErrorLog
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Updater

/** Portada del libro; si el EPUB no trae, se dibuja una sobria con el título. */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, titleSize: Int = 15) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(NB.surface2)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape),
    ) {
        if (book.hasCover) {
            AsyncImage(
                model = Library.coverOf(book.id),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF26262A), Color(0xFF141416)))
                ).padding(12.dp),
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        book.title,
                        fontFamily = Playfair,
                        fontSize = titleSize.sp,
                        lineHeight = (titleSize * 1.2f).sp,
                        color = NB.text,
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.author.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(24.dp).height(1.dp).background(NB.silver.copy(alpha = 0.6f)))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            book.author.uppercase(),
                            fontSize = (titleSize * 0.6f).sp,
                            letterSpacing = 1.5.sp,
                            color = NB.silver,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinProgress(value: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(2.dp).background(NB.text))
    }
}

/** Sección plegable de Ajustes (se despliega al pulsar). */
@Composable
fun FoldSection(title: String, subtitle: String? = null, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NB.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = NB.text)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = NB.text3, fontFamily = Inter)
            }
            Icon(
                Icons.Filled.KeyboardArrowDown, null, tint = NB.text2,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) { content() }
        }
    }
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareText(ctx: Context, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(Intent.createChooser(i, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Aviso con la versión nueva ya descargada y verificada. */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    val st by AppState.update
    val dismissed by AppState.updateDismissed
    val ready = st as? UpdateState.Ready ?: return
    if (dismissed == ready.rel.versionCode) return
    AlertDialog(
        onDismissRequest = { AppState.updateDismissed.intValue = ready.rel.versionCode },
        containerColor = NB.surface2,
        title = { Text("Nueva versión", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Hay una versión nueva: ${ready.rel.name}. Pulsa Descargar: se abrirá el navegador y, al terminar, toca el archivo para instalarla. Tus libros y tu progreso se mantienen.",
                fontFamily = Inter, color = NB.text2,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (!Updater.openDownload(ctx, ready.rel)) AppState.toast("No se pudo abrir el navegador")
                AppState.updateDismissed.intValue = ready.rel.versionCode
            }) { Text("Descargar", fontWeight = FontWeight.SemiBold, color = NB.text) }
        },
        dismissButton = {
            TextButton(onClick = { AppState.updateDismissed.intValue = ready.rel.versionCode }) {
                Text("Más tarde", color = NB.text2)
            }
        },
    )
}

/** Si la app se cerró sola la última vez, ofrece copiar o compartir el informe. */
@Composable
fun CrashReportPrompt() {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(ErrorLog.lastCrash(Prefs.crashSeenAt)) }
    val c = crash ?: return
    fun close() {
        Prefs.crashSeenAt = c.first
        crash = null
    }
    AlertDialog(
        onDismissRequest = { close() },
        containerColor = NB.surface2,
        title = { Text("La app se cerró inesperadamente", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Este es el informe (sin datos personales). Cópialo o compártelo para arreglarlo.", color = NB.text2, fontFamily = Inter)
                Spacer(Modifier.height(10.dp))
                Text(
                    c.second,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NB.text2,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { copyText(ctx, "Informe Noir Books", c.second); AppState.toast("Informe copiado") }) {
                    Text("Copiar", color = NB.text)
                }
                TextButton(onClick = { shareText(ctx, "Informe Noir Books", c.second) }) { Text("Compartir", color = NB.text) }
                TextButton(onClick = { close() }) { Text("Cerrar", color = NB.text2) }
            }
        },
    )
}

@Composable
fun DownloadBar(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = NB.text,
        trackColor = NB.line,
    )
}
