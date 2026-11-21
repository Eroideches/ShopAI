package com.eroideches.shopai.core.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Regole dei siti: URL di ricerca, metodi di lettura, selettori, cookie, guide per le chiavi.
 * Vivono in rules/sites.json nella repo (scaricate dall'app una volta al giorno) con una copia negli asset.
 */
@Serializable
data class RulesFile(
    val version: Int,
    val minAppVersion: Int = 1,
    val updated: String? = null,
    val sites: List<SiteRule>,
    val guides: List<KeyGuide> = emptyList(),
) {
    fun site(id: String) = sites.firstOrNull { it.id == id }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; explicitNulls = false }
        fun parse(text: String): RulesFile = json.decodeFromString(serializer(), text)
    }
}

@Serializable
enum class FetchMode {
    @SerialName("http") HTTP,
    @SerialName("webview") WEBVIEW,
}

@Serializable
enum class Method {
    /** API ufficiale (solo con chiavi). */
    @SerialName("api") API,
    /** JSON intercettati dalle richieste di rete della pagina. */
    @SerialName("network") NETWORK,
    /** JSON-LD e stati incorporati (__NEXT_DATA__, __INITIAL_STATE__, runParams...). */
    @SerialName("structured") STRUCTURED,
    /** Selettori CSS delle regole. */
    @SerialName("selectors") SELECTORS,
    /** Riconoscimento generico delle schede ripetute. */
    @SerialName("heuristic") HEURISTIC,
}

@Serializable
data class Selectors(
    val card: String,
    val title: String,
    val price: String,
    val link: String = "a[href]",
    val image: String? = "img",
    val shipping: String? = null,
    val delivery: String? = null,
    val rating: String? = null,
    val reviews: String? = null,
    val description: String? = null,
    val condition: String? = null,
)

@Serializable
data class DetailSelectors(
    val price: String? = null,
    val shipping: String? = null,
    val delivery: String? = null,
)

@Serializable
data class SiteRule(
    val id: String,
    val name: String,
    /** Colore del tag, "#RRGGBB". */
    val color: String,
    val group: String,
    val domains: List<String>,
    /** {q} = ricerca codificata, {zip} = CAP. */
    val searchUrl: String,
    val eanSearchUrl: String? = null,
    val fetch: FetchMode = FetchMode.WEBVIEW,
    val methods: List<Method> = listOf(Method.NETWORK, Method.STRUCTURED, Method.SELECTORS, Method.HEURISTIC),
    /** "ebay" / "aliexpress": usa l'API se ci sono le chiavi. */
    val api: String? = null,
    val assumeEuro: Boolean = true,
    /** Nei JSON i prezzi interi sono in centesimi (es. Temu). */
    val minorUnits: Boolean = false,
    val defaultEnabled: Boolean = true,
    val experimental: Boolean = false,
    val usedMarket: Boolean = false,
    /** Condizione da assegnare a tutti i prodotti del sito (es. Back Market = ricondizionato). */
    val defaultCondition: com.eroideches.shopai.core.model.Condition? = null,
    val needsSetup: Boolean = false,
    val setupUrl: String? = null,
    /** JS eseguito nella pagina di setup (es. impostare il CAP). {zip} viene sostituito. */
    val setupScript: String? = null,
    val cookies: Map<String, String> = emptyMap(),
    val selectors: Selectors? = null,
    val detail: DetailSelectors? = null,
    /** Sottostringhe degli URL di rete da catturare (vuoto = tutti i JSON). */
    val networkPatterns: List<String> = emptyList(),
    /** Template per costruire il link quando il JSON dà solo l'id: {id}. */
    val productUrlTemplate: String? = null,
    val waitMs: Long = 2500,
    val scrolls: Int = 2,
    val blockMarkers: List<String> = emptyList(),
    /** Apre le pagine prodotto scelte per leggere spedizione e consegna esatte. */
    val enrich: Boolean = false,
    val desktopUserAgent: Boolean = false,
    val appPackage: String? = null,
    /** Nota mostrata se la spedizione non è indicata (es. "gratis con Prime"). */
    val shippingHint: String? = null,
) {
    fun searchUrlFor(query: String, zip: String): String = searchUrl
        .replace("{q}", URLEncoder.encode(query.trim(), "UTF-8"))
        .replace("{zip}", zip)

    fun eanUrlFor(ean: String, zip: String): String = (eanSearchUrl ?: searchUrl)
        .replace("{q}", URLEncoder.encode(ean.trim(), "UTF-8"))
        .replace("{zip}", zip)

    fun ownsUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        return domains.any { d -> host == d || host.endsWith(".$d") }
    }
}

@Serializable
data class KeyField(val id: String, val label: String, val hint: String = "", val secret: Boolean = true)

@Serializable
data class GuideStep(val text: String, val link: String? = null, val linkLabel: String? = null)

@Serializable
data class KeyGuide(
    val siteId: String,
    val title: String,
    val summary: String = "",
    val fields: List<KeyField> = emptyList(),
    val steps: List<GuideStep> = emptyList(),
    val notes: List<String> = emptyList(),
)
