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
        manager.createNotificationChannels(listOf(courseChannel, calendarChannel))
    }

    companion object {
        const val CHANNEL_COURSE_REMINDER = "funclass_course_reminder"
        const val CHANNEL_CALENDAR_REMINDER = "funclass_calendar_reminder"
    }
}
