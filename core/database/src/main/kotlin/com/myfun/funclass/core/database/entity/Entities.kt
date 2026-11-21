package com.myfun.funclass.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "time_schemes",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class TimeSchemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String,
    val name: String,
    val defaultCourseMinutes: Int,
    val defaultBreakMinutes: Int,
    val uniformCourseLength: Boolean,
    val uniformBreakLength: Boolean,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "time_slots",
    foreignKeys = [
        ForeignKey(
            entity = TimeSchemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["schemeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["schemeId"])]
)
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val schemeId: Long,
    val slotType: Int,
    val nodeIndex: Int,
    val title: String,
    val startTime: String,
    val endTime: String,
    val sortOrder: Int
)

@Entity(
    tableName = "schedule_tables",
    foreignKeys = [
        ForeignKey(
            entity = TimeSchemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["timeSchemeId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["timeSchemeId"])
    ]
)
data class ScheduleTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String,
    val name: String,
    val schoolName: String,
    val timeSchemeId: Long,
    val termStartDate: String,
    val maxWeeks: Int,
    val maxNodes: Int,
    val sundayFirst: Boolean,
    val appearanceJson: String,
    val displayOrder: Int,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["tableId"])
    ]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String,
    val tableId: Long,
    val name: String,
    val colorHex: String,
    val credit: Float,
    val note: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "course_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ScheduleTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["courseId"]),
        Index(value = ["tableId", "dayOfWeek"])
    ]
)
data class CourseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String,
    val courseId: Long,
    val tableId: Long,
    val dayOfWeek: Int,
    val startNode: Int,
    val nodeCount: Int,
    val weeksMask: String,
    val useCustomTime: Boolean,
    val customStartTime: String,
    val customEndTime: String,
    val room: String,
    val teacher: String,
    val sessionNote: String,
    val weekNotesJson: String,
    val label: Int,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "holiday_adjustments",
    indices = [Index(value = ["tableId", "targetDate"], unique = true)]
)
data class HolidayAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tableId: Long,
    val targetDate: String,
    val adjustType: Int,
    val sourceDate: String,
    val label: String,
    val isPreset: Boolean
)

@Entity(
    tableName = "calendar_events",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String,
    val title: String,
    val location: String,
    val colorHex: String,
    val isAllDay: Boolean,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val repeatType: Int,
    val effectiveDate: String,
    val endRepeatDate: String,
    val note: String,
    val label: Int,
    val remindMinutesBefore: Int,
    val countdownEnabled: Boolean,
    val countdownAutoDismissMinutes: Int,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "calendar_completions",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId", "occurrenceDate"], unique = true)
    ]
)
data class CalendarCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventId: Long,
    val occurrenceDate: String,
    val completedAt: Long
)

@Entity(tableName = "widget_bindings")
data class WidgetBindingEntity(
    @PrimaryKey val appWidgetId: Int,
    val widgetType: Int,
    val tableId: Long,
    val styleConfigJson: String
)
