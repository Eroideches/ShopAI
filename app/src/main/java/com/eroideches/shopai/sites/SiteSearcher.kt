package com.eroideches.shopai.sites

import android.content.Context
import com.eroideches.shopai.core.extract.DetailEnricher
import com.eroideches.shopai.core.extract.ExtractionChain
import com.eroideches.shopai.core.extract.ExtractionResult
import com.eroideches.shopai.core.extract.PageCapture
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.rules.FetchMode
import com.eroideches.shopai.core.rules.Method
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.net.Http
import com.eroideches.shopai.net.WebViewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SiteOutcome(
    val siteId: String,
    val offers: List<Offer>,
    /** "api", "network", "structured", "selectors", "heuristic" */
    val method: String?,
    val degraded: Boolean,
    val blocked: Boolean,
    val error: String?,
    val ms: Long,
    val attempts: List<String> = emptyList(),
    val apiError: String? = null,
)

/**
 * Legge un sito: API ufficiale (se ci sono le chiavi) → pagina (HTTP o WebView) → catena di metodi con ripiego.
 * In caso di problemi salva un'istantanea (HTML + JSON) per la Diagnostica.
 */
class SiteSearcher(
    private val context: Context,
    private val http: Http,
    private val webviews: WebViewFetcher,
    private val api: ApiClients,
) {
    private val snapshots = File(context.filesDir, "diag").apply { mkdirs() }
    private val _health = kotlinx.coroutines.flow.MutableStateFlow<Map<String, SiteOutcome>>(emptyMap())
    /** Ultimo esito di ogni sito (per i pallini di stato in Impostazioni → Siti). */
    val health: kotlinx.coroutines.flow.StateFlow<Map<String, SiteOutcome>> = _health

    suspend fun search(rule: SiteRule, query: String, zip: String, condition: ConditionFilter, url: String? = null): SiteOutcome =
        doSearch(rule, query, zip, condition, url).also { o -> _health.value = _health.value + (rule.id to o) }
    private val json = Json { encodeDefaults = true }

    private suspend fun doSearch(rule: SiteRule, query: String, zip: String, condition: ConditionFilter, url: String?): SiteOutcome {
        val t0 = System.currentTimeMillis()
        var apiError: String? = null
        if (url == null && rule.api != null && Method.API in rule.methods && api.hasKeys(rule.api)) {
            val r = api.search(rule.api, rule, query, zip, condition)
            r.getOrNull()?.let { offers ->
                return SiteOutcome(rule.id, offers.map { fixCondition(it, rule) }, "api", false, false, null, System.currentTimeMillis() - t0)
            }
            apiError = r.exceptionOrNull()?.message
        }
        val target = url ?: rule.searchUrlFor(query, zip)
        val capture = runCatching { capture(rule, target) }.getOrElse { e ->
            return SiteOutcome(rule.id, emptyList(), null, true, false, e.message ?: "errore di rete", System.currentTimeMillis() - t0, apiError = apiError)
        } ?: return SiteOutcome(rule.id, emptyList(), null, true, false, "la pagina non ha risposto in tempo", System.currentTimeMillis() - t0, apiError = apiError)

        val res: ExtractionResult = withContext(Dispatchers.Default) { ExtractionChain.run(capture, rule) }
        val ms = System.currentTimeMillis() - t0
        if (res.offers.isEmpty() || res.degraded) saveSnapshot(rule, capture, res)
        val error = when {
            res.blocked -> "il sito chiede una verifica (CAPTCHA)"
            res.offers.isEmpty() -> "nessun prodotto letto"
            else -> null
        }
        return SiteOutcome(
            siteId = rule.id,
            offers = res.offers.map { fixCondition(it, rule) },
            method = res.method?.name?.lowercase(),
            degraded = res.degraded && res.offers.isNotEmpty(),
            blocked = res.blocked,
            error = error,
            ms = ms,
            attempts = res.attempts.map { "${it.method.name.lowercase()}: ${it.valid}/${it.found}" + (it.error?.let { e -> " ($e)" } ?: "") },
            apiError = apiError,
        )
    }

    /** Apre la pagina del prodotto per leggere spedizione e consegna esatte. */
    suspend fun enrich(rule: SiteRule, offer: Offer): Offer {
        val cap = runCatching { capture(rule, offer.url) }.getOrNull() ?: return offer
        return withContext(Dispatchers.Default) { runCatching { DetailEnricher.enrich(offer, cap, rule) }.getOrDefault(offer) }
    }

    suspend fun capture(rule: SiteRule, url: String): PageCapture? = when (rule.fetch) {
        FetchMode.HTTP -> {
            val p = http.get(url)
            if (p.code in 200..299 || p.body.length > 2000) PageCapture(p.url, p.body) else
                // pagina rifiutata via HTTP: riprova con il motore WebView
                webviews.fetch(rule, url)
        }
        FetchMode.WEBVIEW -> webviews.fetch(rule, url)
    }

    private fun fixCondition(o: Offer, rule: SiteRule): Offer =
        if (rule.defaultCondition != null && (o.condition == com.eroideches.shopai.core.model.Condition.UNKNOWN || o.condition == com.eroideches.shopai.core.model.Condition.NEW)) {
            o.copy(condition = rule.defaultCondition!!)
        } else o

    private fun saveSnapshot(rule: SiteRule, capture: PageCapture, res: ExtractionResult) = runCatching {
        File(snapshots, "${rule.id}.html").writeText(capture.html.take(3_000_000))
        File(snapshots, "${rule.id}.network.json").writeText(json.encodeToString(PageCapture.serializer(), capture.copy(html = "")).take(5_000_000))
        File(snapshots, "${rule.id}.result.json").writeText(json.encodeToString(ExtractionResult.serializer(), res.copy(offers = res.offers.take(5))))
    }

    fun snapshotFiles(): List<File> = snapshots.listFiles()?.toList().orEmpty()
}
