package com.eroideches.shopai.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.model.Shipping
import com.eroideches.shopai.data.FollowedSearch
import com.eroideches.shopai.data.SavedProduct
import com.eroideches.shopai.ui.components.EmptyState
import com.eroideches.shopai.ui.components.ProductImage
import com.eroideches.shopai.ui.components.SiteTag
import com.eroideches.shopai.ui.components.Sparkline
import com.eroideches.shopai.ui.theme.LocalExtra
import com.eroideches.shopai.work.PriceCheckScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(g: AppGraph, onOpenDetail: () -> Unit, onSearch: (String) -> Unit) {
    val products by g.saved.products.collectAsState(emptyList())
    val follows by g.saved.follows.collectAsState(emptyList())
    val rules by g.rules.rules.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Salvati") },
            actions = {
                IconButton(onClick = { PriceCheckScheduler.runNow(ctx) }) { Icon(Icons.Outlined.Sync, "Controlla i prezzi adesso") }
            },
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Prodotti (${products.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ricerche seguite (${follows.size})") })
        }
        if (tab == 0) {
            if (products.isEmpty()) EmptyState("Nessun prodotto salvato", "Tocca il cuore su un risultato: ShopAI ne segue il prezzo e ti avvisa quando scende.", Modifier.padding(top = 40.dp)) {
                Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(products, key = { it.key }) { p ->
                    Swipe(onDelete = { scope.launch { g.saved.remove(p.key) } }) {
                        SavedRow(g, p, rules.site(p.siteId)) {
                            g.openOffer = p.toOffer(); g.openQuery = p.query; onOpenDetail()
                        }
                    }
                }
            }
        } else {
            if (follows.isEmpty()) EmptyState("Nessuna ricerca seguita", "Dopo una ricerca tocca la campanella: ShopAI la ripete in background e ti avvisa se il prezzo migliore scende.", Modifier.padding(top = 40.dp)) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(follows, key = { it.query }) { f ->
                    Swipe(onDelete = { scope.launch { g.saved.unfollow(f.query) } }) {
                        FollowRow(f, rules.site(f.bestSite ?: "")?.name) { onSearch(f.query) }
                    }
                }
            }
        }
    }
}

fun SavedProduct.toOffer() = Offer(
    siteId = siteId, title = title,
    price = Money(lastCents - (lastShippingCents ?: 0)),
    url = url, imageUrl = imageUrl,
    shipping = Shipping(lastShippingCents?.let(::Money)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Swipe(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false })
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Outlined.DeleteOutline, "Elimina", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
private fun SavedRow(g: AppGraph, p: SavedProduct, rule: com.eroideches.shopai.core.rules.SiteRule?, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val history by remember(p.key) { g.saved.history(p.key) }.collectAsState(emptyList())
    val change = if (p.initialCents > 0) (p.lastCents - p.initialCents) * 100.0 / p.initialCents else 0.0
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp)) {
                ProductImage(p.imageUrl, Modifier.fillMaxSize())
                SiteTag(rule, p.siteId, Modifier.align(Alignment.TopStart).padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Money(p.lastCents).format(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (kotlin.math.abs(change) >= 0.5) {
                        Text(
                            (if (change < 0) "▼ " else "▲ ") + String.format(java.util.Locale.ITALY, "%.0f%%", kotlin.math.abs(change)),
                            style = MaterialTheme.typography.labelMedium, color = if (change < 0) extra.good else extra.bad,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Sparkline(history, Modifier.size(56.dp, 22.dp))
                }
                p.targetCents?.let { t ->
                    Text("Avviso ≤ ${Money(t).format()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun FollowRow(f: FollowedSearch, siteName: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.query, style = MaterialTheme.typography.titleSmall)
                f.bestCents?.let { Text("Migliore: ${Money(it).format()}" + (siteName?.let { s -> " su $s" } ?: ""), style = MaterialTheme.typography.bodySmall) }
                f.bestTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            f.targetCents?.let { Text("≤ ${Money(it).format()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}
