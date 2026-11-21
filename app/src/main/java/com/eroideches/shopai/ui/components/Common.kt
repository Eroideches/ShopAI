package com.eroideches.shopai.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.rules.SiteRule

fun siteColor(rule: SiteRule?): Color = runCatching { Color(android.graphics.Color.parseColor(rule?.color ?: "#607D8B")) }.getOrDefault(Color(0xFF607D8B))

/** Colore del testo leggibile sopra il colore del sito. */
fun onColor(c: Color): Color {
    val l = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
    return if (l > 0.62) Color(0xFF1A1A1A) else Color.White
}

/** Piccolo tag con il nome del sito, nel colore del sito. */
@Composable
fun SiteTag(rule: SiteRule?, fallback: String, modifier: Modifier = Modifier) {
    val c = siteColor(rule)
    Text(
        text = rule?.name ?: fallback,
        color = onColor(c),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(c, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

object Links {
    /** Apre il prodotto nell'app del sito se installata, altrimenti in una scheda del browser. */
    fun open(context: Context, url: String, appPackage: String?) {
        val uri = Uri.parse(url)
        if (appPackage != null && isInstalled(context, appPackage)) {
            val i = Intent(Intent.ACTION_VIEW, uri).setPackage(appPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(i); return } catch (_: ActivityNotFoundException) { }
        }
        browser(context, url)
    }

    fun browser(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (_: Exception) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun isInstalled(context: Context, pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun share(context: Context, offer: Offer, siteName: String) {
        val ship = when {
            offer.shipping.isFree -> "spedizione gratuita"
            offer.shipping.cost != null -> "+ ${offer.shipping.cost!!.format()} spedizione"
            else -> "spedizione da verificare"
        }
        val text = "${offer.title}\n${offer.price.format()} ($ship) su $siteName\n${offer.url}"
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(i, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
