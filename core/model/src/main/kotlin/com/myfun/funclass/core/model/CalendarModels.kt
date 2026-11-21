package com.myfun.funclass.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Serializable
enum class RepeatType(val code: Int, val label: String) {
    NONE(0, "不重复"),
    DAILY(1, "每天"),
    WEEKLY(2, "每周"),
    MONTHLY(3, "每月"),
    YEARLY(4, "每年");

    companion object {
        fun fromCode(code: Int): RepeatType = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * First-class Calendar & Todo item.
 */
@Serializable
data class CalendarEvent(
    val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val title: String,
    val location: String = "",
    val colorHex: String = "#7262FD",
    val isAllDay: Boolean = false,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val repeatType: RepeatType = RepeatType.NONE,
    /** ISO yyyy-MM-dd: start boundary for repeating events. */
    val effectiveDate: String,
    /** ISO yyyy-MM-dd or empty string for no end date. */
    val endRepeatDate: String = "",
    val note: String = "",
    val label: Int = 0,
    val remindMinutesBefore: Int = 15,
    val countdownEnabled: Boolean = false,
    val countdownAutoDismissMinutes: Int = 15,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    fun startDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(startEpochMillis), zone)

    fun endDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(endEpochMillis), zone)

    fun effectiveLocalDate(): LocalDate = runCatching { LocalDate.parse(effectiveDate) }
        .getOrElse { startDateTime().toLocalDate() }

    fun endRepeatLocalDate(): LocalDate? =
        endRepeatDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

@Serializable
data class CalendarCompletion(
    val id: Long = 0L,
    val eventId: Long,
    /** ISO yyyy-MM-dd of the specific occurrence completed. */
    val occurrenceDate: String,
    val completedAt: Long = System.currentTimeMillis()
)

data class CalendarOccurrence(
    val event: CalendarEvent,
    val date: LocalDate,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val isCompleted: Boolean
)
