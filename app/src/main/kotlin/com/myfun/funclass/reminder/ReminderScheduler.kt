package com.myfun.funclass.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import com.myfun.funclass.MainActivity
import com.myfun.funclass.core.database.preferences.AppPreferencesRepository
import com.myfun.funclass.core.database.repository.ScheduleRepository
import com.myfun.funclass.core.model.AlarmItemType
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.ScheduledAlarmItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-JVM decision engine for reminder reconciliation:
 * - Deterministic per-occurrence notification/alarm IDs (avoiding index-shift ghost alarms).
 * - Immediate posting for items created inside an active countdown window.
 * - Cancellation of deleted/completed/edited alarms and stale active notifications.
 * - Suppression of already-posted or manually dismissed occurrences.
 */
object ReminderDecisionEngine {

    const val MAX_ALARMS = 64
    const val TRIGGER_ID_BASE = 10_000
    const val TRIGGER_ID_SPAN = 20_000
    const val DISMISS_ACTION_OFFSET = 30_000
    const val AUTO_DISMISS_OFFSET = 60_000
    const val DND_RESTORE_OFFSET = 90_000

    data class PostedEntry(
        val key: String,
        val notificationId: Int,
        val eventStartMillis: Long,
        val expiresAtMillis: Long
    ) {
        val occurrenceId: String get() = key.substringBefore('@')
    }

    data class ReconcilePlan(
        val alarmCodesToCancel: Set<Int>,
        val activeNotifIdsToDismiss: Set<Int>,
        val itemsToPostImmediately: List<ScheduledAlarmItem>,
        val futureItemsToSchedule: List<ScheduledAlarmItem>
    )

    fun stableNotificationId(occurrenceId: String): Int =
        TRIGGER_ID_BASE + (occurrenceId.hashCode() and 0x7FFFFFFF) % TRIGGER_ID_SPAN

    fun occurrenceKey(item: ScheduledAlarmItem): String =
        "${item.id}@${item.triggerEpochMillis}"

    fun computeReconcilePlan(
        items: List<ScheduledAlarmItem>,
        previouslyScheduledCodes: Set<Int>,
        previouslyPosted: List<PostedEntry>,
        dismissedKeys: Set<String>,
        validTodayOccurrenceIds: Set<String>,
        nowMillis: Long,
        maxAlarms: Int = MAX_ALARMS
    ): ReconcilePlan {
        val activeItems = items.filter { it.isActiveCountdownWindow }
        val futureItems = items.filter { !it.isActiveCountdownWindow }.take(maxAlarms)
        val futureCodes = futureItems.map { stableNotificationId(it.id) }.toSet()
        val alarmCodesToCancel = previouslyScheduledCodes - futureCodes

        val validActiveKeys = activeItems.map { occurrenceKey(it) }.toSet()
        val postedKeys = previouslyPosted.map { it.key }.toSet()

        val activeNotifIdsToDismiss = previouslyPosted
            .filter { entry ->
                val stillInCountdownWindow = entry.key in validActiveKeys
                val alreadyStartedAndStillValid =
                    nowMillis >= entry.eventStartMillis && entry.occurrenceId in validTodayOccurrenceIds
                !stillInCountdownWindow && !alreadyStartedAndStillValid
            }
            .map { it.notificationId }
            .toSet()

        val itemsToPostImmediately = activeItems.filter { item ->
            val key = occurrenceKey(item)
            key !in dismissedKeys && key !in postedKeys
        }

        return ReconcilePlan(
            alarmCodesToCancel = alarmCodesToCancel,
            activeNotifIdsToDismiss = activeNotifIdsToDismiss,
            itemsToPostImmediately = itemsToPostImmediately,
            futureItemsToSchedule = futureItems
        )
    }
}

internal class ReminderAlarmStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("funclass_reminder_state", Context.MODE_PRIVATE)

    @Synchronized
    fun getScheduledCodes(): Set<Int> {
        return prefs.getStringSet(KEY_SCHEDULED_CODES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    @Synchronized
    fun saveScheduledCodes(codes: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    @Synchronized
    fun getActiveDismissedKeys(nowMillis: Long): Set<String> {
        return prefs.getStringSet(KEY_DISMISSED_ENTRIES, emptySet())
            .orEmpty()
            .mapNotNull { raw ->
                val sep = raw.lastIndexOf('|')
                if (sep <= 0) return@mapNotNull null
                val key = raw.substring(0, sep)
                val expiresAt = raw.substring(sep + 1).toLongOrNull() ?: return@mapNotNull null
                if (expiresAt > nowMillis) key else null
            }
            .toSet()
    }

    @Synchronized
    fun getActivePostedEntries(nowMillis: Long): List<ReminderDecisionEngine.PostedEntry> {
        return prefs.getStringSet(KEY_POSTED_ENTRIES, emptySet())
            .orEmpty()
            .mapNotNull { raw -> parsePostedEntry(raw) }
            .filter { it.expiresAtMillis > nowMillis }
    }

    @Synchronized
    fun markDismissed(key: String, expiresAtMillis: Long) {
        if (key.isBlank()) return
        val existing = prefs.getStringSet(KEY_DISMISSED_ENTRIES, emptySet())
            .orEmpty()
            .filterNot { it.startsWith("$key|") }
            .toMutableSet()
        existing += "$key|$expiresAtMillis"
        prefs.edit().putStringSet(KEY_DISMISSED_ENTRIES, existing).apply()
    }

    @Synchronized
    fun markPosted(key: String, notificationId: Int, eventStartMillis: Long, expiresAtMillis: Long) {
        if (key.isBlank()) return
        val effectiveExpiry = maxOf(expiresAtMillis, eventStartMillis + 60_000L)
        val existing = prefs.getStringSet(KEY_POSTED_ENTRIES, emptySet())
            .orEmpty()
            .filterNot { raw ->
                val parsed = parsePostedEntry(raw)
                parsed == null || parsed.key == key || parsed.notificationId == notificationId
            }
            .toMutableSet()
        existing += "$key|$notificationId|$eventStartMillis|$effectiveExpiry"
        prefs.edit().putStringSet(KEY_POSTED_ENTRIES, existing).apply()
    }

    @Synchronized
    fun removePostedByNotifId(notificationId: Int) {
        val updated = prefs.getStringSet(KEY_POSTED_ENTRIES, emptySet())
            .orEmpty()
            .filterNot { parsePostedEntry(it)?.notificationId == notificationId }
            .toSet()
        prefs.edit().putStringSet(KEY_POSTED_ENTRIES, updated).apply()
    }

    @Synchronized
    fun pruneExpired(nowMillis: Long) {
        val validDismissed = prefs.getStringSet(KEY_DISMISSED_ENTRIES, emptySet())
            .orEmpty()
            .filter { raw ->
                val exp = raw.substringAfterLast('|', "").toLongOrNull() ?: 0L
                exp > nowMillis
            }
            .toSet()
        val validPosted = prefs.getStringSet(KEY_POSTED_ENTRIES, emptySet())
            .orEmpty()
            .filter { raw ->
                val parsed = parsePostedEntry(raw)
                parsed != null && parsed.expiresAtMillis > nowMillis
            }
            .toSet()
        prefs.edit()
            .putStringSet(KEY_DISMISSED_ENTRIES, validDismissed)
            .putStringSet(KEY_POSTED_ENTRIES, validPosted)
            .apply()
    }

    private fun parsePostedEntry(raw: String): ReminderDecisionEngine.PostedEntry? {
        val parts = raw.split('|')
        if (parts.size != 4) return null
        val key = parts[0]
        val notifId = parts[1].toIntOrNull() ?: return null
        val startMillis = parts[2].toLongOrNull() ?: return null
        val expiresAt = parts[3].toLongOrNull() ?: return null
        return ReminderDecisionEngine.PostedEntry(key, notifId, startMillis, expiresAt)
    }

    companion object {
        private const val KEY_SCHEDULED_CODES = "scheduled_codes"
        private const val KEY_DISMISSED_ENTRIES = "dismissed_entries"
        private const val KEY_POSTED_ENTRIES = "posted_entries"
    }
}

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val prefs: AppPreferencesRepository
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    internal val stateStore = ReminderAlarmStateStore(context)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun hasDndPermission(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Recomputes upcoming 7 days of alarms through ScheduleDateResolver (honoring holidays & shifts),
     * cancels obsolete alarms/active notifications, immediately posts newly created active-window
     * countdown reminders, and schedules upcoming alarms with stable IDs.
     */
    suspend fun rescheduleAll(): Int {
        val nowMillis = System.currentTimeMillis()
        stateStore.pruneExpired(nowMillis)

        val activeId = scheduleRepository.ensureInitialized()
        val table = scheduleRepository.getTable(activeId) ?: return 0
        val scheme = scheduleRepository.getTimeScheme(table.timeSchemeId)
            ?: DefaultTimeSchemes.standardCollege(table.timeSchemeId)
        val courses = scheduleRepository.getCourses(table.id)
        val events = scheduleRepository.getCalendarEvents()
        val completions = scheduleRepository.getCalendarCompletions()
        val adjustments = scheduleRepository.getHolidayAdjustments(table.id)
        val settings = prefs.getReminderSettingsOnce()
        val nowDt = LocalDateTime.now()

        val upcoming = ScheduleDateResolver.generateUpcomingAlarms(
            table = table,
            timeScheme = scheme,
            courses = courses,
            calendarEvents = events,
            completions = completions,
            adjustments = adjustments,
            settings = settings,
            now = nowDt,
            horizonDays = 7,
            includeActiveCountdownWindow = true
        )

        val validTodayIds = if (!settings.enabled) {
            emptySet()
        } else {
            val today = nowDt.toLocalDate()
            val resolvedToday = ScheduleDateResolver.resolveDay(
                table = table,
                timeScheme = scheme,
                date = today,
                courses = courses,
                calendarEvents = events,
                completions = completions,
                adjustments = adjustments
            )
            buildSet {
                resolvedToday.courses.forEach { c ->
                    add("course_${c.course.id}_${c.session.id}_$today")
                }
                if (settings.calendarRemindersEnabled) {
                    resolvedToday.calendarEvents.filter { !it.isCompleted }.forEach { occ ->
                        add("calendar_${occ.event.id}_$today")
                    }
                }
            }
        }

        val plan = ReminderDecisionEngine.computeReconcilePlan(
            items = upcoming,
            previouslyScheduledCodes = stateStore.getScheduledCodes(),
            previouslyPosted = stateStore.getActivePostedEntries(nowMillis),
            dismissedKeys = stateStore.getActiveDismissedKeys(nowMillis),
            validTodayOccurrenceIds = validTodayIds,
            nowMillis = nowMillis,
            maxAlarms = ReminderDecisionEngine.MAX_ALARMS
        )

        // 1. Cancel stale future alarms that were deleted, completed, or moved
        for (oldCode in plan.alarmCodesToCancel) {
            cancelTriggerAlarm(oldCode)
        }

        // 2. Dismiss active notifications for deleted, completed, or edited items
        for (oldNotifId in plan.activeNotifIdsToDismiss) {
            FluidCloudNotifier.dismiss(context, oldNotifId)
            cancelCountdownDismiss(oldNotifId)
            stateStore.removePostedByNotifId(oldNotifId)
        }

        // 3. Immediately post reminders for items created inside the active countdown window
        val nm = context.getSystemService(NotificationManager::class.java)
        val activeIds = nm?.activeNotifications?.map { it.id }?.toSet().orEmpty()
        for (item in plan.itemsToPostImmediately) {
            val notifId = ReminderDecisionEngine.stableNotificationId(item.id)
            val occKey = ReminderDecisionEngine.occurrenceKey(item)
            val autoDismissAt = item.eventStartEpochMillis + item.autoDismissMinutes * 60_000L
            if (notifId in activeIds) {
                stateStore.markPosted(occKey, notifId, item.eventStartEpochMillis, autoDismissAt)
                continue
            }
            val chipText = item.location.ifBlank { item.title.take(5) }
            val result = FluidCloudNotifier.postLiveReminder(
                context = context,
                notificationId = notifId,
                courseTitle = item.title,
                subtitle = item.subtitle,
                shortChipText = chipText,
                isCourse = item.type == AlarmItemType.COURSE,
                eventStartMillis = item.eventStartEpochMillis,
                countdown = item.countdownEnabled,
                useFluidCloud = settings.fluidCloudEnabled,
                showCountdownInChip = settings.fluidCloudShowCountdown,
                location = item.location,
                occurrenceKey = occKey
            )
            if (result.posted) {
                stateStore.markPosted(occKey, notifId, item.eventStartEpochMillis, autoDismissAt)
                if ((item.countdownEnabled || settings.fluidCloudEnabled) && item.autoDismissMinutes > 0) {
                    scheduleCountdownDismiss(autoDismissAt, notifId, occKey)
                }
            }
        }

        // 4. Schedule upcoming triggers using stable per-occurrence request codes
        val exact = canScheduleExactAlarms()
        val newScheduledCodes = linkedSetOf<Int>()
        plan.futureItemsToSchedule.forEachIndexed { index, item ->
            val requestCode = ReminderDecisionEngine.stableNotificationId(item.id)
            val occKey = ReminderDecisionEngine.occurrenceKey(item)
            newScheduledCodes += requestCode

            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ReminderBroadcastReceiver.ACTION_TRIGGER_REMINDER
                putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, item.title)
                putExtra(ReminderBroadcastReceiver.EXTRA_SUBTITLE, item.subtitle)
                putExtra(ReminderBroadcastReceiver.EXTRA_LOCATION, item.location)
                putExtra(ReminderBroadcastReceiver.EXTRA_IS_COURSE, item.type == AlarmItemType.COURSE)
                putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_START, item.eventStartEpochMillis)
                putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_END, item.eventEndEpochMillis)
                putExtra(ReminderBroadcastReceiver.EXTRA_COUNTDOWN, item.countdownEnabled)
                putExtra(ReminderBroadcastReceiver.EXTRA_AUTO_DISMISS_MIN, item.autoDismissMinutes)
                putExtra(
                    ReminderBroadcastReceiver.EXTRA_AUTO_MUTE,
                    settings.autoMuteDuringClass && item.type == AlarmItemType.COURSE
                )
                putExtra(ReminderBroadcastReceiver.EXTRA_FLUID_CLOUD, settings.fluidCloudEnabled)
                putExtra(ReminderBroadcastReceiver.EXTRA_FLUID_SHOW_COUNTDOWN, settings.fluidCloudShowCountdown)
                putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, requestCode)
                putExtra(ReminderBroadcastReceiver.EXTRA_OCCURRENCE_KEY, occKey)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (index == 0 && settings.useAlarmClockChannel && exact) {
                scheduleAlarmClockChannel(item.triggerEpochMillis, pi)
            } else {
                scheduleAlarmInternal(item.triggerEpochMillis, pi, exact)
            }
        }
        stateStore.saveScheduledCodes(newScheduledCodes)
        return plan.futureItemsToSchedule.size + plan.itemsToPostImmediately.size
    }

    fun scheduleAutoRestoreDnd(endEpochMillis: Long, notificationId: Int) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_RESTORE_DND
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId + ReminderDecisionEngine.DND_RESTORE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmInternal(endEpochMillis, pi, canScheduleExactAlarms())
    }

    fun scheduleCountdownDismiss(
        dismissAtEpochMillis: Long,
        notificationId: Int,
        occurrenceKey: String = ""
    ) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderBroadcastReceiver.EXTRA_OCCURRENCE_KEY, occurrenceKey)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId + ReminderDecisionEngine.AUTO_DISMISS_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmInternal(dismissAtEpochMillis, pi, canScheduleExactAlarms())
    }

    fun cancelCountdownDismiss(notificationId: Int) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_DISMISS_NOTIFICATION
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId + ReminderDecisionEngine.AUTO_DISMISS_OFFSET,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun cancelTriggerAlarm(requestCode: Int) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_TRIGGER_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun scheduleAlarmClockChannel(triggerMillis: Long, pi: PendingIntent) {
        val showIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showIntent), pi)
        }.onFailure {
            scheduleAlarmInternal(triggerMillis, pi, exact = true)
        }
    }

    private fun scheduleAlarmInternal(triggerMillis: Long, pi: PendingIntent, exact: Boolean) {
        if (exact) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }
}
