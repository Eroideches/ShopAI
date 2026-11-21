package com.eroideches.shopai.core.api

import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.rules.SiteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.Base64

/** eBay Browse API (marketplace EBAY_IT, prezzi nativi in €, spedizione calcolata sul CAP). */
object EbayApi {
    private val json = Json { ignoreUnknownKeys = true }
    const val SCOPE = "https://api.ebay.com/oauth/api_scope"

    fun tokenRequest(clientId: String, clientSecret: String): HttpSpec {
        val basic = Base64.getEncoder().encodeToString("${clientId.trim()}:${clientSecret.trim()}".toByteArray())
        return HttpSpec(
            method = "POST",
            url = "https://api.ebay.com/identity/v1/oauth2/token",
            headers = mapOf("Authorization" to "Basic $basic"),
            body = "grant_type=client_credentials&scope=" + URLEncoder.encode(SCOPE, "UTF-8"),
            contentType = "application/x-www-form-urlencoded",
        )
    }

    /** (token, secondi di validità) oppure errore leggibile. */
    fun parseToken(body: String): Result<Pair<String, Long>> = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        o["access_token"]?.jsonPrimitive?.contentOrNull?.let { tok ->
            return@runCatching tok to (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L)
        }
        val err = o["error_description"]?.jsonPrimitive?.contentOrNull ?: o["error"]?.jsonPrimitive?.contentOrNull
        throw IllegalStateException(
            when {
                err == null -> "risposta inattesa da eBay"
                err.contains("client authentication failed", true) -> "App ID o Cert ID non validi (usa il keyset Production)"
                else -> err
            },
        )
    }

    fun searchRequest(token: String, query: String, zip: String, condition: ConditionFilter, limit: Int = 50): HttpSpec {
        val cond = when (condition) {
            ConditionFilter.NEW -> ",conditions:{NEW}"
            ConditionFilter.USED -> ",conditions:{USED}"
            ConditionFilter.ANY -> ""
        }
        val filter = "buyingOptions:{FIXED_PRICE},priceCurrency:EUR,deliveryCountry:IT$cond"
        val url = "https://api.ebay.com/buy/browse/v1/item_summary/search?q=" + URLEncoder.encode(query, "UTF-8") +
            "&limit=$limit&filter=" + URLEncoder.encode(filter, "UTF-8")
        return HttpSpec(
            method = "GET",
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "X-EBAY-C-MARKETPLACE-ID" to "EBAY_IT",
                "X-EBAY-C-ENDUSERCTX" to "contextualLocation=" + URLEncoder.encode("country=IT,zip=$zip", "UTF-8"),
                "Accept-Language" to "it-IT",
            ),
        )
    }

    fun parseSearch(body: String, rule: SiteRule): Result<List<Offer>> = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        errorOf(o)?.let { throw IllegalStateException(it) }
        val items = o["itemSummaries"] as? JsonArray ?: return@runCatching emptyList()
        items.mapNotNull { el ->
            val it = el as? JsonObject ?: return@mapNotNull null
            val title = it.str("title") ?: return@mapNotNull null
            val priceObj = it["price"] as? JsonObject ?: return@mapNotNull null
            val price = PriceParser.fromJson(priceObj.str("value"), priceObj.str("currency")) ?: return@mapNotNull null
            val url = it.str("itemWebUrl") ?: return@mapNotNull null
            val ship = (it["shippingOptions"] as? JsonArray)?.firstOrNull() as? JsonObject
            val shipping = ship?.let { s ->
                val costObj = s["shippingCost"] as? JsonObject
                when {
                    costObj != null && costObj.str("value")?.toDoubleOrNull() == 0.0 -> Shipping.FREE
                    costObj != null -> PriceParser.fromJson(costObj.str("value"), costObj.str("currency"))?.let { c -> Shipping(c) } ?: Shipping.UNKNOWN
                    s.str("shippingCostType") == "CALCULATED" -> Shipping(null, "calcolata al checkout")
                    else -> Shipping.UNKNOWN
                }
            } ?: Shipping.UNKNOWN
            val delivery = ship?.str("maxEstimatedDeliveryDate")?.take(10)?.let(::formatDate)?.let { "Arriva entro $it" }
            val condId = it.str("conditionId")?.toIntOrNull()
            val condition = when {
                condId == null -> Condition.UNKNOWN
                condId == 1000 || condId == 1500 -> Condition.NEW
                condId in 2000..2999 -> Condition.REFURBISHED
                else -> Condition.USED
            }
            val seller = it["seller"] as? JsonObject
            Offer(
                siteId = rule.id,
                title = title,
                price = price,
                url = url,
                imageUrl = (it["image"] as? JsonObject)?.str("imageUrl")
                    ?: ((it["thumbnailImages"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("imageUrl"),
                shipping = shipping,
                description = it.str("shortDescription") ?: it.str("condition")?.let { c -> "Condizione: $c" },
                delivery = delivery,
                rating = seller?.str("feedbackPercentage")?.toDoubleOrNull()?.let { p -> Math.round(p / 20 * 10) / 10.0 },
                reviews = seller?.str("feedbackScore")?.toIntOrNull(),
                seller = seller?.str("username"),
                condition = condition,
            )
        }
    }

    private fun errorOf(o: JsonObject): String? {
        val errs = o["errors"] as? JsonArray ?: return null
        val first = errs.firstOrNull() as? JsonObject ?: return "errore eBay"
        return first.str("longMessage") ?: first.str("message") ?: "errore eBay"
    }

    private fun formatDate(iso: String): String = runCatching {
        val d = java.time.LocalDate.parse(iso)
        val f = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ITALIAN)
        d.format(f)
    }.getOrDefault(iso)

    private fun JsonObject.str(k: String): String? = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    @Suppress("unused")
    private fun JsonObject.arr(k: String) = this[k]?.jsonArray
}
