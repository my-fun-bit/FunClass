package com.myfun.funclass.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
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

            ACTION_RESTORE_DND -> {
                if (nm.isNotificationPolicyAccessGranted) {
                    runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                }
            }

            ACTION_TRIGGER_REMINDER -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "上课提醒"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
                val explicitLocation = intent.getStringExtra(EXTRA_LOCATION).orEmpty().trim()
                val isCourse = intent.getBooleanExtra(EXTRA_IS_COURSE, true)
                val eventStart = intent.getLongExtra(EXTRA_EVENT_START, System.currentTimeMillis())
                val eventEnd = intent.getLongExtra(EXTRA_EVENT_END, eventStart + 45 * 60_000L)
                val countdown = intent.getBooleanExtra(EXTRA_COUNTDOWN, true)
                val autoDismissMin = intent.getIntExtra(EXTRA_AUTO_DISMISS_MIN, 10)
                val autoMute = intent.getBooleanExtra(EXTRA_AUTO_MUTE, false)
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

                if (autoMute && nm.isNotificationPolicyAccessGranted) {
                    runCatching {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        scheduler.scheduleAutoRestoreDnd(eventEnd, notifId)
                    }
                }

                // Chain-reschedule subsequent days so reminders never dry up without opening the app
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        scheduler.rescheduleAll()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.myfun.funclass.ACTION_TRIGGER_REMINDER"
        const val ACTION_DISMISS_NOTIFICATION = "com.myfun.funclass.ACTION_DISMISS_NOTIFICATION"
        const val ACTION_RESTORE_DND = "com.myfun.funclass.ACTION_RESTORE_DND"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_IS_COURSE = "extra_is_course"
        const val EXTRA_EVENT_START = "extra_event_start"
        const val EXTRA_EVENT_END = "extra_event_end"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_AUTO_DISMISS_MIN = "extra_auto_dismiss_min"
        const val EXTRA_AUTO_MUTE = "extra_auto_mute"
        const val EXTRA_FLUID_CLOUD = "extra_fluid_cloud"
        const val EXTRA_FLUID_SHOW_COUNTDOWN = "extra_fluid_show_countdown"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_OCCURRENCE_KEY = "extra_occurrence_key"
    }
}

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderRescheduleWorker.enqueuePeriodic(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
                ep.reminderScheduler().rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
