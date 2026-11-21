package com.eroideches.shopai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Condition { NEW, USED, REFURBISHED, UNKNOWN }

/** Spedizione: [cost] null = non indicata dal sito (si mostra "vedi sito"). */
@Serializable
data class Shipping(val cost: Money? = null, val note: String? = null) {
    val isFree: Boolean get() = cost == Money.ZERO
    val isKnown: Boolean get() = cost != null

    companion object {
        val FREE = Shipping(Money.ZERO)
        val UNKNOWN = Shipping(null)
    }
}

/** Un prodotto trovato su un sito, così come lo mostra il sito (prezzo nativo in €). */
@Serializable
data class Offer(
    val siteId: String,
    val title: String,
    val price: Money,
    val url: String,
    val imageUrl: String? = null,
    val shipping: Shipping = Shipping.UNKNOWN,
    val description: String? = null,
    val delivery: String? = null,
    val rating: Double? = null,
    val reviews: Int? = null,
    val seller: String? = null,
    val condition: Condition = Condition.UNKNOWN,
) {
    /** Prezzo + spedizione. Se la spedizione non è nota si usa il solo prezzo (e l'interfaccia lo dice). */
    val total: Money get() = price + (shipping.cost ?: Money.ZERO)

    /** Chiave stabile per salvati/storico: sito + URL senza parametri di tracciamento. */
    val key: String get() = "$siteId|${canonicalUrl(url)}"

    companion object {
        private val trackingParams = Regex(
            "^(utm_.*|ref|ref_|tag|pf_rd_.*|pd_rd_.*|_trkparms|_trksid|spm|scm|algo_.*|aff_.*|sk|qid|sr|crid|sprefix|dib.*|content-id|hash|epid|itmmeta|mkevt|mkcid|campid|toolid|customid)$",
        )

        fun canonicalUrl(url: String): String {
            val q = url.indexOf('?')
            if (q < 0) return url.substringBefore('#')
            val base = url.substring(0, q)
            val kept = url.substring(q + 1).substringBefore('#').split('&')
                .filter { it.isNotBlank() && !trackingParams.matches(it.substringBefore('=')) }
            return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
        }
    }
}
