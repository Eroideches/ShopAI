package com.eroideches.shopai.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.BuildConfig
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.ranking.SortOrder
import com.eroideches.shopai.data.AppSettings
import com.eroideches.shopai.ui.components.SectionTitle
import com.eroideches.shopai.work.PriceCheckScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(g: AppGraph, onSites: () -> Unit, onKeys: () -> Unit, onDiagnostics: () -> Unit) {
    val s by g.settings.flow.collectAsState(AppSettings())
    val rules by g.rules.rules.collectAsState()
    val keys by g.keys.flow.collectAsState(emptyMap())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var editZip by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var canNotify by remember { mutableStateOf(g.notifier.canNotify()) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Impostazioni") })
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
            item {
                Group {
                    Nav(Icons.Outlined.Place, "Spedizione", "${s.zip} · ${s.city}") { editZip = true }
                    HorizontalDivider()
                    Nav(Icons.Outlined.Storefront, "Siti", "${rules.sites.count { s.isEnabled(it) }} accesi su ${rules.sites.size}", onSites)
                    HorizontalDivider()
                    val active = listOf("ebay_client_id" to "eBay", "ali_app_key" to "AliExpress").filter { keys[it.first] != null }.map { it.second }
                    Nav(Icons.Outlined.Key, "Chiavi e guide", if (active.isEmpty()) "Nessuna chiave: tutti i siti letti dalla pagina" else "Attive: ${active.joinToString()}", onKeys)
                }
            }

            item { SectionTitle("Risultati") }
            item {
                Group {
                    Column(Modifier.padding(16.dp)) {
                        Text("Prodotti per sito")
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf(1, 2, 3, 4, 5).map { it to "$it" }, s.maxPerSite) { scope.launch { g.settings.setMaxPerSite(it) } }
                        Spacer(Modifier.height(16.dp))
                        Text("Filtro accessori")
                        Text("Scarta cover, cavi e pellicole quando cerchi il prodotto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf(0 to "Spento", 1 to "Normale", 2 to "Severo"), s.accessoryStrictness) { scope.launch { g.settings.setStrictness(it) } }
                        Spacer(Modifier.height(16.dp))
                        Text("Condizione predefinita")
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf(ConditionFilter.NEW to "Nuovo", ConditionFilter.USED to "Usato", ConditionFilter.ANY to "Tutti"), s.condition) { scope.launch { g.settings.setCondition(it) } }
                        Spacer(Modifier.height(16.dp))
                        Text("Ordine predefinito")
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf(SortOrder.TOTAL to "Totale", SortOrder.PRICE to "Prezzo", SortOrder.RELEVANCE to "Pertin.", SortOrder.DELIVERY to "Consegna"), s.sort) { scope.launch { g.settings.setSort(it) } }
                    }
                    HorizontalDivider()
                    Toggle("Leggi spedizione e consegna dalla pagina del prodotto", "Più preciso, un po' più lento", s.enrich) { scope.launch { g.settings.setEnrich(it) } }
                }
            }

            item { SectionTitle("Avvisi di prezzo") }
            item {
                Group {
                    Column(Modifier.padding(16.dp)) {
                        Text("Controlla ogni")
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf(3 to "3 ore", 6 to "6 ore", 12 to "12 ore", 24 to "24 ore"), s.checkHours) { h ->
                            scope.launch { g.settings.setCheckHours(h); PriceCheckScheduler.schedule(ctx, h, s.wifiOnly) }
                        }
                    }
                    HorizontalDivider()
                    Toggle("Solo con Wi-Fi", "I controlli in background non usano i dati mobili", s.wifiOnly) { v ->
                        scope.launch { g.settings.setWifiOnly(v); PriceCheckScheduler.schedule(ctx, s.checkHours, v) }
                    }
                    if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                        HorizontalDivider()
                        Nav(Icons.Outlined.Notifications, "Attiva le notifiche", "Servono per gli avvisi di prezzo") { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    }
                }
            }

            item { SectionTitle("Strumenti") }
            item {
                Group {
                    Nav(Icons.Outlined.BugReport, "Diagnostica", "Prova tutti i siti, esporta il rapporto", onDiagnostics)
                    HorizontalDivider()
                    val last = if (g.rules.lastUpdate > 0) java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ITALIAN).format(java.util.Date(g.rules.lastUpdate)) else "mai"
                    Nav(Icons.Outlined.Sync, "Aggiorna regole dei siti", "Versione ${rules.version} · ultimo aggiornamento $last") {
                        scope.launch { message = g.rules.refresh(force = true) }
                    }
                    HorizontalDivider()
                    Nav(Icons.Outlined.DeleteOutline, "Svuota cache e cronologia", "Risultati recenti e ricerche fatte") {
                        scope.launch { g.db.cache().clear(); g.db.recents().clear(); message = "Cache e cronologia svuotate" }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("ShopAI ${BuildConfig.VERSION_NAME} · regole v${rules.version} · prezzi sempre in € come sul sito, mai convertiti.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (editZip) ZipDialog(s.zip, s.city, onSave = { z, c -> editZip = false; scope.launch { g.settings.setZip(z, c); g.db.cache().clear() } }, onDismiss = { editZip = false })
    message?.let { m -> AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(m) }) }
}

@Composable
fun Group(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
fun Nav(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick),
    )
}

@Composable
fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow { onChange(!checked) },
    )
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(selected = v == selected, onClick = { onSelect(v) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun ZipDialog(zip: String, city: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var z by remember { mutableStateOf(zip) }
    var c by remember { mutableStateOf(city) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spedizione") },
        text = {
            Column {
                Text("I costi e i tempi di consegna sono calcolati verso questo CAP. L'indirizzo del punto di ritiro lo scegli tu al momento dell'acquisto.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(z, { v -> z = v.filter(Char::isDigit).take(5) }, label = { Text("CAP") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(c, { c = it }, label = { Text("Comune") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { if (z.length == 5) onSave(z, c) }, enabled = z.length == 5) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
