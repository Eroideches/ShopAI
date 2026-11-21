package com.eroideches.shopai.core.ranking

import com.eroideches.shopai.core.model.Condition

/**
 * Quanto un titolo corrisponde a ciò che si cerca, da 0 a 1.
 * - copertura delle parole della ricerca (tollera plurali e piccoli errori);
 * - numeri e modelli ("15", "s24", "256gb") devono esserci, altrimenti il punteggio crolla;
 * - parole da accessorio (cover, pellicola, cavo, "compatibile con", ...) penalizzano se non sono nella ricerca;
 * - usato/ricondizionato penalizzati se si cerca il nuovo.
 */
class RelevanceScorer(
    /** 0 = filtro accessori spento, 1 = normale, 2 = severo. */
    private val accessoryStrictness: Int = 1,
) {
    data class Score(val value: Double, val reason: String? = null)

    fun score(query: String, title: String, condition: Condition = Condition.UNKNOWN, wantNew: Boolean = true): Score {
        val q = Text.tokens(query)
        if (q.isEmpty()) return Score(0.0, "ricerca vuota")
        val titleNorm = Text.normalize(title)
        val tt = Text.tokens(title)
        if (tt.isEmpty()) return Score(0.0, "titolo vuoto")
        val tStems = tt.map(Text::stem).toSet()
        val tSet = tt.toSet()

        var matched = 0.0
        var missingNumbers = 0
        for (tok in q) {
            val hit = when {
                tok in tSet -> 1.0
                Text.stem(tok) in tStems -> 1.0
                Text.hasDigit(tok) -> if (titleNorm.replace(" ", "").contains(tok)) 0.9 else 0.0
                tok.length >= 5 && tt.any { it.length >= 4 && Text.levenshtein(it, tok) <= 1 } -> 0.8
                tok.length >= 4 && tt.any { it.startsWith(tok) || (it.length >= 4 && tok.startsWith(it)) } -> 0.7
                else -> 0.0
            }
            if (hit == 0.0 && Text.hasDigit(tok)) missingNumbers++
            matched += hit
        }
        var s = matched / q.size
        var reason: String? = null

        if (missingNumbers > 0) {
            s *= 0.25
            reason = "modello o numero diverso"
        } else {
            // Numeri nel titolo che contraddicono la ricerca ("iphone 15" vs titolo "iphone 13")
            val qNums = q.filter { it.all(Char::isDigit) }.toSet()
            if (qNums.isNotEmpty()) {
                val qWords = q.filterNot(Text::hasDigit).toSet()
                val conflicting = tt.withIndex().any { (i, t) ->
                    t.all(Char::isDigit) && t !in qNums && i > 0 && tt[i - 1] in qWords && tt.getOrNull(i + 1) != "in"
                }
                if (conflicting && tt.none { it in qNums }) {
                    s *= 0.3
                    reason = "modello diverso"
                }
            }
        }

        if (accessoryStrictness > 0) {
            val qn = Text.normalize(query)
            val acc = accessoryHits(titleNorm, qn)
            if (acc > 0) {
                s *= if (accessoryStrictness >= 2) 0.15 else 0.3
                reason = reason ?: "sembra un accessorio"
            }
        }

        if (wantNew && (condition == Condition.USED || condition == Condition.REFURBISHED)) {
            s *= 0.5
            reason = reason ?: "usato o ricondizionato"
        }
        // Piccolo bonus per titoli concisi (meno "keyword stuffing").
        if (tt.size <= 12 && s > 0.5) s = minOf(1.0, s + 0.03)
        return Score(s.coerceIn(0.0, 1.0), reason)
    }

    private fun accessoryHits(titleNorm: String, queryNorm: String): Int {
        val words = titleNorm.split(' ')
        val qStems = Text.tokens(queryNorm).map(Text::stem).toSet()
        var n = 0
        for (w in accessoryWords) {
            val re = Regex("\\b$w\\b")
            if (re.containsMatchIn(queryNorm)) continue
            for (m in re.findAll(titleNorm)) {
                // "con custodia di ricarica", "custodia inclusa": è una dotazione, non un accessorio venduto da solo.
                val before = titleNorm.substring(0, m.range.first).trim().split(' ').takeLast(2)
                val after = titleNorm.substring(m.range.last + 1).trim().split(' ').take(3)
                val included = before.any { it in includedMarkers } || after.any { it.startsWith("inclus") || it == "incl" }
                if (!included) { n++; break }
            }
        }
        // "cover per iphone 15", "compatibile con galaxy s24": il prodotto è PER ciò che si cerca.
        val forRe = Regex("\\b(?:per|for|compatibile con|compatibili con|compatible with|adatto a|adatta per|adatto per)\\s+([a-z0-9]+)")
        for (m in forRe.findAll(titleNorm)) {
            if (Text.stem(m.groupValues[1]) in qStems && !Regex("\\b(per|for|compatibile)\\b").containsMatchIn(queryNorm)) { n++; break }
        }
        if (words.isEmpty()) return 0
        return n
    }

    private val includedMarkers = setOf("con", "e", "+", "incluso", "inclusa", "compreso", "compresa", "with", "and", "plus")

    companion object {
        val accessoryWords = listOf(
            "cover", "custodia", "custodie", "case", "pellicola", "pellicole", "vetro temperato", "protezione schermo",
            "screen protector", "cavo", "cavi", "caricatore", "caricabatterie", "alimentatore", "adattatore", "supporto",
            "staffa", "skin", "sticker", "adesivo", "adesivi", "cinturino", "cinturini", "laccetto", "portachiavi",
            "gommini", "copri", "astuccio", "borsa", "sacca", "kit pulizia", "ricambio", "ricambi", "pennino",
            "auricolari di ricambio", "dock", "hub", "stand", "tappetino", "fodera", "involucro", "bumper", "flip",
            "portafoglio", "wallet", "batteria di ricambio", "proteggi schermo", "guscio",
        )
    }
}
