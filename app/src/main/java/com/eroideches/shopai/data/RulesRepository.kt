package com.eroideches.shopai.data

import android.content.Context
import com.eroideches.shopai.BuildConfig
import com.eroideches.shopai.core.rules.RulesFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Regole dei siti: copia negli asset (sempre disponibile) + versione scaricata dalla repo pubblica,
 * così un sito che cambia grafica si ripara senza reinstallare l'app.
 */
class RulesRepository(private val context: Context, private val http: OkHttpClient) {
    private val cached = File(context.filesDir, "sites.json")
    private val bundled: RulesFile by lazy {
        RulesFile.parse(context.assets.open("sites.json").bufferedReader().use { it.readText() })
    }

    private val _rules = MutableStateFlow(loadBest())
    val rules: StateFlow<RulesFile> = _rules

    val current: RulesFile get() = _rules.value

    private fun loadBest(): RulesFile {
        val local = runCatching { if (cached.exists()) RulesFile.parse(cached.readText()) else null }.getOrNull()
        return if (local != null && local.version >= bundled.version && local.minAppVersion <= BuildConfig.VERSION_CODE) local else bundled
    }

    val lastUpdate: Long get() = if (cached.exists()) cached.lastModified() else 0L

    /** Scarica le regole se più vecchie di [maxAgeMs]. Restituisce un messaggio per l'interfaccia. */
    suspend fun refresh(force: Boolean = false, maxAgeMs: Long = 24 * 3600_000L): String = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastUpdate < maxAgeMs) return@withContext "Regole già aggiornate"
        runCatching {
            val req = Request.Builder().url(REMOTE_URL).header("Cache-Control", "no-cache").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val text = resp.body?.string() ?: error("risposta vuota")
                val parsed = RulesFile.parse(text)
                if (parsed.minAppVersion > BuildConfig.VERSION_CODE) error("serve una versione più recente dell'app")
                if (parsed.version < bundled.version) error("versione remota più vecchia")
                cached.writeText(text)
                _rules.value = parsed
                "Regole aggiornate (v${parsed.version}, ${parsed.sites.size} siti)"
            }
        }.getOrElse { "Aggiornamento regole non riuscito: ${it.message}" }
    }

    companion object {
        const val REMOTE_URL = "https://raw.githubusercontent.com/eroideches/ShopAI/main/rules/sites.json"
    }
}
