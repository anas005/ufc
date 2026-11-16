package com.anas.ufc.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import kotlin.math.max

private val Context.ufcCacheDataStore by preferencesDataStore(name = "ufc_cache")
private val cacheSnapshotKey = stringPreferencesKey("snapshot")

class CacheStore(private val context: Context) : UfcCacheStore {
    private val json = Json { ignoreUnknownKeys = true }

    override val snapshots: Flow<CacheSnapshot> = context.ufcCacheDataStore.data.map { preferences ->
        preferences[cacheSnapshotKey]?.let(json::decodeFromString) ?: CacheSnapshot()
    }

    override suspend fun snapshot(): CacheSnapshot = snapshots.first()

    override suspend fun hasRequestBudget(required: Int, limit: Int, now: Instant): Boolean {
        val current = snapshot()
        val count = requestsForCurrentMonth(
            current.requestMonth,
            current.requestCount,
            now,
        )
        return canMakeRequests(count, limit, required)
    }

    override suspend fun reserveRequest(limit: Int, now: Instant): Boolean {
        var reserved = false
        val month = YearMonth.from(now.atZone(ZoneId.systemDefault())).toString()
        context.ufcCacheDataStore.edit { preferences ->
            val current = preferences[cacheSnapshotKey]?.let(json::decodeFromString) ?: CacheSnapshot()
            val currentCount = if (current.requestMonth == month) current.requestCount else 0
            if (currentCount < limit) {
                preferences[cacheSnapshotKey] = json.encodeToString(
                    current.copy(requestMonth = month, requestCount = currentCount + 1),
                )
                reserved = true
            }
        }
        return reserved
    }

    override suspend fun reserveApiRequestSlot(nowMillis: Long, spacingMillis: Long): Long {
        var delayMillis = 0L
        context.ufcCacheDataStore.edit { preferences ->
            val current = preferences[cacheSnapshotKey]?.let(json::decodeFromString) ?: CacheSnapshot()
            val slot = max(nowMillis, current.nextApiRequestMillis)
            delayMillis = slot - nowMillis
            preferences[cacheSnapshotKey] = json.encodeToString(
                current.copy(nextApiRequestMillis = slot + spacingMillis),
            )
        }
        return delayMillis
    }

    override suspend fun storeSuccess(
        events: List<UfcEvent>,
        profiles: Map<String, CachedFighterProfile>,
        selectedEventSlug: String?,
        nowMillis: Long,
        status: RefreshStatus,
    ) {
        context.ufcCacheDataStore.edit { preferences ->
            val current = preferences[cacheSnapshotKey]?.let(json::decodeFromString) ?: CacheSnapshot()
            preferences[cacheSnapshotKey] = json.encodeToString(
                current.copy(
                    events = events,
                    fighterProfiles = current.fighterProfiles + profiles,
                    selectedEventSlug = selectedEventSlug,
                    lastSuccessfulFetchMillis = nowMillis,
                    refreshStatus = status,
                ),
            )
        }
    }

    override suspend fun selectEvent(slug: String) {
        context.ufcCacheDataStore.edit { preferences ->
            val current = preferences[cacheSnapshotKey]?.let(json::decodeFromString) ?: CacheSnapshot()
            preferences[cacheSnapshotKey] = json.encodeToString(current.copy(selectedEventSlug = slug))
        }
    }

    override suspend fun setStatus(status: RefreshStatus) {
        context.ufcCacheDataStore.edit { preferences ->
            val current = preferences[cacheSnapshotKey]?.let(json::decodeFromString) ?: CacheSnapshot()
            preferences[cacheSnapshotKey] = json.encodeToString(current.withFailureStatus(status))
        }
    }
}

fun canMakeRequests(requestCount: Int, monthlyLimit: Int, required: Int): Boolean =
    required > 0 && requestCount >= 0 && requestCount + required <= monthlyLimit

fun CacheSnapshot.withFailureStatus(status: RefreshStatus): CacheSnapshot =
    copy(
        selectedEventSlug = selectedEventSlug
            ?.takeIf { slug ->
                events.any { event -> event.slug == slug && event.card.values.any { it.isNotEmpty() } }
            }
            ?: events.firstOrNull { event -> event.card.values.any { it.isNotEmpty() } }?.slug
            ?: selectedEventSlug,
        refreshStatus = status,
    )
