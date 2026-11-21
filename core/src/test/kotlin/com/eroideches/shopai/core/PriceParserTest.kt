package com.eroideches.shopai.core

import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.price.PriceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {
    @Test fun italianFormats() {
        assertEquals(Money(2399), PriceParser.parseEuro("23,99 €"))
        assertEquals(Money(129900), PriceParser.parseEuro("€ 1.299,00"))
        assertEquals(Money(129990), PriceParser.parseEuro("1.299,90€"))
        assertEquals(Money(1250), PriceParser.parseEuro("EUR 12.50"))
        assertEquals(Money(1299), PriceParser.parseEuro("12€99"))
        assertEquals(Money(500), PriceParser.parseEuro("5 €"))
        assertEquals(Money(1500000), PriceParser.parseEuro("15.000 €"))
    }

    @Test fun foreignCurrenciesAreRejectedNotConverted() {
        assertNull(PriceParser.parseEuro("US $23.99"))
        assertNull(PriceParser.parseEuro("$12.00"))
        assertNull(PriceParser.parseEuro("£9.99"))
        assertNull(PriceParser.parseEuro("23,99"))
        assertNull(PriceParser.parseEuro("CHF 20.00 (circa 21 €)"))
        assertNull(PriceParser.fromJson("12.99", "USD"))
        assertNull(PriceParser.fromJson("12.99", null, assumeEuro = false))
        assertEquals(Money(1299), PriceParser.fromJson("12.99", "EUR"))
        assertEquals(Money(1299), PriceParser.fromJson("1299", null, assumeEuro = true, minorUnits = true))
    }

    @Test fun shipping() {
        assertEquals(Money.ZERO, PriceParser.parseShipping("Consegna GRATUITA mer 26 nov"))
        assertEquals(Money(399), PriceParser.parseShipping("+ 3,99 € di spedizione"))
        assertEquals(Money(490), PriceParser.parseShipping("Spedizione: 4,90 €"))
        assertEquals(Money.ZERO, PriceParser.parseShipping("Spedizione gratuita per ordini oltre 29 €", Money(3500)))
        assertNull(PriceParser.parseShipping("Spedizione gratuita per ordini oltre 29 €", Money(1500)))
        assertNull(PriceParser.parseShipping("Spedizione veloce"))
    }

    @Test fun moneyFormat() {
        assertEquals("1.299,90 €", Money(129990).format())
        assertEquals("0,99 €", Money(99).format())
    }
}
