package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.rules.SiteRule
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Riconosce i risultati senza selettori: cerca le "schede" che si ripetono nella pagina e che contengono
 * un link a una pagina del sito, un'immagine e un prezzo in €. Funziona anche quando il sito cambia
 * classi CSS, perché si basa solo sulla struttura ripetuta.
 */
object HeuristicExtractor {
    private val euroText = Regex("""(€\s?\d|\d\s?€|\bEUR\s?\d)""")

    fun extract(doc: Document, rule: SiteRule): List<Offer> {
        val body = doc.body() ?: return emptyList()
        body.select("script, style, noscript, svg, header, footer, nav").remove()

        // 1) per ogni link al sito con un prezzo vicino, risali fino al contenitore minimo con link + prezzo + immagine
        val containers = LinkedHashSet<Element>()
        for (a in body.select("a[href]")) {
            val href = a.absUrl("href")
            if (href.isEmpty() || !rule.ownsUrl(href)) continue
            var cur: Element? = a
            var hops = 0
            while (cur != null && cur != body && hops < 8) {
                if (cur.selectFirst("img") != null && euroText.containsMatchIn(cur.text()) && cur.text().length < 1500) {
                    containers += cur; break
                }
                cur = cur.parent(); hops++
            }
        }
        if (containers.isEmpty()) return emptyList()

        // 2) raggruppa per "firma" (tag + classi senza numeri/hash) e tieni il gruppo che si ripete di più
        val groups = containers.groupBy(::signature)
        val best = groups.values.filter { it.size >= 3 }.maxByOrNull { g -> g.size * 10 + g.count { DomText.imageOf(it) != null } }
            ?: containers.toList()

        // Togli i contenitori annidati l'uno nell'altro
        val cards = best.filter { c -> best.none { other -> other !== c && c.parents().contains(other) } }
        return cards.mapNotNull { fromCard(it, rule) }.distinctBy { it.key }
    }

    private fun signature(e: Element): String {
        val classes = e.classNames().map { it.replace(Regex("[0-9]+"), "").replace(Regex("[_-][a-zA-Z]{5,6}$"), "") }.sorted()
        return e.tagName() + "." + classes.joinToString(".") + "@" + (e.parent()?.tagName() ?: "")
    }

    private fun fromCard(card: Element, rule: SiteRule): Offer? {
        val links = card.select("a[href]").map { it to it.absUrl("href") }.filter { rule.ownsUrl(it.second) }
        if (links.isEmpty() && !(card.tagName() == "a" && rule.ownsUrl(card.absUrl("href")))) return null
        val url = links.maxByOrNull { (a, _) -> (if (a.selectFirst("img") != null) 50 else 0) + a.text().length }?.second
            ?: card.absUrl("href")

        val title = titleOf(card) ?: return null
        val price = priceOf(card) ?: return null
        val all = card.text()
        val shipText = sentenceAround(all, Regex("(spedizion|consegna gratuita|consegna gratis|spese di|\\+\\s?\\d+[.,]\\d{2}\\s?€\\s?(di\\s)?sped)", RegexOption.IGNORE_CASE))
        val shipping = shipText?.let { t -> PriceParser.parseShipping(t, price)?.let { Shipping(it, t.take(60)) } } ?: Shipping.UNKNOWN
        val delivery = sentenceAround(all, Regex("(arriva|consegna (prevista|entro|stimata)|ricevilo|consegna (gio|ven|sab|dom|lun|mar|mer)|domani|in \\d+-?\\d* giorni)", RegexOption.IGNORE_CASE))
            ?.takeIf { it.length <= 90 }
        val rating = Regex("""(\d[.,]\d)\s?(su 5|/5|stelle|out of 5|★)""").find(all)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        return Offer(
            siteId = rule.id, title = title, price = price, url = url, imageUrl = DomText.imageOf(card),
            shipping = shipping, delivery = delivery, rating = rating, condition = DomText.conditionOf(title + " " + all.take(300)),
        )
    }

    private fun titleOf(card: Element): String? {
        val cands = mutableListOf<String>()
        card.select("h1, h2, h3, h4, [class*=title], [class*=Title], [class*=name], [class*=Name]").forEach { cands += it.text() }
        card.select("a[title]").forEach { cands += it.attr("title") }
        card.select("a[aria-label]").forEach { cands += it.attr("aria-label") }
        card.select("img[alt]").forEach { cands += it.attr("alt") }
        card.select("a").forEach { cands += it.ownText(); if (it.selectFirst("img") == null) cands += it.text() }
        card.select("span, p, div").filter { it.children().size <= 1 }.forEach { cands += it.text() }
        return cands.map(DomText::clean)
            .filter { it.length in 8..300 && it.count(Char::isLetter) >= 5 && !euroText.containsMatchIn(it) && !notTitle.containsMatchIn(it) }
            .maxByOrNull { scoreTitle(it) }
    }

    private val notTitle = Regex("^(consegna|spedizion|arriva|ricevilo|disponibil|solo \\d|\\d[.,]\\d su 5|aggiungi|vedi |scopri|sponsorizzat|più vendut|risparmia|coupon)", RegexOption.IGNORE_CASE)

    private fun scoreTitle(t: String): Int = minOf(t.length, 140) - (if (t.length > 200) 50 else 0) + t.split(' ').size

    private fun priceOf(card: Element): Money? {
        // elementi "foglia" (o quasi) che contengono un prezzo, esclusi i barrati
        val cands = card.select("*").filter { e ->
            val own = e.ownText()
            (euroText.containsMatchIn(own) || (e.children().size <= 3 && e.text().length <= 24 && euroText.containsMatchIn(e.text()))) &&
                !DomText.isStruck(e, card)
        }
        for (e in cands) {
            val t = e.text()
            if (Regex("(spedizion|/\\s?(kg|l|pz|unit)|al (kg|litro)|risparmi|sconto|coupon)", RegexOption.IGNORE_CASE).containsMatchIn(t)) continue
            PriceParser.parseEuro(t.take(40))?.let { return it }
        }
        return null
    }

    private fun sentenceAround(text: String, re: Regex): String? {
        val m = re.find(text) ?: return null
        val start = maxOf(0, m.range.first - 30)
        val end = minOf(text.length, m.range.last + 50)
        return DomText.clean(text.substring(start, end))
    }
}
