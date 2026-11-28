package com.myfun.funclass

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.myfun.funclass.reminder.ReminderRescheduleWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FunClassApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        ReminderRescheduleWorker.enqueuePeriodic(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val courseChannel = NotificationChannel(
            CHANNEL_COURSE_REMINDER,
            "上课提醒与倒计时",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课前提醒通知及倒计时"
            enableVibration(true)
        }
        val calendarChannel = NotificationChannel(
            CHANNEL_CALENDAR_REMINDER,
            "日程与待办提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "个人日程与待办提醒通知"
            enableVibration(true)
        }
        val keepAliveChannel = NotificationChannel(
            CHANNEL_KEEP_ALIVE,
            "后台提醒守护（静默）",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "开启后台常驻防杀时用于保障课前提醒与流体云稳定触发，当日课程结束后自动关闭"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(courseChannel, calendarChannel, keepAliveChannel))
    }

    companion object {
        const val CHANNEL_COURSE_REMINDER = "funclass_course_reminder"
        const val CHANNEL_CALENDAR_REMINDER = "funclass_calendar_reminder"
        const val CHANNEL_KEEP_ALIVE = "funclass_keep_alive"
    }
}
