package com.eroideches.shopai.data

import com.eroideches.shopai.core.model.Offer

/** Prodotti salvati, ricerche seguite e storico prezzi. */
class SavedRepository(private val db: AppDatabase) {
    val products = db.saved().observe()
    val savedKeys = db.saved().observeKeys()
    val follows = db.follows().observe()

    fun observe(key: String) = db.saved().observeOne(key)
    fun history(key: String) = db.prices().observe(key)

    suspend fun isSaved(key: String) = db.saved().get(key) != null

    suspend fun save(offer: Offer, query: String, targetCents: Long? = null) {
        val now = System.currentTimeMillis()
        val existing = db.saved().get(offer.key)
        db.saved().upsert(
            SavedProduct(
                key = offer.key, siteId = offer.siteId, title = offer.title, url = offer.url, imageUrl = offer.imageUrl,
                query = existing?.query ?: query,
                initialCents = existing?.initialCents ?: offer.total.cents,
                lastCents = offer.total.cents,
                lastShippingCents = offer.shipping.cost?.cents,
                targetCents = targetCents ?: existing?.targetCents,
                savedAt = existing?.savedAt ?: now,
                lastCheckedAt = now,
                lowestCents = minOf(existing?.lowestCents ?: Long.MAX_VALUE, offer.total.cents),
            ),
        )
        recordPrice(offer.key, offer.total.cents, offer.shipping.cost?.cents)
    }

    suspend fun setTarget(key: String, targetCents: Long?) {
        val p = db.saved().get(key) ?: return
        db.saved().upsert(p.copy(targetCents = targetCents, notifiedAtCents = null))
    }

    suspend fun remove(key: String) {
        db.saved().delete(key)
        db.prices().deleteFor(key)
    }

    /** Salva un punto dello storico se il prezzo è cambiato o se l'ultimo punto è di più di 12 ore fa. */
    suspend fun recordPrice(key: String, cents: Long, shippingCents: Long?) {
        val last = db.prices().last(key)
        val now = System.currentTimeMillis()
        if (last == null || last.cents != cents || now - last.at > 12 * 3600_000L) {
            db.prices().insert(PricePoint(key = key, cents = cents, shippingCents = shippingCents, at = now))
        }
    }

    suspend fun follow(query: String, targetCents: Long?, best: Offer?) {
        val now = System.currentTimeMillis()
        val ex = db.follows().get(query)
        db.follows().upsert(
            FollowedSearch(
                query = query, targetCents = targetCents,
                bestCents = best?.total?.cents ?: ex?.bestCents, bestTitle = best?.title ?: ex?.bestTitle,
                bestSite = best?.siteId ?: ex?.bestSite, bestUrl = best?.url ?: ex?.bestUrl,
                createdAt = ex?.createdAt ?: now, lastCheckedAt = now,
            ),
        )
    }

    suspend fun unfollow(query: String) = db.follows().delete(query)
    suspend fun isFollowed(query: String) = db.follows().get(query) != null
}
