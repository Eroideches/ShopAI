package com.eroideches.shopai.core.extract

import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.rules.SiteRule
import org.jsoup.nodes.Document

/** JSON-LD + stati incorporati nella pagina (Next.js, Nuxt, Redux, runParams di AliExpress...). */
object StructuredExtractor {
    private val stateMarkers = listOf(
        "window.__INITIAL_STATE__", "window.__PRELOADED_STATE__", "window.__APOLLO_STATE__", "window.__NUXT__",
        "window.runParams", "window._dida_config_", "window.rawData", "window.__INIT_DATA__", "window.__STATE__",
        "window.__data", "__REDUX_STATE__", "window.pageData",
    )

    fun extract(doc: Document, rule: SiteRule, baseUrl: String): List<Offer> {
        val finder = JsonOfferFinder(rule, baseUrl)
        val out = mutableListOf<Offer>()
        for (s in doc.select("script[type=application/ld+json]")) out += finder.find(s.data())
        doc.selectFirst("script#__NEXT_DATA__")?.let { out += finder.find(it.data()) }
        for (s in doc.select("script[type=application/json]")) {
            val d = s.data()
            if (d.length > 200) out += finder.find(d)
        }
        for (s in doc.select("script:not([src])")) {
            val d = s.data()
            if (d.length < 200) continue
            for (marker in stateMarkers) {
                var idx = d.indexOf(marker)
                while (idx >= 0) {
                    embeddedJsonAfter(d, idx + marker.length)?.let { out += finder.find(it) }
                    idx = d.indexOf(marker, idx + marker.length)
                }
            }
        }
        return out.distinctBy { it.key }
    }

    /** Dopo "marker = " (o "marker:" / JSON.parse('...')) estrae l'oggetto JSON bilanciando le parentesi. */
    fun embeddedJsonAfter(src: String, from: Int): String? {
        var i = from
        while (i < src.length && src[i] != '{' && src[i] != '[') {
            if (src[i] == ';' || src[i] == '\n' && i - from > 200) return null
            if (i - from > 300) return null
            i++
        }
        if (i >= src.length) return null
        val open = src[i]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inStr = false
        var quote = '"'
        var esc = false
        var j = i
        while (j < src.length) {
            val c = src[j]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == quote) inStr = false
            } else {
                when (c) {
                    '"', '\'' -> { inStr = true; quote = c }
                    open -> depth++
                    close -> { depth--; if (depth == 0) return src.substring(i, j + 1) }
                }
            }
            j++
        }
        return null
    }
}
