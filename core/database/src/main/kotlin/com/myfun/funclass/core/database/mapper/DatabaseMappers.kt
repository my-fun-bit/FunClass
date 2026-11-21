package com.myfun.funclass.core.database.mapper

import com.myfun.funclass.core.database.dao.CourseWithSessions
import com.myfun.funclass.core.database.dao.TimeSchemeWithSlots
import com.myfun.funclass.core.database.entity.CalendarCompletionEntity
import com.myfun.funclass.core.database.entity.CalendarEventEntity
import com.myfun.funclass.core.database.entity.CourseEntity
import com.myfun.funclass.core.database.entity.CourseSessionEntity
import com.myfun.funclass.core.database.entity.HolidayAdjustmentEntity
import com.myfun.funclass.core.database.entity.ScheduleTableEntity
import com.myfun.funclass.core.database.entity.TimeSchemeEntity
import com.myfun.funclass.core.database.entity.TimeSlotEntity
import com.myfun.funclass.core.database.entity.WidgetBindingEntity
import com.myfun.funclass.core.model.CalendarCompletion
import com.myfun.funclass.core.model.CalendarEvent
import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.CourseSession
import com.myfun.funclass.core.model.HolidayAdjustType
import com.myfun.funclass.core.model.HolidayAdjustment
import com.myfun.funclass.core.model.RepeatType
import com.myfun.funclass.core.model.ScheduleAppearance
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.TimeSlot
import com.myfun.funclass.core.model.TimeSlotType
import com.myfun.funclass.core.model.WeekSet
import com.myfun.funclass.core.model.WidgetBinding
import com.myfun.funclass.core.model.WidgetStyleConfig
import com.myfun.funclass.core.model.WidgetType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val mapperJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun TimeSlotEntity.toDomain() = TimeSlot(
    id = id,
    schemeId = schemeId,
    slotType = TimeSlotType.fromCode(slotType),
    nodeIndex = nodeIndex,
    title = title,
    startTime = startTime,
    endTime = endTime,
    sortOrder = sortOrder
)

fun TimeSlot.toEntity(overrideSchemeId: Long = schemeId) = TimeSlotEntity(
    id = id,
    schemeId = overrideSchemeId,
    slotType = slotType.code,
    nodeIndex = nodeIndex,
    title = title,
    startTime = startTime,
    endTime = endTime,
    sortOrder = sortOrder
)

fun TimeSchemeWithSlots.toDomain() = TimeScheme(
    id = scheme.id,
    syncId = scheme.syncId,
    name = scheme.name,
    defaultCourseMinutes = scheme.defaultCourseMinutes,
    defaultBreakMinutes = scheme.defaultBreakMinutes,
    uniformCourseLength = scheme.uniformCourseLength,
    uniformBreakLength = scheme.uniformBreakLength,
    slots = slots.map { it.toDomain() },
    updatedAt = scheme.updatedAt,
    isDeleted = scheme.isDeleted
)

fun TimeScheme.toEntity() = TimeSchemeEntity(
    id = id,
    syncId = syncId,
    name = name,
    defaultCourseMinutes = defaultCourseMinutes,
    defaultBreakMinutes = defaultBreakMinutes,
    uniformCourseLength = uniformCourseLength,
    uniformBreakLength = uniformBreakLength,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun ScheduleTableEntity.toDomain() = ScheduleTable(
    id = id,
    syncId = syncId,
    name = name,
    schoolName = schoolName,
    timeSchemeId = timeSchemeId,
    termStartDate = termStartDate,
    maxWeeks = maxWeeks,
    maxNodes = maxNodes,
    sundayFirst = sundayFirst,
    appearance = runCatching {
        mapperJson.decodeFromString<ScheduleAppearance>(appearanceJson)
    }.getOrElse { ScheduleAppearance() },
    displayOrder = displayOrder,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun ScheduleTable.toEntity() = ScheduleTableEntity(
    id = id,
    syncId = syncId,
    name = name,
    schoolName = schoolName,
    timeSchemeId = timeSchemeId,
    termStartDate = termStartDate,
    maxWeeks = maxWeeks,
    maxNodes = maxNodes,
    sundayFirst = sundayFirst,
    appearanceJson = mapperJson.encodeToString(appearance),
    displayOrder = displayOrder,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun CourseSessionEntity.toDomain() = CourseSession(
    id = id,
    syncId = syncId,
    courseId = courseId,
    tableId = tableId,
    dayOfWeek = dayOfWeek,
    startNode = startNode,
    nodeCount = nodeCount,
    weeks = WeekSet.decode(weeksMask),
    useCustomTime = useCustomTime,
    customStartTime = customStartTime,
    customEndTime = customEndTime,
    room = room,
    teacher = teacher,
    sessionNote = sessionNote,
    weekNotes = runCatching {
        mapperJson.decodeFromString<Map<Int, String>>(weekNotesJson)
    }.getOrElse { emptyMap() },
    label = label,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun CourseSession.toEntity(overrideCourseId: Long = courseId, overrideTableId: Long = tableId) =
    CourseSessionEntity(
        id = id,
        syncId = syncId,
        courseId = overrideCourseId,
        tableId = overrideTableId,
        dayOfWeek = dayOfWeek,
        startNode = startNode,
        nodeCount = nodeCount,
        weeksMask = weeks.encode(),
        useCustomTime = useCustomTime,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        room = room,
        teacher = teacher,
        sessionNote = sessionNote,
        weekNotesJson = mapperJson.encodeToString(weekNotes),
        label = label,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

fun CourseWithSessions.toDomain() = Course(
    id = course.id,
    syncId = course.syncId,
    tableId = course.tableId,
    name = course.name,
    colorHex = course.colorHex,
    credit = course.credit,
    note = course.note,
    sessions = sessions.filter { !it.isDeleted }.map { it.toDomain() },
    updatedAt = course.updatedAt,
    isDeleted = course.isDeleted
)

fun Course.toEntity() = CourseEntity(
    id = id,
    syncId = syncId,
    tableId = tableId,
    name = name,
    colorHex = colorHex,
    credit = credit,
    note = note,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun HolidayAdjustmentEntity.toDomain() = HolidayAdjustment(
    id = id,
    tableId = tableId,
    targetDate = targetDate,
    adjustType = HolidayAdjustType.fromCode(adjustType),
    sourceDate = sourceDate,
    label = label,
    isPreset = isPreset
)

fun HolidayAdjustment.toEntity() = HolidayAdjustmentEntity(
    id = id,
    tableId = tableId,
    targetDate = targetDate,
    adjustType = adjustType.code,
    sourceDate = sourceDate,
    label = label,
    isPreset = isPreset
)

fun CalendarEventEntity.toDomain() = CalendarEvent(
    id = id,
    syncId = syncId,
    title = title,
    location = location,
    colorHex = colorHex,
    isAllDay = isAllDay,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    repeatType = RepeatType.fromCode(repeatType),
    effectiveDate = effectiveDate,
    endRepeatDate = endRepeatDate,
    note = note,
    label = label,
    remindMinutesBefore = remindMinutesBefore,
    countdownEnabled = countdownEnabled,
    countdownAutoDismissMinutes = countdownAutoDismissMinutes,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun CalendarEvent.toEntity() = CalendarEventEntity(
    id = id,
    syncId = syncId,
    title = title,
    location = location,
    colorHex = colorHex,
    isAllDay = isAllDay,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    repeatType = repeatType.code,
    effectiveDate = effectiveDate,
    endRepeatDate = endRepeatDate,
    note = note,
    label = label,
    remindMinutesBefore = remindMinutesBefore,
    countdownEnabled = countdownEnabled,
    countdownAutoDismissMinutes = countdownAutoDismissMinutes,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun CalendarCompletionEntity.toDomain() = CalendarCompletion(
    id = id,
    eventId = eventId,
    occurrenceDate = occurrenceDate,
    completedAt = completedAt
)

fun WidgetBindingEntity.toDomain() = WidgetBinding(
    appWidgetId = appWidgetId,
    widgetType = WidgetType.fromCode(widgetType),
    tableId = tableId,
    styleConfig = runCatching {
        mapperJson.decodeFromString<WidgetStyleConfig>(styleConfigJson)
    }.getOrElse { WidgetStyleConfig() }
)

fun WidgetBinding.toEntity() = WidgetBindingEntity(
    appWidgetId = appWidgetId,
    widgetType = widgetType.code,
    tableId = tableId,
    styleConfigJson = mapperJson.encodeToString(styleConfig)
)
