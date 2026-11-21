package com.noirbooks.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.CHARS_PER_PRINTED_PAGE
import com.noirbooks.app.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diseños de estantería: fondo del hueco, balda y color de los rótulos. */
data class ShelfStyle(
    val key: String,
    val label: String,
    val back: List<Color>?,      // fondo del hueco (null = sin fondo)
    val plank: List<Color>,      // balda, de arriba abajo
    val edge: Color,             // canto inferior de la balda
    val onBack: Color?,          // color del texto sobre el fondo (null = el del tema)
)

val SHELF_STYLES = listOf(
    ShelfStyle("noir", "Noir", null, listOf(Color(0xFF8A8A8E), Color(0xFF5A5A5E)), Color(0x33000000), null),
    ShelfStyle("walnut", "Nogal", listOf(Color(0xFF2B1B12), Color(0xFF3B271A)),
        listOf(Color(0xFF7A5034), Color(0xFF5C3A24)), Color(0xFF2E1D12), Color(0xFFE9DCCB)),
    ShelfStyle("oak", "Roble", listOf(Color(0xFFCDAE84), Color(0xFFDDC29B)),
        listOf(Color(0xFFC0935F), Color(0xFFA67A48)), Color(0xFF7D5A33), Color(0xFF3A2814)),
    ShelfStyle("white", "Blanca", listOf(Color(0xFFE9E7E2), Color(0xFFF4F2EE)),
        listOf(Color(0xFFFFFFFF), Color(0xFFDAD7D0)), Color(0xFFBFBBB2), Color(0xFF1C1C1E)),
)

fun shelfStyle(key: String) = SHELF_STYLES.firstOrNull { it.key == key } ?: SHELF_STYLES[1]

/** Color medio de la portada, para pintar el lomo (se calcula una vez por libro). */
private val spineColors = mutableStateMapOf<String, Color>()

private val FALLBACK = listOf(
    Color(0xFF2C3E50), Color(0xFF6B2D2D), Color(0xFF2F4A3A), Color(0xFF4A3B2A),
    Color(0xFF3A3550), Color(0xFF1F1F22), Color(0xFF5A4632), Color(0xFF2D4654),
)

private fun stableHash(s: String) = s.fold(7) { h, c -> h * 31 + c.code } and 0x7fffffff

/** Lomo de un libro, de pie: color de su portada, título en vertical. Ancho según el número de páginas. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookSpine(b: Book, maxHeight: Dp, onClick: () -> Unit, onLongClick: () -> Unit, widthScale: Float = 1f) {
    LaunchedEffect(b.id, b.hasCover) {
        if (b.id in spineColors) return@LaunchedEffect
        val c = withContext(Dispatchers.IO) { if (b.hasCover) averageColor(Library.coverOf(b.id)) else null }
        spineColors[b.id] = c ?: FALLBACK[stableHash(b.id) % FALLBACK.size]
    }
    val color = spineColors[b.id] ?: FALLBACK[stableHash(b.id) % FALLBACK.size]
    val pages = if (b.pages > 0) b.pages else b.totalChars / CHARS_PER_PRINTED_PAGE
    val width = (16 + pages / 22).coerceIn(22, 44).dp * widthScale.coerceIn(0.8f, 1.25f)
    val h = maxHeight * (0.84f + (stableHash(b.title) % 16) / 100f)
    val ink = if (color.luminance() > 0.45f) Color(0xFF1A1A1A) else Color(0xFFF3EEE6)
    Box(
        Modifier.width(width).height(h)
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
            .background(Brush.horizontalGradient(listOf(color.darken(0.75f), color, color, color.darken(0.8f))))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // Filetes de adorno arriba y abajo, como en los lomos de tapa dura
        Column(Modifier.fillMaxSize().padding(vertical = 7.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.35f)))
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.2f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.2f)))
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.35f)))
        }
        Box(Modifier.fillMaxSize().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                b.title, color = ink, fontFamily = Playfair, fontSize = if (width < 28.dp) 10.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.verticalText().rotate(-90f),
            )
        }
    }
}

/** Gira el hueco de un texto 90°: lo mide con el alto disponible como ancho. */
fun Modifier.verticalText() = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) {
        p.place(x = -(p.width / 2 - p.height / 2), y = -(p.height / 2 - p.width / 2))
    }
}

private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)

private fun averageColor(f: java.io.File): Color? = runCatching {
    if (!f.exists()) return null
    val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 16 }) ?: return null
    var r = 0L; var g = 0L; var bl = 0L; var n = 0
    // Franja izquierda de la portada (la más parecida al lomo real)
    val w = (bmp.width / 4).coerceAtLeast(1)
    for (y in 0 until bmp.height) for (x in 0 until w) {
        val c = bmp.getPixel(x, y)
        r += (c shr 16) and 0xff; g += (c shr 8) and 0xff; bl += c and 0xff; n++
    }
    bmp.recycle()
    if (n == 0) null else Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
}.getOrNull()

/** Hueco de la estantería con su fondo y su balda; [content] son los libros, apoyados abajo. */
@Composable
fun ShelfCompartment(style: ShelfStyle, height: Dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(height)
                .then(if (style.back != null) Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(Brush.verticalGradient(style.back)) else Modifier),
            contentAlignment = Alignment.BottomStart,
        ) { content() }
        Box(Modifier.fillMaxWidth().height(if (style.back != null) 12.dp else 5.dp)
            .background(Brush.verticalGradient(style.plank)))
        Box(Modifier.fillMaxWidth().height(if (style.back != null) 4.dp else 3.dp).background(style.edge))
    }
}

/** Ajustes → Apariencia: libros por fila en la biblioteca, y madera y tamaño de las estanterías. */
@Composable
fun LibraryLookSettings() {
    var columns by remember { mutableStateOf(com.noirbooks.app.data.Prefs.libraryColumns.coerceIn(2, 4)) }
    var style by remember { mutableStateOf(com.noirbooks.app.data.Prefs.shelfStyle) }
    var size by remember { mutableStateOf(com.noirbooks.app.data.Prefs.shelfSize) }
    var recent by remember { mutableStateOf(com.noirbooks.app.data.Prefs.recentSize) }
    Column {
        Text("Inicio: portadas de Recientes", color = NB.text2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("s" to "Pequeñas", "m" to "Medianas", "l" to "Grandes").forEach { (k, l) ->
                Chip(l, recent == k) { recent = k; com.noirbooks.app.data.Prefs.recentSize = k }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Biblioteca: libros por fila", color = NB.text2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { n -> Chip("$n", columns == n) { columns = n; com.noirbooks.app.data.Prefs.libraryColumns = n } }
        }
        Spacer(Modifier.height(18.dp))
        Text("Estanterías: madera", color = NB.text2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHELF_STYLES.forEach { st ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .border(if (st.key == style) 2.dp else 1.dp, if (st.key == style) NB.text else NB.line, RoundedCornerShape(10.dp))
                        .clickable { style = st.key; com.noirbooks.app.data.Prefs.shelfStyle = st.key }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp))
                        .background(Brush.verticalGradient(st.back ?: listOf(NB.bg, NB.bg)))) {
                        Row(Modifier.align(Alignment.BottomCenter).fillMaxHeight().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                            listOf(Color(0xFF6B2D2D), Color(0xFF2C3E50), Color(0xFFB59A5B)).forEachIndexed { i, c ->
                                Box(Modifier.width(7.dp).fillMaxHeight(if (i == 1) 1f else 0.85f).background(c))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Brush.verticalGradient(st.plank)))
                    Spacer(Modifier.height(6.dp))
                    Text(st.label, fontSize = 12.sp, color = NB.text, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Estanterías: tamaño", color = NB.text2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("s" to "Pequeña", "m" to "Mediana", "l" to "Grande").forEach { (k, l) ->
                Chip(l, size == k) { size = k; com.noirbooks.app.data.Prefs.shelfSize = k }
            }
        }
    }
}
