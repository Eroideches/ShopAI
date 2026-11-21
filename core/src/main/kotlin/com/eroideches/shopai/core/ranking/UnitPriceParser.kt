package com.eroideches.shopai.core.ranking

import com.eroideches.shopai.core.model.Money

data class UnitPrice(val value: Money, val unit: String) {
    fun format() = "${value.format()}/$unit"
}

/** Prezzo per unità dal titolo: "500 g", "2 kg", "1,5 l", "750 ml", "6 x 330 ml", "12 pezzi", "conf. da 6". */
object UnitPriceParser {
    private val multi = Regex("""(\d+)\s?[x×]\s?(\d+(?:[.,]\d+)?)\s?(kg|g|gr|l|lt|ml|cl)\b""", RegexOption.IGNORE_CASE)
    private val qty = Regex("""(\d+(?:[.,]\d+)?)\s?(kg|g|gr|grammi|l|lt|litri|ml|cl)\b""", RegexOption.IGNORE_CASE)
    private val pieces = Regex(
        """(?:(?:confezione|conf\.?|pack|set|kit)\s?(?:da|di)?\s?(\d+))|(?:(\d+)\s?(?:pezzi|pz|pcs|unità|capsule|bustine|rotoli|pile|batterie)\b)""",
        RegexOption.IGNORE_CASE,
    )

    fun unitPrice(title: String, total: Money): UnitPrice? {
        multi.find(title)?.let { m ->
            val n = m.groupValues[1].toDouble()
            val per = m.groupValues[2].replace(',', '.').toDouble()
            return fromQuantity(n * per, m.groupValues[3], total)
        }
        qty.find(title)?.let { m ->
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            return fromQuantity(v, m.groupValues[2], total)
        }
        pieces.find(title)?.let { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() ?: return null
            if (n in 2..1000) return UnitPrice(Money(Math.round(total.cents.toDouble() / n)), "pz")
        }
        return null
    }

    private fun fromQuantity(v: Double, unitRaw: String, total: Money): UnitPrice? {
        if (v <= 0) return null
        val (base, unit) = when (unitRaw.lowercase()) {
            "kg" -> v to "kg"
            "g", "gr", "grammi" -> v / 1000 to "kg"
            "l", "lt", "litri" -> v to "l"
            "ml" -> v / 1000 to "l"
            "cl" -> v / 100 to "l"
            else -> return null
        }
        if (base <= 0.0) return null
        return UnitPrice(Money(Math.round(total.cents / base)), unit)
    }
}
