package com.eroideches.shopai.core

import com.eroideches.shopai.core.api.AliExpressApi
import com.eroideches.shopai.core.api.EbayApi
import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.ranking.ConditionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTest {
    @Test fun ebayRequestUsesItalyAndZip() {
        val r = EbayApi.searchRequest("TOKEN", "airpods pro 2", "20096", ConditionFilter.NEW)
        assertEquals("EBAY_IT", r.headers["X-EBAY-C-MARKETPLACE-ID"])
        assertTrue(r.headers["X-EBAY-C-ENDUSERCTX"]!!.contains("zip%3D20096"))
        assertTrue(r.url.contains("priceCurrency%3AEUR"))
    }

    @Test fun ebayParse() {
        val offers = EbayApi.parseSearch(TestRules.resource("ebay_api.json"), TestRules.site("ebay")).getOrThrow()
        assertEquals(2, offers.size)
        assertEquals(Money(18900), offers[0].price)
        assertEquals(Money(490), offers[0].shipping.cost)
        assertEquals(Condition.NEW, offers[0].condition)
        assertTrue(offers[1].shipping.isFree)
    }

    @Test fun ebayErrorsAreReadable() {
        val e = EbayApi.parseToken("""{"error":"invalid_client","error_description":"client authentication failed"}""").exceptionOrNull()
        assertTrue(e!!.message!!.contains("Production"))
    }

    @Test fun aliexpressSignIsDeterministicHexUpper() {
        val s = AliExpressApi.sign(mapOf("b" to "2", "a" to "1", "sign" to "x"), "secret")
        assertEquals(64, s.length)
        assertEquals(s.uppercase(), s)
        assertEquals(s, AliExpressApi.sign(mapOf("a" to "1", "b" to "2"), "secret"))
        val req = AliExpressApi.searchRequest("123456", "secret", "shopai", "cuffie", timestampMs = 1700000000000)
        assertTrue(req.body!!.contains("target_currency=EUR"))
        assertTrue(req.body!!.contains("sign="))
    }

    @Test fun aliexpressParse() {
        val json = """{"aliexpress_affiliate_product_query_response":{"resp_result":{"resp_code":200,"resp_msg":"ok","result":{"products":{"product":[
            {"product_title":"Cuffie Bluetooth 5.3 TWS","target_sale_price":"7.49","target_sale_price_currency":"EUR","promotion_link":"https://s.click.aliexpress.com/e/_abc","product_detail_url":"https://www.aliexpress.com/item/100500.html","product_main_image_url":"https://ae01.alicdn.com/kf/a.jpg","evaluate_rate":"94.0%"}]}}}}}"""
        val offers = AliExpressApi.parseSearch(json, TestRules.site("aliexpress")).getOrThrow()
        assertEquals(Money(749), offers.single().price)
        assertEquals(4.7, offers.single().rating)
        val err = AliExpressApi.parseSearch("""{"error_response":{"code":"IncompleteSignature","msg":"x"}}""", TestRules.site("aliexpress"))
        assertTrue(err.exceptionOrNull()!!.message!!.contains("App Secret"))
    }
}
