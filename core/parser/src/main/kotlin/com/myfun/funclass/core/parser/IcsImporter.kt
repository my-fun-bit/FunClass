package com.myfun.funclass.core.parser

import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.WeekSet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * RFC 5545 iCalendar (.ics) importer.
 * Supports individual VEVENTs and recurring RRULE (FREQ=WEEKLY/DAILY with COUNT/UNTIL/INTERVAL/BYDAY),
 * EXDATE/RDATE, TZID conversion, and 3-line timetable DESCRIPTION format compatibility.
 */
object IcsImporter {

    private val basicDateTimeFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val basicDateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val hmFmt = DateTimeFormatter.ofPattern("HH:mm")

    private data class ParsedIcsTime(
        val dateTime: LocalDateTime,
        val isDateOnly: Boolean
    )

    private data class RawIcsEvent(
        val summary: String,
        val location: String,
        val description: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val isAllDay: Boolean,
        val rrule: String,
        val exDates: Set<LocalDate>,
        val rDates: List<LocalDate>
    )

    private data class ConcreteOccurrence(
        val courseName: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val room: String,
        val teacher: String,
        val note: String,
        val explicitNodes: Pair<Int, Int>?,
        val explicitWeek: Int?
    )

    fun isIcsContent(raw: String): Boolean =
        raw.contains("BEGIN:VCALENDAR", ignoreCase = true) && raw.contains("BEGIN:VEVENT", ignoreCase = true)

    fun parse(
        rawText: String,
        fallbackTableName: String = "ICS 导入课表",
        timeScheme: TimeScheme = DefaultTimeSchemes.standardCollege(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        overrideTermStartDate: LocalDate? = null
    ): ParsedScheduleResult {
        val unfolded = unfoldLines(rawText)
        var calName = ""
        val events = mutableListOf<RawIcsEvent>()

        var inEvent = false
        var inAlarm = false
        var summary = ""
        var location = ""
        var description = ""
        var dtStartRaw = ""
        var dtEndRaw = ""
        var rrule = ""
        val exDates = mutableSetOf<LocalDate>()
        val rDates = mutableListOf<LocalDate>()

        for (line in unfolded) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VALARM", ignoreCase = true)) {
                inAlarm = true
                continue
            }
            if (trimmed.equals("END:VALARM", ignoreCase = true)) {
                inAlarm = false
                continue
            }
            if (inAlarm) continue
            if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true
                summary = ""
                location = ""
                description = ""
                dtStartRaw = ""
                dtEndRaw = ""
                rrule = ""
                exDates.clear()
                rDates.clear()
                continue
            }
            if (trimmed.equals("END:VEVENT", ignoreCase = true)) {
                if (inEvent && summary.isNotBlank() && dtStartRaw.isNotBlank()) {
                    val parsedStart = parseIcsDateTime(dtStartRaw, zoneId)
                    if (parsedStart != null) {
                        val parsedEnd = parseIcsDateTime(dtEndRaw, zoneId)
                        val endDt = parsedEnd?.dateTime ?: parsedStart.dateTime.plusMinutes(90)
                        events += RawIcsEvent(
                            summary = unescapeIcs(summary),
                            location = unescapeIcs(location),
                            description = unescapeIcs(description),
                            start = parsedStart.dateTime,
                            end = endDt,
                            isAllDay = parsedStart.isDateOnly,
                            rrule = rrule,
                            exDates = exDates.toSet(),
                            rDates = rDates.toList()
                        )
                    }
                }
                inEvent = false
                continue
            }

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue
            val propKey = trimmed.substring(0, colonIdx).substringBefore(';').uppercase()
            val propVal = trimmed.substring(colonIdx + 1)

            if (!inEvent) {
                if (propKey == "X-WR-CALNAME" && propVal.isNotBlank()) {
                    calName = unescapeIcs(propVal)
                }
            } else {
                when (propKey) {
                    "SUMMARY" -> summary = propVal
                    "LOCATION" -> location = propVal
                    "DESCRIPTION" -> description = propVal
                    "DTSTART" -> dtStartRaw = trimmed
                    "DTEND" -> dtEndRaw = trimmed
                    "RRULE" -> rrule = propVal
                    "EXDATE" -> exDates += parseMultiDates(trimmed, zoneId)
                    "RDATE" -> rDates += parseMultiDates(trimmed, zoneId)
                }
            }
        }

        // Skip all-day calendar markers (e.g. holidays / semester labels) when importing courses
        val courseEvents = events.filter { !it.isAllDay && !it.summary.startsWith("[日程]") }
        val effectiveEvents = courseEvents.ifEmpty { events }

        if (effectiveEvents.isEmpty()) {
            return ParsedScheduleResult(
                tableName = calName.ifBlank { fallbackTableName },
                warnings = listOf("未在 .ics 文件中解析到有效的课程事件 (VEVENT)")
            )
        }

        // Expand RRULE + RDATE - EXDATE into concrete date occurrences
        val occurrences = mutableListOf<ConcreteOccurrence>()
        for (ev in effectiveEvents) {
            val (cleanName, roomFromSummary) = splitSummaryNameAndRoom(ev.summary)
            val parsedMeta = extractWakeupOrGenericMeta(ev.location, roomFromSummary, ev.description)

            val expanded = expandRruleDates(ev.start, ev.rrule, zoneId)
            val allDates = (expanded + ev.rDates)
                .distinct()
                .filterNot { it in ev.exDates }
                .sorted()

            val startTime = ev.start.toLocalTime()
            val endTime = if (ev.end.toLocalTime().isAfter(startTime)) ev.end.toLocalTime() else startTime.plusMinutes(90)

            for (d in allDates) {
                occurrences += ConcreteOccurrence(
                    courseName = cleanName,
                    date = d,
                    startTime = startTime,
                    endTime = endTime,
                    room = parsedMeta.room,
                    teacher = parsedMeta.teacher,
                    note = parsedMeta.note,
                    explicitNodes = parsedMeta.explicitNodes,
                    explicitWeek = parsedMeta.explicitWeek
                )
            }
        }

        if (occurrences.isEmpty()) {
            return ParsedScheduleResult(
                tableName = calName.ifBlank { fallbackTableName },
                warnings = listOf("未在 .ics 文件中解析到有效的上课日期")
            )
        }

        val inferredTermStartDate = inferTermStartMonday(occurrences)
        val termStartDate = (overrideTermStartDate ?: inferredTermStartDate)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Group identical recurring course slots across weeks into discrete WeekSets
        val grouped = linkedMapOf<SessionGroupKey, MutableSet<Int>>()
        val customTimes = mutableMapOf<SessionGroupKey, Pair<String, String>>()
        var maxObservedWeek = 16
        var maxObservedNode = 12

        for (occ in occurrences) {
            val week = ScheduleDateResolver.computeWeekNumber(termStartDate, occ.date, sundayFirst = false)
            if (week !in 1..30) continue
            if (week > maxObservedWeek) maxObservedWeek = week

            val (startNode, nodeCount, needsCustomTime) = mapTimeToNodes(occ.startTime, occ.endTime, occ.explicitNodes, timeScheme)
            if (startNode + nodeCount - 1 > maxObservedNode) {
                maxObservedNode = (startNode + nodeCount - 1).coerceAtMost(20)
            }

            val key = SessionGroupKey(
                courseName = occ.courseName,
                dayOfWeek = occ.date.dayOfWeek.value,
                startNode = startNode,
                nodeCount = nodeCount,
                room = occ.room,
                teacher = occ.teacher,
                note = occ.note
            )
            grouped.getOrPut(key) { mutableSetOf() }.add(week)
            if (needsCustomTime) {
                customTimes[key] = occ.startTime.format(hmFmt) to occ.endTime.format(hmFmt)
            }
        }

        val rawSessions = grouped.entries.map { (key, weeks) ->
            val custom = customTimes[key]
            RawParsedSession(
                courseName = key.courseName,
                dayOfWeek = key.dayOfWeek,
                startNode = key.startNode,
                nodeCount = key.nodeCount,
                weeks = WeekSet(weeks),
                room = key.room,
                teacher = key.teacher,
                note = key.note,
                customStartTime = custom?.first.orEmpty(),
                customEndTime = custom?.second.orEmpty()
            )
        }

        return ParsedScheduleResult(
            tableName = calName.ifBlank { fallbackTableName },
            courses = CourseGroupingHelper.groupSessions(rawSessions),
            termStartDate = termStartDate.toString(),
            maxWeeks = maxObservedWeek.coerceIn(16, 30),
            maxNodes = maxObservedNode.coerceIn(12, 20)
        )
    }

    /**
     * Infers Week 1 Monday:
     * 1. If occurrences carry explicit "第N周" markers, derive Week 1 Monday directly.
     * 2. Otherwise use the Monday of the earliest course occurrence date.
     */
    private fun inferTermStartMonday(occurrences: List<ConcreteOccurrence>): LocalDate {
        val explicitCandidates = occurrences.mapNotNull { occ ->
            val w = occ.explicitWeek?.takeIf { it in 1..30 } ?: return@mapNotNull null
            occ.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((w - 1).toLong())
        }
        if (explicitCandidates.isNotEmpty()) {
            // Pick the most frequent candidate (robust against a single holiday shift)
            return explicitCandidates.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        }
        val earliestDate = occurrences.minOf { it.date }
        return earliestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private data class ParsedEventMeta(
        val room: String,
        val teacher: String,
        val note: String,
        val explicitNodes: Pair<Int, Int>?,
        val explicitWeek: Int?
    )

    private fun extractWakeupOrGenericMeta(
        rawLocation: String,
        roomFromSummary: String,
        description: String
    ): ParsedEventMeta {
        val explicitNodes = extractNodesFromDescription(description)
        val explicitWeek = extractExplicitWeekFromDescription(description)
        val labeledTeacher = extractTeacher(description)
        val extraNote = extractExtraNote(description)

        // Some timetable .ics exports write DESCRIPTION as 3 lines:
        //   Line 1: 第1 - 2节
        //   Line 2: 东区 教A－102   (classroom)
        //   Line 3: 张老师         (teacher)
        // And LOCATION as "东区 教A－102 张老师" (classroom + space + teacher).
        val lines = description.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (labeledTeacher.isBlank() && lines.size >= 3 && extractNodesFromDescription(lines[0]) != null) {
            val line2 = lines[1]
            val line3 = lines[2]
            val locNormalized = rawLocation.trim()
            val looksLikeWakeupTriple = locNormalized.isBlank() ||
                locNormalized == "$line2 $line3" ||
                (locNormalized.startsWith(line2) && locNormalized.endsWith(line3))
            if (looksLikeWakeupTriple && !line3.contains("备注") && !line3.contains("调休")) {
                return ParsedEventMeta(
                    room = line2,
                    teacher = line3,
                    note = extraNote,
                    explicitNodes = explicitNodes,
                    explicitWeek = explicitWeek
                )
            }
        }

        val room = rawLocation.ifBlank { roomFromSummary }
        return ParsedEventMeta(
            room = room,
            teacher = labeledTeacher,
            note = extraNote,
            explicitNodes = explicitNodes,
            explicitWeek = explicitWeek
        )
    }

    private data class SessionGroupKey(
        val courseName: String,
        val dayOfWeek: Int,
        val startNode: Int,
        val nodeCount: Int,
        val room: String,
        val teacher: String,
        val note: String
    )

    private fun unfoldLines(raw: String): List<String> {
        val result = mutableListOf<String>()
        for (line in raw.replace("\r\n", "\n").replace("\r", "\n").split("\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.substring(1)
            } else if (line.isNotEmpty()) {
                result += line
            }
        }
        return result
    }

    private fun parseMultiDates(fullPropLine: String, targetZone: ZoneId): List<LocalDate> {
        val header = fullPropLine.substringBefore(':')
        val values = fullPropLine.substringAfter(':').split(',')
        return values.mapNotNull { v ->
            val trimmed = v.trim()
            if (trimmed.isEmpty()) null else parseIcsDateTime("$header:$trimmed", targetZone)?.dateTime?.toLocalDate()
        }
    }

    private fun parseIcsDateTime(fullPropLine: String, targetZone: ZoneId): ParsedIcsTime? {
        if (fullPropLine.isBlank()) return null
        val header = fullPropLine.substringBefore(':')
        val value = fullPropLine.substringAfter(':').trim()
        if (value.isEmpty()) return null

        val tzidParam = header.split(';')
            .firstOrNull { it.startsWith("TZID=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
        val sourceZone = tzidParam?.let { runCatching { ZoneId.of(it) }.getOrNull() }

        return runCatching {
            when {
                value.endsWith("Z", ignoreCase = true) && value.length >= 16 -> {
                    val ldt = LocalDateTime.parse(value.substring(0, 15), basicDateTimeFmt)
                    val converted = Instant.from(ldt.atOffset(ZoneOffset.UTC)).atZone(targetZone).toLocalDateTime()
                    ParsedIcsTime(converted, isDateOnly = false)
                }
                value.contains("T", ignoreCase = true) && value.length >= 15 -> {
                    val ldt = LocalDateTime.parse(value.substring(0, 15).uppercase(), basicDateTimeFmt)
                    val converted = if (sourceZone != null && sourceZone != targetZone) {
                        ldt.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime()
                    } else {
                        ldt
                    }
                    ParsedIcsTime(converted, isDateOnly = false)
                }
                value.length >= 8 -> {
                    val d = LocalDate.parse(value.substring(0, 8), basicDateFmt)
                    ParsedIcsTime(d.atStartOfDay(), isDateOnly = true)
                }
                else -> null
            }
        }.getOrNull()
    }

    /**
     * Expands RFC 5545 RRULE strictly comparing LocalDateTime against UNTIL (inclusive),
     * preventing the classic +1 phantom week bug when UNTIL is `00:00:00` of the following week
     * (e.g. external exporters emitting `UNTIL=20260923T160000Z` = `2026-09-24T00:00:00+08:00` for a 08:00 class).
     */
    private fun expandRruleDates(
        startDateTime: LocalDateTime,
        rrule: String,
        zoneId: ZoneId
    ): List<LocalDate> {
        val startDate = startDateTime.toLocalDate()
        val startTime = startDateTime.toLocalTime()
        if (rrule.isBlank()) return listOf(startDate)

        val params = rrule.split(';').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) part.substring(0, idx).uppercase() to part.substring(idx + 1).uppercase() else null
        }.toMap()

        val freq = params["FREQ"] ?: return listOf(startDate)
        if (freq != "WEEKLY" && freq != "DAILY") return listOf(startDate)

        val interval = params["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = params["COUNT"]?.toIntOrNull()?.coerceIn(1, 120)
        val untilParsed = params["UNTIL"]?.let { parseIcsDateTime("UNTIL:$it", zoneId) }
        val untilDateTime: LocalDateTime? = untilParsed?.let {
            if (it.isDateOnly) it.dateTime.toLocalDate().atTime(23, 59, 59) else it.dateTime
        }

        val byDays: List<DayOfWeek> = params["BYDAY"]
            ?.split(',')
            ?.mapNotNull { parseIcsDayOfWeek(it.trim()) }
            ?.distinct()
            ?.sortedBy { it.value }
            .orEmpty()

        val dates = mutableListOf<LocalDate>()
        val hardLimit = count ?: if (untilDateTime != null) 120 else 20

        if (freq == "WEEKLY" && byDays.isNotEmpty()) {
            var weekStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var weekLoops = 0
            while (dates.size < hardLimit && weekLoops < 60) {
                var exceededUntil = false
                for (dow in byDays) {
                    val candidateDate = weekStart.with(TemporalAdjusters.nextOrSame(dow))
                    if (candidateDate.isBefore(startDate)) continue
                    val candidateDt = LocalDateTime.of(candidateDate, startTime)
                    if (untilDateTime != null && candidateDt.isAfter(untilDateTime)) {
                        exceededUntil = true
                        break
                    }
                    dates += candidateDate
                    if (dates.size >= hardLimit) break
                }
                if (exceededUntil) break
                weekStart = weekStart.plusWeeks(interval.toLong())
                weekLoops++
            }
            return dates.ifEmpty { listOf(startDate) }
        }

        val stepDays = if (freq == "WEEKLY") (7 * interval).toLong() else interval.toLong()
        var currentDate = startDate
        for (i in 0 until hardLimit) {
            val currentDt = LocalDateTime.of(currentDate, startTime)
            if (untilDateTime != null && currentDt.isAfter(untilDateTime)) break
            dates += currentDate
            currentDate = currentDate.plusDays(stepDays)
        }
        return dates.ifEmpty { listOf(startDate) }
    }

    private fun parseIcsDayOfWeek(token: String): DayOfWeek? {
        val suffix = token.takeLast(2).uppercase()
        return when (suffix) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun mapTimeToNodes(
        start: LocalTime,
        end: LocalTime,
        explicitNodes: Pair<Int, Int>?,
        scheme: TimeScheme
    ): Triple<Int, Int, Boolean> {
        if (explicitNodes != null) {
            val (s, e) = explicitNodes
            val count = (e - s + 1).coerceIn(1, 8)
            val expected = scheme.timeRangeForNodes(s, count)
            val diff = abs(expected.first.toSecondOfDay() - start.toSecondOfDay()) / 60
            return Triple(s, count, diff > 10)
        }
        val regular = scheme.regularSlots()
        if (regular.isEmpty()) return Triple(1, 2, true)
        val bestStart = regular.minByOrNull { abs(it.startLocalTime().toSecondOfDay() - start.toSecondOfDay()) }!!
        val bestEnd = regular.filter { it.nodeIndex >= bestStart.nodeIndex }
            .minByOrNull { abs(it.endLocalTime().toSecondOfDay() - end.toSecondOfDay()) }
            ?: bestStart
        val count = (bestEnd.nodeIndex - bestStart.nodeIndex + 1).coerceIn(1, 8)
        val diffStartMin = abs(bestStart.startLocalTime().toSecondOfDay() - start.toSecondOfDay()) / 60
        return Triple(bestStart.nodeIndex, count, diffStartMin > 10)
    }

    private fun splitSummaryNameAndRoom(summary: String): Pair<String, String> {
        val cleaned = summary.removePrefix("[课程]").removePrefix("[日程]").trim()
        val atIdx = cleaned.indexOf(" @")
        return if (atIdx > 0) {
            cleaned.substring(0, atIdx).trim() to cleaned.substring(atIdx + 2).trim()
        } else {
            cleaned to ""
        }
    }

    private fun extractTeacher(desc: String): String {
        val m = Regex("(?:授课教师|教师|老师)\\s*[:：]\\s*([^\\n\\\\,;，；]+)").find(desc)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun extractNodesFromDescription(desc: String): Pair<Int, Int>? {
        val m = Regex("第?\\s*(\\d+)\\s*[-~至]\\s*(\\d+)\\s*节").find(desc) ?: return null
        val s = m.groupValues[1].toIntOrNull() ?: return null
        val e = m.groupValues[2].toIntOrNull()?.coerceAtLeast(s) ?: s
        return s to e
    }

    private fun extractExplicitWeekFromDescription(desc: String): Int? {
        val m = Regex("第\\s*(\\d{1,2})\\s*周(?!\\s*[-~至])").find(desc) ?: return null
        return m.groupValues[1].toIntOrNull()?.takeIf { it in 1..30 }
    }

    private fun extractExtraNote(desc: String): String {
        val m = Regex("(?:备注|说明)\\s*[:：]\\s*(.+)").find(desc)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun unescapeIcs(raw: String): String =
        raw.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()
}
