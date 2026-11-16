package com.anas.ufc.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object EventDateFormatter {
    fun format(
        timestamp: String?,
        now: Instant,
        zone: ZoneId,
        locale: Locale,
        todayLabel: String,
        tomorrowLabel: String,
    ): String? {
        if (timestamp.isNullOrBlank()) return null
        val eventInstant = try {
            Instant.parse(timestamp)
        } catch (_: DateTimeParseException) {
            return null
        }
        val eventTime = eventInstant.atZone(zone)
        val time = DateTimeFormatter.ofPattern("h:mm a", locale).format(eventTime)
        val today = now.atZone(zone).toLocalDate()
        val eventDate = eventTime.toLocalDate()
        return when (eventDate) {
            today -> "$todayLabel, $time"
            today.plusDays(1) -> "$tomorrowLabel, $time"
            else -> DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", locale).format(eventTime)
        }
    }
}
