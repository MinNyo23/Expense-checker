package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.model.CachedCoupleSnapshot
import com.example.data.model.CoupleMemo
import com.example.data.model.CoupleSyncCounts
import com.example.data.model.DeviceSession
import com.example.data.model.Expense
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryItem
import com.example.data.model.MoneyAccount
import com.example.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent local disk + in-memory cache for couple workspace data.
 *
 * Stores expenses, memos (including Base64 images), profiles, live locations,
 * location history, device sessions, and per-table sync counts/watermarks in
 * app-private storage (`filesDir/couple_cache/`) so:
 * 1. App startup displays cached data immediately with zero backend latency.
 * 2. Periodic sync only checks row counts + latest timestamps and skips fetching
 *    unchanged tables completely.
 */
class LocalCoupleCache(context: Context? = null) {

    private val tag = "LocalCoupleCache"
    private val cacheDir: File? = context?.applicationContext?.filesDir?.let { File(it, "couple_cache") }
    private val mutex = Mutex()
    private val memoryCache = mutableMapOf<String, CachedCoupleSnapshot>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    init {
        cacheDir?.mkdirs()
    }

    suspend fun getSnapshot(coupleId: String): CachedCoupleSnapshot? {
        if (coupleId.isBlank()) return null
        return mutex.withLock {
            memoryCache[coupleId] ?: readSnapshotFromDisk(coupleId)?.also {
                memoryCache[coupleId] = it
            }
        }
    }

    suspend fun updateSnapshot(
        coupleId: String,
        transform: (CachedCoupleSnapshot) -> CachedCoupleSnapshot
    ): CachedCoupleSnapshot? {
        if (coupleId.isBlank()) return null
        return mutex.withLock {
            val current = memoryCache[coupleId]
                ?: readSnapshotFromDisk(coupleId)
                ?: CachedCoupleSnapshot(couple_id = coupleId)
            val updated = transform(current).copy(
                couple_id = coupleId,
                last_synced_at_ms = System.currentTimeMillis()
            )
            memoryCache[coupleId] = updated
            writeSnapshotToDisk(coupleId, updated)
            updated
        }
    }

    suspend fun clearCoupleCache(coupleId: String) {
        if (coupleId.isBlank()) return
        mutex.withLock {
            memoryCache.remove(coupleId)
            withContext(Dispatchers.IO) {
                try {
                    val file = cacheFileFor(coupleId)
                    if (file != null && file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed deleting cache file: ${e.message}")
                }
            }
        }
    }

    suspend fun getCachedItemCount(coupleId: String): Int {
        val snap = getSnapshot(coupleId) ?: return 0
        return snap.expenses.size +
            snap.memos.size +
            snap.profiles.size +
            snap.live_locations.size +
            snap.location_history.size +
            snap.device_sessions.size +
            snap.money_accounts.size
    }

    private suspend fun readSnapshotFromDisk(coupleId: String): CachedCoupleSnapshot? = withContext(Dispatchers.IO) {
        val file = cacheFileFor(coupleId) ?: return@withContext null
        if (!file.exists()) return@withContext null
        try {
            val raw = file.readText()
            if (raw.isBlank()) return@withContext null
            json.decodeFromString<CachedCoupleSnapshot>(raw)
        } catch (e: Exception) {
            Log.w(tag, "Failed reading couple cache for $coupleId: ${e.message}")
            null
        }
    }

    private suspend fun writeSnapshotToDisk(coupleId: String, snapshot: CachedCoupleSnapshot) = withContext(Dispatchers.IO) {
        val file = cacheFileFor(coupleId) ?: return@withContext
        try {
            file.parentFile?.mkdirs()
            val tmpFile = File(file.parentFile, "${file.name}.tmp")
            tmpFile.writeText(json.encodeToString(snapshot))
            if (!tmpFile.renameTo(file)) {
                file.writeText(tmpFile.readText())
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed writing couple cache for $coupleId: ${e.message}")
        }
    }

    private fun cacheFileFor(coupleId: String): File? {
        val dir = cacheDir ?: return null
        val safeId = coupleId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "couple_$safeId.json")
    }

    companion object {
        fun computeCounts(
            expenses: List<Expense>,
            memos: List<CoupleMemo>,
            profiles: List<UserProfile>,
            liveLocations: List<LiveLocation>,
            locationHistory: List<LocationHistoryItem>,
            deviceSessions: List<DeviceSession>,
            moneyAccounts: List<MoneyAccount> = emptyList()
        ): CoupleSyncCounts {
            return CoupleSyncCounts(
                expenses_count = expenses.size,
                expenses_latest = expenses.maxOfOrNull { it.created_at.orEmpty() } ?: "",
                memos_count = memos.size,
                memos_latest = memos.maxOfOrNull { it.created_at } ?: "",
                profiles_count = profiles.size,
                profiles_latest = profiles.mapNotNull { it.updated_at }.maxOrNull() ?: "",
                live_locations_count = liveLocations.size,
                live_locations_latest = liveLocations.maxOfOrNull { it.updated_at } ?: 0L,
                location_history_count = locationHistory.size,
                location_history_latest = locationHistory.maxOfOrNull { it.recorded_at } ?: 0L,
                device_sessions_count = deviceSessions.size,
                device_sessions_latest = deviceSessions.maxOfOrNull { it.last_login_at } ?: "",
                money_accounts_count = moneyAccounts.size,
                money_accounts_latest = moneyAccounts.maxOfOrNull { it.updated_at } ?: 0L
            )
        }
    }
}
