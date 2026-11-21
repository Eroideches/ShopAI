package com.eroideches.shopai.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.data.FollowedSearch
import com.eroideches.shopai.data.RecentSearch
import com.eroideches.shopai.search.SiteState
import com.eroideches.shopai.search.SiteStatus
import com.eroideches.shopai.ui.components.Dot
import com.eroideches.shopai.ui.components.EmptyState
import com.eroideches.shopai.ui.components.Links
import com.eroideches.shopai.ui.components.OfferCard
import com.eroideches.shopai.ui.components.SectionTitle
import com.eroideches.shopai.ui.components.siteColor
import com.eroideches.shopai.ui.theme.LocalExtra

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onOpenDetail: () -> Unit,
    onScan: () -> Unit,
    onUnlockSite: (String) -> Unit,
) {
    val query by vm.query.collectAsState()
    val ui by vm.results.collectAsState()
    val settings by vm.settings.collectAsState()
    val recents by vm.recents.collectAsState()
    val follows by vm.follows.collectAsState()
    val saved by vm.savedKeys.collectAsState()
    val rules by vm.rules.collectAsState()
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var showFilters by remember { mutableStateOf(false) }
    var siteSheet by remember { mutableStateOf<SiteStatus?>(null) }
    var showFollow by remember { mutableStateOf(false) }
    var showDiscarded by remember { mutableStateOf(false) }
    val state = ui.state
    val isFollowed = follows.any { it.query.equals(state?.query, ignoreCase = true) }

    fun doSearch(q: String = query) { keyboard?.hide(); focus.clearFocus(); vm.search(q) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- campo di ricerca ----
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.statusBarsPadding()) {
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Cerca un prodotto") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.clear() }) { Icon(Icons.Outlined.Close, "Cancella") }
                        IconButton(onClick = onScan) { Icon(Icons.Outlined.QrCodeScanner, "Scansiona codice a barre") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        if (state == null) {
            HomeContent(
                recents = recents, follows = follows, settingsZip = "${settings.zip} · ${settings.city}",
                siteCount = rules.sites.count { settings.isEnabled(it) },
                onSearch = { vm.query.value = it; doSearch(it) },
                onDeleteRecent = vm::deleteRecent, onClearRecents = vm::clearRecents,
            )
            return@Column
        }

        // ---- stato dei siti ----
        val byId = rules.sites.associateBy { it.id }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.sites, key = { it.rule.id }) { s -> SiteStatusChip(s) { siteSheet = s } }
        }

        // ---- filtri + segui ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(badge = { if (ui.filters.activeCount() > 0) Badge { Text("${ui.filters.activeCount()}") } }) {
                FilterChip(selected = ui.filters.activeCount() > 0, onClick = { showFilters = true }, label = { Text("Filtri") },
                    leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) })
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.filters.maxPrice?.let { m -> InputChip(selected = true, onClick = { vm.setFilters(ui.filters.copy(maxPrice = null)) }, label = { Text("≤ ${m.short()}") }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(14.dp)) }) }
                if (ui.filters.freeShippingOnly) InputChip(selected = true, onClick = { vm.setFilters(ui.filters.copy(freeShippingOnly = false)) }, label = { Text("Sped. gratis") }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(14.dp)) })
            }
            IconButton(onClick = { if (isFollowed) vm.unfollow() else showFollow = true }) {
                Icon(if (isFollowed) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                    if (isFollowed) "Non seguire più" else "Segui questa ricerca",
                    tint = if (isFollowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(!state.finished) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp))
        }

        val chosen = ui.selection?.chosen.orEmpty()
        val discarded = ui.selection?.discarded.orEmpty()
        PullToRefreshBox(isRefreshing = false, onRefresh = { vm.search(refresh = true) }, modifier = Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                if (chosen.isEmpty() && state.finished) {
                    item { EmptyState("Nessun risultato", "Prova parole diverse, togli qualche filtro o accendi altri siti nelle Impostazioni.") }
                }
                item(key = "summary") {
                    val done = state.sites.count { it.state != SiteState.LOADING }
                    Text(
                        buildString {
                            append("${chosen.size} risultati")
                            if (!state.finished) append(" · ${done}/${state.sites.size} siti")
                            if (state.enriching) append(" · leggo le spedizioni…")
                            if (state.fromCache) append(" · di poco fa, trascina per aggiornare")
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(chosen, key = { it.offer.key }) { so ->
                    val rule = byId[so.offer.siteId]
                    OfferCard(
                        offer = so.offer, rule = rule, saved = so.offer.key in saved, best = so === chosen.first(), unitPrice = so.unitPrice,
                        onClick = { vm.open(so.offer); onOpenDetail() },
                        onLongClick = { Links.share(ctx, so.offer, rule?.name ?: so.offer.siteId) },
                        onToggleSave = { vm.toggleSave(so.offer) },
                        onOpen = { Links.open(ctx, so.offer.url, rule?.appPackage) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (discarded.isNotEmpty() && state.finished) {
                    item(key = "discarded") {
                        TextButton(onClick = { showDiscarded = !showDiscarded }) {
                            Text(if (showDiscarded) "Nascondi scartati" else "Scartati (${discarded.size}): accessori, prezzi sospetti, poco pertinenti")
                        }
                    }
                    if (showDiscarded) items(discarded.take(60), key = { "d" + it.offer.key }) { d ->
                        DiscardedRow(d.offer.title, d.offer.total, d.reason, byId[d.offer.siteId]) { vm.open(d.offer); onOpenDetail() }
                    }
                }
            }
        }
    }

    if (showFilters && state != null) {
        FilterSheet(ui.filters, settings, state.sites.map { it.rule }, onApply = vm::setFilters, onDismiss = { showFilters = false })
    }
    siteSheet?.let { s ->
        SiteStatusSheet(s, onUnlock = { siteSheet = null; onUnlockSite(s.rule.id) }, onDismiss = { siteSheet = null })
    }
    if (showFollow) {
        val best = ui.selection?.chosen?.minByOrNull { it.offer.total }?.offer?.total
        FollowDialog(state?.query.orEmpty(), best, onConfirm = { vm.follow(it); showFollow = false }, onDismiss = { showFollow = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(
    recents: List<RecentSearch>, follows: List<FollowedSearch>, settingsZip: String, siteCount: Int,
    onSearch: (String) -> Unit, onDeleteRecent: (String) -> Unit, onClearRecents: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
        if (recents.isEmpty() && follows.isEmpty()) {
            item {
                EmptyState(
                    "Trova il prezzo più basso",
                    "Scrivi cosa cerchi: confronto $siteCount siti noti (Amazon, eBay, AliExpress, Temu…) e ti mostro i 3 migliori di ciascuno, dal più economico.",
                    Modifier.padding(top = 32.dp),
                ) {
                    Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("Spedizione verso $settingsZip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (recents.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Ricerche recenti", Modifier.weight(1f))
                    TextButton(onClick = onClearRecents, modifier = Modifier.padding(top = 12.dp)) { Text("Cancella") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recents.forEach { r ->
                        InputChip(
                            selected = false, onClick = { onSearch(r.query) },
                            label = { Text(r.query + (r.bestCents?.let { " · ${Money(it).short()}" } ?: ""), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Outlined.History, null, Modifier.size(16.dp)) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Rimuovi", Modifier.size(16.dp).clickable { onDeleteRecent(r.query) }) },
                        )
                    }
                }
            }
        }
        if (follows.isNotEmpty()) {
            item { SectionTitle("Ricerche seguite") }
            items(follows, key = { "f" + it.query }) { f ->
                ElevatedCard(onClick = { onSearch(f.query) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.query, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(f.bestCents?.let { "ora ${Money(it).format()}" }, f.targetCents?.let { "avviso ≤ ${Money(it).short()}" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteStatusChip(s: SiteStatus, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(siteColor(s.rule))
            Spacer(Modifier.width(6.dp))
            Text(s.rule.name, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            when (s.state) {
                SiteState.LOADING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                SiteState.DONE -> Text(
                    "${s.count}" + if (s.outcome?.degraded == true) "!" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (s.outcome?.degraded == true) Color(0xFFE09B00) else extra.good,
                )
                SiteState.EMPTY -> Text("0", style = MaterialTheme.typography.labelMedium, color = extra.muted)
                SiteState.BLOCKED -> Icon(Icons.Outlined.Lock, "Bloccato", Modifier.size(14.dp), tint = extra.bad)
                SiteState.ERROR -> Icon(Icons.Outlined.ErrorOutline, "Errore", Modifier.size(14.dp), tint = extra.bad)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteStatusSheet(s: SiteStatus, onUnlock: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp, 0.dp, 20.dp, 32.dp)) {
            Text(s.rule.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            val o = s.outcome
            val text = when (s.state) {
                SiteState.LOADING -> "Sto leggendo il sito…"
                SiteState.DONE -> "${s.count} prodotti letti in ${(o?.ms ?: 0) / 1000.0} s con il metodo \"${methodName(o?.method)}\"." +
                    if (o?.degraded == true) "\n\nIl metodo principale non ha funzionato: probabilmente il sito ha cambiato qualcosa. ShopAI sta usando un metodo di riserva." else ""
                SiteState.EMPTY -> "Il sito ha risposto ma non ho trovato prodotti per questa ricerca."
                SiteState.BLOCKED -> "Il sito chiede una verifica (CAPTCHA o consenso cookie). Aprilo e risolvila una volta: i cookie restano salvati."
                SiteState.ERROR -> "Errore: ${o?.error ?: "sconosciuto"}"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            o?.apiError?.let { Text("\nAPI: $it (uso la pagina web)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (s.rule.experimental) Text("\nSito sperimentale: può non rispondere sempre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (s.state == SiteState.BLOCKED || s.state == SiteState.ERROR || s.state == SiteState.EMPTY) {
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text(if (s.state == SiteState.BLOCKED) "Sblocca sito" else "Apri il sito") }
            }
        }
    }
}

fun methodName(m: String?) = when (m) {
    "api" -> "API ufficiale"
    "network" -> "dati di rete"
    "structured" -> "dati strutturati"
    "selectors" -> "regole del sito"
    "heuristic" -> "riconoscimento automatico"
    else -> "—"
}

@Composable
private fun DiscardedRow(title: String, total: Money, reason: String, rule: SiteRule?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(siteColor(rule))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(total.format(), style = MaterialTheme.typography.labelMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun FollowDialog(query: String, best: Money?, onConfirm: (Money?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(best?.let { ((it.cents * 0.95) / 100).toLong().toString() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Segui \"$query\"") },
        text = {
            Column {
                Text("ShopAI ricontrolla questa ricerca in background e ti avvisa quando il prezzo migliore scende sotto la soglia." +
                    (best?.let { "\nOra il migliore è ${it.format()}." } ?: ""))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7) },
                    label = { Text("Avvisami sotto") }, suffix = { Text("€") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Lascia vuoto per un avviso a ogni calo del 5%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(PriceParser.parseNumber(text)) }) { Text("Segui") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
