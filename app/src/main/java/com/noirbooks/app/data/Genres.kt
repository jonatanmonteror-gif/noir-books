package com.noirbooks.app.data

import java.text.Normalizer

/** Géneros en español y cómo se deducen de las categorías de los catálogos (en inglés o español). */
object Genres {
    val ALL = listOf(
        "Novela", "Ciencia ficción", "Fantasía", "Misterio y thriller", "Romántica", "Novela histórica", "Terror",
        "Juvenil", "Infantil", "Cómic", "Poesía", "Teatro", "Biografía y memorias", "Ensayo", "Historia", "Ciencia",
        "Filosofía", "Psicología", "Autoayuda", "Negocios y economía", "Política", "Religión", "Arte", "Viajes", "Cocina", "Deporte",
    )

    // De lo más concreto a lo más general: la primera coincidencia gana.
    private val RULES = listOf(
        "Ciencia ficción" to listOf("science fiction", "ciencia ficcion", "sci fi", "dystopia", "distopia", "space opera"),
        "Fantasía" to listOf("fantasy", "fantasia", "magic", "dragons"),
        "Terror" to listOf("horror", "terror", "ghost stories"),
        "Misterio y thriller" to listOf("mystery", "detective", "crime", "thriller", "suspense", "policiaca", "policiaco", "misterio", "novela negra", "intriga", "noir"),
        "Romántica" to listOf("romance", "romantic", "romantica", "love stories"),
        "Novela histórica" to listOf("historical fiction", "novela historica", "fiction historical"),
        "Cómic" to listOf("comics", "graphic novels", "comic", "manga"),
        "Infantil" to listOf("juvenile fiction picture", "picture books", "infantil", "children"),
        "Juvenil" to listOf("young adult", "juvenile", "juvenil"),
        "Poesía" to listOf("poetry", "poesia", "poems", "poemas"),
        "Teatro" to listOf("drama", "teatro", "plays"),
        "Biografía y memorias" to listOf("biography", "autobiography", "memoir", "biografia", "memorias", "autobiografia"),
        "Autoayuda" to listOf("self help", "autoayuda", "personal development", "desarrollo personal", "motivational"),
        "Negocios y economía" to listOf("business", "economics", "management", "empresa", "economia", "negocios", "finance", "finanzas"),
        "Psicología" to listOf("psychology", "psicologia"),
        "Filosofía" to listOf("philosophy", "filosofia"),
        "Política" to listOf("political", "politics", "politica"),
        "Religión" to listOf("religion", "theology", "teologia", "spirituality"),
        "Cocina" to listOf("cooking", "cocina", "recetas", "cookbook"),
        "Viajes" to listOf("travel", "viajes"),
        "Deporte" to listOf("sports", "deporte", "deportes"),
        "Arte" to listOf("art", "arte", "music", "musica", "photography", "architecture"),
        "Historia" to listOf("history", "historia"),
        "Ciencia" to listOf("science", "ciencia", "mathematics", "physics", "biology", "nature", "medical", "matematicas", "fisica", "biologia"),
        "Ensayo" to listOf("essays", "ensayo", "literary criticism", "social science"),
        "Novela" to listOf("fiction", "novela", "narrativa", "literary", "literatura", "novel"),
    )

    private fun norm(s: String) = " " + Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim() + " "

    /** El género que mejor encaja con una lista de categorías, o "" si ninguna encaja. */
    fun from(categories: List<String>): String {
        if (categories.isEmpty()) return ""
        val texts = categories.map(::norm)
        // Se cuenta qué regla aparece en más categorías; a igualdad, la más concreta (la primera).
        val score = RULES.map { (g, words) -> g to texts.count { t -> words.any { w -> t.contains(" $w ") } } }
        val best = score.filter { it.second > 0 }.maxByOrNull { it.second } ?: return ""
        // Si "Novela" empata con algo más concreto, gana lo concreto.
        return score.firstOrNull { it.second == best.second }?.first ?: best.first
    }

    /** Palabra de búsqueda en inglés para un género (los catálogos clasifican en inglés). */
    fun subjectFor(genre: String): String = when (genre) {
        "Misterio y thriller" -> "thriller"
        "Ciencia ficción" -> "science fiction"
        "Fantasía" -> "fantasy"
        "Romántica" -> "romance"
        "Novela histórica" -> "historical fiction"
        "Terror" -> "horror"
        "Juvenil" -> "young adult"
        "Novela" -> "fiction"
        "Autoayuda" -> "self-help"
        "Psicología" -> "psychology"
        "Negocios y economía" -> "business"
        "Biografía y memorias" -> "biography"
        "Historia" -> "history"
        "Ciencia" -> "science"
        "Filosofía" -> "philosophy"
        "Ensayo" -> "essays"
        "" -> ""
        else -> genre.lowercase()
    }
}
