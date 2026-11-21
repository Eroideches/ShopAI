package com.eroideches.shopai.core.ranking

import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import kotlinx.serialization.Serializable

@Serializable
enum class SortOrder { TOTAL, PRICE, RELEVANCE, DELIVERY }

@Serializable
enum class ConditionFilter { NEW, USED, ANY }

@Serializable
data class SelectionSettings(
    val maxPerSite: Int = 3,
    val minScore: Double = 0.55,
    /** Sotto questa frazione della mediana un prezzo è "anomalo" (accessorio, inserzione civetta). */
    val outlierRatio: Double = 0.35,
    val accessoryStrictness: Int = 1,
    val includeAccessories: Boolean = false,
    val condition: ConditionFilter = ConditionFilter.NEW,
    val maxPrice: Money? = null,
    val freeShippingOnly: Boolean = false,
    val maxDeliveryDays: Int? = null,
    val sort: SortOrder = SortOrder.TOTAL,
)

data class ScoredOffer(val offer: Offer, val score: Double, val unitPrice: UnitPrice? = null)

data class Discarded(val offer: Offer, val reason: String)

data class Selection(val chosen: List<ScoredOffer>, val discarded: List<Discarded>)

/**
 * Sceglie al massimo N prodotti per sito: prima filtra i pertinenti, poi tiene i più economici,
 * infine ordina tutto (di default per totale crescente, spedizione inclusa).
 */
class OfferSelector(private val settings: SelectionSettings = SelectionSettings()) {

    fun select(query: String, offers: List<Offer>, deliveryDays: (Offer) -> Int? = { DeliveryParser.daysFrom(it.delivery) }): Selection {
        val scorer = RelevanceScorer(if (settings.includeAccessories) 0 else settings.accessoryStrictness)
        val discarded = mutableListOf<Discarded>()
        val pertinent = mutableListOf<ScoredOffer>()

        for (o in offers.distinctBy { it.key }) {
            val condOk = when (settings.condition) {
                ConditionFilter.NEW -> o.condition == Condition.NEW || o.condition == Condition.UNKNOWN
                ConditionFilter.USED -> o.condition == Condition.USED || o.condition == Condition.REFURBISHED
                ConditionFilter.ANY -> true
            }
            if (!condOk) { discarded += Discarded(o, "condizione"); continue }
            val sc = scorer.score(query, o.title, o.condition, wantNew = settings.condition == ConditionFilter.NEW)
            if (sc.value < settings.minScore) { discarded += Discarded(o, sc.reason ?: "poco pertinente"); continue }
            if (settings.maxPrice != null && o.total > settings.maxPrice) { discarded += Discarded(o, "oltre il prezzo massimo"); continue }
            if (settings.freeShippingOnly && !o.shipping.isFree) { discarded += Discarded(o, "spedizione non gratuita"); continue }
            val days = settings.maxDeliveryDays?.let { deliveryDays(o) }
            if (settings.maxDeliveryDays != null && days != null && days > settings.maxDeliveryDays) {
                discarded += Discarded(o, "consegna troppo lenta"); continue
            }
            pertinent += ScoredOffer(o, sc.value, UnitPriceParser.unitPrice(o.title, o.total))
        }

        // Prezzi anomali rispetto alla mediana dei pertinenti (servono abbastanza campioni per fidarsi).
        val kept = if (pertinent.size >= 4 && !settings.includeAccessories) {
            val sorted = pertinent.map { it.offer.total.cents }.sorted()
            val median = sorted[sorted.size / 2]
            val floor = (median * settings.outlierRatio).toLong()
            pertinent.filter { so ->
                (so.offer.total.cents >= floor).also { ok -> if (!ok) discarded += Discarded(so.offer, "prezzo sospetto rispetto agli altri") }
            }
        } else pertinent

        // Per sito: i più economici tra i pertinenti (a parità di prezzo vince il più pertinente).
        val chosen = kept.groupBy { it.offer.siteId }.flatMap { (_, list) ->
            val ordered = list.sortedWith(compareBy<ScoredOffer> { it.offer.total }.thenByDescending { it.score })
            ordered.drop(settings.maxPerSite).forEach { discarded += Discarded(it.offer, "oltre i ${settings.maxPerSite} migliori del sito") }
            ordered.take(settings.maxPerSite)
        }

        val sorted = when (settings.sort) {
            SortOrder.TOTAL -> chosen.sortedWith(compareBy<ScoredOffer> { it.offer.total }.thenByDescending { it.score })
            SortOrder.PRICE -> chosen.sortedWith(compareBy<ScoredOffer> { it.offer.price }.thenBy { it.offer.total })
            SortOrder.RELEVANCE -> chosen.sortedWith(compareByDescending<ScoredOffer> { it.score }.thenBy { it.offer.total })
            SortOrder.DELIVERY -> chosen.sortedWith(compareBy<ScoredOffer> { deliveryDays(it.offer) ?: Int.MAX_VALUE }.thenBy { it.offer.total })
        }
        return Selection(sorted, discarded)
    }
}
