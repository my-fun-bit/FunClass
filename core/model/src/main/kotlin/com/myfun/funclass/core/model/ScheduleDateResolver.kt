package com.myfun.funclass.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Single course session instance resolved onto a concrete calendar date.
 */
data class ResolvedCourseItem(
    val course: Course,
    val session: CourseSession,
    val date: LocalDate,
    val effectiveWeek: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isCurrentWeek: Boolean,
    val isShiftedFromHoliday: Boolean = false,
    val shiftLabel: String = ""
) {
    val activeNote: String get() = session.noteForWeek(effectiveWeek)
}

/**
 * Complete resolved schedule for a single calendar date.
 */
data class ResolvedDaySchedule(
    val date: LocalDate,
    val weekNumber: Int,
    val courses: List<ResolvedCourseItem>,
    val otherWeekCourses: List<ResolvedCourseItem>,
    val calendarEvents: List<CalendarOccurrence>,
    val holidayAdjustment: HolidayAdjustment? = null
) {
    val isHolidayOff: Boolean get() = holidayAdjustment?.adjustType == HolidayAdjustType.OFF
    val isHolidayShift: Boolean get() = holidayAdjustment?.adjustType == HolidayAdjustType.SHIFT
}

/**
 * Complete resolved schedule for an entire academic week.
 */
data class ResolvedWeekSchedule(
    val weekNumber: Int,
    val days: List<ResolvedDaySchedule>
)

/**
 * Single Source of Truth date & schedule resolution engine.
 * Shared across Main Schedule UI, Glance Widgets, Alarm Scheduler, and ICS Exporter.
 */
object ScheduleDateResolver {

    /**
     * Aligns any date to the start of its week (Monday if sundayFirst == false, Sunday if sundayFirst == true).
     */
    fun startOfWeek(date: LocalDate, sundayFirst: Boolean): LocalDate {
        return if (sundayFirst) {
            val offset = if (date.dayOfWeek == DayOfWeek.SUNDAY) 0L else date.dayOfWeek.value.toLong()
            date.minusDays(offset)
        } else {
            val offset = (date.dayOfWeek.value - 1).toLong()
            date.minusDays(offset)
        }
    }

    /**
     * Computes 1-based academic week number for [date] relative to [termStartDate].
     * May return <= 0 if [date] is before the term starts.
     */
    fun computeWeekNumber(
        termStartDate: LocalDate,
        date: LocalDate,
        sundayFirst: Boolean = false
    ): Int {
        val termWeekStart = startOfWeek(termStartDate, sundayFirst)
        val targetWeekStart = startOfWeek(date, sundayFirst)
        val diffDays = ChronoUnit.DAYS.between(termWeekStart, targetWeekStart)
        return (diffDays / 7L).toInt() + 1
    }

    /**
     * Returns the 7 ordered dates for 1-based [weekNumber].
     */
    fun datesForWeek(
        termStartDate: LocalDate,
        weekNumber: Int,
        sundayFirst: Boolean = false
    ): List<LocalDate> {
        val weekStart = startOfWeek(termStartDate, sundayFirst).plusWeeks((weekNumber - 1).toLong())
        return (0L..6L).map { weekStart.plusDays(it) }
    }

    /**
     * Resolves all courses (including holiday OFF/SHIFT overlay) and calendar events for a concrete [date].
     */
    fun resolveDay(
        table: ScheduleTable,
        timeScheme: TimeScheme,
        date: LocalDate,
        courses: List<Course>,
        calendarEvents: List<CalendarEvent> = emptyList(),
        completions: List<CalendarCompletion> = emptyList(),
        adjustments: List<HolidayAdjustment> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ResolvedDaySchedule {
        val termStart = table.termStartLocalDate()
        val weekNumber = computeWeekNumber(termStart, date, table.sundayFirst)

        // Table-specific adjustment takes precedence over global adjustment (tableId == 0L)
        val dateIso = date.toString()
        val adjustment = adjustments
            .filter { it.targetDate == dateIso && (it.tableId == table.id || it.tableId == 0L) }
            .sortedByDescending { it.tableId }
            .firstOrNull()

        val activeCourses = mutableListOf<ResolvedCourseItem>()
        val otherWeekCourses = mutableListOf<ResolvedCourseItem>()

        when (adjustment?.adjustType) {
            HolidayAdjustType.OFF -> {
                // Regular courses are suspended on holiday OFF days
            }

            HolidayAdjustType.SHIFT -> {
                val sourceDate = adjustment.sourceLocalDate()
                if (sourceDate != null) {
                    val sourceWeek = computeWeekNumber(termStart, sourceDate, table.sundayFirst)
                    val sourceDow = sourceDate.dayOfWeek.value
                    collectCoursesForDay(
                        courses = courses,
                        timeScheme = timeScheme,
                        displayDate = date,
                        queryWeek = sourceWeek,
                        queryDayOfWeek = sourceDow,
                        isShifted = true,
                        shiftLabel = adjustment.label.ifBlank { "调休补课" },
                        activeOut = activeCourses,
                        otherWeekOut = otherWeekCourses
                    )
                }
            }

            null -> {
                collectCoursesForDay(
                    courses = courses,
                    timeScheme = timeScheme,
                    displayDate = date,
                    queryWeek = weekNumber,
                    queryDayOfWeek = date.dayOfWeek.value,
                    isShifted = false,
                    shiftLabel = "",
                    activeOut = activeCourses,
                    otherWeekOut = otherWeekCourses
                )
            }
        }

        val resolvedEvents = resolveCalendarEventsForDay(
            date = date,
            events = calendarEvents,
            completions = completions,
            zone = zone
        )

        return ResolvedDaySchedule(
            date = date,
            weekNumber = weekNumber,
            courses = activeCourses.sortedWith(compareBy<ResolvedCourseItem> { it.session.startNode }.thenBy { it.startTime }),
            otherWeekCourses = otherWeekCourses.sortedWith(compareBy<ResolvedCourseItem> { it.session.startNode }.thenBy { it.startTime }),
            calendarEvents = resolvedEvents,
            holidayAdjustment = adjustment
        )
    }

    fun resolveWeek(
        table: ScheduleTable,
        timeScheme: TimeScheme,
        weekNumber: Int,
        courses: List<Course>,
        calendarEvents: List<CalendarEvent> = emptyList(),
        completions: List<CalendarCompletion> = emptyList(),
        adjustments: List<HolidayAdjustment> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ResolvedWeekSchedule {
        val dates = datesForWeek(table.termStartLocalDate(), weekNumber, table.sundayFirst)
        val days = dates.map { date ->
            resolveDay(
                table = table,
                timeScheme = timeScheme,
                date = date,
                courses = courses,
                calendarEvents = calendarEvents,
                completions = completions,
                adjustments = adjustments,
                zone = zone
            )
        }
        return ResolvedWeekSchedule(weekNumber = weekNumber, days = days)
    }

    fun resolveCalendarEventsForDay(
        date: LocalDate,
        events: List<CalendarEvent>,
        completions: List<CalendarCompletion>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CalendarOccurrence> {
        val completionMap = completions
            .filter { it.occurrenceDate == date.toString() }
            .map { it.eventId }
            .toSet()

        return events
            .asSequence()
            .filter { !it.isDeleted }
            .filter { occursOnDate(it, date, zone) }
            .map { event ->
                val origStart = event.startDateTime(zone)
                val origEnd = event.endDateTime(zone)
                val durationMinutes = ChronoUnit.MINUTES.between(origStart, origEnd).coerceAtLeast(15L)
                val occStart = if (event.isAllDay) date.atStartOfDay() else LocalDateTime.of(date, origStart.toLocalTime())
                val occEnd = if (event.isAllDay) date.atTime(23, 59) else occStart.plusMinutes(durationMinutes)
                CalendarOccurrence(
                    event = event,
                    date = date,
                    startDateTime = occStart,
                    endDateTime = occEnd,
                    isCompleted = event.id in completionMap
                )
            }
            .sortedWith(compareByDescending<CalendarOccurrence> { it.event.isAllDay }.thenBy { it.startDateTime })
            .toList()
    }

    fun occursOnDate(
        event: CalendarEvent,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val effective = event.effectiveLocalDate()
        if (date.isBefore(effective)) return false
        val endRepeat = event.endRepeatLocalDate()
        if (endRepeat != null && date.isAfter(endRepeat)) return false

        val startDate = event.startDateTime(zone).toLocalDate()
        val endDate = event.endDateTime(zone).toLocalDate()

        return when (event.repeatType) {
            RepeatType.NONE -> !date.isBefore(startDate) && !date.isAfter(endDate)
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> date.dayOfWeek == startDate.dayOfWeek
            RepeatType.MONTHLY -> date.dayOfMonth == startDate.dayOfMonth
            RepeatType.YEARLY -> date.month == startDate.month && date.dayOfMonth == startDate.dayOfMonth
        }
    }

    /**
     * Generates upcoming reminder triggers within [horizonDays] from [now].
     * When [includeActiveCountdownWindow] is true, items already inside `[triggerTime, startTime)`
     * are also emitted with `isActiveCountdownWindow = true` so newly added events notify immediately.
     */
    fun generateUpcomingAlarms(
        table: ScheduleTable,
        timeScheme: TimeScheme,
        courses: List<Course>,
        calendarEvents: List<CalendarEvent>,
        completions: List<CalendarCompletion>,
        adjustments: List<HolidayAdjustment>,
        settings: ReminderSettings,
        now: LocalDateTime = LocalDateTime.now(),
        horizonDays: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
        includeActiveCountdownWindow: Boolean = false
    ): List<ScheduledAlarmItem> {
        if (!settings.enabled) return emptyList()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val results = mutableListOf<ScheduledAlarmItem>()

        for (dayOffset in 0 until horizonDays) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val resolved = resolveDay(
                table = table,
                timeScheme = timeScheme,
                date = date,
                courses = courses,
                calendarEvents = calendarEvents,
                completions = completions,
                adjustments = adjustments,
                zone = zone
            )

            for (item in resolved.courses) {
                val startDt = LocalDateTime.of(date, item.startTime)
                val endDt = LocalDateTime.of(date, item.endTime)
                val triggerDt = startDt.minusMinutes(settings.courseMinutesBefore.toLong())
                val startMillis = startDt.atZone(zone).toInstant().toEpochMilli()
                val triggerMillis = triggerDt.atZone(zone).toInstant().toEpochMilli()
                val isFutureTrigger = triggerMillis > nowMillis
                val isActiveWindow = !isFutureTrigger &&
                    includeActiveCountdownWindow &&
                    nowMillis < startMillis &&
                    (settings.countdownNotificationEnabled || settings.fluidCloudEnabled)
                if (isFutureTrigger || isActiveWindow) {
                    val roomPart = item.session.room.takeIf { it.isNotBlank() }?.let { "@$it " } ?: ""
                    results += ScheduledAlarmItem(
                        id = "course_${item.course.id}_${item.session.id}_$date",
                        type = AlarmItemType.COURSE,
                        title = item.course.name,
                        subtitle = "${roomPart}${item.startTime}-${item.endTime}",
                        location = item.session.room.trim(),
                        triggerEpochMillis = triggerMillis,
                        eventStartEpochMillis = startMillis,
                        eventEndEpochMillis = endDt.atZone(zone).toInstant().toEpochMilli(),
                        countdownEnabled = settings.countdownNotificationEnabled,
                        autoDismissMinutes = settings.countdownDismissAfterStartMinutes,
                        isActiveCountdownWindow = isActiveWindow
                    )
                }
            }

            if (settings.calendarRemindersEnabled) {
                for (occ in resolved.calendarEvents) {
                    if (occ.isCompleted || occ.event.remindMinutesBefore < 0) continue
                    val triggerDt = occ.startDateTime.minusMinutes(occ.event.remindMinutesBefore.toLong())
                    val startMillis = occ.startDateTime.atZone(zone).toInstant().toEpochMilli()
                    val triggerMillis = triggerDt.atZone(zone).toInstant().toEpochMilli()
                    val isFutureTrigger = triggerMillis > nowMillis
                    val isActiveWindow = !isFutureTrigger &&
                        includeActiveCountdownWindow &&
                        nowMillis < startMillis &&
                        (occ.event.countdownEnabled || settings.fluidCloudEnabled)
                    if (isFutureTrigger || isActiveWindow) {
                        results += ScheduledAlarmItem(
                            id = "calendar_${occ.event.id}_$date",
                            type = AlarmItemType.CALENDAR,
                            title = occ.event.title,
                            subtitle = occ.event.location.ifBlank { occ.event.note },
                            location = occ.event.location.trim(),
                            triggerEpochMillis = triggerMillis,
                            eventStartEpochMillis = startMillis,
                            eventEndEpochMillis = occ.endDateTime.atZone(zone).toInstant().toEpochMilli(),
                            countdownEnabled = occ.event.countdownEnabled,
                            autoDismissMinutes = occ.event.countdownAutoDismissMinutes,
                            isActiveCountdownWindow = isActiveWindow
                        )
                    }
                }
            }
        }
        return results.sortedBy { it.triggerEpochMillis }
    }

    /**
     * Generates upcoming course auto-DND windows (`[startTime, endTime)`) within [horizonDays]
     * from [now], honoring holidays and shifted workdays.
     */
    fun generateCourseDndWindows(
        table: ScheduleTable,
        timeScheme: TimeScheme,
        courses: List<Course>,
        adjustments: List<HolidayAdjustment>,
        settings: ReminderSettings,
        now: LocalDateTime = LocalDateTime.now(),
        horizonDays: Int = 7,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CourseDndWindow> {
        if (!settings.autoMuteDuringClass) return emptyList()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val results = mutableListOf<CourseDndWindow>()

        for (dayOffset in 0 until horizonDays) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val resolved = resolveDay(
                table = table,
                timeScheme = timeScheme,
                date = date,
                courses = courses,
                adjustments = adjustments,
                zone = zone
            )

            for (item in resolved.courses) {
                val startMillis = LocalDateTime.of(date, item.startTime)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                val endMillis = LocalDateTime.of(date, item.endTime)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (endMillis > startMillis && endMillis > nowMillis) {
                    results += CourseDndWindow(
                        id = "course_${item.course.id}_${item.session.id}_$date",
                        title = item.course.name,
                        startEpochMillis = startMillis,
                        endEpochMillis = endMillis
                    )
                }
            }
        }
        return results.sortedBy { it.startEpochMillis }
    }

    private fun collectCoursesForDay(
        courses: List<Course>,
        timeScheme: TimeScheme,
        displayDate: LocalDate,
        queryWeek: Int,
        queryDayOfWeek: Int,
        isShifted: Boolean,
        shiftLabel: String,
        activeOut: MutableList<ResolvedCourseItem>,
        otherWeekOut: MutableList<ResolvedCourseItem>
    ) {
        for (course in courses) {
            if (course.isDeleted) continue
            for (session in course.sessions) {
                if (session.isDeleted || session.dayOfWeek != queryDayOfWeek) continue
                val (startTime, endTime) = if (session.useCustomTime && session.customStartTime.isNotBlank() && session.customEndTime.isNotBlank()) {
                    LocalTime.parse(TimeSlot.normalizeTime(session.customStartTime)) to
                        LocalTime.parse(TimeSlot.normalizeTime(session.customEndTime))
                } else {
                    timeScheme.timeRangeForNodes(session.startNode, session.nodeCount)
                }
                val isCurrent = queryWeek in session.weeks
                val item = ResolvedCourseItem(
                    course = course,
                    session = session,
                    date = displayDate,
                    effectiveWeek = queryWeek,
                    startTime = startTime,
                    endTime = endTime,
                    isCurrentWeek = isCurrent,
                    isShiftedFromHoliday = isShifted,
                    shiftLabel = shiftLabel
                )
                if (isCurrent) {
                    activeOut += item
                } else if (!isShifted) {
                    otherWeekOut += item
                }
            }
        }
    }
}
