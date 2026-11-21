package com.eroideches.shopai.search

import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.ranking.OfferSelector
import com.eroideches.shopai.core.ranking.Selection
import com.eroideches.shopai.core.ranking.SelectionSettings
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.data.AppDatabase
import com.eroideches.shopai.data.AppSettings
import com.eroideches.shopai.data.CachedResult
import com.eroideches.shopai.data.RecentSearch
import com.eroideches.shopai.sites.SiteOutcome
import com.eroideches.shopai.sites.SiteSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class SiteState { LOADING, DONE, EMPTY, ERROR, BLOCKED }

data class SiteStatus(
    val rule: SiteRule,
    val state: SiteState,
    val count: Int = 0,
    val outcome: SiteOutcome? = null,
)

data class SearchState(
    val query: String,
    val sites: List<SiteStatus>,
    val selection: Selection,
    val raw: List<Offer>,
    val finished: Boolean,
    val fromCache: Boolean = false,
    val enriching: Boolean = false,
)

/** Cerca su tutti i siti accesi in parallelo e ricalcola la scelta ogni volta che un sito risponde. */
class SearchEngine(private val searcher: SiteSearcher, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun search(query: String, rules: List<SiteRule>, settings: AppSettings, refresh: Boolean = false): Flow<SearchState> = callbackFlow {
        val sel = settings.selection()
        val cacheKey = "${query.trim().lowercase()}|${settings.zip}|${settings.condition}|${rules.joinToString(",") { it.id }}"
        val statuses = linkedMapOf<String, SiteStatus>()
        rules.forEach { statuses[it.id] = SiteStatus(it, SiteState.LOADING) }
        val outcomes = mutableMapOf<String, SiteOutcome>()
        val lock = Mutex()

        fun snapshot(finished: Boolean, fromCache: Boolean = false, enriching: Boolean = false): SearchState {
            val raw = outcomes.values.flatMap { it.offers }
            return SearchState(query, statuses.values.toList(), OfferSelector(sel).select(query, raw), raw, finished, fromCache, enriching)
        }

        // Cache di 30 minuti per la stessa ricerca
        if (!refresh) {
            val c = db.cache().get(cacheKey)
            if (c != null && System.currentTimeMillis() - c.at < CACHE_MS) {
                val cached = runCatching { json.decodeFromString(ListSerializer(SiteOutcome.serializer()), c.json) }.getOrNull()
                if (cached != null) {
                    cached.forEach { o ->
                        outcomes[o.siteId] = o
                        statuses[o.siteId]?.let { s -> statuses[o.siteId] = s.copy(state = stateOf(o), count = o.offers.size, outcome = o) }
                    }
                    if (cached.map { it.siteId }.containsAll(rules.map { it.id })) {
                        trySend(snapshot(finished = true, fromCache = true))
                        close()
                        awaitClose { }
                        return@callbackFlow
                    }
                }
            }
        }
        trySend(snapshot(false))

        val job = launch(Dispatchers.Default) {
            coroutineScope {
                for (rule in rules.filter { outcomes[it.id] == null }) launch {
                    val o = withTimeoutOrNull(SITE_TIMEOUT_MS) { searcher.search(rule, query, settings.zip, settings.condition) }
                        ?: SiteOutcome(rule.id, emptyList(), null, true, false, "tempo scaduto", SITE_TIMEOUT_MS)
                    lock.withLock {
                        outcomes[rule.id] = o
                        statuses[rule.id] = SiteStatus(rule, stateOf(o), o.offers.size, o)
                        trySend(snapshot(false))
                    }
                }
            }
            // Spedizione e consegna esatte per i prodotti scelti che non le mostrano nella ricerca
            if (settings.enrich) enrich(rules, sel, query, outcomes, lock) { trySend(snapshot(false, enriching = true)) }
            lock.withLock { trySend(snapshot(true)) }
            runCatching {
                db.cache().purge(System.currentTimeMillis() - CACHE_MS)
                db.cache().put(CachedResult(cacheKey, json.encodeToString(ListSerializer(SiteOutcome.serializer()), outcomes.values.toList()), System.currentTimeMillis()))
                val best = OfferSelector(sel).select(query, outcomes.values.flatMap { it.offers }).chosen.minByOrNull { it.offer.total }
                db.recents().upsert(RecentSearch(query.trim(), System.currentTimeMillis(), best?.offer?.total?.cents))
            }
            close()
        }
        awaitClose { job.cancel() }
    }

    private suspend fun CoroutineScope.enrich(
        rules: List<SiteRule>, sel: SelectionSettings, query: String,
        outcomes: MutableMap<String, SiteOutcome>, lock: Mutex, onUpdate: () -> Unit,
    ) {
        val byId = rules.associateBy { it.id }
        val targets = lock.withLock {
            OfferSelector(sel).select(query, outcomes.values.flatMap { it.offers }).chosen
                .map { it.offer }
                .filter { !it.shipping.isKnown && byId[it.siteId]?.enrich == true }
                .take(MAX_ENRICH)
        }
        if (targets.isEmpty()) return
        val gate = Semaphore(2)
        coroutineScope {
            for (o in targets) launch {
                gate.withPermit {
                    val rule = byId[o.siteId] ?: return@withPermit
                    val better = withTimeoutOrNull(20_000) { searcher.enrich(rule, o) } ?: return@withPermit
                    if (better == o) return@withPermit
                    lock.withLock {
                        val out = outcomes[o.siteId] ?: return@withLock
                        outcomes[o.siteId] = out.copy(offers = out.offers.map { if (it.key == o.key) better else it })
                        onUpdate()
                    }
                }
            }
        }
    }

    private fun stateOf(o: SiteOutcome) = when {
        o.offers.isNotEmpty() -> SiteState.DONE
        o.blocked -> SiteState.BLOCKED
        o.error != null && o.error != "nessun prodotto letto" -> SiteState.ERROR
        else -> SiteState.EMPTY
    }

    companion object {
        const val CACHE_MS = 30 * 60_000L
        const val SITE_TIMEOUT_MS = 35_000L
        const val MAX_ENRICH = 8
    }
}
