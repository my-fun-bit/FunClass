package com.myfun.funclass.core.parser

import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.CourseColorPalette
import com.myfun.funclass.core.model.CourseSession
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.TimeSlot
import com.myfun.funclass.core.model.TimeSlotType
import com.myfun.funclass.core.model.WeekSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bidirectional compatibility codec for the 5-line JSON `.funclass` / `.wakeup_schedule` interchange format.
 *
 * Line 1: LegacyTimeConfig JSON object
 * Line 2: List<LegacyTimeDetail> JSON array
 * Line 3: LegacyTableConfig JSON object
 * Line 4: List<LegacyCourseBase> JSON array
 * Line 5: List<LegacyCourseDetail> JSON array
 */
object WakeupScheduleCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Serializable
    data class LegacyTimeConfig(
        val courseLen: Int = 45,
        val id: Int = 1,
        val name: String = "默认",
        val sameBreakLen: Boolean = false,
        val sameLen: Boolean = true,
        val theBreakLen: Int = 10
    )

    @Serializable
    data class LegacyTimeDetail(
        val endTime: String,
        val node: Int,
        val startTime: String,
        val timeTable: Int = 1
    )

    @Serializable
    data class LegacyTableConfig(
        val background: String = "",
        val courseTextColor: Int = -1,
        val id: Int = 1,
        val itemAlpha: Int = 90,
        val itemHeight: Int = 58,
        val itemTextSize: Int = 12,
        val maxWeek: Int = 20,
        val nodes: Int = 12,
        val showOtherWeekCourse: Boolean = true,
        val showSat: Boolean = true,
        val showSun: Boolean = true,
        val showTime: Boolean = true,
        val startDate: String = "2026-09-07",
        val strokeColor: Int = -2130706433,
        val sundayFirst: Boolean = false,
        val tableName: String = "导入课表",
        val textColor: Int = -16777216,
        val timeTable: Int = 1,
        val type: Int = 0
    )

    @Serializable
    data class LegacyCourseBase(
        val color: String = "",
        val courseName: String,
        val credit: Float = 0f,
        val id: Int,
        val note: String = "",
        val tableId: Int = 1
    )

    @Serializable
    data class LegacyCourseDetail(
        val day: Int,
        val endTime: String = "",
        val endWeek: Int,
        val id: Int,
        val level: Int = 0,
        val ownTime: Boolean = false,
        val room: String = "",
        val startNode: Int,
        val startTime: String = "",
        val startWeek: Int,
        val step: Int = 2,
        val tableId: Int = 1,
        val teacher: String = "",
        val type: Int = 0
    )

    fun encode(
        table: ScheduleTable,
        timeScheme: TimeScheme,
        courses: List<Course>
    ): String {
        val line1 = json.encodeToString(
            LegacyTimeConfig(
                courseLen = timeScheme.defaultCourseMinutes,
                id = 1,
                name = timeScheme.name,
                sameBreakLen = timeScheme.uniformBreakLength,
                sameLen = timeScheme.uniformCourseLength,
                theBreakLen = timeScheme.defaultBreakMinutes
            )
        )
        val line2 = json.encodeToString(
            timeScheme.regularSlots().map { slot ->
                LegacyTimeDetail(
                    node = slot.nodeIndex,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    timeTable = 1
                )
            }
        )
        val line3 = json.encodeToString(
            LegacyTableConfig(
                id = 1,
                itemAlpha = (table.appearance.itemAlpha * 100).toInt().coerceIn(10, 100),
                itemHeight = table.appearance.itemHeightDp,
                itemTextSize = table.appearance.itemTextSizeSp,
                maxWeek = table.maxWeeks,
                nodes = table.maxNodes,
                showOtherWeekCourse = table.appearance.showOtherWeekCourse,
                showSat = table.appearance.showSaturday,
                showSun = table.appearance.showSunday,
                showTime = table.appearance.showTime,
                startDate = table.termStartDate,
                sundayFirst = table.sundayFirst,
                tableName = table.name,
                timeTable = 1
            )
        )

        val bases = mutableListOf<LegacyCourseBase>()
        val details = mutableListOf<LegacyCourseDetail>()
        courses.filter { !it.isDeleted }.forEachIndexed { idx, course ->
            val legacyId = idx + 1
            bases += LegacyCourseBase(
                id = legacyId,
                courseName = course.name,
                color = course.colorHex,
                credit = course.credit,
                note = course.note,
                tableId = 1
            )
            for (session in course.sessions.filter { !it.isDeleted }) {
                for (seg in session.weeks.toLegacySegments()) {
                    details += LegacyCourseDetail(
                        id = legacyId,
                        day = session.dayOfWeek,
                        startNode = session.startNode,
                        step = session.nodeCount,
                        startWeek = seg.startWeek,
                        endWeek = seg.endWeek,
                        type = seg.type,
                        ownTime = session.useCustomTime,
                        startTime = session.customStartTime,
                        endTime = session.customEndTime,
                        room = session.room,
                        teacher = session.teacher,
                        tableId = 1
                    )
                }
            }
        }
        val line4 = json.encodeToString(bases)
        val line5 = json.encodeToString(details)
        return listOf(line1, line2, line3, line4, line5).joinToString("\n")
    }

    fun decode(rawText: String): ParsedScheduleResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.size >= 5) {
            "无效的 .wakeup_schedule 文件：至少需要 5 行 JSON 数据，实际只有 ${lines.size} 行"
        }

        val timeCfg = json.decodeFromString<LegacyTimeConfig>(lines[0])
        val timeDetails = json.decodeFromString<List<LegacyTimeDetail>>(lines[1])
        val tableCfg = json.decodeFromString<LegacyTableConfig>(lines[2])
        val courseBases = json.decodeFromString<List<LegacyCourseBase>>(lines[3])
        val courseDetails = json.decodeFromString<List<LegacyCourseDetail>>(lines[4])

        val maxTableNodes = tableCfg.nodes.coerceIn(4, 20)
        val filteredDetails = timeDetails
            .filter { td ->
                td.node in 1..20 && !(td.node > maxTableNodes && td.startTime == "00:00" && td.endTime == "00:00")
            }
            .sortedBy { it.node }

        val scheme = TimeScheme(
            name = timeCfg.name.ifBlank { "${tableCfg.tableName}作息" },
            defaultCourseMinutes = timeCfg.courseLen,
            defaultBreakMinutes = timeCfg.theBreakLen,
            uniformCourseLength = timeCfg.sameLen,
            uniformBreakLength = timeCfg.sameBreakLen,
            slots = filteredDetails.map { td ->
                TimeSlot(
                    slotType = TimeSlotType.REGULAR,
                    nodeIndex = td.node,
                    startTime = TimeSlot.normalizeTime(td.startTime),
                    endTime = TimeSlot.normalizeTime(td.endTime),
                    sortOrder = td.node
                )
            }
        )

        val detailsByCourseId = courseDetails.groupBy { it.id }
        val courses = courseBases.map { base ->
            val rawDetails = detailsByCourseId[base.id].orEmpty()
            // Merge adjacent/discrete legacy week segments that share the exact same slot/room/teacher
            val mergedSessions = rawDetails.groupBy { d ->
                listOf(d.day, d.startNode, d.step, d.room, d.teacher, d.ownTime, d.startTime, d.endTime)
            }.values.map { group ->
                val sample = group.first()
                val allWeeks = group.flatMap { d ->
                    WeekSet.range(d.startWeek, d.endWeek, d.type).weeks
                }.toSet()
                CourseSession(
                    dayOfWeek = sample.day.coerceIn(1, 7),
                    startNode = sample.startNode.coerceAtLeast(1),
                    nodeCount = sample.step.coerceAtLeast(1),
                    weeks = if (allWeeks.isEmpty()) WeekSet.range(1, tableCfg.maxWeek) else WeekSet(allWeeks),
                    useCustomTime = sample.ownTime,
                    customStartTime = sample.startTime,
                    customEndTime = sample.endTime,
                    room = sample.room,
                    teacher = sample.teacher
                )
            }
            Course(
                name = base.courseName,
                colorHex = base.color.takeIf { it.startsWith("#") && (it.length == 7 || it.length == 9) }
                    ?: CourseColorPalette.colorForName(base.courseName),
                credit = base.credit,
                note = base.note,
                sessions = mergedSessions
            )
        }

        val normalizedStartDate = ScheduleTable.parseFlexibleDate(tableCfg.startDate)?.toString()
        val (observedMaxWeek, observedMaxNode) = CourseGroupingHelper.computeScheduleBounds(
            courses = courses,
            minWeeks = tableCfg.maxWeek.coerceIn(1, 30),
            minNodes = tableCfg.nodes.coerceIn(4, 20)
        )

        return ParsedScheduleResult(
            tableName = tableCfg.tableName.ifBlank { "导入课表" },
            courses = courses,
            timeScheme = scheme,
            termStartDate = normalizedStartDate,
            maxWeeks = observedMaxWeek,
            maxNodes = observedMaxNode,
            sundayFirst = tableCfg.sundayFirst
        )
    }
}
