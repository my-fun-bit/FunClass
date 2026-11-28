package com.myfun.funclass.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.myfun.funclass.core.database.preferences.AppPreferencesRepository
import com.myfun.funclass.core.database.repository.ScheduleRepository
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.ResolvedDaySchedule
import com.myfun.funclass.core.model.ResolvedWeekSchedule
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.WidgetStyleConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun scheduleRepository(): ScheduleRepository
    fun appPreferencesRepository(): AppPreferencesRepository
}

data class WidgetSnapshot(
    val table: ScheduleTable,
    val style: WidgetStyleConfig,
    val today: ResolvedDaySchedule,
    val tomorrow: ResolvedDaySchedule,
    val week: ResolvedWeekSchedule
)

object WidgetDataLoader {
    private const val ACTION_RESCHEDULE_SYNC = "com.myfun.funclass.ACTION_RESCHEDULE_SYNC"
    private const val REMINDER_RECEIVER_CLASS = "com.myfun.funclass.reminder.ReminderBroadcastReceiver"
    private const val SYNC_MIN_INTERVAL_MILLIS = 5 * 60_000L

    @Volatile
    private var lastSyncBroadcastMillis: Long = 0L

    private fun triggerReminderSyncThrottled(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSyncBroadcastMillis < SYNC_MIN_INTERVAL_MILLIS) return
        lastSyncBroadcastMillis = now
        runCatching {
            val appCtx = context.applicationContext
            val intent = Intent(ACTION_RESCHEDULE_SYNC).apply {
                component = ComponentName(appCtx.packageName, REMINDER_RECEIVER_CLASS)
            }
            appCtx.sendBroadcast(intent)
        }
    }

    suspend fun loadSnapshot(context: Context, appWidgetId: Int): WidgetSnapshot {
        triggerReminderSyncThrottled(context)
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repo = ep.scheduleRepository()
        val prefs = ep.appPreferencesRepository()
        val activeId = repo.ensureInitialized()
        val binding = repo.getWidgetBinding(appWidgetId)
        val targetTableId = binding?.tableId?.takeIf { it > 0L } ?: prefs.getActiveTableIdOnce().takeIf { it > 0L } ?: activeId
        val table = repo.getTable(targetTableId) ?: repo.getAllTables().first()
        val scheme = repo.getTimeScheme(table.timeSchemeId) ?: DefaultTimeSchemes.standardCollege(table.timeSchemeId)
        val courses = repo.getCourses(table.id)
        val events = repo.getCalendarEvents()
        val completions = repo.getCalendarCompletions()
        val adjustments = repo.getHolidayAdjustments(table.id)

        val todayDate = LocalDate.now()
        val tomorrowDate = todayDate.plusDays(1)
        val currentWeek = ScheduleDateResolver.computeWeekNumber(table.termStartLocalDate(), todayDate, table.sundayFirst)
            .coerceIn(1, table.maxWeeks)

        val todayResolved = ScheduleDateResolver.resolveDay(
            table = table,
            timeScheme = scheme,
            date = todayDate,
            courses = courses,
            calendarEvents = events,
            completions = completions,
            adjustments = adjustments
        )
        val tomorrowResolved = ScheduleDateResolver.resolveDay(
            table = table,
            timeScheme = scheme,
            date = tomorrowDate,
            courses = courses,
            calendarEvents = events,
            completions = completions,
            adjustments = adjustments
        )
        val weekResolved = ScheduleDateResolver.resolveWeek(
            table = table,
            timeScheme = scheme,
            weekNumber = currentWeek,
            courses = courses,
            calendarEvents = events,
            completions = completions,
            adjustments = adjustments
        )

        return WidgetSnapshot(
            table = table,
            style = binding?.styleConfig ?: WidgetStyleConfig(),
            today = todayResolved,
            tomorrow = tomorrowResolved,
            week = weekResolved
        )
    }
}

object WidgetRefresher {
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(TodayGlanceWidget::class.java).forEach {
            TodayGlanceWidget().update(context, it)
        }
        manager.getGlanceIds(TodayTomorrowGlanceWidget::class.java).forEach {
            TodayTomorrowGlanceWidget().update(context, it)
        }
        manager.getGlanceIds(WeekGridGlanceWidget::class.java).forEach {
            WeekGridGlanceWidget().update(context, it)
        }
    }
}
