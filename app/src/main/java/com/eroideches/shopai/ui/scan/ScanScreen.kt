package com.eroideches.shopai.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.ui.components.EmptyState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

data class ScanUi(val code: String? = null, val resolving: Boolean = false, val name: String = "", val note: String? = null)

/** Dal codice EAN al nome del prodotto: lo cerca su comparatori e marketplace e prende il titolo più affidabile. */
class ScanViewModel(private val g: AppGraph) : ViewModel() {
    val ui = MutableStateFlow(ScanUi())

    fun onCode(code: String) {
        if (ui.value.code != null) return
        ui.value = ScanUi(code = code, resolving = true)
        viewModelScope.launch {
            val zip = g.settings.current().zip
            val order = listOf("trovaprezzi", "ebay", "amazon", "idealo")
            val rules = order.mapNotNull { g.rules.current.site(it) }
            val titles = rules.map { r ->
                async {
                    withTimeoutOrNull(25_000) {
                        g.searcher.search(r, code, zip, ConditionFilter.ANY, url = r.eanUrlFor(code, zip)).offers.firstOrNull()?.title
                    }
                }
            }.awaitAll()
            val title = titles.firstOrNull { !it.isNullOrBlank() }
            ui.value = ui.value.copy(
                resolving = false,
                name = title?.let(::cleanName) ?: code,
                note = if (title == null) "Nome non trovato: cerco direttamente il codice." else null,
            )
        }
    }

    fun setName(n: String) { ui.value = ui.value.copy(name = n) }
    fun reset() { ui.value = ScanUi() }

    companion object {
        /** "Apple iPhone 15 (128 GB) - Nero, con..." → "Apple iPhone 15 128 GB Nero" (max 8 parole). */
        fun cleanName(t: String): String {
            val cut = t.split(" - ", " | ", " – ", ", ").first().ifBlank { t }
            return cut.replace(Regex("[()\\[\\]]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }.take(8).joinToString(" ")
        }
    }
}

@Composable
fun ScanScreen(g: AppGraph, onSearch: (String) -> Unit) {
    val vm: ScanViewModel = viewModel { ScanViewModel(g) }
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState("Scansiona un codice a barre", "Inquadra il codice EAN di un prodotto in negozio: ShopAI trova il nome e cerca il prezzo online.") {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Consenti fotocamera") }
            }
            return@Box
        }
        CameraPreview(enabled = ui.code == null, onCode = vm::onCode)
        Box(Modifier.align(Alignment.Center).size(280.dp, 160.dp).border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp)))
        if (ui.code == null) {
            Text(
                "Inquadra il codice a barre", color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp),
            )
        }
        if (ui.code != null) {
            Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Codice ${ui.code}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (ui.resolving) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text("Cerco il nome del prodotto…")
                        }
                    } else {
                        Text("Trovato:", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = ui.name, onValueChange = vm::setName, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(ui.name); vm.reset() }),
                        )
                        ui.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = vm::reset) { Text("Riprova") }
                            Button(onClick = { onSearch(ui.name); vm.reset() }, modifier = Modifier.weight(1f)) { Text("Cerca prezzi") }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraPreview(enabled: Boolean, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val enabledState = remember { mutableStateOf(enabled) }
    enabledState.value = enabled
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build(),
        )
    }
    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { c ->
            val view = PreviewView(c)
            val providerFuture = ProcessCameraProvider.getInstance(c)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || !enabledState.value) { proxy.close(); return@setAnalyzer }
                    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(img)
                        .addOnSuccessListener { codes ->
                            codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let { v -> if (enabledState.value) onCode(v) }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(c))
            view
        }, modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)),
        ) {
            Icon(if (torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, "Torcia", tint = Color.White)
        }
    }
}
