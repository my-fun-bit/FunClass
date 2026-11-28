package com.myfun.funclass.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetType(val code: Int, val displayName: String) {
    WEEK_GRID(0, "周课表网格"),
    TODAY(1, "今日课表"),
    TODAY_TOMORROW(2, "今日与明日课表");

    companion object {
        fun fromCode(code: Int): WidgetType = entries.firstOrNull { it.code == code } ?: TODAY
    }
}

@Serializable
data class WidgetStyleConfig(
    val backgroundAlpha: Float = 0.92f,
    val cardCornerRadiusDp: Int = 12,
    val showDateHeader: Boolean = true,
    val showTeacher: Boolean = false,
    val showLocation: Boolean = true,
    val showTime: Boolean = true,
    val showCalendarEvents: Boolean = true,
    val useColorfulCards: Boolean = true,
    val backgroundColorHex: String = "#FFFFFF",
    val textColorHex: String = "#1C1B1F"
)

@Serializable
data class WidgetBinding(
    val appWidgetId: Int,
    val widgetType: WidgetType,
    /** 0L means follow the currently active schedule table. */
    val tableId: Long = 0L,
    val styleConfig: WidgetStyleConfig = WidgetStyleConfig()
)

@Serializable
data class ReminderSettings(
    val enabled: Boolean = true,
    val courseMinutesBefore: Int = 15,
    val countdownNotificationEnabled: Boolean = true,
    val countdownDismissAfterStartMinutes: Int = 10,
    val autoMuteDuringClass: Boolean = false,
    val calendarRemindersEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val fluidCloudEnabled: Boolean = true,
    val fluidCloudShowCountdown: Boolean = true,
    val useAlarmClockChannel: Boolean = false
)

enum class AlarmItemType {
    COURSE, CALENDAR
}

data class ScheduledAlarmItem(
    val id: String,
    val type: AlarmItemType,
    val title: String,
    val subtitle: String,
    val location: String = "",
    val triggerEpochMillis: Long,
    val eventStartEpochMillis: Long,
    val eventEndEpochMillis: Long,
    val countdownEnabled: Boolean,
    val autoDismissMinutes: Int,
    val isActiveCountdownWindow: Boolean = false
)

data class CourseDndWindow(
    val id: String,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long
)
