package com.eroideches.shopai.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.ranking.OfferSelector
import com.eroideches.shopai.core.ranking.Selection
import com.eroideches.shopai.core.ranking.SortOrder
import com.eroideches.shopai.data.AppSettings
import com.eroideches.shopai.search.SearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Filtri della sessione di ricerca (il pannello "Filtri"). */
data class Filters(
    val sites: Set<String>? = null,
    val maxPrice: Money? = null,
    val freeShippingOnly: Boolean = false,
    val maxDays: Int? = null,
    val includeAccessories: Boolean = false,
    val condition: ConditionFilter? = null,
    val sort: SortOrder? = null,
) {
    fun activeCount(): Int = listOf(sites != null, maxPrice != null, freeShippingOnly, maxDays != null, includeAccessories, condition != null, sort != null).count { it }
}

data class ResultsUi(
    val state: SearchState?,
    val selection: Selection?,
    val filters: Filters,
)

class SearchViewModel(private val g: AppGraph) : ViewModel() {
    val query = MutableStateFlow("")
    private val search = MutableStateFlow<SearchState?>(null)
    val filters = MutableStateFlow(Filters())
    private var job: Job? = null

    val settings: StateFlow<AppSettings> = g.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val recents = g.db.recents().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val follows = g.saved.follows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedKeys = g.saved.savedKeys.map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val rules = g.rules.rules

    /** Risultati ricalcolati sui filtri locali, senza rifare la ricerca. */
    val results: StateFlow<ResultsUi> = combine(search, filters, settings) { s, f, st ->
        if (s == null) return@combine ResultsUi(null, null, f)
        val base = st.selection()
        val sel = base.copy(
            maxPrice = f.maxPrice,
            freeShippingOnly = f.freeShippingOnly,
            maxDeliveryDays = f.maxDays,
            includeAccessories = f.includeAccessories,
            condition = f.condition ?: base.condition,
            sort = f.sort ?: base.sort,
        )
        val raw = if (f.sites == null) s.raw else s.raw.filter { it.siteId in f.sites }
        ResultsUi(s, OfferSelector(sel).select(s.query, raw), f)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ResultsUi(null, null, Filters()))

    fun search(q: String = query.value, refresh: Boolean = false) {
        val text = q.trim()
        if (text.isEmpty()) return
        query.value = text
        job?.cancel()
        job = viewModelScope.launch {
            val st = g.settings.current()
            val cond = filters.value.condition
            val s = if (cond != null) st.copy(condition = cond) else st
            // Con "Nuovo" i siti dell'usato e del ricondizionato sarebbero tutti scartati: non li interroghiamo.
            // Con "Usato"/"Tutti" Subito e Vinted entrano anche se spenti di default (a meno che tu li abbia spenti).
            val rules = g.rules.current.sites.filter { r ->
                val secondHand = r.usedMarket || r.defaultCondition != null
                when {
                    secondHand && s.condition == ConditionFilter.NEW -> false
                    secondHand -> r.id !in s.siteOff
                    else -> s.isEnabled(r)
                }
            }
            g.engine.search(text, rules, s, refresh).collect { search.value = it }
        }
    }

    fun clear() {
        job?.cancel()
        search.value = null
        query.value = ""
        filters.value = Filters()
    }

    fun setFilters(f: Filters) {
        val condChanged = f.condition != filters.value.condition
        filters.value = f
        if (condChanged && search.value != null) search(refresh = false)
    }

    fun toggleSave(offer: Offer) = viewModelScope.launch {
        if (g.saved.isSaved(offer.key)) g.saved.remove(offer.key) else g.saved.save(offer, search.value?.query ?: query.value)
    }

    fun follow(target: Money?) = viewModelScope.launch {
        val q = search.value?.query ?: return@launch
        val best = results.value.selection?.chosen?.minByOrNull { it.offer.total }?.offer
        g.saved.follow(q, target?.cents, best)
    }

    fun unfollow() = viewModelScope.launch { search.value?.query?.let { g.saved.unfollow(it) } }

    fun deleteRecent(q: String) = viewModelScope.launch { g.db.recents().delete(q) }
    fun clearRecents() = viewModelScope.launch { g.db.recents().clear() }

    fun open(offer: Offer) { g.openOffer = offer; g.openQuery = search.value?.query ?: query.value }
}
