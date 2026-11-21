package com.eroideches.shopai.ui.settings

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.core.rules.KeyField
import com.eroideches.shopai.ui.components.Links
import com.eroideches.shopai.ui.components.SiteTag
import com.eroideches.shopai.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** Elenco delle guide: una per ogni sito che ha (o avrà) chiavi API. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(g: AppGraph, onBack: () -> Unit, onGuide: (String) -> Unit) {
    val rules by g.rules.rules.collectAsState()
    val keys by g.keys.flow.collectAsState(emptyMap())
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Chiavi e guide") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Le chiavi sono facoltative: senza, ShopAI legge i siti dalle pagine web. Con le chiavi ufficiali la lettura è più veloce e non viene bloccata. Restano solo sul telefono, cifrate.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rules.guides.forEach { guide ->
                item(key = guide.siteId) {
                    val filled = guide.fields.isNotEmpty() && guide.fields.filter { it.secret }.all { keys[it.id] != null }
                    Group {
                        Nav(Icons.Outlined.Key, guide.title, if (guide.fields.isEmpty()) guide.summary else if (filled) "Chiavi inserite" else "Nessuna chiave") { onGuide(guide.siteId) }
                    }
                }
            }
        }
    }
}

/** Tutorial passo-passo con link + campi per inserire, cambiare, verificare e rimuovere le chiavi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(g: AppGraph, siteId: String, onBack: () -> Unit) {
    val rules by g.rules.rules.collectAsState()
    val guide = rules.guides.firstOrNull { it.siteId == siteId }
    val rule = rules.site(siteId)
    val stored by g.keys.flow.collectAsState(emptyMap())
    val values = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<String>?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val extra = LocalExtra.current

    LaunchedEffect(stored) {
        if (!loaded && guide != null) { guide.fields.forEach { f -> values[f.id] = stored[f.id] ?: "" }; loaded = stored.isNotEmpty() || loaded }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(guide?.title ?: "Guida") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
        if (guide == null) { Text("Guida non disponibile", Modifier.padding(16.dp)); return@Column }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 40.dp)) {
            item {
                SiteTag(rule, siteId)
                Spacer(Modifier.height(8.dp))
                Text(guide.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
            }
            itemsIndexed(guide.steps) { i, step ->
                Row(Modifier.padding(vertical = 8.dp)) {
                    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.text, style = MaterialTheme.typography.bodyMedium)
                        if (!step.link.isNullOrBlank()) {
                            TextButton(onClick = { Links.browser(ctx, step.link!!) }, contentPadding = PaddingValues(0.dp)) {
                                Text(step.linkLabel?.ifBlank { null } ?: step.link!!)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            if (guide.fields.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text("Le tue chiavi", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }
                guide.fields.forEach { f ->
                    item(key = f.id) { KeyInput(f, values[f.id] ?: "") { values[f.id] = it.trim(); result = null } }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch { g.keys.remove(guide.fields.map { it.id }); guide.fields.forEach { values[it.id] = "" }; result = null }
                        }) { Text("Rimuovi") }
                        Button(
                            onClick = {
                                scope.launch {
                                    guide.fields.forEach { f -> g.keys.set(f.id, values[f.id] ?: "") }
                                    val api = rule?.api
                                    if (api != null && rule != null) {
                                        verifying = true
                                        result = g.api.verify(api, rule, g.settings.current().zip)
                                        verifying = false
                                    } else result = Result.success("Salvato")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (verifying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Salva e verifica")
                        }
                    }
                    result?.let { r ->
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (r.isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (r.isSuccess) extra.good else extra.bad)
                            Spacer(Modifier.width(8.dp))
                            Text(r.getOrNull() ?: ("Non funziona: " + (r.exceptionOrNull()?.message ?: "errore")), color = if (r.isSuccess) extra.good else extra.bad)
                        }
                    }
                }
            }
            if (guide.notes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    guide.notes.forEach { n -> Text("• $n", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
    }
}

@Composable
private fun KeyInput(f: KeyField, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(!f.secret) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(f.label) },
        placeholder = { Text(f.hint) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (f.secret) KeyboardType.Password else KeyboardType.Text, autoCorrectEnabled = false),
        trailingIcon = {
            if (f.secret) IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Nascondi" else "Mostra")
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
