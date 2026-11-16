package com.anas.ufc.data

import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

object RefreshConfig {
    const val automaticIntervalHours = 24L
    const val automaticRefreshHour = 7
    const val monthlyRequestLimit = 100
    const val apiRequestsPerMinute = 10
    val manualCooldown: Duration = Duration.ofMinutes(10)
    val workerBackoff: Duration = Duration.ofMinutes(30)
    val apiRequestSpacing: Duration = Duration.ofSeconds(60L / apiRequestsPerMinute)
}

fun isWithinManualCooldown(
    lastSuccessfulFetchMillis: Long?,
    nowMillis: Long,
    cooldown: Duration = RefreshConfig.manualCooldown,
): Boolean = lastSuccessfulFetchMillis?.let {
    nowMillis >= it && Duration.ofMillis(nowMillis - it) < cooldown
} ?: false

fun requestsForCurrentMonth(
    storedMonth: String,
    storedCount: Int,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Int = if (storedMonth == YearMonth.from(now.atZone(zone)).toString()) storedCount else 0

fun nextScheduledRefreshMillis(
    now: ZonedDateTime,
    refreshHour: Int = RefreshConfig.automaticRefreshHour,
): Long {
    val next = now.withHour(refreshHour).withMinute(0).withSecond(0).withNano(0)
        .let { if (it.isAfter(now)) it else it.plusDays(1) }
    return Duration.between(now, next).toMillis()
}
