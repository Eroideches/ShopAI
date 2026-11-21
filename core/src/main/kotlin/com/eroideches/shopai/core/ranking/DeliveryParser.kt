package com.eroideches.shopai.core.ranking

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Giorni stimati di consegna da testi come "Arriva gio 27 nov", "Consegna in 3-5 giorni", "domani". */
object DeliveryParser {
    private val months = mapOf(
        "gen" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mag" to 5, "giu" to 6,
        "lug" to 7, "ago" to 8, "set" to 9, "ott" to 10, "nov" to 11, "dic" to 12,
    )

    fun daysFrom(text: String?, today: LocalDate = LocalDate.now()): Int? {
        if (text.isNullOrBlank()) return null
        val t = text.lowercase()
        if ("oggi" in t) return 0
        if ("domani" in t && "dopodomani" !in t) return 1
        if ("dopodomani" in t) return 2
        Regex("""(\d{1,2})\s?[-–]\s?(\d{1,2})\s?(giorni|gg|giorni lavorativi)""").find(t)?.let { return it.groupValues[2].toInt() }
        Regex("""(?:in|entro)\s?(\d{1,2})\s?(giorni|gg)""").find(t)?.let { return it.groupValues[1].toInt() }
        // "27 nov" o "27 novembre" (prendiamo l'ultima data citata: in "27 nov - 2 dic" conta la seconda)
        val dates = Regex("""(\d{1,2})\s(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*""").findAll(t).toList()
        val m = dates.lastOrNull() ?: return null
        val day = m.groupValues[1].toInt()
        val month = months[m.groupValues[2]] ?: return null
        var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        if (date.isBefore(today.minusDays(7))) date = date.plusYears(1)
        return ChronoUnit.DAYS.between(today, date).toInt().coerceAtLeast(0)
    }
}
