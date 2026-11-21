package com.myfun.funclass.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WeekSetAndResolverTest {

    @Test
    fun weekSet_encodeDecodeAndLegacySegments_roundTrip() {
        val irregular = WeekSet.decode("1-4, 6, 8-12")
        assertEquals(setOf(1, 2, 3, 4, 6, 8, 9, 10, 11, 12), irregular.weeks)
        assertEquals("1-4,6,8-12", irregular.encode())
        assertEquals("第1-4,6,8-12周", irregular.toDisplayString())
        assertEquals(3, irregular.toLegacySegments().size)

        val odd = WeekSet.range(1, 15, WeekSet.LEGACY_TYPE_ODD)
        assertTrue(odd.isAllOdd())
        assertEquals("第1-15周(单)", odd.toDisplayString())
        val oddSegs = odd.toLegacySegments()
        assertEquals(1, oddSegs.size)
        assertEquals(WeekSet.LEGACY_TYPE_ODD, oddSegs.first().type)
        assertEquals(1, oddSegs.first().startWeek)
        assertEquals(15, oddSegs.first().endWeek)

        val even = WeekSet.decode("2-16(双)")
        assertTrue(even.isAllEven())
        assertEquals("第2-16周(双)", even.toDisplayString())
    }

    @Test
    fun resolver_handlesHolidayOffAndShiftNonDestructively() {
        val scheme = DefaultTimeSchemes.standardCollege(1L)
        val table = ScheduleTable(
            id = 1L,
            name = "2026秋",
            timeSchemeId = 1L,
            termStartDate = "2026-09-07", // Monday = Week 1
            sundayFirst = false
        )
        val mondayCourse = Course(
            id = 10L,
            tableId = 1L,
            name = "高等数学",
            sessions = listOf(
                CourseSession(
                    id = 100L,
                    courseId = 10L,
                    tableId = 1L,
                    dayOfWeek = 1, // Monday
                    startNode = 1,
                    nodeCount = 2,
                    weeks = WeekSet.range(1, 16),
                    room = "教A-101",
                    teacher = "张教授"
                )
            )
        )

        // Oct 5, 2026 is Monday of Week 5 -> National Day holiday OFF
        // Sep 20, 2026 is Sunday of Week 2 -> Shift from Oct 5 (Monday of Week 5)
        val adjustments = listOf(
            HolidayAdjustment(
                id = 1L,
                tableId = 0L,
                targetDate = "2026-10-05",
                adjustType = HolidayAdjustType.OFF,
                label = "国庆放假"
            ),
            HolidayAdjustment(
                id = 2L,
                tableId = 0L,
                targetDate = "2026-09-20",
                adjustType = HolidayAdjustType.SHIFT,
                sourceDate = "2026-10-05",
                label = "国庆调休(补周一课)"
            )
        )

        // 1. Holiday OFF day should suppress regular Monday course
        val offDay = ScheduleDateResolver.resolveDay(
            table = table,
            timeScheme = scheme,
            date = LocalDate.parse("2026-10-05"),
            courses = listOf(mondayCourse),
            adjustments = adjustments
        )
        assertTrue(offDay.isHolidayOff)
        assertTrue(offDay.courses.isEmpty())

        // 2. Make-up Sunday should show Monday's course with shift metadata
        val shiftDay = ScheduleDateResolver.resolveDay(
            table = table,
            timeScheme = scheme,
            date = LocalDate.parse("2026-09-20"),
            courses = listOf(mondayCourse),
            adjustments = adjustments
        )
        assertTrue(shiftDay.isHolidayShift)
        assertEquals(1, shiftDay.courses.size)
        assertEquals("高等数学", shiftDay.courses.first().course.name)
        assertTrue(shiftDay.courses.first().isShiftedFromHoliday)
        assertEquals(LocalTime.of(8, 0), shiftDay.courses.first().startTime)
        assertEquals(LocalTime.of(9, 40), shiftDay.courses.first().endTime)
    }

    @Test
    fun resolver_expandsRepeatingCalendarEventsAndCompletions() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2026, 9, 7, 18, 30).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 9, 7, 19, 30).atZone(zone).toInstant().toEpochMilli()
        val event = CalendarEvent(
            id = 5L,
            title = "英语打卡",
            startEpochMillis = start,
            endEpochMillis = end,
            repeatType = RepeatType.WEEKLY,
            effectiveDate = "2026-09-07",
            endRepeatDate = "2026-09-30"
        )
        val completion = CalendarCompletion(
            id = 1L,
            eventId = 5L,
            occurrenceDate = "2026-09-14"
        )

        val onSep14 = ScheduleDateResolver.resolveCalendarEventsForDay(
            date = LocalDate.parse("2026-09-14"),
            events = listOf(event),
            completions = listOf(completion),
            zone = zone
        )
        assertEquals(1, onSep14.size)
        assertTrue(onSep14.first().isCompleted)

        val onSep21 = ScheduleDateResolver.resolveCalendarEventsForDay(
            date = LocalDate.parse("2026-09-21"),
            events = listOf(event),
            completions = listOf(completion),
            zone = zone
        )
        assertEquals(1, onSep21.size)
        assertFalse(onSep21.first().isCompleted)

        val afterEnd = ScheduleDateResolver.resolveCalendarEventsForDay(
            date = LocalDate.parse("2026-10-05"),
            events = listOf(event),
            completions = listOf(completion),
            zone = zone
        )
        assertTrue(afterEnd.isEmpty())
    }

    @Test
    fun timeSchemeGenerator_separatesSmallAndBigBreaksAndPeriods() {
        val cfg = TimeSchemeGeneratorConfig(
            courseMinutes = 45,
            smallBreakMinutes = 10,
            bigBreakMinutes = 20,
            morningNodes = 4,
            morningStartTime = "08:00",
            afternoonNodes = 4,
            afternoonStartTime = "14:00",
            eveningNodes = 2,
            eveningStartTime = "19:00",
            includeLunchBreak = true
        )
        val slots = TimeSchemeGenerator.generateSlots(schemeId = 1L, config = cfg)
        val regular = slots.filter { it.slotType == TimeSlotType.REGULAR }.sortedBy { it.nodeIndex }
        val breaks = slots.filter { it.slotType == TimeSlotType.CUSTOM_BREAK }

        assertEquals(10, regular.size)
        // Morning: 1-2 (+10m), 2-3 (+20m), 3-4 (+10m)
        assertEquals("08:00" to "08:45", regular[0].startTime to regular[0].endTime)
        assertEquals("08:55" to "09:40", regular[1].startTime to regular[1].endTime)
        assertEquals("10:00" to "10:45", regular[2].startTime to regular[2].endTime)
        assertEquals("10:55" to "11:40", regular[3].startTime to regular[3].endTime)
        // Afternoon: 5-6 (+10m), 6-7 (+20m), 7-8 (+10m)
        assertEquals("14:00" to "14:45", regular[4].startTime to regular[4].endTime)
        assertEquals("14:55" to "15:40", regular[5].startTime to regular[5].endTime)
        assertEquals("16:00" to "16:45", regular[6].startTime to regular[6].endTime)
        assertEquals("16:55" to "17:40", regular[7].startTime to regular[7].endTime)
        // Evening: 9-10 (+10m)
        assertEquals("19:00" to "19:45", regular[8].startTime to regular[8].endTime)
        assertEquals("19:55" to "20:40", regular[9].startTime to regular[9].endTime)

        // Lunch break automatically spans morning end to afternoon start after node 4
        assertEquals(1, breaks.size)
        assertEquals(4, breaks.first().nodeIndex)
        assertEquals("午休", breaks.first().title)
        assertEquals("11:40", breaks.first().startTime)
        assertEquals("14:00", breaks.first().endTime)

        // Inference round-trip
        val inferred = TimeSchemeGenerator.inferConfig(TimeScheme(id = 1L, slots = slots))
        assertEquals(45, inferred.courseMinutes)
        assertEquals(10, inferred.smallBreakMinutes)
        assertEquals(20, inferred.bigBreakMinutes)
        assertEquals(4, inferred.morningNodes)
        assertEquals(4, inferred.afternoonNodes)
        assertEquals(2, inferred.eveningNodes)
        assertEquals("08:00", inferred.morningStartTime)
        assertEquals("14:00", inferred.afternoonStartTime)
        assertEquals("19:00", inferred.eveningStartTime)
        assertTrue(inferred.includeLunchBreak)

        // Disabling lunch break removes it
        val noLunchSlots = TimeSchemeGenerator.generateSlots(
            schemeId = 1L,
            config = cfg.copy(includeLunchBreak = false),
            existingCustomBreaks = breaks
        )
        assertTrue(noLunchSlots.none { it.slotType == TimeSlotType.CUSTOM_BREAK })
    }

    @Test
    fun appearance_deserializesLegacyJsonWithNewDefaults() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacyJson = """{"itemHeightDp":72,"itemAlpha":0.9,"showLocation":true}"""
        val decoded = json.decodeFromString<ScheduleAppearance>(legacyJson)
        assertEquals(72, decoded.itemHeightDp)
        assertEquals(3, decoded.courseNameMaxLines)
        assertEquals(3, decoded.locationMaxLines)
        assertTrue(decoded.showCustomBreaks)
        assertEquals(null, decoded.customThemeColorHex)
        assertTrue(decoded.autoExtractThemeFromBg)
        assertEquals(0.55f, decoded.chromeSurfaceAlpha, 0.001f)
        assertTrue(!decoded.applyBackgroundGlobally)
        assertTrue(decoded.pastelCourseStyle)
    }

    @Test
    fun courseOverlapLayout_sideBySideForTwoAndFoldedForThree() {
        val date = LocalDate.of(2026, 9, 7)
        fun mkItem(id: Long, name: String, start: Int, count: Int) = ResolvedCourseItem(
            course = Course(id = id, name = name),
            session = CourseSession(id = id * 10, courseId = id, dayOfWeek = 1, startNode = start, nodeCount = count),
            date = date,
            effectiveWeek = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 40),
            isCurrentWeek = true
        )

        // 2 overlapping courses -> side by side (2 columns)
        val two = listOf(
            mkItem(1L, "高等数学", 1, 2),
            mkItem(2L, "大学英语", 2, 2)
        )
        val placedTwo = CourseOverlapLayout.layoutDay(two)
        assertEquals(2, placedTwo.size)
        assertTrue(placedTwo.all { !it.isFolded && it.columnCount == 2 })
        assertEquals(setOf(0, 1), placedTwo.map { it.columnIndex }.toSet())
        assertEquals(1, placedTwo[0].conflictPeers.size)
        assertEquals(1, placedTwo[1].conflictPeers.size)

        // 3 concurrent overlapping courses -> folded into 1 block with +2 badge
        val three = listOf(
            mkItem(1L, "高等数学", 1, 2),
            mkItem(2L, "大学英语", 1, 2),
            mkItem(3L, "体育课", 2, 2)
        )
        val placedThree = CourseOverlapLayout.layoutDay(three)
        assertEquals(1, placedThree.size)
        assertTrue(placedThree.first().isFolded)
        assertEquals(3, placedThree.first().foldedItems.size)
        assertEquals(2, placedThree.first().extraFoldedCount)
        assertEquals(1, placedThree.first().startNode)
        assertEquals(3, placedThree.first().endNode)
    }

    @Test
    fun generateUpcomingAlarms_populatesCourseLocation() {
        val scheme = DefaultTimeSchemes.standardCollege(1L)
        val table = ScheduleTable(id = 1L, timeSchemeId = 1L, termStartDate = "2026-09-07")
        val course = Course(
            id = 1L,
            tableId = 1L,
            name = "数据结构",
            sessions = listOf(
                CourseSession(
                    id = 10L,
                    courseId = 1L,
                    tableId = 1L,
                    dayOfWeek = 1,
                    startNode = 1,
                    nodeCount = 2,
                    weeks = WeekSet.range(1, 16),
                    room = "教学楼B-305"
                )
            )
        )
        val alarms = ScheduleDateResolver.generateUpcomingAlarms(
            table = table,
            timeScheme = scheme,
            courses = listOf(course),
            calendarEvents = emptyList(),
            completions = emptyList(),
            adjustments = emptyList(),
            settings = ReminderSettings(enabled = true, courseMinutesBefore = 15),
            now = LocalDateTime.of(2026, 9, 7, 7, 0),
            horizonDays = 1,
            zone = ZoneId.of("Asia/Shanghai")
        )
        assertEquals(1, alarms.size)
        assertEquals("数据结构", alarms.first().title)
        assertEquals("教学楼B-305", alarms.first().location)
    }

    @Test
    fun generateUpcomingAlarms_includesActiveCountdownWindowAndSkipsStartedOrCompleted() {
        val zone = ZoneId.of("Asia/Shanghai")
        val scheme = DefaultTimeSchemes.standardCollege(1L)
        val table = ScheduleTable(id = 1L, timeSchemeId = 1L, termStartDate = "2026-09-07")
        // Monday Week 1: node 1 starts 08:00; with courseMinutesBefore=15 -> trigger=07:45
        val course = Course(
            id = 1L,
            tableId = 1L,
            name = "编译原理",
            sessions = listOf(
                CourseSession(
                    id = 10L,
                    courseId = 1L,
                    tableId = 1L,
                    dayOfWeek = 1,
                    startNode = 1,
                    nodeCount = 2,
                    weeks = WeekSet.range(1, 16),
                    room = "教A-201"
                )
            )
        )
        // Calendar event 1: starts 08:10 (now=07:55 -> inside active countdown window, remind=20 -> trigger=07:50)
        val ev1Start = LocalDateTime.of(2026, 9, 7, 8, 10).atZone(zone).toInstant().toEpochMilli()
        val ev1End = LocalDateTime.of(2026, 9, 7, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val activeEvent = CalendarEvent(
            id = 20L,
            title = "班会",
            startEpochMillis = ev1Start,
            endEpochMillis = ev1End,
            remindMinutesBefore = 20,
            countdownEnabled = true,
            effectiveDate = "2026-09-07"
        )
        // Calendar event 2: already started at 07:50 -> should NOT emit
        val ev2Start = LocalDateTime.of(2026, 9, 7, 7, 50).atZone(zone).toInstant().toEpochMilli()
        val ev2End = LocalDateTime.of(2026, 9, 7, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val startedEvent = CalendarEvent(
            id = 21L,
            title = "已开始日程",
            startEpochMillis = ev2Start,
            endEpochMillis = ev2End,
            remindMinutesBefore = 15,
            effectiveDate = "2026-09-07"
        )
        // Calendar event 3: future trigger at 09:45 (starts 10:00, remind=15)
        val ev3Start = LocalDateTime.of(2026, 9, 7, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val ev3End = LocalDateTime.of(2026, 9, 7, 11, 0).atZone(zone).toInstant().toEpochMilli()
        val futureEvent = CalendarEvent(
            id = 22L,
            title = "组会",
            startEpochMillis = ev3Start,
            endEpochMillis = ev3End,
            remindMinutesBefore = 15,
            effectiveDate = "2026-09-07"
        )
        // Calendar event 4: completed inside window -> skipped
        val completedEvent = activeEvent.copy(id = 23L, title = "已完成事项")
        val completion = CalendarCompletion(id = 1L, eventId = 23L, occurrenceDate = "2026-09-07")

        val alarms = ScheduleDateResolver.generateUpcomingAlarms(
            table = table,
            timeScheme = scheme,
            courses = listOf(course),
            calendarEvents = listOf(activeEvent, startedEvent, futureEvent, completedEvent),
            completions = listOf(completion),
            adjustments = emptyList(),
            settings = ReminderSettings(enabled = true, courseMinutesBefore = 15),
            now = LocalDateTime.of(2026, 9, 7, 7, 55),
            horizonDays = 1,
            zone = zone,
            includeActiveCountdownWindow = true
        )

        assertEquals(3, alarms.size)
        val activeCourseAlarm = alarms.first { it.title == "编译原理" }
        assertTrue(activeCourseAlarm.isActiveCountdownWindow)

        val activeCalAlarm = alarms.first { it.title == "班会" }
        assertTrue(activeCalAlarm.isActiveCountdownWindow)

        val futureCalAlarm = alarms.first { it.title == "组会" }
        assertFalse(futureCalAlarm.isActiveCountdownWindow)
    }
}
