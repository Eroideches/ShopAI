package com.eroideches.shopai.core

import com.eroideches.shopai.core.extract.BlockDetector
import com.eroideches.shopai.core.extract.ExtractionChain
import com.eroideches.shopai.core.extract.JsonOfferFinder
import com.eroideches.shopai.core.extract.NetworkBody
import com.eroideches.shopai.core.extract.PageCapture
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.rules.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorsTest {
    @Test fun rulesFileParses() {
        val f = TestRules.file
        assertTrue(f.sites.size >= 20)
        assertTrue(f.sites.all { it.color.matches(Regex("#[0-9A-Fa-f]{6}")) })
        assertTrue(f.sites.all { it.searchUrlFor("a b", "20096").startsWith("https://") })
        assertEquals(setOf("ebay", "aliexpress", "amazon"), f.guides.map { it.siteId }.toSet())
    }

    @Test fun networkJsonTemuStyle() {
        val rule = TestRules.site("temu")
        val body = TestRules.resource("temu_search.json")
        val offers = JsonOfferFinder(rule, "https://www.temu.com/it/search_result.html").find(body)
        assertEquals(3, offers.size)
        val first = offers.first()
        assertEquals(Money(1299), first.price)           // da priceStr "12,99€", non dai centesimi
        assertTrue(first.url.startsWith("https://www.temu.com/it/"))
        assertTrue(first.shipping.isFree)
        assertEquals(Money(849), offers[1].price)        // 849 centesimi (minorUnits)
        assertTrue(offers[2].url.contains("goods_id=601099"))   // URL dal template
    }

    @Test fun nonEuroJsonIsDropped() {
        val rule = TestRules.site("aliexpress")
        val json = """{"items":[{"title":"Cuffie wireless bluetooth","price":{"value":"9.99","currency":"USD"},"productUrl":"https://it.aliexpress.com/item/1.html"},
            {"title":"Cuffie wireless bluetooth pro","price":{"value":"10.49","currency":"EUR"},"productUrl":"https://it.aliexpress.com/item/2.html"}]}"""
        val offers = JsonOfferFinder(rule, "https://it.aliexpress.com/").find(json)
        assertEquals(1, offers.size)
        assertEquals(Money(1049), offers.single().price)
    }

    @Test fun jsonLdAndEmbeddedState() {
        val rule = TestRules.site("mediaworld")
        val html = TestRules.resource("structured_page.html")
        val r = ExtractionChain.run(PageCapture("https://www.mediaworld.it/it/search.html?query=tv", html), rule)
        assertEquals(Method.STRUCTURED, r.method)
        assertFalse(r.degraded)
        assertEquals(4, r.offers.size)
        assertTrue(r.offers.any { it.price == Money(49900) && it.rating == 4.6 })
        assertTrue(r.offers.any { it.title.contains("Soundbar") && it.shipping.cost == Money(990) })
    }

    @Test fun amazonSelectorsThenHeuristicFallbackWhenLayoutChanges() {
        val rule = TestRules.site("amazon")
        val html = TestRules.resource("amazon_search.html")
        val ok = ExtractionChain.run(PageCapture("https://www.amazon.it/s?k=airpods", html), rule)
        assertEquals(Method.SELECTORS, ok.method)
        assertFalse(ok.degraded)
        assertEquals(4, ok.offers.size)
        val a = ok.offers.first { it.title.startsWith("Apple AirPods Pro 2") }
        assertEquals(Money(22900), a.price)               // il prezzo barrato 279 € viene ignorato
        assertTrue(a.shipping.isFree)
        assertEquals(4.7, a.rating)

        // Stessa pagina con classi e attributi rimescolati (Amazon ha cambiato grafica): i selettori non trovano nulla
        val scrambled = html
            .replace("data-component-type=\"s-search-result\"", "data-x=\"res\"")
            .replace("s-result-item", "zz-q91")
            .replace("a-offscreen", "k8f2").replace("a-price", "pp-x").replace("s-image", "im-7")
            .replace("<h2", "<div").replace("</h2>", "</div>")
        val fb = ExtractionChain.run(PageCapture("https://www.amazon.it/s?k=airpods", scrambled), rule)
        assertEquals(Method.HEURISTIC, fb.method)
        assertTrue(fb.degraded)
        assertEquals(4, fb.offers.size)
        val b = fb.offers.first { it.title.startsWith("Apple AirPods Pro 2") }
        assertEquals(Money(22900), b.price)
        assertTrue(b.imageUrl!!.startsWith("https://m.media-amazon.com/"))
    }

    @Test fun networkPreferredForJsSites() {
        val rule = TestRules.site("temu")
        val cap = PageCapture(
            "https://www.temu.com/it/search_result.html?search_key=cuffie",
            "<html><body><div id=app></div></body></html>",
            listOf(NetworkBody("https://www.temu.com/api/poppy/v1/search", TestRules.resource("temu_search.json"))),
        )
        val r = ExtractionChain.run(cap, rule)
        assertEquals(Method.NETWORK, r.method)
        assertFalse(r.degraded)
    }

    @Test fun captchaIsDetected() {
        val rule = TestRules.site("amazon")
        val html = "<html><body><form action='/errors/validateCaptcha'><p>Inserisci i caratteri che vedi</p></form></body></html>"
        val r = ExtractionChain.run(PageCapture("https://www.amazon.it/s?k=x", html), rule)
        assertTrue(r.blocked)
        assertTrue(BlockDetector.check(html, rule).blocked)
        assertTrue(r.offers.isEmpty())
    }
}
