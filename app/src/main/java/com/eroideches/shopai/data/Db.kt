package com.eroideches.shopai.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_products")
data class SavedProduct(
    @PrimaryKey val key: String,
    val siteId: String,
    val title: String,
    val url: String,
    val imageUrl: String?,
    val query: String,
    val initialCents: Long,
    val lastCents: Long,
    val lastShippingCents: Long?,
    val targetCents: Long?,
    val savedAt: Long,
    val lastCheckedAt: Long,
    val lowestCents: Long,
    val notifiedAtCents: Long? = null,
)

@Entity(tableName = "price_points", indices = [Index("key")])
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val cents: Long,
    val shippingCents: Long?,
    val at: Long,
)

@Entity(tableName = "followed_searches")
data class FollowedSearch(
    @PrimaryKey val query: String,
    val targetCents: Long?,
    val bestCents: Long?,
    val bestTitle: String?,
    val bestSite: String?,
    val bestUrl: String?,
    val createdAt: Long,
    val lastCheckedAt: Long,
    val notifiedAtCents: Long? = null,
)

@Entity(tableName = "recent_searches")
data class RecentSearch(
    @PrimaryKey val query: String,
    val at: Long,
    val bestCents: Long?,
)

@Entity(tableName = "cached_results")
data class CachedResult(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val at: Long,
)

@Dao
interface SavedDao {
    @Query("SELECT * FROM saved_products ORDER BY savedAt DESC") fun observe(): Flow<List<SavedProduct>>
    @Query("SELECT * FROM saved_products") suspend fun all(): List<SavedProduct>
    @Query("SELECT * FROM saved_products WHERE `key` = :key") suspend fun get(key: String): SavedProduct?
    @Query("SELECT * FROM saved_products WHERE `key` = :key") fun observeOne(key: String): Flow<SavedProduct?>
    @Query("SELECT `key` FROM saved_products") fun observeKeys(): Flow<List<String>>
    @Upsert suspend fun upsert(p: SavedProduct)
    @Query("DELETE FROM saved_products WHERE `key` = :key") suspend fun delete(key: String)
}

@Dao
interface PriceDao {
    @Insert suspend fun insert(p: PricePoint)
    @Query("SELECT * FROM price_points WHERE `key` = :key ORDER BY at") fun observe(key: String): Flow<List<PricePoint>>
    @Query("SELECT * FROM price_points WHERE `key` = :key ORDER BY at") suspend fun history(key: String): List<PricePoint>
    @Query("SELECT * FROM price_points WHERE `key` = :key ORDER BY at DESC LIMIT 1") suspend fun last(key: String): PricePoint?
    @Query("DELETE FROM price_points WHERE `key` = :key") suspend fun deleteFor(key: String)
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM followed_searches ORDER BY createdAt DESC") fun observe(): Flow<List<FollowedSearch>>
    @Query("SELECT * FROM followed_searches") suspend fun all(): List<FollowedSearch>
    @Query("SELECT * FROM followed_searches WHERE `query` = :q") suspend fun get(q: String): FollowedSearch?
    @Upsert suspend fun upsert(f: FollowedSearch)
    @Query("DELETE FROM followed_searches WHERE `query` = :q") suspend fun delete(q: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_searches ORDER BY at DESC LIMIT 12") fun observe(): Flow<List<RecentSearch>>
    @Upsert suspend fun upsert(r: RecentSearch)
    @Query("DELETE FROM recent_searches WHERE `query` = :q") suspend fun delete(q: String)
    @Query("DELETE FROM recent_searches") suspend fun clear()
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_results WHERE cacheKey = :k") suspend fun get(k: String): CachedResult?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(c: CachedResult)
    @Query("DELETE FROM cached_results WHERE at < :before") suspend fun purge(before: Long)
    @Query("DELETE FROM cached_results") suspend fun clear()
}

@Database(
    entities = [SavedProduct::class, PricePoint::class, FollowedSearch::class, RecentSearch::class, CachedResult::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saved(): SavedDao
    abstract fun prices(): PriceDao
    abstract fun follows(): FollowDao
    abstract fun recents(): RecentDao
    abstract fun cache(): CacheDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "shopai.db").fallbackToDestructiveMigration().build()
    }
}
