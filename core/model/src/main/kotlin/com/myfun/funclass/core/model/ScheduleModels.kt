package com.myfun.funclass.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable
enum class TimeSlotType(val code: Int) {
    REGULAR(0),
    CUSTOM_BREAK(1);

    companion object {
        fun fromCode(code: Int): TimeSlotType = entries.firstOrNull { it.code == code } ?: REGULAR
    }
}

@Serializable
data class TimeSlot(
    val id: Long = 0L,
    val schemeId: Long = 0L,
    val slotType: TimeSlotType = TimeSlotType.REGULAR,
    /** For REGULAR: 1-based node index; for CUSTOM_BREAK: inserted after this node index. */
    val nodeIndex: Int,
    val title: String = "",
    val startTime: String, // "HH:mm"
    val endTime: String,   // "HH:mm"
    val sortOrder: Int = 0
) {
    fun startLocalTime(): LocalTime = LocalTime.parse(normalizeTime(startTime))
    fun endLocalTime(): LocalTime = LocalTime.parse(normalizeTime(endTime))

    companion object {
        fun normalizeTime(raw: String): String {
            val parts = raw.trim().split(":")
            if (parts.size < 2) return "08:00"
            val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: 8
            val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: 0
            return "%02d:%02d".format(h, m)
        }
    }
}

@Serializable
data class TimeScheme(
    val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String = "默认作息时间",
    val defaultCourseMinutes: Int = 45,
    val defaultBreakMinutes: Int = 10,
    val uniformCourseLength: Boolean = true,
    val uniformBreakLength: Boolean = false,
    val slots: List<TimeSlot> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    fun regularSlots(): List<TimeSlot> =
        slots.filter { it.slotType == TimeSlotType.REGULAR }.sortedBy { it.nodeIndex }

    fun customBreaks(): List<TimeSlot> =
        slots.filter { it.slotType == TimeSlotType.CUSTOM_BREAK }
            .sortedWith(compareBy<TimeSlot> { it.nodeIndex }.thenBy { it.sortOrder })

    fun slotForNode(node: Int): TimeSlot? =
        slots.firstOrNull { it.slotType == TimeSlotType.REGULAR && it.nodeIndex == node }

    fun timeRangeForNodes(startNode: Int, nodeCount: Int): Pair<LocalTime, LocalTime> {
        val start = slotForNode(startNode)?.startLocalTime() ?: LocalTime.of(8, 0)
        val endNode = (startNode + nodeCount - 1).coerceAtLeast(startNode)
        val end = slotForNode(endNode)?.endLocalTime() ?: start.plusMinutes((defaultCourseMinutes * nodeCount).toLong())
        return start to end
    }
}

@Serializable
data class TimeSchemeGeneratorConfig(
    val courseMinutes: Int = 45,
    val smallBreakMinutes: Int = 10,
    val bigBreakMinutes: Int = 20,
    val morningNodes: Int = 4,
    val morningStartTime: String = "08:00",
    val afternoonNodes: Int = 4,
    val afternoonStartTime: String = "14:00",
    val eveningNodes: Int = 4,
    val eveningStartTime: String = "19:00",
    val includeLunchBreak: Boolean = true,
    val lunchBreakTitle: String = "午休"
) {
    val totalNodes: Int get() = (morningNodes + afternoonNodes + eveningNodes).coerceIn(1, 20)
}

object TimeSchemeGenerator {
    private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    private fun parseSafe(raw: String, fallback: LocalTime): LocalTime =
        runCatching { LocalTime.parse(TimeSlot.normalizeTime(raw)) }.getOrElse { fallback }

    private fun addMinutesClamped(time: LocalTime, minutes: Int): LocalTime {
        val total = (time.hour * 60 + time.minute + minutes).coerceIn(0, 23 * 60 + 59)
        return LocalTime.of(total / 60, total % 60)
    }

    /**
     * Generates regular course slots for Morning / Afternoon / Evening using separate
     * small-break (within a 2-period class block) and big-break (between 2-period class blocks) durations,
     * plus optional lunch break and preserved non-lunch custom breaks.
     */
    fun generateSlots(
        schemeId: Long,
        config: TimeSchemeGeneratorConfig,
        existingCustomBreaks: List<TimeSlot> = emptyList()
    ): List<TimeSlot> {
        val courseMin = config.courseMinutes.coerceIn(15, 120)
        val smallBreak = config.smallBreakMinutes.coerceIn(0, 60)
        val bigBreak = config.bigBreakMinutes.coerceIn(0, 60)

        val morningCount = config.morningNodes.coerceIn(0, 10)
        val afternoonCount = config.afternoonNodes.coerceIn(0, 10)
        val eveningCount = config.eveningNodes.coerceIn(0, 10)

        val regular = mutableListOf<TimeSlot>()
        var nodeIndex = 1

        fun appendPeriod(count: Int, startRaw: String, fallback: LocalTime): Pair<TimeSlot?, TimeSlot?> {
            if (count <= 0) return null to null
            var cursor = parseSafe(startRaw, fallback)
            var first: TimeSlot? = null
            var last: TimeSlot? = null
            for (localIdx in 1..count) {
                val end = addMinutesClamped(cursor, courseMin)
                val slot = TimeSlot(
                    schemeId = schemeId,
                    slotType = TimeSlotType.REGULAR,
                    nodeIndex = nodeIndex,
                    startTime = formatTime(cursor),
                    endTime = formatTime(end),
                    sortOrder = nodeIndex
                )
                regular += slot
                if (first == null) first = slot
                last = slot
                nodeIndex++

                if (localIdx < count) {
                    val gap = if (localIdx % 2 == 1) smallBreak else bigBreak
                    cursor = addMinutesClamped(end, gap)
                }
            }
            return first to last
        }

        val (_, morningLast) = appendPeriod(morningCount, config.morningStartTime, LocalTime.of(8, 0))
        val (afternoonFirst, _) = appendPeriod(afternoonCount, config.afternoonStartTime, LocalTime.of(14, 0))
        appendPeriod(eveningCount, config.eveningStartTime, LocalTime.of(19, 0))

        val totalRegular = regular.size
        val preservedCustomBreaks = existingCustomBreaks
            .filter { it.slotType == TimeSlotType.CUSTOM_BREAK }
            .filterNot { it.title.contains("午休") || it.title.contains("中午") }
            .filter { it.nodeIndex in 1 until totalRegular }

        val lunchBreakList = if (config.includeLunchBreak && morningLast != null && afternoonFirst != null) {
            listOf(
                TimeSlot(
                    schemeId = schemeId,
                    slotType = TimeSlotType.CUSTOM_BREAK,
                    nodeIndex = morningLast.nodeIndex,
                    title = config.lunchBreakTitle.ifBlank { "午休" },
                    startTime = morningLast.endTime,
                    endTime = afternoonFirst.startTime,
                    sortOrder = 0
                )
            )
        } else {
            emptyList()
        }

        return regular + lunchBreakList + preservedCustomBreaks
    }

    /**
     * Infers a best-fit [TimeSchemeGeneratorConfig] from an existing [TimeScheme]'s slots.
     */
    fun inferConfig(scheme: TimeScheme): TimeSchemeGeneratorConfig {
        val regular = scheme.regularSlots()
        if (regular.isEmpty()) return TimeSchemeGeneratorConfig()

        val noonCutoff = LocalTime.of(12, 30)
        val eveningCutoff = LocalTime.of(18, 0)

        val morning = regular.filter { it.startLocalTime().isBefore(noonCutoff) }
        val afternoon = regular.filter { !it.startLocalTime().isBefore(noonCutoff) && it.startLocalTime().isBefore(eveningCutoff) }
        val evening = regular.filter { !it.startLocalTime().isBefore(eveningCutoff) }

        val firstSlot = regular.first()
        val inferredCourseMin = java.time.Duration.between(firstSlot.startLocalTime(), firstSlot.endLocalTime())
            .toMinutes().toInt().takeIf { it in 15..120 } ?: scheme.defaultCourseMinutes

        fun gapBetween(list: List<TimeSlot>, idxA: Int, idxB: Int): Int? {
            if (idxB >= list.size) return null
            val mins = java.time.Duration.between(list[idxA].endLocalTime(), list[idxB].startLocalTime()).toMinutes().toInt()
            return mins.takeIf { it in 0..60 }
        }

        val smallBreak = gapBetween(morning, 0, 1)
            ?: gapBetween(afternoon, 0, 1)
            ?: gapBetween(evening, 0, 1)
            ?: scheme.defaultBreakMinutes

        val bigBreak = gapBetween(morning, 1, 2)
            ?: gapBetween(afternoon, 1, 2)
            ?: gapBetween(evening, 1, 2)
            ?: 20

        val lunchBreak = scheme.customBreaks().firstOrNull {
            it.title.contains("午休") || it.title.contains("中午") || (morning.isNotEmpty() && it.nodeIndex == morning.size)
        }

        return TimeSchemeGeneratorConfig(
            courseMinutes = inferredCourseMin,
            smallBreakMinutes = smallBreak,
            bigBreakMinutes = bigBreak,
            morningNodes = morning.size,
            morningStartTime = morning.firstOrNull()?.startTime ?: "08:00",
            afternoonNodes = afternoon.size,
            afternoonStartTime = afternoon.firstOrNull()?.startTime ?: "14:00",
            eveningNodes = evening.size,
            eveningStartTime = evening.firstOrNull()?.startTime ?: "19:00",
            includeLunchBreak = lunchBreak != null,
            lunchBreakTitle = lunchBreak?.title?.ifBlank { "午休" } ?: "午休"
        )
    }
}

object DefaultTimeSchemes {
    fun standardCollege(schemeId: Long = 1L): TimeScheme {
        val regular = listOf(
            1 to ("08:00" to "08:45"),
            2 to ("08:55" to "09:40"),
            3 to ("10:00" to "10:45"),
            4 to ("10:55" to "11:40"),
            5 to ("14:00" to "14:45"),
            6 to ("14:55" to "15:40"),
            7 to ("16:00" to "16:45"),
            8 to ("16:55" to "17:40"),
            9 to ("19:00" to "19:45"),
            10 to ("19:55" to "20:40"),
            11 to ("20:50" to "21:35"),
            12 to ("21:45" to "22:30")
        ).map { (node, range) ->
            TimeSlot(
                schemeId = schemeId,
                slotType = TimeSlotType.REGULAR,
                nodeIndex = node,
                startTime = range.first,
                endTime = range.second,
                sortOrder = node
            )
        }
        val lunchBreak = TimeSlot(
            schemeId = schemeId,
            slotType = TimeSlotType.CUSTOM_BREAK,
            nodeIndex = 4,
            title = "午休",
            startTime = "12:00",
            endTime = "13:50",
            sortOrder = 0
        )
        return TimeScheme(
            id = schemeId,
            name = "标准高校作息 (12节)",
            defaultCourseMinutes = 45,
            defaultBreakMinutes = 10,
            slots = regular + lunchBreak
        )
    }
}

@Serializable
enum class DarkModePolicy {
    SYSTEM, LIGHT, DARK
}

/**
 * Complete timetable appearance customization, unified cleanly into a serializable model.
 */
@Serializable
data class ScheduleAppearance(
    val itemHeightDp: Int = 58,
    val itemAlpha: Float = 0.90f,
    val otherWeekCourseAlpha: Float = 0.35f,
    val cornerRadiusDp: Int = 12,
    val itemTextSizeSp: Int = 12,
    val headerTextSizeSp: Int = 12,
    /** Max lines for course name on timetable card; 0 means unlimited (auto-fit cell height). */
    val courseNameMaxLines: Int = 3,
    /** Max lines for classroom/location on timetable card; 0 means unlimited (auto-fit cell height). */
    val locationMaxLines: Int = 3,
    val showGrid: Boolean = true,
    val useDottedLine: Boolean = true,
    val showTime: Boolean = true,
    val showTimeBar: Boolean = true,
    val showCustomBreaks: Boolean = true,
    val showSaturday: Boolean = true,
    val showSunday: Boolean = true,
    val showLocation: Boolean = true,
    val showRoomPrefix: Boolean = false,
    val showTeacher: Boolean = true,
    val showRemark: Boolean = false,
    val showOtherWeekCourse: Boolean = true,
    val centerHorizontal: Boolean = false,
    val centerVertical: Boolean = false,
    val backgroundUri: String? = null,
    val backgroundDimAlpha: Float = 0.12f,
    val themePreset: String = "sky",
    val darkModePolicy: DarkModePolicy = DarkModePolicy.SYSTEM,
    val useDynamicColor: Boolean = false,
    val customThemeColorHex: String? = null,
    val autoExtractThemeFromBg: Boolean = true,
    val chromeSurfaceAlpha: Float = 0.55f,
    val applyBackgroundGlobally: Boolean = false,
    val pastelCourseStyle: Boolean = true
)

@Serializable
data class ScheduleTable(
    val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String = "我的课表",
    val schoolName: String = "",
    val timeSchemeId: Long = 1L,
    val termStartDate: String = LocalDate.now().toString(), // ISO yyyy-MM-dd
    val maxWeeks: Int = 20,
    val maxNodes: Int = 12,
    val sundayFirst: Boolean = false,
    val appearance: ScheduleAppearance = ScheduleAppearance(),
    val displayOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    fun termStartLocalDate(): LocalDate = parseFlexibleDate(termStartDate) ?: LocalDate.now()

    companion object {
        /**
         * Parses ISO (`2026-09-07`) as well as non-zero-padded dates (`2026-9-7`, `2026/9/7`).
         */
        fun parseFlexibleDate(raw: String?): LocalDate? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            runCatching { LocalDate.parse(trimmed) }.getOrNull()?.let { return it }
            val m = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$").find(trimmed) ?: return null
            val y = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val d = m.groupValues[3].toIntOrNull() ?: return null
            return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
        }
    }
}

@Serializable
data class CourseSession(
    val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val courseId: Long = 0L,
    val tableId: Long = 0L,
    /** ISO day of week: 1 = Monday .. 7 = Sunday. */
    val dayOfWeek: Int,
    val startNode: Int,
    val nodeCount: Int = 2,
    val weeks: WeekSet = WeekSet.range(1, 16),
    val useCustomTime: Boolean = false,
    val customStartTime: String = "",
    val customEndTime: String = "",
    val room: String = "",
    val teacher: String = "",
    val sessionNote: String = "",
    val weekNotes: Map<Int, String> = emptyMap(),
    val label: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    val endNode: Int get() = (startNode + nodeCount - 1).coerceAtLeast(startNode)

    fun noteForWeek(week: Int): String = weekNotes[week]?.takeIf { it.isNotBlank() } ?: sessionNote
}

@Serializable
data class Course(
    val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val tableId: Long = 0L,
    val name: String,
    val colorHex: String = CourseColorPalette.colorForName(name),
    val credit: Float = 0f,
    val note: String = "",
    val sessions: List<CourseSession> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

object CourseColorPalette {
    val colors: List<String> = listOf(
        "#5B8FF9",
        "#5AD8A6",
        "#F6BD16",
        "#E8684A",
        "#6DC8EC",
        "#9270CA",
        "#FF9D4D",
        "#269A99",
        "#FF99C3",
        "#7262FD",
        "#00B4D8",
        "#F28482",
        "#84A59D",
        "#E07A5F",
        "#3D5A80",
        "#90BE6D"
    )

    val macaronColors: List<String> = listOf(
        "#F48FB1", // 樱花粉
        "#FFAB91", // 蜜桃橘
        "#F4C96B", // 奶油黄
        "#81C798", // 抹茶绿
        "#70D0C6", // 薄荷青
        "#7AB8F5", // 晴空蓝
        "#A990E8", // 香芋紫
        "#E580B0", // 莓果粉
        "#6FC3DF", // 海盐蓝
        "#A8C97F", // 牛油果
        "#E8A87C", // 杏仁奶
        "#C59CE8"  // 丁香紫
    )

    fun colorForName(name: String): String {
        if (name.isBlank()) return colors.first()
        val idx = (name.hashCode() and 0x7FFFFFFF) % colors.size
        return colors[idx]
    }
}
