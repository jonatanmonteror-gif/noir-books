package com.noirbooks.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.combinedClickable
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirbooks.app.R
import com.noirbooks.app.data.Book
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.DayRead
import com.noirbooks.app.data.Highlight
import com.noirbooks.app.data.HighlightColor
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.Prefs
import com.noirbooks.app.data.Stats
import com.noirbooks.app.data.durationLabel
import kotlinx.coroutines.launch
import java.util.Calendar

private val MONTHS = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
private val MONTHS_SHORT = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Pantalla principal con barra inferior de iconos (como Noir+ en el móvil). */
@Composable
fun MainScreen() {
    val tab by AppState.tab
    Column(Modifier.fillMaxSize().background(NB.bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> LibraryScreen()
                1 -> CalendarScreen()
                2 -> QuotesScreen()
                4 -> LogScreen()
                else -> StatsScreen()
            }
        }
        TimerBar()
        Row(
            Modifier.fillMaxWidth().background(NB.bg).navigationBarsPadding().height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Orden en pantalla: Biblioteca, Calendario, Registro, Citas, Estadísticas (el número es la pestaña).
            listOf(Triple(0, R.drawable.ic_library, "Biblioteca"), Triple(1, R.drawable.ic_calendar, "Calendario"),
                Triple(4, R.drawable.ic_log, "Registro de lecturas"), Triple(2, R.drawable.ic_quote, "Citas"),
                Triple(3, R.drawable.ic_stats, "Estadísticas")).forEach { (i, icon, label) ->
                Box(
                    Modifier.weight(1f).height(58.dp).clickable { AppState.tab.intValue = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), label, tint = if (tab == i) NB.text else NB.text3, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Registro de lecturas: cada lectura (en la app o apuntada) en una lista; mantener pulsado para borrar
// ---------------------------------------------------------------------------------------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogScreen() {
    val v by Stats.version
    val books by Library.books
    val byId = books.associateBy { it.id }
    val list = remember(v) { Stats.sessions() }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<com.noirbooks.app.data.Session?>(null) }
    val hm = remember { SimpleDateFormat("HH:mm", Locale("es", "ES")) }
    val dayFmt = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")) }
    val today = Stats.today()
    val yesterday = Stats.dayOf(System.currentTimeMillis() - 24 * 3600_000L)

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Registro") {
            Box(
                Modifier.padding(end = 10.dp).clip(RoundedCornerShape(50)).background(NB.text)
                    .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("+ Apuntar lectura", color = NB.onText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (list.isEmpty()) {
            Text(
                "Aquí aparecerá cada lectura: lo que leas en la app y lo que apuntes de Kindle o papel. Mantén pulsada una lectura para editarla o borrarla.",
                color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(20.dp),
            )
            return@Column
        }
        Text("Mantén pulsada una lectura para editarla o borrarla.", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            list.groupBy { Stats.dayOf(it.start) }.forEach { (day, items) ->
                item(key = "d$day") {
                    val label = when (day) {
                        today -> "Hoy"
                        yesterday -> "Ayer"
                        else -> dayFmt.format(items.first().start).replaceFirstChar { it.uppercase() }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(label, color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(durationLabel(items.sumOf { it.seconds }), color = NB.text3, fontSize = 13.sp)
                    }
                }
                items(items, key = { "s${it.id}" }) { s ->
                    val b = byId[s.bookId]
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { b?.let { AppState.screen.value = Screen.Book(it.id) } }, onLongClick = { confirm = s })
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (b != null) BookCover(b, Modifier.width(38.dp), titleSize = 6) else Box(Modifier.width(38.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b?.title ?: "Libro eliminado", color = NB.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${hm.format(s.start)} – ${hm.format(s.end)}", color = NB.text2, fontSize = 13.sp)
                            Text(
                                (if (s.manual) "Apuntada a mano" else "Leída en la app") + if (s.pages > 0) " · ${s.pages} páginas" else "",
                                color = NB.text3, fontSize = 12.sp,
                            )
                        }
                        Text(durationLabel(s.seconds), color = NB.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (picking) BookPickerDialog(onPick = { b -> picking = false; AppState.logSession.value = LogRequest(b.id) }, onDismiss = { picking = false })
    confirm?.let { s -> DeleteReadingDialog(s.bookId, session = s, onDismiss = { confirm = null }) }
}

@Composable
private fun ScreenTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = NB.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------------------------------------
// Calendario con portadas (se rellena solo con lo leído en la app)
// ---------------------------------------------------------------------------------------------

@Composable
fun CalendarScreen() {
    val statsVersion by Stats.version
    val books by Library.books
    val byId = books.associateBy { it.id }
    val now = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var dayOpen by remember { mutableStateOf<String?>(null) }
    var yearView by rememberSaveable { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    /** Eligiendo libro para apuntar una lectura: "" = hoy, o la fecha del día abierto. */
    var picking by remember { mutableStateOf<String?>(null) }
    picking?.let { d ->
        BookPickerDialog(
            onPick = { b ->
                picking = null
                val req = if (d.isEmpty()) LogRequest(b.id) else {
                    val p = d.split("-").map { it.toInt() }
                    val st = Calendar.getInstance().apply { clear(); set(p[0], p[1] - 1, p[2], 21, 0) }.timeInMillis
                    LogRequest(b.id, st, st + 30 * 60_000L)
                }
                AppState.logSession.value = req
            },
            onDismiss = { picking = null },
        )
    }
    val days = remember(statsVersion) { Stats.byDay() }

    val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lunes = 0
    val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    fun key(d: Int) = "%04d-%02d-%02d".format(year, month + 1, d)
    val monthDays = (1..dim).mapNotNull { d -> days[key(d)]?.let { d to it } }.toMap()
    val monthSeconds = monthDays.values.sumOf { l -> l.sumOf { it.seconds } }
    val monthPages = monthDays.values.sumOf { l -> l.sumOf { it.pages } }
    val monthBooks = monthDays.values.flatten().map { it.bookId }.distinct()
    val finishedThisMonth = books.filter { b ->
        !b.inTrash && b.status == BookStatus.Read &&
            b.readings.lastOrNull { it.end > 0 }?.let { r -> Calendar.getInstance().apply { timeInMillis = r.end }.let { it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month } } == true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Calendario") {
            Box(
                Modifier.padding(end = 10.dp).clip(RoundedCornerShape(50)).background(NB.text)
                    .clickable { picking = "" }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("+ Apuntar lectura", color = NB.onText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        // Mes o año entero, y compartir el mes como imagen
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Mes", !yearView) { yearView = false }
            Chip("Año", yearView) { yearView = true }
            Spacer(Modifier.weight(1f))
            if (!yearView) Text(
                "Compartir mes", color = NB.text2, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    shareBitmap(ctx, renderMonth(ctx, year, month, days, books), "mes", "Mi ${MONTHS[month].lowercase()} de lectura en Noir Books")
                }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (yearView) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.Filled.KeyboardArrowLeft, "Año anterior", tint = NB.text) }
                Text("$year", fontFamily = Playfair, fontSize = 22.sp, color = NB.text, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.Filled.KeyboardArrowRight, "Año siguiente", tint = NB.text) }
            }
            YearCalendar(year, days) { m -> month = m; yearView = false }
        } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (month == 0) { month = 11; year-- } else month-- }) {
                Icon(Icons.Filled.KeyboardArrowLeft, "Mes anterior", tint = NB.text)
            }
            Text("${MONTHS[month]} $year", fontFamily = Playfair, fontSize = 22.sp, color = NB.text, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { if (month == 11) { month = 0; year++ } else month++ }) {
                Icon(Icons.Filled.KeyboardArrowRight, "Mes siguiente", tint = NB.text)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, color = NB.text3, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = firstDow + dim
        val rows = (cells + 6) / 7
        val today = Stats.today()
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val d = r * 7 + c - firstDow + 1
                        Box(Modifier.weight(1f).aspectRatio(2f / 3f)) {
                            if (d in 1..dim) {
                                val reads = monthDays[d]
                                val main = reads?.maxByOrNull { it.seconds }?.let { byId[it.bookId] }
                                DayCell(d, main, isToday = key(d) == today, onClick = if (reads != null || key(d) <= today) ({ dayOpen = key(d) }) else null)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(durationLabel(monthSeconds).takeIf { monthSeconds > 0 } ?: "0 min", "leyendo", Modifier.weight(1f))
            StatCard("$monthPages", "páginas", Modifier.weight(1f))
            StatCard("${monthDays.size}", if (monthDays.size == 1) "día" else "días", Modifier.weight(1f))
        }
        if (finishedThisMonth.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Terminados este mes")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                finishedThisMonth.forEach { b -> BookCover(b, Modifier.width(70.dp), titleSize = 9) }
            }
        }
        if (monthBooks.isEmpty()) {
            Text(
                "Los días que leas en Noir Books aparecerán aquí con la portada del libro, sin apuntar nada.",
                color = NB.text3, fontSize = 13.sp, fontFamily = Inter, modifier = Modifier.padding(20.dp),
            )
        }
        }
        Spacer(Modifier.height(24.dp))
    }

    dayOpen?.let { d -> DayDialog(d, days[d].orEmpty(), byId, onDismiss = { dayOpen = null }, onAdd = { dayOpen = null; picking = d }) }
}

@Composable
private fun DayCell(day: Int, book: Book?, isToday: Boolean, onClick: (() -> Unit)?) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp))
            .background(if (book == null) NB.surface else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (book != null) BookCover(book, Modifier.fillMaxSize(), titleSize = 7)
        Box(
            Modifier.align(Alignment.TopStart).padding(3.dp).clip(CircleShape)
                .background(if (isToday) NB.text else if (book != null) Color.Black.copy(alpha = 0.55f) else Color.Transparent)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text("$day", fontSize = 10.sp, color = if (isToday) NB.onText else if (book != null) Color.White else NB.text2, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DayDialog(date: String, reads: List<DayRead>, byId: Map<String, Book>, onDismiss: () -> Unit, onAdd: () -> Unit) {
    val v by Stats.version
    val sessions = remember(v, date) { Stats.sessions().filter { Stats.dayOf(it.start) == date } }
    var deleting by remember { mutableStateOf<String?>(null) }
    deleting?.let { id -> DeleteReadingDialog(id, date = date, onDismiss = { deleting = null }); return }
    val parts = date.split("-")
    val label = "${parts.getOrNull(2)?.toIntOrNull() ?: ""} de ${MONTHS.getOrNull((parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1)?.lowercase().orEmpty()}"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NB.surface2,
        title = { Text(label, fontFamily = Playfair, fontSize = 22.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (reads.isEmpty()) Text("Este día no hay lecturas.", color = NB.text3, fontSize = 14.sp)
                else Text("Mantén pulsado un libro para borrar lo leído.", color = NB.text3, fontSize = 12.sp)
                reads.sortedByDescending { it.seconds }.forEach { r ->
                    val b = byId[r.bookId]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = {}, onLongClick = { deleting = r.bookId }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (b != null) BookCover(b, Modifier.width(40.dp), titleSize = 7)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b?.title ?: "Libro eliminado", color = NB.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${durationLabel(r.seconds)} · ${r.pages} páginas", color = NB.text3, fontSize = 13.sp)
                        }
                    }
                }
                if (sessions.isNotEmpty()) {
                    Text("LECTURAS", fontSize = 10.sp, letterSpacing = 1.3.sp, color = NB.text3, modifier = Modifier.padding(top = 6.dp))
                    Column { SessionRows(sessions, showBook = true) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("Apuntar lectura", color = NB.text) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = NB.text2) } },
    )
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(NB.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = NB.text, maxLines = 1)
        Text(label, fontFamily = Inter, fontSize = 12.sp, color = NB.text3)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.text3, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Citas: cuaderno con todas las citas, cita del día, filtro por color, exportar; y Mis palabras
// ---------------------------------------------------------------------------------------------

@Composable
fun QuotesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by Library.books
    val vocab by Library.vocabulary
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var color by rememberSaveable { mutableIntStateOf(-1) }
    var open by remember { mutableStateOf<Pair<Book, Highlight>?>(null) }
    var defineWord by remember { mutableStateOf<String?>(null) }

    val all = books.filter { !it.inTrash }.flatMap { b -> b.highlights.map { b to it } }.sortedByDescending { it.second.id }
    var search by rememberSaveable { mutableStateOf("") }
    var photoQuote by remember { mutableStateOf(false) }
    if (photoQuote) PhotoQuoteFlow(onDone = { photoQuote = false })
    val sq = fold(search.trim())
    val shown = all.filter { color < 0 || it.second.color == color }
        .filter { (b, h) -> sq.isEmpty() || fold(h.text).contains(sq) || fold(h.note).contains(sq) || fold(b.title).contains(sq) || fold(b.author).contains(sq) }
    val daily = remember(all.size) {
        if (all.isEmpty()) null else all[(Stats.today().hashCode() and 0x7fffffff) % all.size]
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Citas") {
            if (tab == 0) TextButton(onClick = { photoQuote = true }) { Text("+ Foto", color = NB.text, fontFamily = Inter) }
            if (tab == 0 && all.isNotEmpty()) {
                TextButton(onClick = { shareText(ctx, "Mis citas — Noir Books", exportMarkdown(all)) }) {
                    Text("Exportar", color = NB.text, fontFamily = Inter)
                }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Citas · ${all.size}", tab == 0) { tab = 0 }
            Chip("Mis palabras · ${vocab.size}", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) {
            if (all.isEmpty()) {
                Text(
                    "Mientras lees, mantén pulsada una palabra y arrastra para seleccionar. Elige un color y la cita aparecerá aquí. De un libro de papel, usa «+ Foto».\n\nCada color puede tener un sentido: amarillo, frase bonita; azul, idea; verde, para recordar; rosa, duda.",
                    color = NB.text3, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = Inter, modifier = Modifier.padding(20.dp),
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                daily?.let { (b, h) ->
                    item(key = "daily") {
                        Column(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface)
                                .clickable { open = b to h }.padding(18.dp),
                        ) {
                            Text("CITA DEL DÍA", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text("“${h.text}”", fontFamily = Playfair, fontSize = 18.sp, lineHeight = 26.sp, color = NB.text, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Text(b.title + if (b.author.isNotBlank()) " · ${b.author}" else "", fontSize = 12.sp, color = NB.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item(key = "filters") {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text("Buscar en tus citas", color = NB.text3) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line,
                            focusedContainerColor = NB.surface, unfocusedContainerColor = NB.surface, cursorColor = NB.text,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Todas", color < 0) { color = -1 }
                        HighlightColor.entries.forEach { hc ->
                            val n = all.count { it.second.color == hc.ordinal }
                            if (n > 0) Chip("${hc.meaning} · $n", color == hc.ordinal) { color = hc.ordinal }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                items(shown, key = { "${it.first.id}-${it.second.id}" }) { (b, h) ->
                    QuoteRow(h, subtitle = b.title + (h.chapterTitle.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")) { open = b to h }
                }
            }
        } else {
            if (vocab.isEmpty()) {
                Text(
                    "Selecciona una palabra mientras lees y pulsa \"Definir\": verás su significado y se guardará aquí con la frase del libro.",
                    color = NB.text3, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = Inter, modifier = Modifier.padding(20.dp),
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(vocab, key = { it.word }) { w ->
                    Row(
                        Modifier.fillMaxWidth().clickable { defineWord = w.word }.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.word, fontFamily = Playfair, fontSize = 18.sp, color = NB.text)
                            if (w.sentence.isNotBlank()) Text(w.sentence, fontSize = 13.sp, color = NB.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(w.bookTitle, fontSize = 12.sp, color = NB.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { scope.launch { Library.removeWord(w.word) } }) {
                            Icon(Icons.Filled.Close, "Quitar", tint = NB.text3, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    open?.let { (b, h) ->
        HighlightSheet(
            h = h, bookTitle = b.title, author = b.author,
            onChange = { nh ->
                scope.launch { Library.update(b.id) { bk -> bk.copy(highlights = bk.highlights.map { if (it.id == nh.id) nh else it }) } }
                open = b to nh
            },
            onDelete = {
                scope.launch { Library.update(b.id) { bk -> bk.copy(highlights = bk.highlights.filterNot { it.id == h.id }) } }
                open = null
            },
            onDismiss = { open = null },
            onOpen = if (!b.isEpub || h.range.isBlank()) null else ({
                AppState.jumpTo.value = b.id to h
                AppState.openBook(b)
                open = null
            }),
        )
    }
    defineWord?.let { DictionarySheet(it, onDismiss = { defineWord = null }) }
}

private fun exportMarkdown(all: List<Pair<Book, Highlight>>): String = buildString {
    append("# Mis citas\n\n")
    all.groupBy { it.first }.forEach { (b, hs) ->
        append("## ${b.title}")
        if (b.author.isNotBlank()) append(" — ${b.author}")
        append("\n\n")
        hs.sortedWith(compareBy({ it.second.chapter }, { it.second.fraction })).forEach { (_, h) ->
            append("> ${h.text.replace("\n", " ")}\n")
            if (h.note.isNotBlank()) append("\n${h.note}\n")
            val meta = listOf(h.chapterTitle, HighlightColor.of(h.color).meaning).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) append("\n*$meta*\n")
            append("\n")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Estadísticas: tiempo real medido por el lector, gratis y sin apuntar nada
// ---------------------------------------------------------------------------------------------

@Composable
fun StatsScreen() {
    val statsVersion by Stats.version
    val books by Library.books
    val now = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var goalKind by remember { mutableStateOf(Prefs.goalKind) }
    var goal by remember { mutableIntStateOf(when (Prefs.goalKind) { "hours" -> Prefs.goalHours; "pages" -> Prefs.goalPages; else -> Prefs.yearGoal }) }
    var editGoal by remember { mutableStateOf(false) }
    val all = remember(statsVersion) { Stats.all() }
    val streak = remember(statsVersion) { Stats.streak() }
    val cpm = remember(statsVersion) { Stats.charsPerMinute() }

    val yearReads = all.filter { it.date.startsWith("$year-") }
    val seconds = yearReads.sumOf { it.seconds }
    val pages = yearReads.sumOf { it.pages }
    val daysRead = yearReads.map { it.date }.distinct().size
    // Solo cuentan los libros marcados como leídos, una vez cada uno (en el año en que lo terminaste).
    val finished = books.filter { b ->
        !b.inTrash && b.status == BookStatus.Read &&
            b.readings.lastOrNull { it.end > 0 }?.let { r -> Calendar.getInstance().apply { timeInMillis = r.end }.get(Calendar.YEAR) == year } == true
    }
    val abandoned = books.count { !it.inTrash && it.status == BookStatus.Abandoned }
    val byMonth = (0 until 12).map { m -> yearReads.filter { it.date.substring(5, 7).toInt() == m + 1 }.sumOf { it.seconds } }
    val byHour = LongArray(24).also { h -> yearReads.forEach { d -> for (i in 0 until 24) h[i] += d.hourSeconds[i] } }
    val topBooks = yearReads.groupBy { it.bookId }.mapValues { (_, l) -> l.sumOf { it.seconds } }
        .entries.sortedByDescending { it.value }.take(5)
    val byId = books.associateBy { it.id }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Estadísticas") {
            IconButton(onClick = { year-- }) { Icon(Icons.Filled.KeyboardArrowLeft, "Año anterior", tint = NB.text2) }
            Text("$year", color = NB.text, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { if (year < now.get(Calendar.YEAR)) year++ }) { Icon(Icons.Filled.KeyboardArrowRight, "Año siguiente", tint = NB.text2) }
        }

        // Meta anual
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface)
                .clickable { editGoal = true }.padding(18.dp),
        ) {
            Text("META DE $year", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            // La meta puede ser de libros terminados, horas o páginas.
            val current = when (goalKind) { "hours" -> (seconds / 3600).toInt(); "pages" -> pages; else -> finished.size }
            val unit = when (goalKind) { "hours" -> "horas"; "pages" -> "páginas"; else -> "libros" }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$current", fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = NB.text)
                Text(if (goal > 0) " de $goal $unit" else " " + when (goalKind) { "hours" -> "horas leídas"; "pages" -> "páginas leídas"; else -> "libros terminados" },
                    fontFamily = Inter, fontSize = 15.sp, color = NB.text2, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (goal > 0) {
                Spacer(Modifier.height(8.dp))
                ThinProgress((current.toFloat() / goal).coerceAtMost(1f), Modifier.fillMaxWidth())
                if (year == now.get(Calendar.YEAR)) {
                    val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
                    val expected = goal * dayOfYear / 365f
                    val diff = current - expected
                    val slack = when (goalKind) { "hours" -> 3f; "pages" -> 100f; else -> 1f }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            diff >= slack / 2 -> "Vas por delante de tu ritmo"
                            diff > -slack -> "Vas a buen ritmo"
                            else -> "Te faltan unos ${(-diff).toInt().coerceAtLeast(1)} $unit para ir al día; sin prisa"
                        },
                        fontSize = 13.sp, color = NB.text3, fontFamily = Inter,
                    )
                }
            }
        }
        if (year == now.get(Calendar.YEAR)) {
            Spacer(Modifier.height(12.dp))
            DailyGoalCard(all)
            Spacer(Modifier.height(12.dp))
            ReportCard(all, books.filter { !it.inTrash })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(if (seconds > 0) durationLabel(seconds) else "0 min", "leyendo", Modifier.weight(1f))
            StatCard("$pages", "páginas", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("$daysRead", "días con lectura", Modifier.weight(1f))
            StatCard(if (streak > 0) "$streak" else "—", if (streak == 1) "día seguido" else "días seguidos", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("${(cpm / 5.6f).toInt()}", "palabras por minuto", Modifier.weight(1f))
            StatCard("$abandoned", if (abandoned == 1) "abandonado" else "abandonados", Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))
        // Tiempo de lectura por semana (días), mes (días) o año (meses)
        val isNow = year == now.get(Calendar.YEAR)
        var chart by rememberSaveable { mutableStateOf("year") }
        val mode = if (!isNow) "year" else chart
        val perDay = remember(all) { all.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.seconds } } }
        fun dk(c: Calendar) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TIEMPO DE LECTURA", fontSize = 11.sp, letterSpacing = 1.6.sp, color = NB.silver, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            if (isNow) {
                Chip("Semana", mode == "week") { chart = "week" }
                Spacer(Modifier.width(6.dp))
                Chip("Mes", mode == "month") { chart = "month" }
                Spacer(Modifier.width(6.dp))
                Chip("Año", mode == "year") { chart = "year" }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (mode) {
            "week" -> {
                val start = Calendar.getInstance().apply { while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1) }
                val vals = (0 until 7).map { i -> (perDay[dk((start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) })] ?: 0L) }
                BarChart(vals.map { it / 60f }, listOf("L", "M", "X", "J", "V", "S", "D"), highlight = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7)
                Text("Esta semana: ${durationLabel(vals.sum()).takeIf { vals.sum() > 0 } ?: "0 min"}", color = NB.text3, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            "month" -> {
                val dim = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                val vals = (1..dim).map { d -> perDay["%04d-%02d-%02d".format(year, now.get(Calendar.MONTH) + 1, d)] ?: 0L }
                BarChart(vals.map { it / 60f }, (1..dim).map { if (it == 1 || it % 5 == 0) "$it" else "" }, highlight = now.get(Calendar.DAY_OF_MONTH) - 1)
                Text("Este mes: ${durationLabel(vals.sum()).takeIf { vals.sum() > 0 } ?: "0 min"} · minutos por día", color = NB.text3, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            else -> {
                BarChart(byMonth.map { it / 3600f }, MONTHS_SHORT, highlight = if (isNow) now.get(Calendar.MONTH) else -1)
                Text("Horas por mes", color = NB.text3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("A qué hora lees")
        BarChart(byHour.map { it / 60f }, (0 until 24).map { if (it % 6 == 0) "$it h" else "" }, highlight = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 } ?: -1)

        if (topBooks.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionLabel("Más tiempo de lectura")
            topBooks.forEach { (id, s) ->
                val b = byId[id] ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BookCover(b, Modifier.width(36.dp), titleSize = 7)
                    Spacer(Modifier.width(12.dp))
                    Text(b.title, color = NB.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(durationLabel(s), color = NB.text3, fontSize = 13.sp)
                }
            }
        }
        if (yearReads.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionLabel("Por género")
            GenreStats(yearReads, books, year)
        }
        if (all.isEmpty()) {
            Text(
                "Las estadísticas se rellenan solas mientras lees en Noir Books: cada página cuenta si estás al menos 5 segundos en ella.",
                color = NB.text3, fontSize = 13.sp, fontFamily = Inter, modifier = Modifier.padding(20.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editGoal) {
        var kind by remember { mutableStateOf(goalKind) }
        fun saved(k: String) = when (k) { "hours" -> Prefs.goalHours; "pages" -> Prefs.goalPages; else -> Prefs.yearGoal }
        var v by remember { mutableIntStateOf(goal) }
        val step = when (kind) { "hours" -> 5; "pages" -> 250; else -> 1 }
        val maxV = when (kind) { "hours" -> 2000; "pages" -> 100_000; else -> 365 }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            containerColor = NB.surface2,
            title = { Text("Meta del año", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("books" to "Libros", "hours" to "Horas", "pages" to "Páginas").forEach { (k, l) ->
                            Chip(l, kind == k) { if (kind != k) { kind = k; v = saved(k) } }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        PillButton("−") { v = (v - step).coerceAtLeast(0) }
                        Text(if (v == 0) "Sin meta" else "$v", fontFamily = InterDisplay, fontSize = 28.sp, color = NB.text,
                            textAlign = TextAlign.Center, modifier = Modifier.width(130.dp))
                        PillButton("+") { v = (v + step).coerceAtMost(maxV) }
                    }
                    Text(when (kind) { "hours" -> "horas de lectura"; "pages" -> "páginas"; else -> "libros terminados" },
                        color = NB.text3, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                when (kind) { "hours" -> Prefs.goalHours = v; "pages" -> Prefs.goalPages = v; else -> Prefs.yearGoal = v }
                Prefs.goalKind = kind; goalKind = kind; goal = v; editGoal = false
            }) { Text("Guardar", color = NB.text, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Cancelar", color = NB.text2) } },
        )
    }
}

/** Gráfico de barras sencillo, monocromo; la barra destacada en blanco. */
@Composable
private fun BarChart(values: List<Float>, labels: List<String>, highlight: Int) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    val barHi = NB.text
    val barLo = NB.line
    Column(Modifier.padding(horizontal = 20.dp)) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = values.size
            val gap = size.width / n * 0.25f
            val bw = size.width / n - gap
            values.forEachIndexed { i, v ->
                val h = (v / max) * (size.height - 4f)
                val x = i * (bw + gap) + gap / 2
                drawRoundRect(
                    color = if (i == highlight) barHi else barLo,
                    topLeft = Offset(x, size.height - h.coerceAtLeast(2f)),
                    size = Size(bw, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l -> Text(l, fontSize = 10.sp, color = NB.text3, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f)) }
        }
    }
}
