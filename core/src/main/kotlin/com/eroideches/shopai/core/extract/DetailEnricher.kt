package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.rules.SiteRule
import org.jsoup.Jsoup

/** Dalla pagina del prodotto ricava spedizione e consegna esatte (quando la ricerca non le mostra). */
object DetailEnricher {
    fun enrich(offer: Offer, capture: PageCapture, rule: SiteRule): Offer {
        val doc = Jsoup.parse(capture.html, capture.url)
        var shipping = offer.shipping
        var delivery = offer.delivery

        rule.detail?.shipping?.let { css ->
            doc.selectFirst(css)?.text()?.let { t -> PriceParser.parseShipping(t, offer.price)?.let { shipping = Shipping(it, DomText.clean(t).take(60)) } }
        }
        rule.detail?.delivery?.let { css -> doc.selectFirst(css)?.text()?.let { delivery = DomText.clean(it).take(80) } }

        val text = doc.body()?.text() ?: ""
        if (!shipping.isKnown) {
            Regex("""([^.]{0,40}(spedizione|consegna)[^.]{0,60})""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
                if (!shipping.isKnown) PriceParser.parseShipping(m.value, offer.price)?.let { shipping = Shipping(it, DomText.clean(m.value).take(60)) }
            }
        }
        if (delivery == null) {
            Regex("""((arriva|consegna|ricevilo)[^.]{0,20}(oggi|domani|\d{1,2}\s(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*(\s?[-–]\s?\d{1,2}\s(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*)?))""", RegexOption.IGNORE_CASE)
                .find(text)?.let { delivery = DomText.clean(it.value).take(80) }
        }
        return offer.copy(shipping = shipping, delivery = delivery)
    }
}
