package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.rules.SiteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI

/**
 * Cerca prodotti dentro un JSON qualsiasi: gli oggetti che hanno insieme un titolo, un prezzo in € e un link.
 * Non dipende dalla struttura del sito, quindi sopravvive ai cambi di grafica e di API interne.
 */
class JsonOfferFinder(private val rule: SiteRule, private val baseUrl: String) {

    fun find(text: String): List<Offer> {
        val el = parseLenient(text) ?: return emptyList()
        return find(el)
    }

    fun find(root: JsonElement): List<Offer> {
        val out = mutableListOf<Offer>()
        var budget = 200_000
        fun walk(e: JsonElement, depth: Int, inheritedCurrency: String?) {
            if (depth > 40 || budget-- <= 0) return
            when (e) {
                is JsonObject -> {
                    val cur = currencyOf(e) ?: inheritedCurrency
                    val offer = tryOffer(e, cur)
                    if (offer != null) { out += offer; return }
                    for ((_, v) in e) walk(v, depth + 1, cur)
                }
                is JsonArray -> for (v in e) walk(v, depth + 1, inheritedCurrency)
                is JsonPrimitive -> {
                    // JSON dentro una stringa (succede spesso negli stati incorporati)
                    if (e.isString && e.content.length > 50) {
                        val c = e.content.trimStart()
                        if ((c.startsWith("{") || c.startsWith("[")) && depth < 10) parseLenient(c)?.let { walk(it, depth + 1, inheritedCurrency) }
                    }
                }
            }
        }
        walk(root, 0, null)
        return out.distinctBy { it.key }
    }

    private fun tryOffer(o: JsonObject, currency: String?): Offer? {
        val title = firstString(o, titleKeys)?.let(::cleanText)?.takeIf { it.length >= 6 && it.any(Char::isLetter) && !it.startsWith("http") }
            ?: return null
        val price = priceFrom(o, currency) ?: return null
        val url = firstString(o, urlKeys)?.let(::resolveUrl)?.takeIf { rule.ownsUrl(it) }
            ?: rule.productUrlTemplate?.let { t -> firstString(o, idKeys)?.let { t.replace("{id}", it) } }
            ?: return null
        val image = imageFrom(o)
        val shipping = shippingFrom(o, currency, price)
        val rating = ratingFrom(o)
        val reviews = firstValue(o, reviewKeys)?.let { prim(it)?.filter(Char::isDigit)?.toIntOrNull() }
        val condition = firstString(o, conditionKeys)?.let(::conditionOf) ?: Condition.UNKNOWN
        val desc = firstString(o, descKeys)?.let(::cleanText)?.take(220)
        val delivery = firstString(o, deliveryKeys)?.let(::cleanText)?.take(80)
        val seller = firstValue(o, sellerKeys)?.let { v -> if (v is JsonObject) firstString(v, listOf("name", "username", "shopName")) else prim(v) }
        return Offer(
            siteId = rule.id, title = title, price = price, url = url, imageUrl = image, shipping = shipping,
            description = desc, delivery = delivery, rating = rating, reviews = reviews, seller = seller, condition = condition,
        )
    }

    // ---- prezzi ----

    private fun currencyOf(o: JsonObject): String? = firstString(o, currencyKeys)?.takeIf { it.length in 1..4 }

    private fun priceFrom(o: JsonObject, currency: String?): Money? {
        for (k in priceKeys) {
            val v = getCi(o, k) ?: continue
            val m = priceValue(v, currency, depth = 0)
            if (m != null) return m
        }
        return null
    }

    private fun priceValue(v: JsonElement, currency: String?, depth: Int): Money? {
        if (depth > 3) return null
        return when (v) {
            is JsonPrimitive -> if (v is JsonNull || v.booleanOrNull != null) null
            else PriceParser.fromJson(v.content, currency, rule.assumeEuro, minorUnits = rule.minorUnits && !v.isString)
            is JsonObject -> {
                val cur = currencyOf(v) ?: currency
                if (cur != null && cur.uppercase() !in setOf("EUR", "€")) return null
                for (k in formattedPriceKeys) getCi(v, k)?.let { prim(it) }?.let { PriceParser.parseEuro(it) }?.let { return it }
                for (k in numericPriceKeys) {
                    val inner = getCi(v, k) ?: continue
                    val minor = k.contains("cent", ignoreCase = true)
                    if (inner is JsonPrimitive && !minor) priceValue(inner, cur, depth + 1)?.let { return it }
                    if (inner is JsonPrimitive && minor) inner.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }?.let { return Money(it) }
                    if (inner is JsonObject) priceValue(inner, cur, depth + 1)?.let { return it }
                }
                null
            }
            is JsonArray -> v.firstOrNull()?.let { priceValue(it, currency, depth + 1) }
        }
    }

    private fun shippingFrom(o: JsonObject, currency: String?, price: Money): Shipping {
        for (k in freeShipKeys) if ((getCi(o, k) as? JsonPrimitive)?.booleanOrNull == true) return Shipping.FREE
        for (k in shippingKeys) {
            val v = getCi(o, k) ?: continue
            when (v) {
                is JsonPrimitive -> {
                    if (v is JsonNull) continue
                    val txt = v.content
                    PriceParser.parseShipping(txt, price)?.let { return Shipping(it, if (v.isString) cleanText(txt).take(60) else null) }
                    if (!v.isString) PriceParser.fromJson(txt, currency, rule.assumeEuro, rule.minorUnits)?.let { return Shipping(it) }
                    if (txt == "0" || txt == "0.0") return Shipping.FREE
                }
                is JsonObject -> {
                    priceValue(v, currency, 0)?.let { return Shipping(it) }
                    firstString(v, listOf("text", "label", "title", "displayText"))?.let { t -> PriceParser.parseShipping(t, price)?.let { return Shipping(it, cleanText(t).take(60)) } }
                }
                is JsonArray -> (v.firstOrNull() as? JsonObject)?.let { first ->
                    for (sk in listOf("shippingCost", "cost", "price", "shippingRate")) getCi(first, sk)?.let { priceValue(it, currency, 0) }?.let { return Shipping(it) }
                }
            }
        }
        return Shipping.UNKNOWN
    }

    private fun ratingFrom(o: JsonObject): Double? {
        for (k in ratingKeys) {
            val v = getCi(o, k) ?: continue
            val raw = when (v) {
                is JsonObject -> firstString(v, listOf("ratingValue", "value", "average", "rating"))
                else -> prim(v)
            } ?: continue
            val pct = raw.endsWith("%")
            val d = raw.trimEnd('%').replace(',', '.').toDoubleOrNull() ?: continue
            val r = if (pct || d > 5) d / 20 else d
            if (r in 0.5..5.0) return Math.round(r * 10) / 10.0
        }
        return null
    }

    private fun imageFrom(o: JsonObject): String? {
        for (k in imageKeys) {
            val v = getCi(o, k) ?: continue
            val s = when (v) {
                is JsonPrimitive -> v.contentOrNull
                is JsonArray -> v.firstOrNull()?.let { if (it is JsonObject) firstString(it, listOf("url", "src", "imageUrl", "contentUrl")) else prim(it) }
                is JsonObject -> firstString(v, listOf("url", "src", "imageUrl", "contentUrl", "large", "medium", "thumbnail"))
            } ?: continue
            val u = resolveUrl(s)
            if (u.startsWith("http")) return u
        }
        return null
    }

    // ---- utilità ----

    private fun resolveUrl(raw: String): String {
        val s = raw.trim().replace("\\/", "/")
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.isEmpty() -> s
            else -> runCatching { URI(baseUrl).resolve(s.replace(" ", "%20")).toString() }.getOrDefault(s)
        }
    }

    private fun getCi(o: JsonObject, key: String): JsonElement? =
        o[key] ?: o.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private fun firstValue(o: JsonObject, keys: List<String>): JsonElement? {
        for (k in keys) getCi(o, k)?.takeIf { it !is JsonNull }?.let { return it }
        return null
    }

    private fun firstString(o: JsonObject, keys: List<String>): String? {
        for (k in keys) {
            val v = getCi(o, k) ?: continue
            prim(v)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun prim(v: JsonElement): String? = (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun cleanText(s: String) = org.jsoup.parser.Parser.unescapeEntities(s.replace(Regex("<[^>]+>"), " "), false)
        .replace(Regex("\\s+"), " ").trim()

    private fun conditionOf(s: String): Condition {
        val t = s.lowercase()
        return when {
            "refurb" in t || "ricondizion" in t || "rigenerat" in t -> Condition.REFURBISHED
            "used" in t || "usato" in t || "usata" in t || "second" in t -> Condition.USED
            "new" in t || "nuovo" in t || "nuova" in t -> Condition.NEW
            else -> Condition.UNKNOWN
        }
    }

    companion object {
        private val lenient = Json { isLenient = true; ignoreUnknownKeys = true }

        fun parseLenient(text: String): JsonElement? = runCatching { lenient.parseToJsonElement(text) }.getOrNull()

        val titleKeys = listOf(
            "title", "name", "productName", "product_title", "productTitle", "goods_name", "goodsName", "subject",
            "displayName", "itemTitle", "item_title", "headline", "label_title",
        )
        val priceKeys = listOf(
            "salePrice", "sale_price", "target_sale_price", "app_sale_price", "currentPrice", "current_price", "finalPrice",
            "final_price", "price", "priceInfo", "price_info", "prices", "offers", "offer", "displayPrice", "formattedPrice",
            "priceStr", "price_str", "minPrice", "min_price", "lowPrice", "priceAmount", "amount", "pricing",
        )
        val formattedPriceKeys = listOf(
            "formatted", "formattedPrice", "formattedValue", "priceStr", "price_str", "displayPrice", "display", "text",
            "priceText", "str", "amountWithSymbol", "price_text", "salePriceStr", "formattedAmount", "minPriceFormatted",
        )
        val numericPriceKeys = listOf(
            "salePrice", "sale_price", "price", "amount", "value", "current", "minPrice", "min_price", "lowPrice", "finalPrice",
            "amountInCents", "priceInCents", "centAmount", "cents", "minAmount",
        )
        val currencyKeys = listOf("currency", "currencyCode", "priceCurrency", "currency_code", "cur", "currencySymbol", "target_sale_price_currency")
        val urlKeys = listOf(
            "url", "link", "href", "productUrl", "product_url", "detailUrl", "detail_url", "product_detail_url", "promotion_link",
            "seo_link", "seoUrl", "goods_url", "link_url", "linkUrl", "itemWebUrl", "canonicalUrl", "pdpUrl", "pageUrl", "itemUrl",
        )
        val idKeys = listOf("goods_id", "goodsId", "productId", "product_id", "itemId", "item_id", "asin", "sku", "id")
        val imageKeys = listOf(
            "image", "imageUrl", "image_url", "img", "imgUrl", "img_url", "thumbnail", "thumb_url", "thumbUrl", "product_main_image_url",
            "mainImage", "main_image", "picture", "pic", "picUrl", "hd_thumb_url", "imageSrc", "images", "primaryImage", "media",
        )
        val shippingKeys = listOf(
            "shipping", "shippingCost", "shipping_cost", "shippingFee", "shipping_fee", "deliveryCost", "delivery_cost", "shippingPrice",
            "freight", "shippingOptions", "shippingDetails", "logistics", "deliveryPrice",
        )
        val freeShipKeys = listOf("freeShipping", "free_shipping", "isFreeShipping", "is_free_shipping", "freeDelivery")
        val ratingKeys = listOf("aggregateRating", "rating", "ratingValue", "averageRating", "average_rating", "stars", "star", "evaluate_rate", "goods_score", "reviewRating")
        val reviewKeys = listOf("reviewCount", "ratingCount", "reviewsCount", "review_count", "comment_num", "commentCount", "totalReviews", "reviews_num")
        val conditionKeys = listOf("condition", "itemCondition", "conditionDisplayName", "item_condition", "state")
        val descKeys = listOf("description", "shortDescription", "short_description", "subtitle", "snippet", "shortDesc", "brand_name")
        val deliveryKeys = listOf("deliveryText", "delivery", "deliveryTime", "delivery_time", "estimatedDelivery", "deliveryDate", "arrival")
        val sellerKeys = listOf("seller", "sellerName", "seller_name", "shop", "store", "storeName", "merchant", "shopName")
    }
}
