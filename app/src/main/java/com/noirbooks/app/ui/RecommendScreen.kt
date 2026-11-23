package com.noirbooks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.noirbooks.app.data.BookFormat
import com.noirbooks.app.data.BookSearch
import com.noirbooks.app.data.BookStatus
import com.noirbooks.app.data.FoundBook
import com.noirbooks.app.data.Genres
import com.noirbooks.app.data.Library
import com.noirbooks.app.data.ProgressUnit
import com.noirbooks.app.data.Recommender
import com.noirbooks.app.data.Recommender.Known
import com.noirbooks.app.data.Recommender.Length
import com.noirbooks.app.data.Recommender.Mood
import com.noirbooks.app.data.Recommender.Way
import kotlinx.coroutines.launch

/** Tarjeta de Inicio: «¿Qué leo ahora?» y «Novedades para ti». */
@Composable
fun DiscoverCard() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NB.surface).padding(16.dp)) {
        Text("¿QUÉ LEO AHORA?", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("Unas preguntas rápidas y te recomiendo qué leer, también lo recién salido.", color = NB.text, fontSize = 15.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Empezar", primary = true) { AppState.recommend.value = "quiz" }
            PillButton("Novedades para ti") { AppState.recommend.value = "news" }
        }

    }
}

/** Pantalla completa del cuestionario o de las novedades. */
@Composable
fun RecommendDialog(mode: String, onDismiss: () -> Unit) {
    val navH = activityNavBarHeight()
    val statusH = activityStatusBarHeight()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullScreenDialogWindow()
        Box(Modifier.fillMaxSize().background(NB.bg).padding(top = statusH + 4.dp, bottom = navH.coerceAtLeast(16.dp))) {
            when (mode) {
                "news" -> NewsPane()
                "ai" -> AiPane(null)
                else -> QuizPane()
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) {
                Icon(Icons.Filled.Close, "Cerrar", tint = NB.text2)
            }
        }
    }
}

// ---------------- Cuestionario ----------------

private data class Opt(val label: String, val hint: String = "", val count: Int = -1, val onPick: () -> Unit)

@Composable
private fun QuizPane() {
    val books by Library.books
    var a by remember { mutableStateOf(Recommender.Answers()) }
    var step by remember { mutableIntStateOf(0) }
    var surprise by remember { mutableStateOf(false) }
    val cands = remember(books) { Recommender.candidates(books) }
    val ways = remember(books) { Recommender.waysAvailable(books) }
    fun count(genres: Set<String>) = cands.count { it.genre in genres }
    // Las sagas solo se ofrecen si alguna de tus sagas tiene el siguiente pendiente
    val hasSagaNext = remember(books) { Recommender.pick(books, Recommender.Answers(known = Known.Saga)).any { p -> p.reasons.any { "saga" in it || "siguiente" in it } } }

    // Pasos (se saltan los que no aportan nada)
    val steps = buildList {
        add("tipo")
        if (!surprise) add("animo")
        add("conocido"); add("duracion")
        if (ways.size > 1) add("forma")
        add("resultado")
    }
    val cur = steps.getOrElse(step) { "resultado" }
    fun next() { step++ }

    Column(Modifier.fillMaxSize()) {
        // Progreso
        if (cur != "resultado") Row(Modifier.padding(start = 24.dp, top = 18.dp, end = 60.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(steps.size - 1) { i ->
                Box(Modifier.height(4.dp).width(if (i == step) 22.dp else 10.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) NB.text else NB.line))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (cur) {
                "tipo" -> Question("¿Qué buscas hoy?", "Vamos al grano: elijo entre tus ${cands.size} libros pendientes.", listOf(
                    Opt("Una historia", "Novela, ficción") { a = a.copy(fiction = true); surprise = false; next() },
                    Opt("Aprender o mejorar algo", "No ficción") { a = a.copy(fiction = false); surprise = false; next() },
                    Opt("Sorpréndeme", "Lo decido según tus gustos") { a = a.copy(fiction = null, mood = null); surprise = true; next() },
                ))
                "animo" -> Question(if (a.fiction == true) "¿Qué te apetece sentir?" else "¿Para qué lo quieres?", "Entre paréntesis, cuántos tienes de eso.",
                    Mood.entries.filter { it.fiction == (a.fiction == true) }.map { m ->
                        Opt(m.label, m.hint, count(m.genres)) { a = a.copy(mood = m); next() }
                    })
                "conocido" -> Question("¿Algo conocido o algo nuevo?", "", buildList {
                    if (hasSagaNext) add(Opt(Known.Saga.label, Known.Saga.hint) { a = a.copy(known = Known.Saga); next() })
                    add(Opt(Known.Author.label, Known.Author.hint) { a = a.copy(known = Known.Author); next() })
                    add(Opt(Known.New.label, Known.New.hint) { a = a.copy(known = Known.New); next() })
                    add(Opt(Known.Any.label) { a = a.copy(known = Known.Any); next() })
                })
                "duracion" -> Question("¿Cuánto tiempo le vas a dar?", "", Length.entries.map { l ->
                    Opt(l.label, l.hint) { a = a.copy(length = l); next() }
                })
                "forma" -> Question("¿Cómo lo vas a leer?", "", (ways + Way.Any).map { w ->
                    Opt(w.label) { a = a.copy(way = w); next() }
                })
                else -> QuizResult(a) { a = Recommender.Answers(); surprise = false; step = 0 }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (cur != "resultado" && step > 0) Text("‹ Atrás", color = NB.text2, fontSize = 15.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { step-- }.padding(10.dp))
    }
}

@Composable
private fun Question(title: String, sub: String, opts: List<Opt>) {
    Spacer(Modifier.height(28.dp))
    Text(title, fontFamily = Playfair, fontSize = 28.sp, lineHeight = 34.sp, color = NB.text)
    if (sub.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(sub, color = NB.text2, fontSize = 14.sp) }
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        opts.forEach { o ->
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, NB.line, RoundedCornerShape(14.dp))
                    .clickable(onClick = o.onPick).padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(o.label, color = NB.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (o.hint.isNotBlank()) Text(o.hint, color = NB.text3, fontSize = 13.sp)
                }
                if (o.count >= 0) Text("${o.count}", color = if (o.count > 0) NB.text2 else NB.text3, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun QuizResult(a: Recommender.Answers, onRestart: () -> Unit) {
    val ai = com.noirbooks.app.data.AiRecommender
    // Con IA disponible (clave puesta y cupo), el resultado lo da la IA; si no, se elige entre tus pendientes
    if (com.noirbooks.app.data.Prefs.aiKey.isNotBlank() && ai.left() > 0) {
        Spacer(Modifier.height(28.dp))
        Text("Para ti, hoy", fontFamily = Playfair, fontSize = 28.sp, color = NB.text)
        AiBlock(a, auto = true)
        val books by Library.books
        val own = remember(a) { Recommender.pick(books, a).filter { p ->
            a.mood == null || p.book.genre in a.mood.genres || p.book.genre.isBlank() } .take(3) }
        if (own.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("DE LO QUE YA TIENES", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
            own.forEach { p -> OwnRow(p) }
        }
        Spacer(Modifier.height(18.dp))
        Text("Volver a empezar", color = NB.text2, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRestart).padding(vertical = 8.dp))
        return
    }
    if (com.noirbooks.app.data.Prefs.aiKey.isNotBlank() && ai.left() <= 0) {
        Spacer(Modifier.height(20.dp))
        Text("Has usado tus recomendaciones de este mes (${ai.limit()}). Te propongo de lo que ya tienes.",
            color = NB.text2, fontSize = 13.sp, lineHeight = 18.sp)
        if (!com.noirbooks.app.data.Edition.isPro) Text("Con Pro tienes ${ai.PRO_PER_MONTH} al mes", color = NB.text, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { AppState.showPro.value = true }.padding(vertical = 4.dp))
    }
    val books by Library.books
    val picks = remember(a) { Recommender.pick(books, a) }
    val top = picks.firstOrNull()
    // «Encaja» si cumple lo principal que pediste
    val fits = top != null && (a.mood == null || top.book.genre in a.mood.genres || top.book.genre.isBlank())
    Spacer(Modifier.height(28.dp))
    if (top == null || !fits) {
        Text(if (picks.isEmpty()) "No tienes libros pendientes" else "Entre tus pendientes no hay ${a.mood?.label?.lowercase() ?: "nada que encaje"}",
            fontFamily = Playfair, fontSize = 26.sp, lineHeight = 32.sp, color = NB.text)
        Spacer(Modifier.height(8.dp))
        Text("Mira abajo las novedades que encajan con lo que buscas; puedes guardarlas en «Lo quiero».", color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
    } else {
        Text("Hoy, este", fontFamily = Playfair, fontSize = 28.sp, color = NB.text)
        Spacer(Modifier.height(16.dp))
        val b = top.book
        Row {
            BookCover(b, Modifier.width(110.dp), titleSize = 12)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(b.title, fontFamily = Playfair, fontSize = 20.sp, lineHeight = 25.sp, color = NB.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotBlank()) Text(b.author, color = NB.text2, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                top.reasons.forEach { r ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", color = NB.text2, fontSize = 13.sp)
                        Text(r, color = NB.text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val scope = rememberCoroutineScope()
            PillButton(if (b.inApp) "Empezar a leer" else "Empezar", primary = true) {
                AppState.recommend.value = null
                if (b.inApp) AppState.openBook(b) else scope.launch {
                    Library.setStatus(b.id, BookStatus.Reading); AppState.toast("«${b.title}» en Leyendo")
                }
            }
            PillButton("Ver ficha") { AppState.recommend.value = null; AppState.screen.value = Screen.Book(b.id) }
        }
        val others = picks.drop(1).filter { it.score > 0f }.take(2)
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("TAMBIÉN TE PUEDE ENCAJAR", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
            others.forEach { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                    AppState.recommend.value = null; AppState.screen.value = Screen.Book(p.book.id)
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BookCover(p.book, Modifier.width(44.dp), titleSize = 6)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.book.title, color = NB.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.reasons.firstOrNull() ?: p.book.author, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    // Fuera de tu biblioteca: novedades que encajan con lo elegido
    Spacer(Modifier.height(26.dp))
    Text("FUERA DE TU BIBLIOTECA", fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
    NewsList(a.mood?.genres, limit = 5)
    Spacer(Modifier.height(18.dp))
    Text("Volver a empezar", color = NB.text2, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRestart).padding(vertical = 8.dp))
}

// ---------------- Novedades ----------------

@Composable
private fun NewsPane() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("Novedades para ti", fontFamily = Playfair, fontSize = 28.sp, color = NB.text)
        Spacer(Modifier.height(6.dp))
        Text("Libros de los dos últimos años de tus autores, tus sagas y tus géneros. Los que ya tienes no salen.",
            color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
        NewsList(null, limit = 30, sections = true)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NewsList(genres: Set<String>?, limit: Int, sections: Boolean = false) {
    val books by Library.books
    var news by remember { mutableStateOf<Recommender.News?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(genres) {
        news = null; failed = false
        runCatching { Recommender.news(books, genres) }.onSuccess { news = it }.onFailure { failed = true }
    }
    val n = news
    when {
        failed -> Text("No se pudieron buscar novedades. Revisa la conexión.", color = NB.text3, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        n == null -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = NB.text, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Buscando lo último…", color = NB.text2, fontSize = 14.sp)
        }
        n.authors.isEmpty() && n.sagas.isEmpty() && n.genre.isEmpty() ->
            Text("No he encontrado novedades. Puntúa algunos libros que hayas leído y ponles género: así sé qué buscarte.",
                color = NB.text3, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
        sections -> {
            if (n.sagas.isNotEmpty()) { SectionTitle("Tus sagas"); n.sagas.forEach { NewRow(it) } }
            if (n.authors.isNotEmpty()) { SectionTitle("De autores que te gustan"); n.authors.forEach { NewRow(it) } }
            if (n.genre.isNotEmpty()) { SectionTitle("Recién salido en ${n.genreName.lowercase()}"); n.genre.forEach { NewRow(it) } }
        }
        else -> (n.sagas + n.authors + n.genre).take(limit).forEach { NewRow(it) }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Spacer(Modifier.height(22.dp))
    Text(t.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = NB.silver, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun NewRow(nb: Recommender.NewBook, wrapWhy: Boolean = false) {
    val f = nb.found
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp).height(72.dp).clip(RoundedCornerShape(4.dp)).background(NB.surface2)) {
            if (f.coverUrl != null) AsyncImage(f.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, color = NB.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(f.author.substringBefore(','), f.year).filter { it.isNotBlank() }.joinToString(" · "), color = NB.text2, fontSize = 12.sp, maxLines = 1)
            Text(nb.why, color = NB.text3, fontSize = 12.sp, lineHeight = 16.sp, maxLines = if (wrapWhy) 4 else 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (added) "✓ Guardado" else "Lo quiero", color = if (added) NB.text2 else NB.onText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (added) NB.surface else NB.text)
                .clickable(enabled = !added) {
                    added = true
                    scope.launch {
                        runCatching {
                            val b = Library.addManual(f.title, f.author, BookFormat.Paper, f.pages,
                                if (f.pages > 0) ProgressUnit.Page else ProgressUnit.Percent, f.coverUrl, f.isbn, f.publisher, f.year,
                                BookStatus.ToRead, f.description, Genres.from(f.categories))
                            Library.update(b.id) { it.copy(wish = true) }
                        }.onFailure { added = false; AppState.toast("No se pudo guardar") }
                    }
                }.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

// ---------------- IA ----------------

@Composable
private fun AiPane(answers: Recommender.Answers?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("Recomendaciones para ti", fontFamily = Playfair, fontSize = 28.sp, color = NB.text)
        Spacer(Modifier.height(6.dp))
        Text("A partir de lo que has leído y cómo lo has puntuado, incluido lo que acaba de salir.",
            color = NB.text2, fontSize = 14.sp, lineHeight = 20.sp)
        AiBlock(answers)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AiBlock(answers: Recommender.Answers?, auto: Boolean = false) {
    val books by Library.books
    val scope = rememberCoroutineScope()
    var wish by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<com.noirbooks.app.data.AiRecommender.Result?>(null) }
    val ai = com.noirbooks.app.data.AiRecommender
    fun ask() {
        if (ai.left() <= 0) { AppState.showPro.value = true; return }
        loading = true; error = null
        scope.launch {
            runCatching { ai.recommend(books, wish, answers) }
                .onSuccess { result = it }
                .onFailure { error = it.message ?: "No se pudo pedir la recomendación"; com.noirbooks.app.data.ErrorLog.note("Recomendación IA", it) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (auto && result == null && !loading) ask() }
    Spacer(Modifier.height(16.dp))
    Text(when {
        com.noirbooks.app.data.Edition.isDev -> "Sin límite (vista desarrollador)"
        else -> "Te quedan ${ai.left()} de ${ai.limit()} recomendaciones este mes" + if (!com.noirbooks.app.data.Edition.isPro) " · con Pro, ${ai.PRO_PER_MONTH}" else ""
    }, color = NB.text3, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.OutlinedTextField(
        wish, { wish = it.take(200) }, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("¿Algo concreto? Ej.: «como La asistenta pero más corto»", color = NB.text3) },
        shape = RoundedCornerShape(12.dp), maxLines = 3,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NB.text2, unfocusedBorderColor = NB.line, cursorColor = NB.text,
            focusedTextColor = NB.text, unfocusedTextColor = NB.text),
    )
    Spacer(Modifier.height(10.dp))
    if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = NB.text, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("Pensando en tus libros…", color = NB.text2, fontSize = 14.sp)
    } else if (!auto || result != null || error != null) PillButton(if (result == null) "Recomiéndame" else "Otras ideas", primary = result == null) { ask() }
    error?.let { Text(it, color = NB.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    result?.let { r ->
        Spacer(Modifier.height(8.dp))
        if (r.items.isEmpty()) Text("No he encontrado recomendaciones esta vez. Prueba otra vez.", color = NB.text3, fontSize = 13.sp)
        r.items.forEach { s ->
            val f = s.found ?: FoundBook(s.title, s.author, null, 0, "", "", s.year, "", "IA")
            NewRow(Recommender.NewBook(f.copy(title = s.title, author = s.author.ifBlank { f.author }, year = s.year.ifBlank { f.year }),
                s.why + if (s.found == null) " (no aparece en el catálogo)" else ""), wrapWhy = true)
        }
        // Coste de esta consulta (para calcular precios)
        if (com.noirbooks.app.data.Edition.isDev) {
            Spacer(Modifier.height(10.dp))
            Text("${r.model.substringAfter('/')} · ${r.promptTokens}+${r.completionTokens} tokens · " +
                "\$${"%.4f".format(r.costUsd)} esta consulta" + if (r.web) " · con internet" else "",
                color = NB.text3, fontSize = 11.sp)
        }
    }
}

/** Uno de tus pendientes, compacto, con su porqué. */
@Composable
private fun OwnRow(p: Recommender.Pick) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
        AppState.recommend.value = null; AppState.screen.value = Screen.Book(p.book.id)
    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(p.book, Modifier.width(44.dp), titleSize = 6)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.book.title, color = NB.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.reasons.firstOrNull() ?: p.book.author, color = NB.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
