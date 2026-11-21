package com.eroideches.shopai.net

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.eroideches.shopai.core.api.HttpSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Condivide i cookie tra WebView e OkHttp: ciò che imposti in "Prepara sito" vale anche per le letture HTTP. */
class WebViewCookieJar : CookieJar {
    private val cm: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = runCatching { cm.getCookie(url.toString()) }.getOrNull() ?: return emptyList()
        return raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }
}

class Http(context: Context) {
    /** User-agent vero della WebView di sistema (Chrome per Android). */
    val userAgent: String = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault(
        "Mozilla/5.0 (Linux; Android 15; SM-A566B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
    )

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Client senza cookie della WebView, per le API ufficiali e le regole remote. */
    val plain: OkHttpClient = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    data class Page(val url: String, val code: Int, val body: String)

    suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): Page = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9")
        extraHeaders.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r -> Page(r.request.url.toString(), r.code, r.body?.string() ?: "") }
    }

    suspend fun execute(spec: HttpSpec): Page = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(spec.url)
        spec.headers.forEach { (k, v) -> b.header(k, v) }
        b.header("Accept", "application/json")
        if (spec.method == "POST") {
            b.post((spec.body ?: "").toRequestBody((spec.contentType ?: "application/json").toMediaType()))
        } else b.get()
        plain.newCall(b.build()).execute().use { r -> Page(r.request.url.toString(), r.code, r.body?.string() ?: "") }
    }
}
