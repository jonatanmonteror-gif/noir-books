package com.noirbooks.app.ui

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Close
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
    Box(modifier.height(2.dp).clip(RoundedCornerShape(1.dp)).background(NB.line)) {
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
    val rel = when (st) {
        is UpdateState.Available -> (st as UpdateState.Available).rel
        is UpdateState.Ready -> (st as UpdateState.Ready).rel
        else -> null
    }
    val downloading = st as? UpdateState.Downloading
    if (rel == null && downloading == null) return
    if (rel != null && dismissed == rel.versionCode) return
    if (downloading != null && dismissed == -1) return
    AlertDialog(
        onDismissRequest = { AppState.updateDismissed.intValue = rel?.versionCode ?: -1 },
        containerColor = NB.surface2,
        title = { Text("Nueva versión", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    when {
                        downloading != null -> "Descargando ${downloading.name}…"
                        st is UpdateState.Ready -> "${rel?.name} descargada y verificada. Pulsa Instalar; tus libros y tu progreso se mantienen."
                        else -> "Hay una versión nueva: ${rel?.name}. Se descarga aquí mismo, se comprueba que es la auténtica y se instala. Tus libros y tu progreso se mantienen."
                    },
                    fontFamily = Inter, color = NB.text2,
                )
                if (downloading != null) {
                    Spacer(Modifier.height(12.dp))
                    DownloadBar(downloading.progress)
                }
            }
        },
        confirmButton = {
            when {
                downloading != null -> TextButton(onClick = { AppState.updateDismissed.intValue = -1 }) { Text("Ocultar", color = NB.text2) }
                st is UpdateState.Ready -> TextButton(onClick = {
                    if (!Updater.install(ctx, (st as UpdateState.Ready).file)) AppState.toast("Permite instalar apps desde Noir Books y vuelve a pulsar Instalar")
                }) { Text("Instalar", fontWeight = FontWeight.SemiBold, color = NB.text) }
                else -> TextButton(onClick = { AppState.downloadUpdate(ctx) }) { Text("Descargar e instalar", fontWeight = FontWeight.SemiBold, color = NB.text) }
            }
        },
        dismissButton = {
            if (downloading == null) TextButton(onClick = { AppState.updateDismissed.intValue = rel?.versionCode ?: -1 }) {
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
                        .background(NB.surface)
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

/** Alto de la barra de navegación del móvil, leído de la ventana principal (en los diálogos a veces llega a 0). */
@Composable
fun activityNavBarHeight(): androidx.compose.ui.unit.Dp {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    return with(density) { px.toDp() }
}

/** Alto de la barra de estado, leído de la ventana principal. */
@Composable
fun activityStatusBarHeight(): androidx.compose.ui.unit.Dp {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
    return with(density) { px.toDp() }
}

/**
 * Llamar dentro del contenido de un Dialog a pantalla completa: obliga a su ventana a ocupar
 * toda la pantalla (de arriba abajo, por detrás de las barras). Sin esto, algunos móviles
 * (Samsung) colocan la ventana por debajo de la barra de estado y el final queda fuera de pantalla.
 * Después, el contenido se separa de las barras con activityStatusBarHeight()/activityNavBarHeight().
 */
@Composable
fun FullScreenDialogWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        window.setGravity(android.view.Gravity.TOP or android.view.Gravity.START)
        val a = window.attributes
        a.x = 0; a.y = 0
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            a.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = a
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        run { window.statusBarColor = android.graphics.Color.TRANSPARENT; window.navigationBarColor = android.graphics.Color.TRANSPARENT }
    }
}

// ---------------- Pro ----------------

/** Etiqueta «PRO» pequeña. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text("PRO", color = NB.onText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(NB.text).padding(horizontal = 5.dp, vertical = 1.dp))
}

/**
 * Sección de ajustes que es Pro. Sin Pro se puede desplegar para ver qué trae, pero se ve apagada
 * y no se puede tocar: cualquier toque abre la pantalla de Pro.
 */
@Composable
fun ProFoldSection(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    if (com.noirbooks.app.data.Edition.isPro) { FoldSection(title, subtitle, content = content); return }
    FoldSection("$title  ·  Pro", subtitle) {
        Box {
            Column(Modifier.alpha(0.35f)) { content() }
            // Capa encima que se come los toques: se ve, pero no se cambia
            Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            ) { AppState.showPro.value = true })
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Desbloquear con Pro", primary = true) { AppState.showPro.value = true }
    }
}

/** Precio de Pro (de momento solo se enseña: la compra llegará con Google Play). */
object ProPrice {
    const val NOW = "9,90 €"
    const val BEFORE = "23,90 €"
    const val OFF = "-58 %"
}

/** Tarjeta «Pásate a Pro» (arriba en Ajustes, solo sin Pro). */
@Composable
fun ProUpsellCard() {
    if (com.noirbooks.app.data.Edition.isPro) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(NB.surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tienes Noir Books Pro", color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ProBadge()
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(NB.text)
            .clickable { AppState.showPro.value = true }.padding(18.dp),
    ) {
        Text("¿Te pasas a Pro?", fontFamily = Playfair, fontSize = 22.sp, color = NB.onText)
        Spacer(Modifier.height(4.dp))
        Text("Más temas, tema automático y el pie de página a tu gusto.", color = NB.onText.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ProPrice.NOW, color = NB.onText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(" /año  ", color = NB.onText.copy(alpha = 0.75f), fontSize = 13.sp)
            Text(ProPrice.BEFORE, color = NB.onText.copy(alpha = 0.55f), fontSize = 13.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
            Spacer(Modifier.weight(1f))
            Text("Ver Pro ›", color = NB.onText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pantalla de Pro a pantalla completa: qué trae, precio con descuento y botón de compra (de momento sin cobrar). Cabe sin desplazar. */
@Composable
fun ProDialog(onDismiss: () -> Unit) {
    val navH = activityNavBarHeight()
    val statusH = activityStatusBarHeight()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullScreenDialogWindow()
        Box(Modifier.fillMaxSize().background(NB.bg).padding(top = statusH + 4.dp, bottom = navH.coerceAtLeast(20.dp) + 6.dp)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Arriba: marca y frase (el espacio libre se reparte para que todo quepa sin desplazar)
                Spacer(Modifier.weight(0.6f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Noir ", fontFamily = Playfair, fontSize = 34.sp, color = NB.text)
                    Text("BOOKS  ", fontSize = 11.sp, letterSpacing = 4.sp, color = NB.text3)
                    ProBadge()
                }
                Spacer(Modifier.height(6.dp))
                Text("Tu lectura, exactamente a tu gusto.", color = NB.text2, fontSize = 15.sp)
                Spacer(Modifier.weight(0.5f))
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    (com.noirbooks.app.data.Edition.FEATURES + "Y todo lo nuevo que llegue a Pro").forEach { f ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("✓", color = NB.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Text(f, color = NB.text, fontSize = 14.sp, lineHeight = 19.sp)
                        }
                    }
                }
                Spacer(Modifier.weight(0.5f))
                // Precio: oferta frente al precio normal
                Text("OFERTA DE LANZAMIENTO  ${ProPrice.OFF}", fontSize = 11.sp, letterSpacing = 1.2.sp, color = NB.text2, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ProPrice.BEFORE, color = NB.text3, fontSize = 16.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    Spacer(Modifier.width(10.dp))
                    Text(ProPrice.NOW, color = NB.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(" /año", color = NB.text2, fontSize = 14.sp)
                }
                Text("Menos de 1 € al mes", color = NB.text3, fontSize = 12.sp)
                Spacer(Modifier.weight(0.5f))
                Box(
                    Modifier.widthIn(max = 460.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(NB.text)
                        .clickable { AppState.toast("La compra estará disponible cuando la app esté en Google Play") },
                    contentAlignment = Alignment.Center,
                ) { Text("Pasarme a Pro · ${ProPrice.NOW}/año", color = NB.onText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(6.dp))
                Text("Se renueva cada año. Cancela cuando quieras desde Google Play.", color = NB.text3, fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Restaurar compra", color = NB.text2, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        AppState.toast("La compra estará disponible cuando la app esté en Google Play")
                    }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            // La X no ocupa fila: va encima, en la esquina
            androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) {
                Icon(Icons.Filled.Close, "Cerrar", tint = NB.text2)
            }
        }
    }
}
