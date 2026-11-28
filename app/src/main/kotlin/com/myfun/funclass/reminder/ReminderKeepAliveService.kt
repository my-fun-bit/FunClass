package com.myfun.funclass.reminder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.myfun.funclass.FunClassApplication
import com.myfun.funclass.MainActivity
import com.myfun.funclass.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional daytime foreground guardian service that keeps the app process in foreground priority
 * while there are remaining reminders/classes today, listens for screen-unlock events to catch up
 * any OEM-dropped alarms, and automatically stops itself after today's last class/event ends.
 */
class ReminderKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastUnlockSyncMillis: Long = 0L

    private val screenUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action != Intent.ACTION_USER_PRESENT && action != Intent.ACTION_SCREEN_ON) return
            val now = System.currentTimeMillis()
            if (now - lastUnlockSyncMillis < UNLOCK_SYNC_MIN_INTERVAL_MILLIS) return
            lastUnlockSyncMillis = now
            serviceScope.launch {
                runCatching {
                    val ep = EntryPointAccessors.fromApplication(
                        applicationContext,
                        ReminderEntryPoint::class.java
                    )
                    ep.reminderScheduler().rescheduleAll()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenUnlockReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundSafely()
            stopSelf()
            return START_NOT_STICKY
        }

        val summary = intent?.getStringExtra(EXTRA_SUMMARY)
            ?.takeIf { it.isNotBlank() }
            ?: "今日课表与流体云提醒守护中，最后一节课后自动停止"

        val startedForeground = runCatching {
            val notification = buildNotification(summary)
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, KEEP_ALIVE_NOTIFICATION_ID, notification, serviceType)
            true
        }.getOrDefault(false)

        if (!startedForeground) {
            stopSelf()
            return START_NOT_STICKY
        }

        // If recreated by the system after memory pressure (intent == null), reconcile schedule state
        if (intent == null) {
            serviceScope.launch {
                runCatching {
                    val ep = EntryPointAccessors.fromApplication(
                        applicationContext,
                        ReminderEntryPoint::class.java
                    )
                    ep.reminderScheduler().rescheduleAll()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { unregisterReceiver(screenUnlockReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun buildNotification(summary: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this,
            KEEP_ALIVE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_KEEP_ALIVE_STOP
        }
        val stopPi = PendingIntent.getBroadcast(
            this,
            KEEP_ALIVE_STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FunClassApplication.CHANNEL_KEEP_ALIVE)
            .setSmallIcon(R.drawable.ic_stat_funclass)
            .setContentTitle("FunClass 提醒守护中")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(contentPi)
            .addAction(0, "关闭守护", stopPi)
            .build()
    }

    companion object {
        const val KEEP_ALIVE_NOTIFICATION_ID = 99_002
        const val KEEP_ALIVE_STOP_REQUEST_CODE = 99_101
        const val KEEP_ALIVE_WAKE_REQUEST_CODE = 99_102

        const val ACTION_START_OR_UPDATE = "com.myfun.funclass.ACTION_KEEP_ALIVE_START_OR_UPDATE"
        const val ACTION_STOP = "com.myfun.funclass.ACTION_KEEP_ALIVE_STOP"
        const val EXTRA_SUMMARY = "extra_summary"

        private const val UNLOCK_SYNC_MIN_INTERVAL_MILLIS = 60_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startOrUpdate(context: Context, summary: String): Boolean {
            val intent = Intent(context, ReminderKeepAliveService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_SUMMARY, summary)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ReminderKeepAliveService::class.java))
            }
        }
    }
}
