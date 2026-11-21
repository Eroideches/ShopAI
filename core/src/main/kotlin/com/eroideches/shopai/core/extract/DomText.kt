package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Condition
import org.jsoup.nodes.Element

internal object DomText {
    private val struckClass = Regex("(old|strike|crossed|barrat|was|list-?price|before|original|rrp|prev|regular|a-text-price|del)", RegexOption.IGNORE_CASE)

    /** True se l'elemento (o un antenato vicino) è un prezzo barrato / di listino. */
    fun isStruck(e: Element, stop: Element): Boolean {
        var cur: Element? = e
        var hops = 0
        while (cur != null && cur != stop && hops < 4) {
            val tag = cur.tagName()
            if (tag == "del" || tag == "s" || tag == "strike") return true
            if (struckClass.containsMatchIn(cur.className())) return true
            val style = cur.attr("style")
            if (style.contains("line-through")) return true
            cur = cur.parent(); hops++
        }
        return false
    }

    fun imageOf(e: Element): String? {
        val img = e.selectFirst("img") ?: return null
        for (attr in listOf("src", "data-src", "data-lazy-src", "data-original", "data-srcset", "srcset")) {
            val v = img.absUrl(attr).ifEmpty { img.attr(attr) }
            if (v.isBlank() || v.startsWith("data:")) continue
            val first = v.split(",").first().trim().split(" ").first()
            val abs = if (first.startsWith("//")) "https:$first" else first
            if (abs.startsWith("http")) return abs
        }
        return null
    }

    fun conditionOf(text: String): Condition {
        val t = text.lowercase()
        return when {
            "ricondizionat" in t || "refurbished" in t || "rigenerat" in t -> Condition.REFURBISHED
            Regex("\\b(usato|usata|di seconda mano|pre-owned|used)\\b").containsMatchIn(t) -> Condition.USED
            Regex("\\b(nuovo|nuova|new|nuovo con)\\b").containsMatchIn(t) -> Condition.NEW
            else -> Condition.UNKNOWN
        }
    }

    fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()
}
