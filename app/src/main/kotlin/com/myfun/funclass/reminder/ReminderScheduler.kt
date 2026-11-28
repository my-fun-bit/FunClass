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
import com.myfun.funclass.core.model.CourseDndWindow
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.ReminderSettings
import com.myfun.funclass.core.model.ScheduleDateResolver
import com.myfun.funclass.core.model.ScheduledAlarmItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    const val DND_ENABLE_OFFSET = 120_000
    const val DND_HANDOVER_TOLERANCE_MILLIS = 5_000L
    const val KEEP_ALIVE_PRE_START_MILLIS = 30L * 60_000L

    data class PostedEntry(
        val key: String,
        val notificationId: Int,
        val eventStartMillis: Long,
        val expiresAtMillis: Long
    ) {
        val occurrenceId: String get() = key.substringBefore('@')
    }

    data class DndSessionEntry(
        val key: String,
        val notificationId: Int,
        val startEpochMillis: Long,
        val endEpochMillis: Long
    ) {
        val occurrenceId: String get() = key.substringBefore('@')
    }

    data class ReconcilePlan(
        val alarmCodesToCancel: Set<Int>,
        val activeNotifIdsToDismiss: Set<Int>,
        val itemsToPostImmediately: List<ScheduledAlarmItem>,
        val futureItemsToSchedule: List<ScheduledAlarmItem>
    )

    data class DndReconcilePlan(
        val enableCodesToCancel: Set<Int>,
        val sessionsToAbort: List<DndSessionEntry>,
        val restoreAlarmsToUpdate: List<CourseDndWindow>,
        val ongoingToActivate: List<CourseDndWindow>,
        val futureToSchedule: List<CourseDndWindow>,
        val shouldRestoreDndNow: Boolean
    )

    data class KeepAlivePlan(
        val shouldRunNow: Boolean,
        val stopAtMillis: Long?,
        val nextStartAtMillis: Long?,
        val nextItemTitle: String?,
        val nextItemStartMillis: Long?
    )

    fun stableNotificationId(occurrenceId: String): Int =
        TRIGGER_ID_BASE + (occurrenceId.hashCode() and 0x7FFFFFFF) % TRIGGER_ID_SPAN

    fun occurrenceKey(item: ScheduledAlarmItem): String =
        "${item.id}@${item.triggerEpochMillis}"

    fun dndStartKey(window: CourseDndWindow): String =
        "${window.id}@${window.startEpochMillis}"

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

    /**
     * Reconciles course auto-DND alarms independently from reminder trigger times so DND
     * only activates at class start (`startEpochMillis`) and restores at class end (`endEpochMillis`).
     */
    fun computeDndReconcilePlan(
        windows: List<CourseDndWindow>,
        previouslyScheduledEnableCodes: Set<Int>,
        activeDndSessions: List<DndSessionEntry>,
        handledStartKeys: Set<String>,
        nowMillis: Long,
        maxAlarms: Int = MAX_ALARMS
    ): DndReconcilePlan {
        val ongoingWindows = windows.filter { it.startEpochMillis <= nowMillis && nowMillis < it.endEpochMillis }
        val futureWindows = windows.filter { it.startEpochMillis > nowMillis }.take(maxAlarms)
        val futureEnableCodes = futureWindows.map { stableNotificationId(it.id) }.toSet()
        val enableCodesToCancel = previouslyScheduledEnableCodes - futureEnableCodes

        val ongoingByKey = ongoingWindows.associateBy { dndStartKey(it) }
        val sessionsToAbort = activeDndSessions.filter { session ->
            ongoingByKey[session.key] == null
        }
        val restoreAlarmsToUpdate = activeDndSessions.mapNotNull { session ->
            val matched = ongoingByKey[session.key]
            if (matched != null && matched.endEpochMillis != session.endEpochMillis) matched else null
        }
        val ongoingToActivate = ongoingWindows.filter { window ->
            dndStartKey(window) !in handledStartKeys
        }
        val shouldRestoreDndNow = sessionsToAbort.isNotEmpty() && ongoingWindows.isEmpty()

        return DndReconcilePlan(
            enableCodesToCancel = enableCodesToCancel,
            sessionsToAbort = sessionsToAbort,
            restoreAlarmsToUpdate = restoreAlarmsToUpdate,
            ongoingToActivate = ongoingToActivate,
            futureToSchedule = futureWindows,
            shouldRestoreDndNow = shouldRestoreDndNow
        )
    }

    /**
     * Returns the handover course window if another course is still in session or starting
     * right at [scheduledEndMillis] (e.g. back-to-back or overlapping classes), preventing a
     * premature restore to normal interruption filter.
     */
    fun shouldKeepDndAtRestore(
        windows: List<CourseDndWindow>,
        autoMuteEnabled: Boolean,
        nowMillis: Long,
        scheduledEndMillis: Long,
        toleranceMillis: Long = DND_HANDOVER_TOLERANCE_MILLIS
    ): CourseDndWindow? {
        if (!autoMuteEnabled) return null
        val checkPoint = maxOf(nowMillis, scheduledEndMillis) + toleranceMillis
        return windows
            .filter { it.startEpochMillis <= checkPoint && it.endEpochMillis > checkPoint }
            .maxByOrNull { it.endEpochMillis }
    }

    /**
     * Pure-JVM decision for the optional daytime foreground guardian service:
     * - Runs only when both reminders and backgroundKeepAliveEnabled are turned on and today still
     *   has at least one upcoming/ongoing item.
     * - Automatically computes the stop timestamp after today's last item finishes so the service
     *   does not stay alive overnight.
     * - When today's items are finished, schedules a wake timestamp before the next future day's
     *   first reminder.
     */
    fun computeKeepAlivePlan(
        items: List<ScheduledAlarmItem>,
        remindersEnabled: Boolean,
        keepAliveEnabled: Boolean,
        nowMillis: Long,
        todayEndMillis: Long
    ): KeepAlivePlan {
        if (!remindersEnabled || !keepAliveEnabled) {
            return KeepAlivePlan(
                shouldRunNow = false,
                stopAtMillis = null,
                nextStartAtMillis = null,
                nextItemTitle = null,
                nextItemStartMillis = null
            )
        }

        fun itemKeepAliveUntil(item: ScheduledAlarmItem): Long {
            val dismissAt = item.eventStartEpochMillis + maxOf(item.autoDismissMinutes, 1) * 60_000L
            return maxOf(item.eventEndEpochMillis, dismissAt)
        }

        val todayRemaining = items
            .filter { item ->
                val isTodayItem = item.triggerEpochMillis < todayEndMillis || item.eventStartEpochMillis < todayEndMillis
                isTodayItem && itemKeepAliveUntil(item) > nowMillis
            }
            .sortedBy { it.eventStartEpochMillis }

        if (todayRemaining.isNotEmpty()) {
            val stopAt = todayRemaining.maxOf { itemKeepAliveUntil(it) }
            val nextItem = todayRemaining.firstOrNull { it.eventStartEpochMillis >= nowMillis }
                ?: todayRemaining.first()
            return KeepAlivePlan(
                shouldRunNow = true,
                stopAtMillis = stopAt,
                nextStartAtMillis = null,
                nextItemTitle = nextItem.title,
                nextItemStartMillis = nextItem.eventStartEpochMillis
            )
        }

        val nextFuture = items
            .filter { it.triggerEpochMillis >= todayEndMillis || it.eventStartEpochMillis >= todayEndMillis }
            .minByOrNull { it.triggerEpochMillis }

        val nextStart = nextFuture?.let {
            (it.triggerEpochMillis - KEEP_ALIVE_PRE_START_MILLIS).coerceAtLeast(nowMillis + 60_000L)
        }
        return KeepAlivePlan(
            shouldRunNow = false,
            stopAtMillis = null,
            nextStartAtMillis = nextStart,
            nextItemTitle = nextFuture?.title,
            nextItemStartMillis = nextFuture?.eventStartEpochMillis
        )
    }
}

internal class ReminderAlarmStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("funclass_reminder_state", Context.MODE_PRIVATE)

    @Synchronized
    fun getScheduledCodes(): Set<Int> = getCodeSet(KEY_SCHEDULED_CODES)

    @Synchronized
    fun saveScheduledCodes(codes: Set<Int>) = saveCodeSet(KEY_SCHEDULED_CODES, codes)

    @Synchronized
    fun getScheduledDndEnableCodes(): Set<Int> = getCodeSet(KEY_DND_ENABLE_CODES)

    @Synchronized
    fun saveScheduledDndEnableCodes(codes: Set<Int>) = saveCodeSet(KEY_DND_ENABLE_CODES, codes)

    @Synchronized
    fun getActiveDismissedKeys(nowMillis: Long): Set<String> =
        getValidTimestampedKeys(KEY_DISMISSED_ENTRIES, nowMillis)

    @Synchronized
    fun getActiveHandledDndKeys(nowMillis: Long): Set<String> =
        getValidTimestampedKeys(KEY_DND_HANDLED_KEYS, nowMillis)

    @Synchronized
    fun getActivePostedEntries(nowMillis: Long): List<ReminderDecisionEngine.PostedEntry> {
        return prefs.getStringSet(KEY_POSTED_ENTRIES, emptySet())
            .orEmpty()
            .mapNotNull { raw -> parsePostedEntry(raw) }
            .filter { it.expiresAtMillis > nowMillis }
    }

    @Synchronized
    fun getAllDndSessions(): List<ReminderDecisionEngine.DndSessionEntry> {
        return prefs.getStringSet(KEY_DND_ACTIVE_SESSIONS, emptySet())
            .orEmpty()
            .mapNotNull { raw -> parseDndSessionEntry(raw) }
    }

    @Synchronized
    fun markDismissed(key: String, expiresAtMillis: Long) =
        putTimestampedKey(KEY_DISMISSED_ENTRIES, key, expiresAtMillis)

    @Synchronized
    fun markDndHandled(key: String, expiresAtMillis: Long) =
        putTimestampedKey(KEY_DND_HANDLED_KEYS, key, expiresAtMillis)

    @Synchronized
    fun clearHandledDndKeys() {
        prefs.edit().remove(KEY_DND_HANDLED_KEYS).apply()
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
    fun markDndSessionActive(key: String, notificationId: Int, startMillis: Long, endMillis: Long) {
        if (key.isBlank()) return
        val existing = prefs.getStringSet(KEY_DND_ACTIVE_SESSIONS, emptySet())
            .orEmpty()
            .filterNot { raw ->
                val parsed = parseDndSessionEntry(raw)
                parsed == null || parsed.key == key || parsed.notificationId == notificationId
            }
            .toMutableSet()
        existing += "$key|$notificationId|$startMillis|$endMillis"
        prefs.edit().putStringSet(KEY_DND_ACTIVE_SESSIONS, existing).apply()
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
    fun removeDndSession(key: String) {
        if (key.isBlank()) return
        val updated = prefs.getStringSet(KEY_DND_ACTIVE_SESSIONS, emptySet())
            .orEmpty()
            .filterNot { parseDndSessionEntry(it)?.key == key }
            .toSet()
        prefs.edit().putStringSet(KEY_DND_ACTIVE_SESSIONS, updated).apply()
    }

    @Synchronized
    fun removeDndSessionByNotifId(notificationId: Int) {
        val updated = prefs.getStringSet(KEY_DND_ACTIVE_SESSIONS, emptySet())
            .orEmpty()
            .filterNot { parseDndSessionEntry(it)?.notificationId == notificationId }
            .toSet()
        prefs.edit().putStringSet(KEY_DND_ACTIVE_SESSIONS, updated).apply()
    }

    @Synchronized
    fun isManagingDnd(nowMillis: Long, graceMillis: Long = 30L * 60_000L): Boolean {
        val until = prefs.getLong(KEY_MANAGED_DND_UNTIL, 0L)
        return until > 0L && nowMillis <= until + graceMillis
    }

    @Synchronized
    fun extendManagedDndUntil(untilMillis: Long) {
        val current = prefs.getLong(KEY_MANAGED_DND_UNTIL, 0L)
        if (untilMillis > current) {
            prefs.edit().putLong(KEY_MANAGED_DND_UNTIL, untilMillis).apply()
        }
    }

    @Synchronized
    fun clearManagedDnd() {
        prefs.edit().remove(KEY_MANAGED_DND_UNTIL).apply()
    }

    @Synchronized
    fun pruneExpired(nowMillis: Long) {
        val validDismissed = filterValidTimestampedSet(KEY_DISMISSED_ENTRIES, nowMillis)
        val validHandledDnd = filterValidTimestampedSet(KEY_DND_HANDLED_KEYS, nowMillis)
        val validPosted = prefs.getStringSet(KEY_POSTED_ENTRIES, emptySet())
            .orEmpty()
            .filter { raw ->
                val parsed = parsePostedEntry(raw)
                parsed != null && parsed.expiresAtMillis > nowMillis
            }
            .toSet()
        prefs.edit()
            .putStringSet(KEY_DISMISSED_ENTRIES, validDismissed)
            .putStringSet(KEY_DND_HANDLED_KEYS, validHandledDnd)
            .putStringSet(KEY_POSTED_ENTRIES, validPosted)
            .apply()
    }

    private fun getCodeSet(prefKey: String): Set<Int> {
        return prefs.getStringSet(prefKey, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    private fun saveCodeSet(prefKey: String, codes: Set<Int>) {
        prefs.edit()
            .putStringSet(prefKey, codes.map { it.toString() }.toSet())
            .apply()
    }

    private fun getValidTimestampedKeys(prefKey: String, nowMillis: Long): Set<String> {
        return prefs.getStringSet(prefKey, emptySet())
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

    private fun putTimestampedKey(prefKey: String, key: String, expiresAtMillis: Long) {
        if (key.isBlank()) return
        val existing = prefs.getStringSet(prefKey, emptySet())
            .orEmpty()
            .filterNot { it.startsWith("$key|") }
            .toMutableSet()
        existing += "$key|$expiresAtMillis"
        prefs.edit().putStringSet(prefKey, existing).apply()
    }

    private fun filterValidTimestampedSet(prefKey: String, nowMillis: Long): Set<String> {
        return prefs.getStringSet(prefKey, emptySet())
            .orEmpty()
            .filter { raw ->
                val exp = raw.substringAfterLast('|', "").toLongOrNull() ?: 0L
                exp > nowMillis
            }
            .toSet()
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

    private fun parseDndSessionEntry(raw: String): ReminderDecisionEngine.DndSessionEntry? {
        val parts = raw.split('|')
        if (parts.size != 4) return null
        val key = parts[0]
        val notifId = parts[1].toIntOrNull() ?: return null
        val startMillis = parts[2].toLongOrNull() ?: return null
        val endMillis = parts[3].toLongOrNull() ?: return null
        return ReminderDecisionEngine.DndSessionEntry(key, notifId, startMillis, endMillis)
    }

    companion object {
        private const val KEY_SCHEDULED_CODES = "scheduled_codes"
        private const val KEY_DISMISSED_ENTRIES = "dismissed_entries"
        private const val KEY_POSTED_ENTRIES = "posted_entries"
        private const val KEY_DND_ENABLE_CODES = "dnd_enable_codes"
        private const val KEY_DND_HANDLED_KEYS = "dnd_handled_keys"
        private const val KEY_DND_ACTIVE_SESSIONS = "dnd_active_sessions"
        private const val KEY_MANAGED_DND_UNTIL = "managed_dnd_until"
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

    private val reconcileMutex = Mutex()
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
     * countdown reminders, schedules upcoming alarms with stable IDs, and reconciles class-time
     * auto-DND windows independently of reminder lead time.
     */
    suspend fun rescheduleAll(): Int = reconcileMutex.withLock {
        val nowMillis = System.currentTimeMillis()
        stateStore.pruneExpired(nowMillis)

        val activeId = scheduleRepository.ensureInitialized()
        val table = scheduleRepository.getTable(activeId) ?: return@withLock 0
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

        // 5. Reconcile class-time auto-DND independently from pre-class reminder lead time
        val dndWindows = ScheduleDateResolver.generateCourseDndWindows(
            table = table,
            timeScheme = scheme,
            courses = courses,
            adjustments = adjustments,
            settings = settings,
            now = nowDt,
            horizonDays = 7
        )
        val dndPlan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = dndWindows,
            previouslyScheduledEnableCodes = stateStore.getScheduledDndEnableCodes(),
            activeDndSessions = stateStore.getAllDndSessions(),
            handledStartKeys = stateStore.getActiveHandledDndKeys(nowMillis),
            nowMillis = nowMillis,
            maxAlarms = ReminderDecisionEngine.MAX_ALARMS
        )

        for (oldCode in dndPlan.enableCodesToCancel) {
            cancelDndEnableAlarm(oldCode)
        }
        for (aborted in dndPlan.sessionsToAbort) {
            cancelAutoRestoreDnd(aborted.notificationId)
            stateStore.removeDndSession(aborted.key)
        }
        if (!settings.autoMuteDuringClass) {
            stateStore.clearHandledDndKeys()
        }
        if (dndPlan.shouldRestoreDndNow) {
            val wasManaged = stateStore.isManagingDnd(nowMillis, graceMillis = 2L * 3600_000L)
            stateStore.clearManagedDnd()
            if (wasManaged && nm != null) {
                restoreDndFilterIfPriority(nm)
            }
        }
        for (updatedWindow in dndPlan.restoreAlarmsToUpdate) {
            val notifId = ReminderDecisionEngine.stableNotificationId(updatedWindow.id)
            val dndKey = ReminderDecisionEngine.dndStartKey(updatedWindow)
            stateStore.markDndSessionActive(
                key = dndKey,
                notificationId = notifId,
                startMillis = updatedWindow.startEpochMillis,
                endMillis = updatedWindow.endEpochMillis
            )
            stateStore.extendManagedDndUntil(updatedWindow.endEpochMillis)
            scheduleAutoRestoreDnd(updatedWindow.endEpochMillis, notifId, dndKey, exact)
        }
        if (settings.autoMuteDuringClass && nm != null) {
            for (window in dndPlan.ongoingToActivate) {
                val notifId = ReminderDecisionEngine.stableNotificationId(window.id)
                val dndKey = ReminderDecisionEngine.dndStartKey(window)
                activateCourseDndIfEligible(
                    nm = nm,
                    notificationId = notifId,
                    dndKey = dndKey,
                    startEpochMillis = window.startEpochMillis,
                    endEpochMillis = window.endEpochMillis,
                    nowMillis = nowMillis,
                    exact = exact
                )
            }
        }
        val newScheduledDndCodes = linkedSetOf<Int>()
        for (window in dndPlan.futureToSchedule) {
            val requestCode = ReminderDecisionEngine.stableNotificationId(window.id)
            val dndKey = ReminderDecisionEngine.dndStartKey(window)
            newScheduledDndCodes += requestCode
            scheduleAutoEnableDnd(
                startEpochMillis = window.startEpochMillis,
                endEpochMillis = window.endEpochMillis,
                notificationId = requestCode,
                dndKey = dndKey,
                exact = exact
            )
        }
        stateStore.saveScheduledDndEnableCodes(newScheduledDndCodes)

        // 6. Reconcile optional daytime foreground keep-alive guardian service
        val todayEndMillis = nowDt.toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val keepAlivePlan = ReminderDecisionEngine.computeKeepAlivePlan(
            items = upcoming,
            remindersEnabled = settings.enabled,
            keepAliveEnabled = settings.backgroundKeepAliveEnabled,
            nowMillis = nowMillis,
            todayEndMillis = todayEndMillis
        )
        syncKeepAliveService(keepAlivePlan, exact)

        return@withLock plan.futureItemsToSchedule.size + plan.itemsToPostImmediately.size
    }

    suspend fun disableKeepAliveFromNotification() = reconcileMutex.withLock {
        val current = prefs.getReminderSettingsOnce()
        if (current.backgroundKeepAliveEnabled) {
            prefs.updateReminderSettings(current.copy(backgroundKeepAliveEnabled = false))
        }
        ReminderKeepAliveService.stop(context)
        cancelKeepAliveStop()
        cancelKeepAliveWake()
    }

    private fun syncKeepAliveService(plan: ReminderDecisionEngine.KeepAlivePlan, exact: Boolean) {
        if (plan.shouldRunNow) {
            cancelKeepAliveWake()
            val summary = buildKeepAliveSummary(plan)
            ReminderKeepAliveService.startOrUpdate(context, summary)
            plan.stopAtMillis?.let { stopAt ->
                scheduleKeepAliveAutoStop(stopAt, exact)
            }
        } else {
            ReminderKeepAliveService.stop(context)
            cancelKeepAliveStop()
            val nextStart = plan.nextStartAtMillis
            if (nextStart != null) {
                scheduleKeepAliveWake(nextStart, exact)
            } else {
                cancelKeepAliveWake()
            }
        }
    }

    private fun buildKeepAliveSummary(plan: ReminderDecisionEngine.KeepAlivePlan): String {
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val nextPart = plan.nextItemTitle?.let { title ->
            val timeStr = plan.nextItemStartMillis?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(timeFmt)
            }
            if (timeStr != null) "下一项 $timeStr $title" else "下一项 $title"
        }
        val stopPart = plan.stopAtMillis?.let {
            val stopStr = Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(timeFmt)
            "${stopStr}自动停止"
        }
        return listOfNotNull(nextPart, stopPart)
            .joinToString(" · ")
            .ifBlank { "今日课表与流体云提醒守护中" }
    }

    private fun scheduleKeepAliveAutoStop(stopAtMillis: Long, exact: Boolean) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_KEEP_ALIVE_AUTO_STOP
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ReminderKeepAliveService.KEEP_ALIVE_STOP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmInternal(stopAtMillis, pi, exact)
    }

    private fun cancelKeepAliveStop() =
        cancelAlarmByAction(
            ReminderBroadcastReceiver.ACTION_KEEP_ALIVE_AUTO_STOP,
            ReminderKeepAliveService.KEEP_ALIVE_STOP_REQUEST_CODE
        )

    private fun scheduleKeepAliveWake(wakeAtMillis: Long, exact: Boolean) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_KEEP_ALIVE_WAKE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ReminderKeepAliveService.KEEP_ALIVE_WAKE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmInternal(wakeAtMillis, pi, exact)
    }

    private fun cancelKeepAliveWake() =
        cancelAlarmByAction(
            ReminderBroadcastReceiver.ACTION_KEEP_ALIVE_WAKE,
            ReminderKeepAliveService.KEEP_ALIVE_WAKE_REQUEST_CODE
        )

    suspend fun handleEnableDnd(
        notificationId: Int,
        dndKey: String,
        eventStartMillis: Long,
        eventEndMillis: Long
    ) = reconcileMutex.withLock {
        val settings = prefs.getReminderSettingsOnce()
        if (!settings.autoMuteDuringClass) return@withLock
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@withLock
        if (!nm.isNotificationPolicyAccessGranted) return@withLock

        val nowMillis = System.currentTimeMillis()
        if (eventEndMillis <= eventStartMillis || nowMillis >= eventEndMillis) return@withLock

        val effectiveEndMillis = if (dndKey.isNotBlank()) {
            val occurrenceId = dndKey.substringBefore('@')
            val windows = loadCourseDndWindows(settings = settings, horizonDays = 2)
            val matched = windows.firstOrNull {
                it.id == occurrenceId &&
                    it.startEpochMillis == eventStartMillis &&
                    it.endEpochMillis > nowMillis
            } ?: return@withLock
            matched.endEpochMillis
        } else {
            eventEndMillis
        }

        activateCourseDndIfEligible(
            nm = nm,
            notificationId = notificationId,
            dndKey = dndKey,
            startEpochMillis = eventStartMillis,
            endEpochMillis = effectiveEndMillis,
            nowMillis = nowMillis,
            exact = canScheduleExactAlarms()
        )
    }

    suspend fun handleRestoreDnd(
        notificationId: Int,
        dndKey: String,
        scheduledEndMillis: Long
    ) = reconcileMutex.withLock {
        if (dndKey.isNotBlank()) {
            stateStore.removeDndSession(dndKey)
        } else {
            stateStore.removeDndSessionByNotifId(notificationId)
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@withLock
        val nowMillis = System.currentTimeMillis()
        val settings = prefs.getReminderSettingsOnce()
        val windows = loadCourseDndWindows(settings = settings, horizonDays = 2)
        val handover = ReminderDecisionEngine.shouldKeepDndAtRestore(
            windows = windows,
            autoMuteEnabled = settings.autoMuteDuringClass,
            nowMillis = nowMillis,
            scheduledEndMillis = scheduledEndMillis
        )
        if (handover != null) {
            stateStore.extendManagedDndUntil(handover.endEpochMillis)
            return@withLock
        }

        val wasManaged = stateStore.isManagingDnd(nowMillis, graceMillis = 24L * 3600_000L)
        stateStore.clearManagedDnd()
        if (wasManaged || dndKey.isBlank()) {
            restoreDndFilterIfPriority(nm)
        }
    }

    private suspend fun loadCourseDndWindows(
        settings: ReminderSettings,
        horizonDays: Int
    ): List<CourseDndWindow> {
        if (!settings.autoMuteDuringClass) return emptyList()
        val activeId = scheduleRepository.ensureInitialized()
        val table = scheduleRepository.getTable(activeId) ?: return emptyList()
        val scheme = scheduleRepository.getTimeScheme(table.timeSchemeId)
            ?: DefaultTimeSchemes.standardCollege(table.timeSchemeId)
        val courses = scheduleRepository.getCourses(table.id)
        val adjustments = scheduleRepository.getHolidayAdjustments(table.id)
        return ScheduleDateResolver.generateCourseDndWindows(
            table = table,
            timeScheme = scheme,
            courses = courses,
            adjustments = adjustments,
            settings = settings,
            now = LocalDateTime.now(),
            horizonDays = horizonDays
        )
    }

    private fun activateCourseDndIfEligible(
        nm: NotificationManager,
        notificationId: Int,
        dndKey: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowMillis: Long,
        exact: Boolean
    ) {
        if (!nm.isNotificationPolicyAccessGranted) return
        val isNormalFilter = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val managingExisting = stateStore.isManagingDnd(nowMillis)
        if (!isNormalFilter && !managingExisting) {
            return
        }
        val activated = if (isNormalFilter) {
            runCatching {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                true
            }.getOrDefault(false)
        } else {
            true
        }
        if (activated) {
            val effectiveKey = dndKey.ifBlank { "dnd_$notificationId@$startEpochMillis" }
            stateStore.markDndHandled(effectiveKey, endEpochMillis + 60_000L)
            stateStore.markDndSessionActive(effectiveKey, notificationId, startEpochMillis, endEpochMillis)
            stateStore.extendManagedDndUntil(endEpochMillis)
            scheduleAutoRestoreDnd(endEpochMillis, notificationId, effectiveKey, exact)
        }
    }

    private fun restoreDndFilterIfPriority(nm: NotificationManager) {
        if (nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        ) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
    }

    private fun scheduleAutoEnableDnd(
        startEpochMillis: Long,
        endEpochMillis: Long,
        notificationId: Int,
        dndKey: String,
        exact: Boolean
    ) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_ENABLE_DND
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_START, startEpochMillis)
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_END, endEpochMillis)
            putExtra(ReminderBroadcastReceiver.EXTRA_OCCURRENCE_KEY, dndKey)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId + ReminderDecisionEngine.DND_ENABLE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmInternal(startEpochMillis, pi, exact)
    }

    fun scheduleAutoRestoreDnd(
        endEpochMillis: Long,
        notificationId: Int,
        dndKey: String = "",
        exact: Boolean = canScheduleExactAlarms()
    ) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_RESTORE_DND
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_END, endEpochMillis)
            putExtra(ReminderBroadcastReceiver.EXTRA_OCCURRENCE_KEY, dndKey)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId + ReminderDecisionEngine.DND_RESTORE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmInternal(endEpochMillis, pi, exact)
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

    fun cancelCountdownDismiss(notificationId: Int) =
        cancelAlarmByAction(
            ReminderBroadcastReceiver.ACTION_DISMISS_NOTIFICATION,
            notificationId + ReminderDecisionEngine.AUTO_DISMISS_OFFSET
        )

    private fun cancelTriggerAlarm(requestCode: Int) =
        cancelAlarmByAction(ReminderBroadcastReceiver.ACTION_TRIGGER_REMINDER, requestCode)

    private fun cancelDndEnableAlarm(requestCode: Int) =
        cancelAlarmByAction(
            ReminderBroadcastReceiver.ACTION_ENABLE_DND,
            requestCode + ReminderDecisionEngine.DND_ENABLE_OFFSET
        )

    private fun cancelAutoRestoreDnd(notificationId: Int) =
        cancelAlarmByAction(
            ReminderBroadcastReceiver.ACTION_RESTORE_DND,
            notificationId + ReminderDecisionEngine.DND_RESTORE_OFFSET
        )

    private fun cancelAlarmByAction(action: String, requestCode: Int) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            this.action = action
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
        val safeTriggerMillis = maxOf(triggerMillis, System.currentTimeMillis() + 1_000L)
        val showIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(safeTriggerMillis, showIntent), pi)
        }.onFailure {
            scheduleAlarmInternal(safeTriggerMillis, pi, exact = true)
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
