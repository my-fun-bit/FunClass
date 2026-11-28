package com.myfun.funclass.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reminderScheduler(): ReminderScheduler
}

private const val RECEIVER_WAKELOCK_TIMEOUT_MILLIS = 20_000L

private fun BroadcastReceiver.runAsyncWithWakeLock(
    context: Context,
    tag: String,
    block: suspend () -> Unit
) {
    val pending = goAsync()
    val pm = context.applicationContext.getSystemService(PowerManager::class.java)
    val wakeLock = runCatching {
        pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FunClass:$tag")?.apply {
            setReferenceCounted(false)
            acquire(RECEIVER_WAKELOCK_TIMEOUT_MILLIS)
        }
    }.getOrNull()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            runCatching { block() }
        } finally {
            runCatching {
                if (wakeLock != null && wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
            runCatching { pending.finish() }
        }
    }
}

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val scheduler = ep.reminderScheduler()

        when (intent.action) {
            ACTION_DISMISS_NOTIFICATION -> {
                val occKey = intent.getStringExtra(EXTRA_OCCURRENCE_KEY).orEmpty()
                FluidCloudNotifier.dismiss(context, notifId)
                scheduler.cancelCountdownDismiss(notifId)
                scheduler.stateStore.removePostedByNotifId(notifId)
                if (occKey.isNotBlank()) {
                    scheduler.stateStore.markDismissed(
                        key = occKey,
                        expiresAtMillis = System.currentTimeMillis() + 48L * 3600_000L
                    )
                }
            }

            ACTION_KEEP_ALIVE_STOP -> {
                ReminderKeepAliveService.stop(context.applicationContext)
                runAsyncWithWakeLock(context, "KeepAliveStop") {
                    scheduler.disableKeepAliveFromNotification()
                }
            }

            ACTION_KEEP_ALIVE_AUTO_STOP,
            ACTION_KEEP_ALIVE_WAKE,
            ACTION_RESCHEDULE_SYNC -> {
                runAsyncWithWakeLock(context, "RescheduleSync") {
                    scheduler.rescheduleAll()
                }
            }

            ACTION_ENABLE_DND -> {
                val eventStart = intent.getLongExtra(EXTRA_EVENT_START, System.currentTimeMillis())
                val eventEnd = intent.getLongExtra(EXTRA_EVENT_END, eventStart)
                val dndKey = intent.getStringExtra(EXTRA_OCCURRENCE_KEY).orEmpty()
                runAsyncWithWakeLock(context, "EnableDnd") {
                    scheduler.handleEnableDnd(
                        notificationId = notifId,
                        dndKey = dndKey,
                        eventStartMillis = eventStart,
                        eventEndMillis = eventEnd
                    )
                }
            }

            ACTION_RESTORE_DND -> {
                val eventEnd = intent.getLongExtra(EXTRA_EVENT_END, System.currentTimeMillis())
                val dndKey = intent.getStringExtra(EXTRA_OCCURRENCE_KEY).orEmpty()
                runAsyncWithWakeLock(context, "RestoreDnd") {
                    scheduler.handleRestoreDnd(
                        notificationId = notifId,
                        dndKey = dndKey,
                        scheduledEndMillis = eventEnd
                    )
                }
            }

            ACTION_TRIGGER_REMINDER -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "上课提醒"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
                val explicitLocation = intent.getStringExtra(EXTRA_LOCATION).orEmpty().trim()
                val isCourse = intent.getBooleanExtra(EXTRA_IS_COURSE, true)
                val eventStart = intent.getLongExtra(EXTRA_EVENT_START, System.currentTimeMillis())
                val countdown = intent.getBooleanExtra(EXTRA_COUNTDOWN, true)
                val autoDismissMin = intent.getIntExtra(EXTRA_AUTO_DISMISS_MIN, 10)
                val fluidCloud = intent.getBooleanExtra(EXTRA_FLUID_CLOUD, true)
                val fluidShowCountdown = intent.getBooleanExtra(EXTRA_FLUID_SHOW_COUNTDOWN, true)
                val occKey = intent.getStringExtra(EXTRA_OCCURRENCE_KEY).orEmpty()

                val resolvedLocation = explicitLocation.ifBlank {
                    subtitle.substringAfter("@", "").substringBefore(" ").trim()
                }
                val chipText = resolvedLocation.ifBlank { title.take(5) }
                val autoDismissAt = eventStart + autoDismissMin * 60_000L
                val result = FluidCloudNotifier.postLiveReminder(
                    context = context,
                    notificationId = notifId,
                    courseTitle = title,
                    subtitle = subtitle,
                    shortChipText = chipText,
                    isCourse = isCourse,
                    eventStartMillis = eventStart,
                    countdown = countdown,
                    useFluidCloud = fluidCloud,
                    showCountdownInChip = fluidShowCountdown,
                    location = resolvedLocation,
                    occurrenceKey = occKey
                )
                if (result.posted && occKey.isNotBlank()) {
                    scheduler.stateStore.markPosted(occKey, notifId, eventStart, autoDismissAt)
                }

                if ((countdown || fluidCloud) && autoDismissMin > 0) {
                    scheduler.scheduleCountdownDismiss(autoDismissAt, notifId, occKey)
                }

                // Chain-reschedule under a short WakeLock so CPU does not sleep mid-reconcile
                runAsyncWithWakeLock(context, "TriggerReminder") {
                    scheduler.rescheduleAll()
                }
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.myfun.funclass.ACTION_TRIGGER_REMINDER"
        const val ACTION_DISMISS_NOTIFICATION = "com.myfun.funclass.ACTION_DISMISS_NOTIFICATION"
        const val ACTION_ENABLE_DND = "com.myfun.funclass.ACTION_ENABLE_DND"
        const val ACTION_RESTORE_DND = "com.myfun.funclass.ACTION_RESTORE_DND"
        const val ACTION_RESCHEDULE_SYNC = "com.myfun.funclass.ACTION_RESCHEDULE_SYNC"
        const val ACTION_KEEP_ALIVE_STOP = "com.myfun.funclass.ACTION_KEEP_ALIVE_STOP"
        const val ACTION_KEEP_ALIVE_AUTO_STOP = "com.myfun.funclass.ACTION_KEEP_ALIVE_AUTO_STOP"
        const val ACTION_KEEP_ALIVE_WAKE = "com.myfun.funclass.ACTION_KEEP_ALIVE_WAKE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_IS_COURSE = "extra_is_course"
        const val EXTRA_EVENT_START = "extra_event_start"
        const val EXTRA_EVENT_END = "extra_event_end"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_AUTO_DISMISS_MIN = "extra_auto_dismiss_min"
        const val EXTRA_FLUID_CLOUD = "extra_fluid_cloud"
        const val EXTRA_FLUID_SHOW_COUNTDOWN = "extra_fluid_show_countdown"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_OCCURRENCE_KEY = "extra_occurrence_key"
    }
}

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderRescheduleWorker.enqueuePeriodic(context)
        runAsyncWithWakeLock(context, "BootReschedule") {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
            ep.reminderScheduler().rescheduleAll()
        }
    }
}
