package com.eroideches.shopai.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.price.PriceParser
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.ranking.SortOrder
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    current: Filters,
    settings: AppSettings,
    sites: List<SiteRule>,
    onApply: (Filters) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(current) }
    var maxText by remember { mutableStateOf(current.maxPrice?.let { (it.cents / 100).toString() } ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtri", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { f = Filters(); maxText = "" }) { Text("Azzera") }
            }

            Label("Ordina per")
            val sorts = listOf(SortOrder.TOTAL to "Totale", SortOrder.PRICE to "Prezzo", SortOrder.RELEVANCE to "Pertinenza", SortOrder.DELIVERY to "Consegna")
            val sortNow = f.sort ?: settings.sort
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                sorts.forEachIndexed { i, (s, label) ->
                    SegmentedButton(selected = sortNow == s, onClick = { f = f.copy(sort = if (s == settings.sort) null else s) }, shape = SegmentedButtonDefaults.itemShape(i, sorts.size)) { Text(label, maxLines = 1) }
                }
            }

            Label("Condizione")
            val conds = listOf(ConditionFilter.NEW to "Nuovo", ConditionFilter.USED to "Usato", ConditionFilter.ANY to "Tutti")
            val condNow = f.condition ?: settings.condition
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                conds.forEachIndexed { i, (c, label) ->
                    SegmentedButton(selected = condNow == c, onClick = { f = f.copy(condition = if (c == settings.condition) null else c) }, shape = SegmentedButtonDefaults.itemShape(i, conds.size)) { Text(label) }
                }
            }

            Label("Prezzo massimo (spedizione inclusa)")
            OutlinedTextField(
                value = maxText,
                onValueChange = { v -> maxText = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7); f = f.copy(maxPrice = PriceParser.parseNumber(maxText)) },
                suffix = { Text("€") }, singleLine = true, placeholder = { Text("nessun limite") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Label("Consegna entro")
            val days = listOf(null to "Qualsiasi", 1 to "Domani", 3 to "3 giorni", 7 to "1 settimana")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { (d, label) -> FilterChip(selected = f.maxDays == d, onClick = { f = f.copy(maxDays = d) }, label = { Text(label) }) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Solo spedizione gratuita", modifier = Modifier.weight(1f))
                Switch(checked = f.freeShippingOnly, onCheckedChange = { f = f.copy(freeShippingOnly = it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Includi accessori")
                    Text("cover, cavi, pellicole…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.includeAccessories, onCheckedChange = { f = f.copy(includeAccessories = it) })
            }

            Label("Siti")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sites.forEach { s ->
                    val on = f.sites?.contains(s.id) ?: true
                    FilterChip(selected = on, onClick = {
                        val cur = f.sites ?: sites.map { it.id }.toSet()
                        val next = if (on) cur - s.id else cur + s.id
                        f = f.copy(sites = if (next.size == sites.size) null else next)
                    }, label = { Text(s.name) })
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { onApply(f); onDismiss() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Mostra risultati") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

fun Money.short(): String = if (cents % 100 == 0L) "${cents / 100} €" else format()
