package com.eroideches.shopai.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eroideches.shopai.ShopAIApp
import com.eroideches.shopai.core.model.Money
import com.eroideches.shopai.core.ranking.ConditionFilter
import com.eroideches.shopai.core.ranking.RelevanceScorer
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Ricontrolla prodotti salvati e ricerche seguite, aggiorna lo storico e avvisa se il prezzo scende. */
class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val g = (applicationContext as ShopAIApp).graph
        val settings = g.settings.current()
        val rules = g.rules.current
        val scorer = RelevanceScorer(accessoryStrictness = 0)

        for (p in g.db.saved().all()) {
            val rule = rules.site(p.siteId) ?: continue
            val out = withTimeoutOrNull(45_000) { g.searcher.search(rule, p.query, settings.zip, ConditionFilter.ANY) } ?: continue
            val match = out.offers.firstOrNull { it.key == p.key }
                ?: out.offers
                    .map { it to scorer.score(p.title, it.title).value }
                    .filter { it.second >= 0.85 }
                    .minByOrNull { kotlin.math.abs(it.first.total.cents - p.lastCents) }?.first
                ?: continue
            val now = match.total.cents
            g.saved.recordPrice(p.key, now, match.shipping.cost?.cents)
            val dropped = now < p.lastCents
            var notified = p.notifiedAtCents
            val hitTarget = p.targetCents != null && now <= p.targetCents && (notified == null || now < notified)
            val bigDrop = p.targetCents == null && dropped && now <= p.lastCents * 95 / 100
            if (hitTarget || bigDrop) {
                g.notifier.priceDrop(
                    id = p.key.hashCode(),
                    title = if (hitTarget) "Prezzo raggiunto: ${Money(now).format()}" else "Prezzo sceso: ${Money(now).format()}",
                    text = "${p.title.take(80)} su ${rule.name} (prima ${Money(p.lastCents).format()})",
                    url = p.url,
                )
                notified = now
            }
            g.db.saved().upsert(
                p.copy(lastCents = now, lastShippingCents = match.shipping.cost?.cents, lastCheckedAt = System.currentTimeMillis(),
                    lowestCents = minOf(p.lowestCents, now), notifiedAtCents = notified),
            )
        }

        val enabled = rules.sites.filter { settings.isEnabled(it) }
        for (f in g.db.follows().all()) {
            val state = withTimeoutOrNull(120_000) { g.engine.search(f.query, enabled, settings, refresh = true).last() } ?: continue
            val best = state.selection.chosen.minByOrNull { it.offer.total }?.offer ?: continue
            val cents = best.total.cents
            var notified = f.notifiedAtCents
            val hitTarget = f.targetCents != null && cents <= f.targetCents && (notified == null || cents < notified)
            val bigDrop = f.targetCents == null && f.bestCents != null && cents <= f.bestCents * 95 / 100
            if (hitTarget || bigDrop) {
                g.notifier.priceDrop(
                    id = f.query.hashCode(),
                    title = "\"${f.query}\" ora a ${Money(cents).format()}",
                    text = "${best.title.take(80)} su ${rules.site(best.siteId)?.name ?: best.siteId}",
                    url = best.url,
                )
                notified = cents
            }
            g.db.follows().upsert(
                f.copy(bestCents = cents, bestTitle = best.title, bestSite = best.siteId, bestUrl = best.url,
                    lastCheckedAt = System.currentTimeMillis(), notifiedAtCents = notified),
            )
        }
        return Result.success()
    }
}

object PriceCheckScheduler {
    private const val PERIODIC = "price_check"
    private const val NOW = "price_check_now"

    fun schedule(context: Context, hours: Int, wifiOnly: Boolean) {
        val c = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
            .setConstraints(c)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Controllo immediato (Diagnostica → "Controlla prezzi adesso"). */
    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun observeNow(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW)
}
