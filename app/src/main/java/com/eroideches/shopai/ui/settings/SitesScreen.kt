package com.eroideches.shopai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.data.AppSettings
import com.eroideches.shopai.sites.SiteOutcome
import com.eroideches.shopai.ui.components.Dot
import com.eroideches.shopai.ui.components.SectionTitle
import com.eroideches.shopai.ui.components.SiteTag
import com.eroideches.shopai.ui.search.methodName
import com.eroideches.shopai.ui.theme.LocalExtra
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(g: AppGraph, onBack: () -> Unit, onPrepare: (String) -> Unit, onGuide: (String) -> Unit) {
    val s by g.settings.flow.collectAsState(AppSettings())
    val rules by g.rules.rules.collectAsState()
    val health by g.searcher.health.collectAsState()
    val keys by g.keys.flow.collectAsState(emptyMap())
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<SiteRule?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Siti") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
            item {
                Text("Solo siti noti, con prezzi in € sul sito italiano. Tocca un sito per prepararlo o vedere come sta funzionando.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rules.sites.groupBy { it.group }.forEach { (group, list) ->
                item { SectionTitle(group) }
                item {
                    Group {
                        list.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider()
                            val apiOn = r.api != null && when (r.api) {
                                "ebay" -> keys["ebay_client_id"] != null
                                "aliexpress" -> keys["ali_app_key"] != null
                                else -> false
                            }
                            SiteRow(r, s.isEnabled(r), health[r.id], apiOn, onClick = { sheet = r }) { on -> scope.launch { g.settings.setSiteEnabled(r.id, on) } }
                        }
                    }
                }
            }
        }
    }

    sheet?.let { r ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.padding(20.dp, 0.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SiteTag(r, r.id)
                Text(r.name, style = MaterialTheme.typography.titleLarge)
                Text(health[r.id]?.let(::describe) ?: "Non ancora usato in questa sessione.", style = MaterialTheme.typography.bodyMedium)
                if (r.needsSetup) Text("Questo sito va preparato una volta: ShopAI imposta il CAP ${s.zip} e salva i cookie.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { sheet = null; onPrepare(r.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (r.needsSetup) "Prepara (CAP e cookie)" else "Apri il sito (cookie, verifiche)")
                }
                if (r.api != null) OutlinedButton(onClick = { sheet = null; onGuide(r.id) }, modifier = Modifier.fillMaxWidth()) { Text("Chiavi API e guida") }
            }
        }
    }
}

fun describe(o: SiteOutcome): String = buildString {
    when {
        o.offers.isNotEmpty() -> append("Ultima lettura: ${o.offers.size} prodotti in ${o.ms / 1000.0} s (${methodName(o.method)}).")
        o.blocked -> append("Ultima lettura bloccata da una verifica: apri il sito e risolvila.")
        else -> append("Ultima lettura: ${o.error ?: "nessun prodotto"}.")
    }
    if (o.degraded) append("\nIl metodo principale non funziona più: ShopAI usa un metodo di riserva. Esporta la diagnostica per aggiornare le regole.")
    o.apiError?.let { append("\nAPI: $it") }
}

@Composable
private fun SiteRow(r: SiteRule, enabled: Boolean, h: SiteOutcome?, apiOn: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val extra = LocalExtra.current
    val status = when {
        h == null -> null
        h.offers.isNotEmpty() && !h.degraded -> extra.good
        h.offers.isNotEmpty() -> Color(0xFFE09B00)
        else -> extra.bad
    }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name)
                if (status != null) { Spacer(Modifier.width(8.dp)); Dot(status) }
            }
        },
        supportingContent = {
            Text(listOfNotNull(
                if (apiOn) "API attiva" else null,
                if (r.needsSetup) "da preparare" else null,
                if (r.experimental) "sperimentale" else null,
                if (r.usedMarket) "usato" else null,
                if (r.defaultCondition != null && !r.usedMarket) "ricondizionato" else null,
            ).joinToString(" · ").ifEmpty { r.domains.first() })
        },
        leadingContent = { SiteTag(r, r.id) },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick),
    )
}
