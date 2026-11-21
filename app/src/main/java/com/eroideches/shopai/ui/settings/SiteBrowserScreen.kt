package com.eroideches.shopai.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.data.AppSettings

/**
 * Il sito a schermo, con i cookie di ShopAI: per accettare il consenso, risolvere un CAPTCHA
 * o (Amazon) impostare il CAP. Tutto quello che fai qui resta salvato per le ricerche.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteBrowserScreen(g: AppGraph, siteId: String, onDone: () -> Unit) {
    val rules by g.rules.rules.collectAsState()
    val settings by g.settings.flow.collectAsState(AppSettings())
    val found = rules.site(siteId)
    if (found == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }; return }
    val rule = found
    var progress by remember { mutableIntStateOf(0) }
    var scriptRun by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(
        if (rule.setupScript != null) "Accetta i cookie. ShopAI imposta da solo il CAP ${settings.zip}: se non succede, toccane l'indicatore in alto e inseriscilo tu."
        else "Accetta i cookie e, se compare, risolvi la verifica. Poi tocca Fatto.",
    ) }
    val startUrl = rule.setupUrl ?: rule.searchUrlFor("cuffie bluetooth", settings.zip)

    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(rule.name) },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, "Chiudi") } },
            actions = { TextButton(onClick = onDone) { Text("Fatto") } },
        )
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth().padding(12.dp))
        }
        AndroidView(
            factory = { ctx ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                for ((k, v) in rule.cookies) for (d in rule.domains) cm.setCookie("https://www.$d", "$k=$v; Domain=.$d; Path=/; Secure")
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    cm.setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 1 }
                        override fun onPageFinished(view: WebView, url: String?) {
                            progress = 100
                            val script = rule.setupScript
                            if (script != null && !scriptRun) {
                                scriptRun = true
                                view.evaluateJavascript(script.replace("{zip}", settings.zip), null)
                                info = "CAP ${settings.zip} inviato al sito. Controlla in alto che compaia \"$siteNameHint\" e poi tocca Fatto."
                            }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val s = request.url.scheme ?: return true
                            return s != "https" && s != "http"
                        }
                    }
                    loadUrl(startUrl)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val siteNameHint = "Pioltello 20096"
