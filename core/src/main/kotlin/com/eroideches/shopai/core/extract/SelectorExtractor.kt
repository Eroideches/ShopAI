package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.rules.SiteRule
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Selettori CSS delle regole remote: il metodo più preciso finché il sito non cambia grafica. */
object SelectorExtractor {
    fun extract(doc: Document, rule: SiteRule): List<Offer> {
        val sel = rule.selectors ?: return emptyList()
        return doc.select(sel.card).mapNotNull { card ->
            runCatching { fromCard(card, rule) }.getOrNull()
        }.distinctBy { it.key }
    }

    private fun fromCard(card: Element, rule: SiteRule): Offer? {
        val sel = rule.selectors!!
        val title = textOf(card, sel.title)?.takeIf { it.length >= 4 } ?: return null
        val priceEl = card.select(sel.price).firstOrNull { !DomText.isStruck(it, card) } ?: return null
        val priceText = priceEl.attr("content").ifBlank { priceEl.text() }
        val price = PriceParser.parseEuro(priceText)
            ?: (if (priceEl.hasAttr("content") && rule.assumeEuro) PriceParser.parseNumber(priceText) else null)
            ?: return null
        val linkEl = (if (card.`is`(sel.link)) card else card.selectFirst(sel.link)) ?: return null
        val url = linkEl.absUrl("href").ifEmpty { return null }
        val shippingText = sel.shipping?.let { textOf(card, it) }
        val shipping = shippingText?.let { t -> PriceParser.parseShipping(t, price)?.let { Shipping(it, DomText.clean(t).take(60)) } }
            ?: Shipping.UNKNOWN
        return Offer(
            siteId = rule.id,
            title = title,
            price = price,
            url = url,
            imageUrl = DomText.imageOf(card),
            shipping = shipping,
            description = sel.description?.let { textOf(card, it) }?.take(220),
            delivery = sel.delivery?.let { textOf(card, it) }?.take(80),
            rating = sel.rating?.let { textOf(card, it) }?.let { Regex("(\\d[.,]\\d)").find(it)?.value?.replace(',', '.')?.toDoubleOrNull() },
            reviews = sel.reviews?.let { textOf(card, it) }?.filter(Char::isDigit)?.toIntOrNull(),
            condition = sel.condition?.let { textOf(card, it) }?.let(DomText::conditionOf) ?: DomText.conditionOf(title),
        )
    }

    private fun textOf(card: Element, css: String): String? {
        val e = card.selectFirst(css) ?: return null
        val t = e.attr("title").ifBlank { e.attr("aria-label") }.ifBlank { e.text() }.ifBlank { e.attr("alt") }
        return DomText.clean(t).ifEmpty { null }
    }
}
