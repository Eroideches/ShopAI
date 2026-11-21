package com.eroideches.shopai.net

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eroideches.shopai.core.extract.NetworkBody
import com.eroideches.shopai.core.extract.PageCapture
import com.eroideches.shopai.core.rules.SiteRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.coroutines.resume

/**
 * Apre le pagine dei siti in WebView invisibili (motore Chrome vero, JavaScript, cookie salvati),
 * intercetta i JSON di rete, scorre la pagina per caricare i risultati e restituisce HTML + JSON.
 */
class WebViewFetcher(private val context: Context) {
    private val gate = Semaphore(MAX_PARALLEL)
    private val sniffer: String by lazy { context.assets.open("network_sniffer.js").bufferedReader().use { it.readText() } }

    private class Bridge {
        val bodies: MutableList<NetworkBody> = Collections.synchronizedList(mutableListOf())
        @Volatile var html: String? = null

        @JavascriptInterface fun net(url: String, body: String) {
            if (bodies.size < 40) bodies += NetworkBody(url, body)
        }

        @JavascriptInterface fun html(h: String) { html = h }
    }

    suspend fun fetch(rule: SiteRule, url: String, timeoutMs: Long = 25_000): PageCapture? = gate.withPermit {
        withContext(Dispatchers.Main) {
            val bridge = Bridge()
            val wv = createWebView(rule, bridge)
            try {
                applyCookies(rule)
                withTimeoutOrNull(timeoutMs) {
                    loadAndWait(wv, url)
                    delay(rule.waitMs)
                    repeat(rule.scrolls) {
                        wv.evaluateJavascript("window.scrollBy(0, Math.max(window.innerHeight * 1.5, 1200));", null)
                        delay(700)
                    }
                    wv.evaluateJavascript("window.scrollTo(0, 0);", null)
                    delay(300)
                    bridge.html = null
                    wv.evaluateJavascript("ShopAIBridge.html(document.documentElement.outerHTML);", null)
                    var waited = 0
                    while (bridge.html == null && waited < 3000) { delay(100); waited += 100 }
                }
                val html = bridge.html ?: return@withContext null
                PageCapture(wv.url ?: url, html, bridge.bodies.toList())
            } finally {
                CookieManager.getInstance().flush()
                wv.stopLoading()
                wv.destroy()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(rule: SiteRule, bridge: Bridge): WebView {
        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            if (rule.desktopUserAgent) {
                userAgentString = userAgentString.replace("Mobile", "").replace(Regex("Android [^;)]+;?"), "X11; Linux x86_64;")
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.addJavascriptInterface(bridge, "ShopAIBridge")
        // Dimensioni da telefono anche se la WebView non è a schermo: serve per lazy-load e scroll.
        wv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        wv.layout(0, 0, 1080, 2340)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val origins = rule.domains.flatMap { listOf("https://$it", "https://*.$it") }.toSet()
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, sniffer, origins) }
        }
        return wv
    }

    private fun applyCookies(rule: SiteRule) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        for ((k, v) in rule.cookies) for (d in rule.domains) cm.setCookie("https://www.$d", "$k=$v; Domain=.$d; Path=/; Secure")
    }

    private suspend fun loadAndWait(wv: WebView, url: String): Unit = suspendCancellableCoroutine { cont ->
        var done = false
        val main = Handler(Looper.getMainLooper())
        val finish = Runnable { if (!done) { done = true; if (cont.isActive) cont.resume(Unit) } }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(sniffer, null)
            }

            override fun onPageFinished(view: WebView, u: String?) {
                // Alcuni siti fanno redirect a catena: aspetta un attimo che non ne partano altri.
                main.removeCallbacks(finish)
                main.postDelayed(finish, 600)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "https" && scheme != "http" // blocca intent:// e app esterne
            }
        }
        wv.loadUrl(url, mapOf("Accept-Language" to "it-IT,it;q=0.9"))
        cont.invokeOnCancellation { main.removeCallbacks(finish) }
    }

    companion object {
        const val MAX_PARALLEL = 3
    }
}
