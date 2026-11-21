package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.rules.Method
import com.eroideches.shopai.core.rules.SiteRule
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class Attempt(val method: Method, val found: Int, val valid: Int, val error: String? = null)

@Serializable
data class ExtractionResult(
    val offers: List<Offer>,
    val method: Method?,
    /** True se è servito un metodo di riserva: probabilmente il sito ha cambiato qualcosa. */
    val degraded: Boolean,
    val attempts: List<Attempt>,
    val blocked: Boolean = false,
    val blockMarker: String? = null,
)

/** Controllo qualità di un singolo prodotto estratto. */
object QualityCheck {
    fun isValid(o: Offer, rule: SiteRule): Boolean =
        o.title.length >= 4 &&
            o.price.cents in 1..50_000_000 &&
            (o.url.startsWith("https://") || o.url.startsWith("http://")) &&
            rule.ownsUrl(o.url) &&
            (o.imageUrl == null || o.imageUrl.startsWith("http"))
}

/**
 * Prova i metodi di lettura nell'ordine delle regole; il primo che dà abbastanza prodotti validi vince.
 * Se nessuno raggiunge la soglia si tiene il migliore. Se vince un metodo diverso dal primo, il sito è "degradato".
 */
object ExtractionChain {
    private const val ENOUGH = 3

    fun run(capture: PageCapture, rule: SiteRule, methods: List<Method> = rule.methods): ExtractionResult {
        val order = methods.filter { it != Method.API }
        val attempts = mutableListOf<Attempt>()
        var best: Pair<Method, List<Offer>>? = null

        for (m in order) {
            val found = runCatching { extract(m, capture, rule) }
            val err = found.exceptionOrNull()
            if (err != null) {
                attempts += Attempt(m, 0, 0, err.message ?: err::class.simpleName)
                continue
            }
            val offers = found.getOrThrow()
            val valid = offers.filter { QualityCheck.isValid(it, rule) }
            attempts += Attempt(m, offers.size, valid.size)
            if (valid.size >= ENOUGH) {
                return ExtractionResult(valid, m, degraded = m != order.firstApplicable(rule, capture), attempts)
            }
            if (valid.isNotEmpty() && valid.size > (best?.second?.size ?: 0)) best = m to valid
        }
        val block = BlockDetector.check(capture.html, rule)
        if (best != null) return ExtractionResult(best.second, best.first, degraded = true, attempts, block.blocked, block.marker)
        return ExtractionResult(emptyList(), null, degraded = true, attempts, block.blocked, block.marker)
    }

    /** Il metodo "atteso": il primo della lista che ha senso per questa cattura (NETWORK solo se ci sono JSON). */
    private fun List<Method>.firstApplicable(rule: SiteRule, capture: PageCapture): Method? =
        firstOrNull { m ->
            when (m) {
                Method.NETWORK -> capture.network.isNotEmpty()
                Method.SELECTORS -> rule.selectors != null
                else -> true
            }
        }

    fun extract(m: Method, capture: PageCapture, rule: SiteRule): List<Offer> = when (m) {
        Method.API -> emptyList()
        Method.NETWORK -> {
            val finder = JsonOfferFinder(rule, capture.url)
            capture.network
                .filter { nb -> rule.networkPatterns.isEmpty() || rule.networkPatterns.any { nb.url.contains(it) } }
                .flatMap { finder.find(it.body) }
                .distinctBy { it.key }
        }
        Method.STRUCTURED -> StructuredExtractor.extract(Jsoup.parse(capture.html, capture.url), rule, capture.url)
        Method.SELECTORS -> SelectorExtractor.extract(Jsoup.parse(capture.html, capture.url), rule)
        Method.HEURISTIC -> HeuristicExtractor.extract(Jsoup.parse(capture.html, capture.url), rule)
    }
}
