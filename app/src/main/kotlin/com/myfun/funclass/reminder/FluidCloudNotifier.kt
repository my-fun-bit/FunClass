package com.myfun.funclass.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.myfun.funclass.FunClassApplication
import com.myfun.funclass.MainActivity
import com.myfun.funclass.R

/**
 * Android 16+ Live Updates (Promoted Ongoing) & legacy countdown notification engine.
 *
 * Works across Android 16+ devices supporting standard Live Updates notifications and falls back cleanly on Android 8-15.
 */
object FluidCloudNotifier {

    const val TEST_NOTIFICATION_ID = 99_001

    data class FluidCloudPostResult(
        val posted: Boolean,
        val isAndroid16Plus: Boolean,
        val canPostPromoted: Boolean,
        val hasPromotableCharacteristics: Boolean,
        val message: String
    )

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isLiveUpdatesSupported(): Boolean = Build.VERSION.SDK_INT >= 36

    fun canPostFluidCloud(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canPostPromotedNotifications()
    }

    fun openFluidCloudPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val appDetails = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(appDetails) }
        }
    }

    @SuppressLint("MissingPermission")
    fun postLiveReminder(
        context: Context,
        notificationId: Int,
        courseTitle: String,
        subtitle: String,
        shortChipText: String,
        isCourse: Boolean,
        eventStartMillis: Long,
        countdown: Boolean,
        useFluidCloud: Boolean,
        showCountdownInChip: Boolean,
        location: String = "",
        occurrenceKey: String = ""
    ): FluidCloudPostResult {
        if (!hasNotificationPermission(context)) {
            return FluidCloudPostResult(
                posted = false,
                isAndroid16Plus = isLiveUpdatesSupported(),
                canPostPromoted = canPostFluidCloud(context),
                hasPromotableCharacteristics = false,
                message = "未授予系统通知权限，请先允许通知"
            )
        }

        val nm = context.getSystemService(NotificationManager::class.java)
            ?: return FluidCloudPostResult(false, isLiveUpdatesSupported(), false, false, "无法获取通知服务")

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderBroadcastReceiver.EXTRA_OCCURRENCE_KEY, occurrenceKey)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            notificationId + 30_000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (isCourse) {
            FunClassApplication.CHANNEL_COURSE_REMINDER
        } else {
            FunClassApplication.CHANNEL_CALENDAR_REMINDER
        }

        val cleanLocation = location.trim()
        val titleText = when {
            cleanLocation.isNotBlank() -> "$courseTitle · $cleanLocation"
            isCourse -> "即将上课：$courseTitle"
            else -> "日程提醒：$courseTitle"
        }
        val cleanedSubtitle = subtitle.trim()
        val bodyText = when {
            cleanLocation.isNotBlank() && cleanedSubtitle.isBlank() -> "上课地点：$cleanLocation"
            cleanLocation.isNotBlank() && !cleanedSubtitle.contains(cleanLocation) -> "地点：$cleanLocation · $cleanedSubtitle"
            cleanedSubtitle.isNotBlank() -> cleanedSubtitle
            else -> "请提前做好准备"
        }
        val expandedText = buildString {
            append(courseTitle)
            if (cleanLocation.isNotBlank()) {
                append("\n上课地点：")
                append(cleanLocation)
            }
            if (cleanedSubtitle.isNotBlank()) {
                append("\n")
                append(cleanedSubtitle)
            }
        }
        val effectiveChipText = cleanLocation.ifBlank { shortChipText.trim() }.ifBlank { courseTitle.trim() }.take(7)

        if (Build.VERSION.SDK_INT >= 36 && useFluidCloud) {
            val builder = Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_funclass)
                .setContentTitle(titleText)
                .setContentText(bodyText)
                .setStyle(Notification.BigTextStyle().bigText(expandedText))
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPi)
                .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                        "关闭提醒",
                        dismissPi
                    ).build()
                )

            if (cleanLocation.isNotBlank()) {
                builder.setSubText(cleanLocation)
            }

            val isFuture = eventStartMillis > System.currentTimeMillis()
            if (countdown && isFuture) {
                builder
                    .setWhen(eventStartMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
            if (!showCountdownInChip && effectiveChipText.isNotBlank()) {
                builder.setShortCriticalText(effectiveChipText)
            } else if (!isFuture && effectiveChipText.isNotBlank()) {
                builder.setShortCriticalText(effectiveChipText)
            }

            val notification = builder.build()
            val promotable = notification.hasPromotableCharacteristics()
            val canPromote = nm.canPostPromotedNotifications()
            nm.notify(notificationId, notification)

            val statusMsg = when {
                promotable && canPromote -> "已发送实时活动胶囊（Android 16+ Live Updates）"
                promotable && !canPromote -> "已发送通知，请在系统设置开启本应用的「实时更新 / 实况通知」权限以显示状态栏胶囊"
                else -> "通知已发送（hasPromotableCharacteristics=false）"
            }
            return FluidCloudPostResult(
                posted = true,
                isAndroid16Plus = true,
                canPostPromoted = canPromote,
                hasPromotableCharacteristics = promotable,
                message = statusMsg
            )
        }

        // Fallback for Android 8..15 (API 26..35)
        val compatBuilder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_funclass)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(useFluidCloud && countdown)
            .setAutoCancel(!(useFluidCloud && countdown))
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭提醒", dismissPi)

        if (cleanLocation.isNotBlank()) {
            compatBuilder.setSubText(cleanLocation)
        }

        if (countdown && eventStartMillis > System.currentTimeMillis()) {
            compatBuilder
                .setWhen(eventStartMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, compatBuilder.build())
        return FluidCloudPostResult(
            posted = true,
            isAndroid16Plus = false,
            canPostPromoted = false,
            hasPromotableCharacteristics = false,
            message = "当前系统低于 Android 16，已自动降级为倒计时通知"
        )
    }

    fun dismiss(context: Context, notificationId: Int) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(notificationId)
    }
}
