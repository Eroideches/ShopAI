package com.eroideches.shopai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eroideches.shopai.ui.AppRoot
import com.eroideches.shopai.ui.Launch
import com.eroideches.shopai.ui.theme.ShopAITheme

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf<Launch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as ShopAIApp).graph
        launch = parse(intent)
        setContent {
            ShopAITheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppRoot(graph, launch)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parse(intent)?.let { launch = it }
    }

    private fun parse(i: Intent?): Launch? {
        i ?: return null
        if (i.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            // Da un'altra app arriva spesso "Titolo prodotto https://link": teniamo solo il testo.
            val q = text.replace(Regex("https?://\\S+"), "").replace(Regex("\\s+"), " ").trim().take(100)
            return if (q.isNotEmpty()) Launch(query = q) else null
        }
        return i.getStringExtra(EXTRA_TAB)?.let { Launch(tab = it) }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_URL = "url"
    }
}
