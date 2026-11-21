package com.eroideches.shopai.core.ranking

import java.text.Normalizer

/** Normalizzazione per confrontare ricerca e titoli: minuscole, senza accenti, unità attaccate ai numeri. */
object Text {
    private val diacritics = Regex("\\p{M}+")
    private val units = "gb|tb|mb|mah|w|kw|v|hz|mm|cm|m|ml|cl|l|g|kg|mg|pz|pezzi|pollici|inch|mp|ah|k"

    val stopwords = setOf(
        "di", "da", "del", "della", "dei", "delle", "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
        "e", "ed", "con", "in", "su", "a", "al", "alla", "the", "and", "with", "of", "x",
    )

    fun normalize(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(diacritics, "")
        t = t.replace('’', '\'').replace(Regex("[^a-z0-9.,+]+"), " ")
        // "256 gb" -> "256gb", "1,5 l" -> "1.5l"
        t = t.replace(Regex("(\\d)[,](\\d)"), "$1.$2")
        t = t.replace(Regex("\\b(\\d+(?:\\.\\d+)?)\\s+($units)\\b"), "$1$2")
        t = t.replace(Regex("[.,](?!\\d)"), " ").replace(Regex("(?<!\\d)[.,]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun tokens(s: String): List<String> =
        normalize(s).split(' ').filter { it.isNotBlank() && it !in stopwords && it != "+" }

    /** Radice grezza per singolare/plurale italiano: "cuffie"/"cuffia" -> "cuff". */
    fun stem(t: String): String =
        if (t.length >= 4 && !t.any(Char::isDigit) && t.last() in "aeiou") t.dropLast(1).let { if (it.length > 4 && it.last() in "aeiouh") it.dropLast(1) else it } else t

    fun hasDigit(t: String) = t.any { it.isDigit() }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
