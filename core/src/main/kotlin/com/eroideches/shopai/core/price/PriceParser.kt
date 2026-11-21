package com.eroideches.shopai.core.price

import com.eroideches.shopai.core.model.Money

/**
 * Legge prezzi in euro dal testo delle pagine. Accetta solo prezzi esplicitamente in € (simbolo, "EUR", "euro");
 * qualunque altra valuta (US $, £, CHF, zł, ...) restituisce null: ShopAI non converte mai.
 */
object PriceParser {
    private val foreign = Regex(
        """(US\s?\$|\$|£|¥|₹|CHF|USD|GBP|PLN|zł|SEK|NOK|DKK|kr\b|CZK|Kč|HUF|Ft\b|RON|lei\b|BRL|R\$|CAD|AUD|JPY|CNY|RMB)""",
        RegexOption.IGNORE_CASE,
    )
    private val euroMark = Regex("""(€|\bEUR\b|\beuro\b)""", RegexOption.IGNORE_CASE)
    private val number = Regex("""\d{1,3}(?:[.\s  ']\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")

    /** "23,99 €", "€ 1.299,00", "EUR 12.50", "12€99" (formato Amazon spezzato) → Money; altrimenti null. */
    fun parseEuro(text: String?): Money? {
        if (text.isNullOrBlank()) return null
        val t = text.replace(' ', ' ').trim()
        if (!euroMark.containsMatchIn(t)) return null
        if (foreign.containsMatchIn(t)) return null
        // "12€99" o "12 € 99"
        Regex("""(\d+)\s?€\s?(\d{2})\b""").find(t)?.let { m ->
            return Money(m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong())
        }
        val m = number.find(t) ?: return null
        return parseNumber(m.value)
    }

    /** Numero senza valuta ("1.299,90", "1299.9", "12,5"). Da usare solo quando la valuta è già confermata EUR. */
    fun parseNumber(raw: String): Money? {
        var s = raw.trim().replace(Regex("""[\s  ']"""), "")
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (decimals == 3 && s.count { it == ',' } >= 1 && s.length > 4) s.replace(",", "") else s.replace(',', '.')
            }
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                // "1.299" = milleduecentonovantanove (stile italiano); "12.99" = decimali
                if (decimals == 3 || s.count { it == '.' } > 1) s.replace(".", "") else s
            }
            else -> s
        }
        val v = s.toDoubleOrNull() ?: return null
        if (v <= 0 || v > 1_000_000) return null
        return Money.ofEuros(v)
    }

    /** Prezzo da JSON: valore numerico o stringa + codice valuta. Accetta solo EUR. */
    fun fromJson(value: String?, currency: String?, assumeEuro: Boolean = false, minorUnits: Boolean = false): Money? {
        if (value.isNullOrBlank()) return null
        val cur = currency?.trim()?.uppercase()
        if (cur != null && cur.isNotEmpty() && cur != "EUR" && cur != "€") return null
        if (cur == null || cur.isEmpty()) {
            // Senza codice valuta: va bene solo se il testo stesso contiene € o il sito è noto in EUR.
            parseEuro(value)?.let { return it }
            if (!assumeEuro || foreign.containsMatchIn(value)) return null
        } else {
            parseEuro(value)?.let { return it }
        }
        if (foreign.containsMatchIn(value)) return null
        val digits = number.find(value)?.value ?: return null
        if (minorUnits && digits.all { it.isDigit() }) {
            val c = digits.toLongOrNull() ?: return null
            return if (c > 0) Money(c) else null
        }
        return parseNumber(digits)
    }

    private val freeShipping = Regex(
        """(spedizione|consegna|invio)\s+(gratuita|gratis)|gratuita\b|free\s+shipping|spedizione\s+inclusa|0,00\s?€\s+(di\s+)?spedizione""",
        RegexOption.IGNORE_CASE,
    )

    private val conditional = Regex("""(?:oltre|sopra|superiori? a|a partire da|minimo(?: d'ordine)?(?: di)?)\s*(?:i\s+)?(\d+(?:[.,]\d{1,2})?)\s?€?""", RegexOption.IGNORE_CASE)

    /**
     * Costo di spedizione da testi come "+ 3,99 € di spedizione", "Consegna GRATUITA", "Spedizione: 4,90 €".
     * "Gratuita per ordini oltre 29 €" vale gratuita solo se [itemPrice] supera la soglia.
     * Restituisce Money.ZERO se gratuita, null se non si capisce.
     */
    fun parseShipping(text: String?, itemPrice: Money? = null): Money? {
        if (text.isNullOrBlank()) return null
        if (freeShipping.containsMatchIn(text)) {
            val cond = conditional.find(text) ?: return Money.ZERO
            val threshold = parseNumber(cond.groupValues[1]) ?: return null
            return if (itemPrice != null && itemPrice >= threshold) Money.ZERO else null
        }
        return parseEuro(text)
    }
}
