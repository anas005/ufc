package com.anas.ufc.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class UfcRepositoryTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")

    @Test
    fun exhaustedBudgetReturnsCacheWithoutCallingApi() = runBlocking {
        val cachedEvent = cachedEvent()
        val cache = FakeCache(CacheSnapshot(events = listOf(cachedEvent), selectedEventSlug = cachedEvent.slug), allowBudget = false)
        var apiCalls = 0
        val api = fakeApi { apiCalls++; EventsResponseDto() }

        val result = repository(api, cache).refresh(manual = false)

        assertTrue(result.monthlyLimitReached)
        assertEquals(0, apiCalls)
        assertEquals(cachedEvent, cache.current.events.single())
        assertEquals(RefreshStatus.MONTHLY_LIMIT_REACHED, cache.current.refreshStatus)
    }

    @Test
    fun failedNetworkFetchLeavesCachedEventsAndSuccessTimeUntouched() = runBlocking {
        val cachedEvent = cachedEvent()
        val cache = FakeCache(
            CacheSnapshot(
                events = listOf(cachedEvent),
                selectedEventSlug = cachedEvent.slug,
                lastSuccessfulFetchMillis = 1234L,
            ),
        )
        val api = fakeApi { throw IOException() }

        try {
            repository(api, cache).refresh(manual = false)
            throw AssertionError()
        } catch (_: IOException) {
            assertEquals(listOf(cachedEvent), cache.current.events)
            assertEquals(cachedEvent.slug, cache.current.selectedEventSlug)
            assertEquals(1234L, cache.current.lastSuccessfulFetchMillis)
        }
    }

    @Test
    fun cooldownSkipsManualRequest() = runBlocking {
        val cachedEvent = cachedEvent()
        val cache = FakeCache(
            CacheSnapshot(
                events = listOf(cachedEvent),
                selectedEventSlug = cachedEvent.slug,
                lastSuccessfulFetchMillis = now.toEpochMilli() - 60_000L,
            ),
        )
        var apiCalls = 0
        val api = fakeApi { apiCalls++; EventsResponseDto() }

        val result = repository(api, cache).refresh(manual = true)

        assertTrue(result.skippedForCooldown)
        assertEquals(0, apiCalls)
        assertEquals(RefreshStatus.ALREADY_UP_TO_DATE, cache.current.refreshStatus)
    }

    private fun repository(api: UfcApi, cache: FakeCache) =
        UfcRepositoryImpl(api, cache, apiKeyConfigured = true, now = { now })

    private fun cachedEvent() = UfcEvent(
        id = 1,
        slug = "cached-event",
        name = "Cached event",
        startsAt = "2026-11-15T10:00:00Z",
        status = "live",
        card = emptyMap(),
    )

    private fun fakeApi(onEvents: suspend () -> EventsResponseDto): UfcApi =
        object : UfcApi {
            override suspend fun events(
                organization: String,
                status: String?,
                order: String?,
                limit: Int,
            ): EventsResponseDto = onEvents()

            override suspend fun event(idOrSlug: String, include: String): EventResponseDto {
                throw AssertionError()
            }

            override suspend fun fighter(idOrSlug: String): FighterResponseDto {
                throw AssertionError()
            }
        }

    private class FakeCache(
        initial: CacheSnapshot,
        private val allowBudget: Boolean = true,
    ) : UfcCacheStore {
        var current = initial
            private set
        override val snapshots: Flow<CacheSnapshot> get() = flowOf(current)

        override suspend fun snapshot(): CacheSnapshot = current

        override suspend fun hasRequestBudget(required: Int, limit: Int, now: Instant): Boolean =
            allowBudget

        override suspend fun reserveRequest(limit: Int, now: Instant): Boolean {
            if (!allowBudget) return false
            current = current.copy(requestCount = current.requestCount + 1)
            return true
        }

        override suspend fun reserveApiRequestSlot(nowMillis: Long, spacingMillis: Long): Long = 0

        override suspend fun storeSuccess(
            events: List<UfcEvent>,
            profiles: Map<String, CachedFighterProfile>,
            selectedEventSlug: String?,
            nowMillis: Long,
            status: RefreshStatus,
        ) {
            current = current.copy(
                events = events,
                fighterProfiles = current.fighterProfiles + profiles,
                selectedEventSlug = selectedEventSlug,
                lastSuccessfulFetchMillis = nowMillis,
                refreshStatus = status,
            )
        }

        override suspend fun selectEvent(slug: String) {
            current = current.copy(selectedEventSlug = slug)
        }

        override suspend fun setStatus(status: RefreshStatus) {
            current = current.withFailureStatus(status)
        }
    }
}
