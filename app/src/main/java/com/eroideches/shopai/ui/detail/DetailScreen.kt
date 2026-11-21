package com.eroideches.shopai.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.ranking.UnitPriceParser
import com.eroideches.shopai.ui.components.KeyValue
import com.eroideches.shopai.ui.components.Links
import com.eroideches.shopai.ui.components.PriceChart
import com.eroideches.shopai.ui.components.ProductImage
import com.eroideches.shopai.ui.components.ShippingLine
import com.eroideches.shopai.ui.components.SiteTag
import com.eroideches.shopai.ui.theme.PriceStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(g: AppGraph, onBack: () -> Unit) {
    val opened: Offer? = g.openOffer
    if (opened == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    val offer: Offer = opened
    val query = g.openQuery
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by g.rules.rules.collectAsState()
    val rule = rules.site(offer.siteId)
    val saved by remember(offer.key) { g.saved.observe(offer.key) }.collectAsState(null)
    val history by remember(offer.key) { g.saved.history(offer.key) }.collectAsState(emptyList())
    var askTarget by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rule?.name ?: offer.siteId) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { Links.share(ctx, offer, rule?.name ?: offer.siteId) }) { Icon(Icons.Outlined.Share, "Condividi") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { askTarget = true }, modifier = Modifier.height(52.dp)) {
                        Icon(if (saved != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null,
                            tint = if (saved != null) Color(0xFFE53935) else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(6.dp))
                        Text(if (saved != null) "Salvato" else "Salva e avvisami")
                    }
                    Button(onClick = { Links.open(ctx, offer.url, rule?.appPackage) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Apri su ${rule?.name ?: "sito"}")
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    }
                }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ProductImage(offer.imageUrl, Modifier.fillMaxWidth().aspectRatio(1.3f))
            Spacer(Modifier.height(12.dp))
            SiteTag(rule, offer.siteId)
            Spacer(Modifier.height(8.dp))
            Text(offer.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(offer.price.format(), style = PriceStyle.copy(fontSize = PriceStyle.fontSize * 1.3f))
                Spacer(Modifier.size(10.dp))
                if ((offer.shipping.cost?.cents ?: 0) > 0) Text("Totale ${offer.total.format()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ShippingLine(offer, rule)
            Spacer(Modifier.height(16.dp))

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp, 8.dp)) {
                    offer.delivery?.let { KeyValue("Consegna", it) }
                    KeyValue("Spedizione verso", "20096 Pioltello (MI)")
                    UnitPriceParser.unitPrice(offer.title, offer.total)?.let { KeyValue("Prezzo per unità", it.format()) }
                    offer.rating?.let { r -> KeyValue("Valutazione", "★ ${String.format(java.util.Locale.ITALY, "%.1f", r)}" + (offer.reviews?.let { " ($it)" } ?: "")) }
                    offer.seller?.let { KeyValue("Venditore", it) }
                    KeyValue("Condizione", when (offer.condition) { Condition.NEW -> "Nuovo"; Condition.USED -> "Usato"; Condition.REFURBISHED -> "Ricondizionato"; Condition.UNKNOWN -> "Non indicata" })
                    offer.description?.let { KeyValue("Dettagli", it) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Andamento del prezzo", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (history.size >= 2) {
                PriceChart(history, saved?.targetCents, Modifier.fillMaxWidth())
            } else {
                Text(
                    if (saved != null) "Il grafico si riempie con i controlli automatici dei prossimi giorni."
                    else "Salva il prodotto: ShopAI controlla il prezzo in background e ne disegna l'andamento.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askTarget) {
        TargetDialog(
            current = saved?.targetCents?.let(::Money),
            price = offer.total,
            isSaved = saved != null,
            onSave = { target ->
                askTarget = false
                if (Build.VERSION.SDK_INT >= 33 && !g.notifier.canNotify()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                scope.launch {
                    if (saved == null) g.saved.save(offer, query, target?.cents) else g.saved.setTarget(offer.key, target?.cents)
                }
            },
            onRemove = { askTarget = false; scope.launch { g.saved.remove(offer.key) } },
            onDismiss = { askTarget = false },
        )
    }
}

@Composable
fun TargetDialog(current: Money?, price: Money, isSaved: Boolean, onSave: (Money?) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(((current ?: Money((price.cents * 0.9).toLong())).cents / 100).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSaved) "Avviso di prezzo" else "Salva e avvisami") },
        text = {
            Column {
                Text("Ora costa ${price.format()} (spedizione inclusa). Ti mando una notifica quando scende sotto:")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7) },
                    suffix = { Text("€") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Lascia vuoto per un avviso a ogni calo del 5%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(PriceParser.parseNumber(text)) }) { Text("Salva") } },
        dismissButton = {
            Row {
                if (isSaved) TextButton(onClick = onRemove) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}
