package com.anas.ufc.data

import android.content.Context
import com.anas.ufc.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import java.time.Instant

interface UfcCacheStore {
    val snapshots: Flow<CacheSnapshot>
    suspend fun snapshot(): CacheSnapshot
    suspend fun hasRequestBudget(required: Int, limit: Int, now: Instant): Boolean
    suspend fun reserveRequest(limit: Int, now: Instant): Boolean
    suspend fun reserveApiRequestSlot(nowMillis: Long, spacingMillis: Long): Long
    suspend fun storeSuccess(
        events: List<UfcEvent>,
        profiles: Map<String, CachedFighterProfile>,
        selectedEventSlug: String?,
        nowMillis: Long,
        status: RefreshStatus,
    )
    suspend fun selectEvent(slug: String)
    suspend fun setStatus(status: RefreshStatus)
}

interface UfcRepository {
    val cache: Flow<CacheSnapshot>
    suspend fun snapshot(): CacheSnapshot
    suspend fun refresh(manual: Boolean, requestedEventSlug: String? = null): RefreshOutcome
    suspend fun selectEvent(slug: String)
    suspend fun setRefreshStatus(status: RefreshStatus)
}

class UfcRepositoryImpl(
    private val api: UfcApi,
    private val cacheStore: UfcCacheStore,
    private val apiKeyConfigured: Boolean,
    private val now: () -> Instant = Instant::now,
) : UfcRepository {
    override val cache: Flow<CacheSnapshot> = cacheStore.snapshots

    override suspend fun snapshot(): CacheSnapshot = cacheStore.snapshot()

    override suspend fun setRefreshStatus(status: RefreshStatus) {
        cacheStore.setStatus(status)
    }

    override suspend fun selectEvent(slug: String) {
        cacheStore.selectEvent(slug)
    }

    override suspend fun refresh(manual: Boolean, requestedEventSlug: String?): RefreshOutcome {
        val startedAt = now()
        val initial = cacheStore.snapshot()
        if (manual && isWithinManualCooldown(initial.lastSuccessfulFetchMillis, startedAt.toEpochMilli())) {
            cacheStore.setStatus(RefreshStatus.ALREADY_UP_TO_DATE)
            return RefreshOutcome(skippedForCooldown = true)
        }
        if (!apiKeyConfigured) throw MissingApiKeyException()
        val requiredRequests = if (requestedEventSlug == null) 2 else 1
        if (!cacheStore.hasRequestBudget(requiredRequests, RefreshConfig.monthlyRequestLimit, startedAt)) {
            cacheStore.setStatus(RefreshStatus.MONTHLY_LIMIT_REACHED)
            return RefreshOutcome(monthlyLimitReached = true)
        }

        val eventRows: List<EventDto>
        val current = cacheStore.snapshot()
        val detail = if (requestedEventSlug != null) {
            eventRows = current.events.map { it.toEventDto() }
            callWithBudget { api.event(requestedEventSlug).data }
        } else {
            val upcomingEvents = callWithBudget { api.events(status = "upcoming", order = "asc").data }
            eventRows = if (upcomingEvents.isNotEmpty()) upcomingEvents
            else callWithBudget { api.events(status = "completed", order = "desc", limit = 1).data }
            val selected = selectDefaultEvent(eventRows, startedAt.toEpochMilli())
            if (selected == null) {
                cacheStore.storeSuccess(
                    events = emptyList(),
                    profiles = emptyMap(),
                    selectedEventSlug = null,
                    nowMillis = now().toEpochMilli(),
                    status = RefreshStatus.IDLE,
                )
                return RefreshOutcome()
            }
            callWithBudget { api.event(selected.slug).data }
        }

        val profiles = current.fighterProfiles.toMutableMap()
        val fighterRefs = detail.card.flatMap { listOfNotNull(it.fighterA, it.fighterB) }
            .distinctBy { it.id }
        var budgetExhausted = false
        for (fighter in fighterRefs) {
            val key = fighter.id.toString()
            if (key in profiles) continue
            profiles[key] = try {
                callWithBudget { api.fighter(fighter.slug).data }.toCachedProfile()
            } catch (_: MonthlyRequestBudgetException) {
                budgetExhausted = true
                break
            }
        }

        val domainEvent = detail.toDomain(profiles)
        val summaries = eventRows.map(EventDto::toSummary).toMutableList()
        val detailedIndex = summaries.indexOfFirst { it.slug == domainEvent.slug }
        if (detailedIndex >= 0) summaries[detailedIndex] = domainEvent
        else summaries.add(0, domainEvent)
        cacheStore.storeSuccess(
            events = summaries,
            profiles = profiles,
            selectedEventSlug = domainEvent.slug,
            nowMillis = now().toEpochMilli(),
            status = if (budgetExhausted) RefreshStatus.MONTHLY_LIMIT_REACHED else RefreshStatus.IDLE,
        )
        return RefreshOutcome(monthlyLimitReached = budgetExhausted)
    }

    private suspend fun <T> callWithBudget(call: suspend () -> T): T {
        val instant = now()
        if (!cacheStore.hasRequestBudget(1, RefreshConfig.monthlyRequestLimit, instant)) {
            throw MonthlyRequestBudgetException()
        }
        val waitMillis = cacheStore.reserveApiRequestSlot(
            instant.toEpochMilli(),
            RefreshConfig.apiRequestSpacing.toMillis(),
        )
        if (waitMillis > 0) delay(waitMillis)
        if (!cacheStore.reserveRequest(RefreshConfig.monthlyRequestLimit, now())) {
            throw MonthlyRequestBudgetException()
        }
        return call()
    }

    private fun UfcEvent.toEventDto() = EventDto(id, slug, name, startsAt, status)
}

class MonthlyRequestBudgetException : IllegalStateException()

object UfcRepositoryProvider {
    @Volatile
    private var instance: UfcRepository? = null

    fun get(context: Context): UfcRepository = instance ?: synchronized(this) {
        instance ?: run {
            val applicationContext = context.applicationContext
            UfcRepositoryImpl(
                api = UfcApiFactory.create(),
                cacheStore = CacheStore(applicationContext),
                apiKeyConfigured = BuildConfig.UFCAL_API_KEY.isNotBlank(),
            ).also { instance = it }
        }
    }
}
