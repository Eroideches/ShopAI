package com.eroideches.shopai.core.api

import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.rules.SiteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** AliExpress Affiliate API (aliexpress.affiliate.product.query): prezzi in EUR, spedizione verso l'Italia. */
object AliExpressApi {
    private val json = Json { ignoreUnknownKeys = true }
    const val ENDPOINT = "https://api-sg.aliexpress.com/sync"
    const val METHOD = "aliexpress.affiliate.product.query"

    fun searchRequest(appKey: String, appSecret: String, trackingId: String?, query: String, timestampMs: Long = System.currentTimeMillis(), pageSize: Int = 50): HttpSpec {
        val params = sortedMapOf(
            "app_key" to appKey.trim(),
            "method" to METHOD,
            "timestamp" to timestampMs.toString(),
            "sign_method" to "sha256",
            "keywords" to query,
            "target_currency" to "EUR",
            "target_language" to "IT",
            "ship_to_country" to "IT",
            "page_size" to pageSize.toString(),
            "page_no" to "1",
        )
        if (!trackingId.isNullOrBlank()) params["tracking_id"] = trackingId.trim()
        params["sign"] = sign(params, appSecret.trim())
        val qs = params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        return HttpSpec(method = "POST", url = ENDPOINT, body = qs, contentType = "application/x-www-form-urlencoded;charset=utf-8")
    }

    /** Firma HMAC-SHA256 dei parametri ordinati (chiave+valore concatenati), esadecimale maiuscolo. */
    fun sign(params: Map<String, String>, secret: String): String {
        val base = params.toSortedMap().filterKeys { it != "sign" }.entries.joinToString("") { it.key + it.value }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    fun parseSearch(body: String, rule: SiteRule): Result<List<Offer>> = runCatching {
        val root = json.parseToJsonElement(body) as JsonObject
        (root["error_response"] as? JsonObject)?.let { e ->
            val msg = e.s("msg") ?: e.s("sub_msg") ?: "errore AliExpress"
            val code = e.s("code")
            throw IllegalStateException(
                when (code) {
                    "IncompleteSignature", "InvalidSignature" -> "App Secret non valido (firma rifiutata)"
                    "InvalidAppKey", "AppKeyNotExist", "isv.appkey-not-exists" -> "App Key non valida"
                    "InsufficientPermission", "AppCallLimited" -> "app non ancora approvata o limite di chiamate raggiunto"
                    else -> msg
                },
            )
        }
        val resp = (root["aliexpress_affiliate_product_query_response"] as? JsonObject)?.get("resp_result") as? JsonObject
            ?: throw IllegalStateException("risposta inattesa da AliExpress")
        val code = resp.s("resp_code")
        if (code != null && code != "200") throw IllegalStateException(resp.s("resp_msg") ?: "errore $code")
        val products = ((resp["result"] as? JsonObject)?.get("products") as? JsonObject)?.get("product") as? JsonArray
            ?: return@runCatching emptyList()
        products.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val title = p.s("product_title") ?: return@mapNotNull null
            val price = PriceParser.fromJson(p.s("target_sale_price") ?: p.s("target_app_sale_price"), p.s("target_sale_price_currency") ?: "EUR")
                ?: return@mapNotNull null
            val url = p.s("promotion_link") ?: p.s("product_detail_url") ?: return@mapNotNull null
            val rate = p.s("evaluate_rate")?.trimEnd('%')?.toDoubleOrNull()
            Offer(
                siteId = rule.id,
                title = title,
                price = price,
                url = url,
                imageUrl = p.s("product_main_image_url"),
                shipping = Shipping.UNKNOWN,
                description = p.s("second_level_category_name") ?: p.s("first_level_category_name"),
                rating = rate?.let { Math.round(it / 20 * 10) / 10.0 },
                reviews = p.s("lastest_volume")?.toIntOrNull(),
                seller = p.s("shop_name"),
                condition = Condition.NEW,
            )
        }
    }

    private fun JsonObject.s(k: String): String? = when (val v: JsonElement? = this[k]) {
        is JsonPrimitive -> v.contentOrNull
        else -> null
    }
}
