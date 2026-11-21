package com.eroideches.shopai.core

import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.core.ranking.DeliveryParser
import com.eroideches.shopai.core.ranking.OfferSelector
import com.eroideches.shopai.core.ranking.RelevanceScorer
import com.eroideches.shopai.core.ranking.SelectionSettings
import com.eroideches.shopai.core.ranking.UnitPriceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RankingTest {
    private val scorer = RelevanceScorer()

    @Test fun accessoriesArePenalized() {
        val q = "airpods pro 2"
        val real = scorer.score(q, "Apple AirPods Pro 2 Auricolari wireless con custodia di ricarica MagSafe (USB-C)").value
        val cover = scorer.score(q, "Custodia Cover in silicone per AirPods Pro 2, protezione antiurto").value
        assertTrue("prodotto vero $real", real > 0.8)
        assertTrue("cover $cover", cover < 0.4)
    }

    @Test fun numbersMustMatch() {
        val q = "iphone 15 128gb"
        assertTrue(scorer.score(q, "Apple iPhone 15 (128 GB) - Nero").value > 0.8)
        assertTrue(scorer.score(q, "Apple iPhone 13 (128 GB) - Nero").value < 0.5)
        assertTrue(scorer.score(q, "Apple iPhone 15 (256 GB) - Nero").value < 0.5)
    }

    @Test fun pluralsAndTypos() {
        assertTrue(scorer.score("cuffie bluetooth", "Cuffia Bluetooth over-ear con microfono").value > 0.7)
        assertTrue(scorer.score("aspirapolvere senza filo", "Aspirapolvere senza fili ciclonico 25 kPa").value > 0.7)
    }

    @Test fun usedPenalizedWhenWantingNew() {
        val n = scorer.score("nintendo switch oled", "Nintendo Switch OLED bianca", Condition.NEW).value
        val u = scorer.score("nintendo switch oled", "Nintendo Switch OLED bianca", Condition.USED).value
        assertTrue(u < n)
    }

    private fun offer(site: String, title: String, cents: Long, ship: Long? = 0, cond: Condition = Condition.NEW) =
        Offer(site, title, Money(cents), "https://www.$site.it/p/${title.hashCode()}", shipping = Shipping(ship?.let(::Money)), condition = cond)

    @Test fun selectsThreeCheapestPertinentPerSiteSortedByTotal() {
        val offers = listOf(
            offer("amazon", "Apple AirPods Pro 2 con custodia MagSafe USB-C", 22900),
            offer("amazon", "Apple AirPods Pro (2a generazione) USB-C", 21900, 0),
            offer("amazon", "Apple AirPods Pro 2 ricondizionati", 15900, cond = Condition.REFURBISHED),
            offer("amazon", "Cover per AirPods Pro 2", 899),
            offer("amazon", "Apple AirPods Pro 2 bianco", 23500),
            offer("amazon", "Apple AirPods Pro 2 originali", 24000),
            offer("ebay", "AirPods Pro 2 Apple nuovi sigillati", 19500, 500),
            offer("ebay", "Apple AirPods Pro 2nd gen USB-C nuovi", 18900, 990),
            offer("ebay", "Apple AirPods Pro 2 pellicola protettiva", 499, 0),
            offer("temu", "Auricolari wireless stile pro 2", 1299, 0),
        )
        val sel = OfferSelector(SelectionSettings()).select("airpods pro 2", offers)
        val titles = sel.chosen.map { it.offer.title }
        assertTrue(titles.none { "Cover" in it || "pellicola" in it || "ricondizionati" in it })
        assertEquals(3, sel.chosen.count { it.offer.siteId == "amazon" })
        assertEquals(sel.chosen.map { it.offer.total }, sel.chosen.map { it.offer.total }.sorted())
        assertTrue(sel.discarded.isNotEmpty())
        // il più economico pertinente di Amazon (219 €) deve esserci, il più caro (240 €) no
        assertTrue(titles.contains("Apple AirPods Pro (2a generazione) USB-C"))
        assertTrue(!titles.contains("Apple AirPods Pro 2 originali"))
    }

    @Test fun outliersGoToDiscarded() {
        val offers = (1..6).map { offer("ebay", "Samsung Galaxy S24 128GB nero $it", 60000L + it * 1000) } +
            offer("ebay", "Samsung Galaxy S24 128GB nero offerta", 3000)
        val sel = OfferSelector(SelectionSettings(maxPerSite = 10)).select("samsung galaxy s24 128gb", offers)
        assertTrue(sel.chosen.none { it.offer.price.cents == 3000L })
        assertTrue(sel.discarded.any { it.reason.contains("sospetto") })
    }

    @Test fun unitPrice() {
        assertEquals("5,00 €/kg", UnitPriceParser.unitPrice("Caffè in grani 500 g", Money(250))!!.format())
        assertEquals("1,00 €/l", UnitPriceParser.unitPrice("Acqua 6 x 1,5 l", Money(900))!!.format())
        assertEquals("0,50 €/pz", UnitPriceParser.unitPrice("Pile AA confezione da 12", Money(600))!!.format())
    }

    @Test fun delivery() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals(1, DeliveryParser.daysFrom("Consegna GRATUITA domani", today))
        assertEquals(6, DeliveryParser.daysFrom("Arriva gio 27 nov", today))
        assertEquals(5, DeliveryParser.daysFrom("Consegna in 3-5 giorni", today))
        assertEquals(41, DeliveryParser.daysFrom("Arriva 1 gen", today))
    }
}
