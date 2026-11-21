package com.eroideches.shopai.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.BuildConfig
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.sites.SiteOutcome
import com.eroideches.shopai.ui.components.Dot
import com.eroideches.shopai.ui.components.siteColor
import com.eroideches.shopai.ui.search.methodName
import com.eroideches.shopai.ui.theme.LocalExtra
import com.eroideches.shopai.work.PriceCheckScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class DiagReport(
    val app: String, val rulesVersion: Int, val query: String, val zip: String, val at: Long,
    val device: String, val android: Int, val sites: List<SiteOutcome>,
)

class DiagnosticsViewModel(private val g: AppGraph) : ViewModel() {
    val query = MutableStateFlow("cuffie bluetooth")
    val running = MutableStateFlow(false)
    val results = MutableStateFlow<Map<String, SiteOutcome?>>(emptyMap())

    fun runAll() {
        if (running.value) return
        running.value = true
        val rules = g.rules.current.sites
        results.value = rules.associate { it.id to null }
        viewModelScope.launch {
            val zip = g.settings.current().zip
            g.lastZip = zip
            val gate = Semaphore(3)
            rules.map { r ->
                launch {
                    gate.withPermit {
                        val o = withTimeoutOrNull(45_000) { g.searcher.search(r, query.value, zip, ConditionFilter.ANY) }
                            ?: SiteOutcome(r.id, emptyList(), null, true, false, "tempo scaduto", 45_000)
                        results.value = results.value + (r.id to o)
                    }
                }
            }.forEach { it.join() }
            running.value = false
        }
    }

    /** Zip con rapporto JSON + istantanee delle pagine che non hanno funzionato. */
    fun export(): File {
        val dir = File(g.cacheDir, "export").apply { mkdirs() }
        val report = DiagReport(
            app = BuildConfig.VERSION_NAME, rulesVersion = g.rules.current.version, query = query.value, zip = g.lastZip,
            at = System.currentTimeMillis(), device = android.os.Build.MODEL, android = android.os.Build.VERSION.SDK_INT,
            sites = results.value.values.filterNotNull().map { it.copy(offers = it.offers.take(5)) },
        )
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val out = File(dir, "shopai-diagnostica.zip")
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("rapporto.json")); z.write(json.encodeToString(DiagReport.serializer(), report).toByteArray()); z.closeEntry()
            g.searcher.snapshotFiles().forEach { f -> z.putNextEntry(ZipEntry("pagine/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }
        return out
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(g: AppGraph, onBack: () -> Unit, onOpenSite: (String) -> Unit) {
    val vm: DiagnosticsViewModel = viewModel { DiagnosticsViewModel(g) }
    val q by vm.query.collectAsState()
    val running by vm.running.collectAsState()
    val results by vm.results.collectAsState()
    val rules by g.rules.rules.collectAsState()
    val ctx = LocalContext.current
    val work by PriceCheckScheduler.observeNow(ctx).collectAsState(emptyList())
    val checking = work.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Diagnostica") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Prova ogni sito con una ricerca di esempio. Se qualcosa non va, esporta il rapporto: contiene le pagine lette e serve per aggiornare le regole dei siti.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(q, { vm.query.value = it }, label = { Text("Ricerca di prova") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::runAll, enabled = !running, modifier = Modifier.weight(1f)) {
                        if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Prova tutti i siti")
                    }
                    OutlinedButton(onClick = {
                        val f = vm.export()
                        val uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.files", f)
                        val i = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(i, "Esporta diagnostica"))
                    }) { Text("Esporta") }
                }
                OutlinedButton(onClick = { PriceCheckScheduler.runNow(ctx) }, enabled = !checking, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(if (checking) "Controllo prezzi in corso…" else "Controlla prezzi e avvisi adesso")
                }
            }
            val list = rules.sites.filter { it.id in results.keys }
            items(list, key = { it.id }) { r -> DiagRow(r, results[r.id], running) { onOpenSite(r.id) } }
        }
    }
}

@Composable
private fun DiagRow(r: SiteRule, o: SiteOutcome?, running: Boolean, onOpen: () -> Unit) {
    val extra = LocalExtra.current
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(siteColor(r))
                Spacer(Modifier.width(8.dp))
                Text(r.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                when {
                    o == null && running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    o == null -> {}
                    o.offers.isNotEmpty() -> Text(
                        "${o.offers.size} · ${o.ms / 1000.0}s" + if (o.degraded) " · riserva" else "",
                        color = if (o.degraded) Color(0xFFE09B00) else extra.good, style = MaterialTheme.typography.labelMedium,
                    )
                    else -> Text(if (o.blocked) "bloccato" else "errore", color = extra.bad, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (o != null) {
                Text(
                    listOfNotNull(
                        o.method?.let { "Metodo: ${methodName(it)}" },
                        o.error,
                        o.apiError?.let { "API: $it" },
                        o.attempts.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
