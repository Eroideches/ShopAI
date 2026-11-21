package com.eroideches.shopai.sites

import com.eroideches.shopai.core.api.AliExpressApi
import com.eroideches.shopai.core.api.EbayApi
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.data.SecureKeyStore
import com.eroideches.shopai.net.Http

/** Chiamate alle API ufficiali con le chiavi inserite dall'utente nell'app. */
class ApiClients(private val http: Http, private val keys: SecureKeyStore) {
    private var ebayToken: Pair<String, Long>? = null
    private var ebayTokenFor: String? = null

    suspend fun hasKeys(api: String): Boolean = when (api) {
        "ebay" -> keys.get("ebay_client_id") != null && keys.get("ebay_client_secret") != null
        "aliexpress" -> keys.get("ali_app_key") != null && keys.get("ali_app_secret") != null
        else -> false
    }

    suspend fun search(api: String, rule: SiteRule, query: String, zip: String, condition: ConditionFilter): Result<List<Offer>> = when (api) {
        "ebay" -> ebay(rule, query, zip, condition)
        "aliexpress" -> aliexpress(rule, query)
        else -> Result.failure(IllegalArgumentException("API sconosciuta: $api"))
    }

    /** Verifica delle chiavi dal tutorial: fa una ricerca di prova e dice cosa non va. */
    suspend fun verify(api: String, rule: SiteRule, zip: String): Result<String> {
        if (!hasKeys(api)) return Result.failure(IllegalStateException("inserisci prima tutte le chiavi"))
        ebayToken = null
        return search(api, rule, "cuffie bluetooth", zip, ConditionFilter.NEW).map { "Funziona: ${it.size} prodotti di prova" }
    }

    private suspend fun ebayAccess(): Result<String> = runCatching {
        val id = keys.get("ebay_client_id") ?: error("App ID mancante")
        val secret = keys.get("ebay_client_secret") ?: error("Cert ID mancante")
        val now = System.currentTimeMillis()
        ebayToken?.let { (t, exp) -> if (ebayTokenFor == id && exp > now + 60_000) return@runCatching t }
        val page = http.execute(EbayApi.tokenRequest(id, secret))
        val (tok, secs) = EbayApi.parseToken(page.body).getOrThrow()
        ebayToken = tok to (now + secs * 1000)
        ebayTokenFor = id
        tok
    }

    private suspend fun ebay(rule: SiteRule, query: String, zip: String, condition: ConditionFilter): Result<List<Offer>> =
        ebayAccess().mapCatching { token ->
            val page = http.execute(EbayApi.searchRequest(token, query, zip, condition))
            if (page.code == 401) { ebayToken = null; error("token eBay scaduto, riprova") }
            EbayApi.parseSearch(page.body, rule).getOrThrow()
        }

    private suspend fun aliexpress(rule: SiteRule, query: String): Result<List<Offer>> = runCatching {
        val key = keys.get("ali_app_key") ?: error("App Key mancante")
        val secret = keys.get("ali_app_secret") ?: error("App Secret mancante")
        val page = http.execute(AliExpressApi.searchRequest(key, secret, keys.get("ali_tracking_id"), query))
        AliExpressApi.parseSearch(page.body, rule).getOrThrow()
    }
}
