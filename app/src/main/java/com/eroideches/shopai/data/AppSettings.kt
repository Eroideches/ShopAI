package com.eroideches.shopai.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.ranking.SelectionSettings
import com.eroideches.shopai.core.ranking.SortOrder
import com.eroideches.shopai.core.rules.SiteRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val zip: String = "20096",
    val city: String = "Pioltello (MI)",
    /** Siti accesi/spenti dall'utente; quelli non presenti seguono defaultEnabled delle regole. */
    val siteOn: Set<String> = emptySet(),
    val siteOff: Set<String> = emptySet(),
    val maxPerSite: Int = 3,
    val accessoryStrictness: Int = 1,
    val condition: ConditionFilter = ConditionFilter.NEW,
    val sort: SortOrder = SortOrder.TOTAL,
    val checkHours: Int = 6,
    val wifiOnly: Boolean = false,
    val enrich: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    fun isEnabled(rule: SiteRule): Boolean = when {
        rule.id in siteOff -> false
        rule.id in siteOn -> true
        else -> rule.defaultEnabled
    }

    fun selection(): SelectionSettings = SelectionSettings(
        maxPerSite = maxPerSite,
        accessoryStrictness = accessoryStrictness,
        condition = condition,
        sort = sort,
        minScore = when (accessoryStrictness) { 0 -> 0.45; 2 -> 0.62; else -> 0.55 },
    )
}

class SettingsRepository(private val context: Context) {
    private object K {
        val zip = stringPreferencesKey("zip")
        val city = stringPreferencesKey("city")
        val siteOn = stringSetPreferencesKey("site_on")
        val siteOff = stringSetPreferencesKey("site_off")
        val maxPerSite = intPreferencesKey("max_per_site")
        val strict = intPreferencesKey("accessory_strictness")
        val condition = stringPreferencesKey("condition")
        val sort = stringPreferencesKey("sort")
        val checkHours = intPreferencesKey("check_hours")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val enrich = booleanPreferencesKey("enrich")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val flow: Flow<AppSettings> = context.settingsStore.data.map(::read)

    suspend fun current(): AppSettings = flow.first()

    private fun read(p: Preferences) = AppSettings(
        zip = p[K.zip] ?: "20096",
        city = p[K.city] ?: "Pioltello (MI)",
        siteOn = p[K.siteOn] ?: emptySet(),
        siteOff = p[K.siteOff] ?: emptySet(),
        maxPerSite = p[K.maxPerSite] ?: 3,
        accessoryStrictness = p[K.strict] ?: 1,
        condition = p[K.condition]?.let { runCatching { ConditionFilter.valueOf(it) }.getOrNull() } ?: ConditionFilter.NEW,
        sort = p[K.sort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.TOTAL,
        checkHours = p[K.checkHours] ?: 6,
        wifiOnly = p[K.wifiOnly] ?: false,
        enrich = p[K.enrich] ?: true,
        onboardingDone = p[K.onboarding] ?: false,
    )

    suspend fun setZip(zip: String, city: String) = context.settingsStore.edit { it[K.zip] = zip.trim(); it[K.city] = city.trim() }

    suspend fun setSiteEnabled(id: String, on: Boolean) = context.settingsStore.edit {
        val onSet = (it[K.siteOn] ?: emptySet()).toMutableSet()
        val offSet = (it[K.siteOff] ?: emptySet()).toMutableSet()
        if (on) { onSet += id; offSet -= id } else { offSet += id; onSet -= id }
        it[K.siteOn] = onSet; it[K.siteOff] = offSet
    }

    suspend fun setMaxPerSite(v: Int) = context.settingsStore.edit { it[K.maxPerSite] = v.coerceIn(1, 10) }
    suspend fun setStrictness(v: Int) = context.settingsStore.edit { it[K.strict] = v.coerceIn(0, 2) }
    suspend fun setCondition(v: ConditionFilter) = context.settingsStore.edit { it[K.condition] = v.name }
    suspend fun setSort(v: SortOrder) = context.settingsStore.edit { it[K.sort] = v.name }
    suspend fun setCheckHours(v: Int) = context.settingsStore.edit { it[K.checkHours] = v }
    suspend fun setWifiOnly(v: Boolean) = context.settingsStore.edit { it[K.wifiOnly] = v }
    suspend fun setEnrich(v: Boolean) = context.settingsStore.edit { it[K.enrich] = v }
    suspend fun setOnboardingDone() = context.settingsStore.edit { it[K.onboarding] = true }
}
