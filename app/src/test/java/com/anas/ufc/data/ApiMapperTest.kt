package com.anas.ufc.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ApiMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsWinnerDrawUnfinishedFightAndMissingImage() {
        val event = json.decodeFromString<EventResponseDto>(fixture("event-detail.json")).data
        val fighterWithImage = json.decodeFromString<FighterResponseDto>(fixture("fighter-with-image.json")).data
        val fighterWithoutImage = json.decodeFromString<FighterResponseDto>(fixture("fighter-without-image.json")).data
        val profiles = mapOf(
            fighterWithImage.id.toString() to fighterWithImage.toCachedProfile(),
            fighterWithoutImage.id.toString() to fighterWithoutImage.toCachedProfile(),
        )

        val fights = event.toDomain(profiles).card.getValue(CardSection.MAIN)
        assertTrue(fights[0].left?.isWinner == true)
        assertFalse(fights[0].right?.isWinner == true)
        assertEquals("5-1-0", fights[0].left?.record)
        assertEquals("https://upload.wikimedia.org/example.jpg", fights[0].left?.imageUrl)
        assertEquals("Fixture Artist", fights[0].left?.credits?.single()?.artist)
        assertTrue(fights[1].result?.isDraw == true)
        assertFalse(fights[1].left?.isWinner == true)
        assertFalse(fights[1].right?.isWinner == true)
        assertFalse(fights[2].isFinal)
        assertNull(fights[2].result)
        assertNull(fights[1].left?.imageUrl)
        assertEquals("8-2-0", fights[1].left?.record)
    }

    @Test
    fun buildsResultTextFromApiFields() {
        val submission = FightResult("Submission", "submission", 4, "1:29", false)
        assertEquals(
            "Submission · R4 1:29",
            buildResultText(submission, "Draw", "R%d %s", "%s · %s", "%s", " · "),
        )
        val draw = FightResult("Decision", "draw", 3, "5:00", true)
        assertEquals(
            "Draw · Decision · R3 5:00",
            buildResultText(draw, "Draw", "R%d %s", "%s · %s", "%s", " · "),
        )
    }

    @Test
    fun formatsDateInDeviceZoneAndLocale() {
        val now = Instant.parse("2026-11-15T20:00:00Z")
        val zone = ZoneId.of("America/Los_Angeles")
        assertEquals(
            "Today, 1:30 PM",
            EventDateFormatter.format("2026-11-15T21:30:00Z", now, zone, Locale.US, "Today", "Tomorrow"),
        )
        assertEquals(
            "Tomorrow, 1:30 AM",
            EventDateFormatter.format("2026-11-16T09:30:00Z", now, zone, Locale.US, "Today", "Tomorrow"),
        )
    }

    @Test
    fun manualCooldownEndsAtConfiguredBoundary() {
        val lastSuccess = 1_000_000L
        assertTrue(isWithinManualCooldown(lastSuccess, lastSuccess + 9 * 60 * 1000L))
        assertFalse(isWithinManualCooldown(lastSuccess, lastSuccess + 10 * 60 * 1000L))
    }

    @Test
    fun requestCounterRollsOverOnMonthChange() {
        val now = Instant.parse("2026-12-01T00:00:00Z")
        assertEquals(0, requestsForCurrentMonth("2026-11", 100, now, ZoneId.of("UTC")))
        assertEquals(37, requestsForCurrentMonth("2026-12", 37, now, ZoneId.of("UTC")))
    }

    @Test
    fun exhaustedBudgetRejectsFurtherRequests() {
        assertTrue(canMakeRequests(98, 100, 2))
        assertFalse(canMakeRequests(99, 100, 2))
        assertFalse(canMakeRequests(100, 100, 1))
    }

    @Test
    fun failedFetchStatusChangeKeepsCachedResponseUntouched() {
        val cachedEvent = UfcEvent(1, "cached", "Cached card", "2026-11-15T12:00:00Z", "live", emptyMap())
        val cachedProfile = CachedFighterProfile("1-0-0", null, emptyList())
        val before = CacheSnapshot(
            events = listOf(cachedEvent),
            fighterProfiles = mapOf("1" to cachedProfile),
            selectedEventSlug = "cached",
            lastSuccessfulFetchMillis = 1234L,
            requestMonth = "2026-11",
            requestCount = 8,
        )

        val afterFailure = before.withFailureStatus(RefreshStatus.OFFLINE)

        assertEquals(before.events, afterFailure.events)
        assertEquals(before.fighterProfiles, afterFailure.fighterProfiles)
        assertEquals(before.selectedEventSlug, afterFailure.selectedEventSlug)
        assertEquals(before.lastSuccessfulFetchMillis, afterFailure.lastSuccessfulFetchMillis)
        assertEquals(before.requestMonth, afterFailure.requestMonth)
        assertEquals(before.requestCount, afterFailure.requestCount)
        assertEquals(RefreshStatus.OFFLINE, afterFailure.refreshStatus)
    }

    @Test
    fun defaultEventPrefersLiveThenNextAndFallsBackToMostRecent() {
        val now = Instant.parse("2026-11-15T12:00:00Z").toEpochMilli()
        val live = EventDto(1, "live", "Live", "2026-11-15T10:00:00Z", "live")
        val upcoming = EventDto(2, "next", "Next", "2026-11-16T10:00:00Z", "scheduled")
        val recent = EventDto(3, "recent", "Recent", "2026-11-14T10:00:00Z", "completed")
        assertEquals("live", selectDefaultEvent(listOf(upcoming, live), now)?.slug)
        assertEquals("next", selectDefaultEvent(listOf(upcoming, recent), now)?.slug)
        assertEquals("recent", selectDefaultEvent(listOf(recent), now)?.slug)
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()
}
