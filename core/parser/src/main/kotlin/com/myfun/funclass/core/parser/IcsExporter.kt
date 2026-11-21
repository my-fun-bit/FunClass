package com.myfun.funclass.core.parser

import com.myfun.funclass.core.model.CalendarEvent
import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.HolidayAdjustment
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.TimeScheme
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Standards-compliant RFC 5545 iCalendar (.ics) exporter.
 * Uses ScheduleDateResolver so exported events automatically honor holiday adjustments & irregular weeks.
 */
object IcsExporter {

    private val dtFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcStampFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun export(
        table: ScheduleTable,
        timeScheme: TimeScheme,
        courses: List<Course>,
        adjustments: List<HolidayAdjustment> = emptyList(),
        calendarEvents: List<CalendarEvent> = emptyList(),
        alarmMinutesBefore: Int = 15,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()
        val nowStamp = LocalDateTime.now().format(utcStampFormat)

        sb.appendCrlf("BEGIN:VCALENDAR")
        sb.appendCrlf("VERSION:2.0")
        sb.appendCrlf("PRODID:-//MyFun//FunClass//CN")
        sb.appendCrlf("CALSCALE:GREGORIAN")
        sb.appendCrlf("X-WR-CALNAME:${escapeIcs(table.name)}")
        sb.appendCrlf("X-WR-TIMEZONE:${zoneId.id}")

        for (week in 1..table.maxWeeks) {
            val resolvedWeek = ScheduleDateResolver.resolveWeek(
                table = table,
                timeScheme = timeScheme,
                weekNumber = week,
                courses = courses,
                calendarEvents = calendarEvents,
                adjustments = adjustments,
                zone = zoneId
            )
            for (day in resolvedWeek.days) {
                for (item in day.courses) {
                    val startDt = LocalDateTime.of(day.date, item.startTime)
                    val endDt = LocalDateTime.of(day.date, item.endTime)
                    val uid = "course-${item.course.id}-${item.session.id}-${day.date}@funclass.myfun"
                    val descParts = buildList {
                        if (item.session.teacher.isNotBlank()) add("教师：${item.session.teacher}")
                        add("第${item.effectiveWeek}周 第${item.session.startNode}-${item.session.endNode}节")
                        if (item.isShiftedFromHoliday) add("调休：${item.shiftLabel}")
                        if (item.activeNote.isNotBlank()) add("备注：${item.activeNote}")
                    }
                    sb.appendEvent(
                        uid = uid,
                        stamp = nowStamp,
                        start = startDt,
                        end = endDt,
                        summary = item.course.name,
                        location = item.session.room,
                        description = descParts.joinToString("\n"),
                        alarmMinutes = alarmMinutesBefore
                    )
                }

                for (occ in day.calendarEvents) {
                    val uid = "cal-${occ.event.id}-${day.date}@funclass.myfun"
                    sb.appendEvent(
                        uid = uid,
                        stamp = nowStamp,
                        start = occ.startDateTime,
                        end = occ.endDateTime,
                        summary = "[日程] ${occ.event.title}",
                        location = occ.event.location,
                        description = occ.event.note,
                        alarmMinutes = occ.event.remindMinutesBefore
                    )
                }
            }
        }

        sb.appendCrlf("END:VCALENDAR")
        return sb.toString()
    }

    private fun StringBuilder.appendEvent(
        uid: String,
        stamp: String,
        start: LocalDateTime,
        end: LocalDateTime,
        summary: String,
        location: String,
        description: String,
        alarmMinutes: Int
    ) {
        appendCrlf("BEGIN:VEVENT")
        appendCrlf("UID:$uid")
        appendCrlf("DTSTAMP:$stamp")
        appendCrlf("DTSTART:${start.format(dtFormat)}")
        appendCrlf("DTEND:${end.format(dtFormat)}")
        appendCrlf("SUMMARY:${escapeIcs(summary)}")
        if (location.isNotBlank()) appendCrlf("LOCATION:${escapeIcs(location)}")
        if (description.isNotBlank()) appendCrlf("DESCRIPTION:${escapeIcs(description)}")
        if (alarmMinutes >= 0) {
            appendCrlf("BEGIN:VALARM")
            appendCrlf("ACTION:DISPLAY")
            appendCrlf("DESCRIPTION:${escapeIcs(summary)}")
            appendCrlf("TRIGGER:-PT${alarmMinutes}M")
            appendCrlf("END:VALARM")
        }
        appendCrlf("END:VEVENT")
    }

    private fun escapeIcs(text: String): String =
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")

    private fun StringBuilder.appendCrlf(line: String) {
        append(line).append("\r\n")
    }
}
