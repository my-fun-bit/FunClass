package com.myfun.funclass

import com.myfun.funclass.core.model.AlarmItemType
import com.myfun.funclass.core.model.ScheduledAlarmItem
import com.myfun.funclass.reminder.FluidCloudNotifier
import com.myfun.funclass.reminder.ReminderDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderStateLogicTest {

    private fun mkItem(
        id: String,
        trigger: Long,
        start: Long,
        isActiveWindow: Boolean
    ) = ScheduledAlarmItem(
        id = id,
        type = AlarmItemType.CALENDAR,
        title = "测试日程",
        subtitle = "教A-101",
        location = "教A-101",
        triggerEpochMillis = trigger,
        eventStartEpochMillis = start,
        eventEndEpochMillis = start + 3600_000L,
        countdownEnabled = true,
        autoDismissMinutes = 10,
        isActiveCountdownWindow = isActiveWindow
    )

    @Test
    fun stableNotificationId_usesDisjointBandFromOffsetsAndTestId() {
        val ids = (1..200).map { ReminderDecisionEngine.stableNotificationId("calendar_${it}_2026-11-21") }
        for (id in ids) {
            assertTrue(id in 10_000 until 30_000)
            assertNotEquals(FluidCloudNotifier.TEST_NOTIFICATION_ID, id)
            assertFalse((id + ReminderDecisionEngine.DISMISS_ACTION_OFFSET) in 10_000 until 30_000)
            assertFalse((id + ReminderDecisionEngine.AUTO_DISMISS_OFFSET) in 40_000 until 60_000)
            assertFalse((id + ReminderDecisionEngine.DND_RESTORE_OFFSET) in 70_000 until 90_000)
        }
    }

    @Test
    fun reconcilePlan_postsNewActiveWindowItemAndSuppressesAfterDismissal() {
        val now = 1_000_000L
        val activeItem = mkItem(
            id = "calendar_10_2026-11-21",
            trigger = now - 300_000L,
            start = now + 600_000L,
            isActiveWindow = true
        )

        // 1. Newly created inside countdown window -> immediately post
        val plan1 = ReminderDecisionEngine.computeReconcilePlan(
            items = listOf(activeItem),
            previouslyScheduledCodes = emptySet(),
            previouslyPosted = emptyList(),
            dismissedKeys = emptySet(),
            validTodayOccurrenceIds = setOf(activeItem.id),
            nowMillis = now
        )
        assertEquals(listOf(activeItem), plan1.itemsToPostImmediately)
        assertTrue(plan1.activeNotifIdsToDismiss.isEmpty())

        // 2. User dismissed notification -> reopen/reconcile must NOT re-post
        val occKey = ReminderDecisionEngine.occurrenceKey(activeItem)
        val plan2 = ReminderDecisionEngine.computeReconcilePlan(
            items = listOf(activeItem),
            previouslyScheduledCodes = emptySet(),
            previouslyPosted = emptyList(),
            dismissedKeys = setOf(occKey),
            validTodayOccurrenceIds = setOf(activeItem.id),
            nowMillis = now + 10_000L
        )
        assertTrue(plan2.itemsToPostImmediately.isEmpty())
    }

    @Test
    fun reconcilePlan_dismissesDeletedEventAndPostsRecreatedSameTimeEvent() {
        val now = 1_000_000L
        val oldItem = mkItem(
            id = "calendar_10_2026-11-21",
            trigger = now - 300_000L,
            start = now + 600_000L,
            isActiveWindow = true
        )
        val oldNotifId = ReminderDecisionEngine.stableNotificationId(oldItem.id)
        val oldOccKey = ReminderDecisionEngine.occurrenceKey(oldItem)

        // User deleted oldItem (id=10) and created newItem (id=11) at exact same time
        val newItem = mkItem(
            id = "calendar_11_2026-11-21",
            trigger = now - 300_000L,
            start = now + 600_000L,
            isActiveWindow = true
        )

        val plan = ReminderDecisionEngine.computeReconcilePlan(
            items = listOf(newItem),
            previouslyScheduledCodes = emptySet(),
            previouslyPosted = listOf(
                ReminderDecisionEngine.PostedEntry(
                    key = oldOccKey,
                    notificationId = oldNotifId,
                    eventStartMillis = oldItem.eventStartEpochMillis,
                    expiresAtMillis = oldItem.eventStartEpochMillis + 600_000L
                )
            ),
            dismissedKeys = setOf(oldOccKey),
            validTodayOccurrenceIds = setOf(newItem.id),
            nowMillis = now
        )

        assertTrue(oldNotifId in plan.activeNotifIdsToDismiss)
        assertEquals(listOf(newItem), plan.itemsToPostImmediately)
    }

    @Test
    fun reconcilePlan_cancelsDeletedFutureAlarmAndKeepsZeroLeadNotificationUntilDeleted() {
        val now = 1_000_000L
        val deletedFutureCode = ReminderDecisionEngine.stableNotificationId("calendar_55_2026-11-22")
        val keptFuture = mkItem(
            id = "calendar_56_2026-11-22",
            trigger = now + 3_600_000L,
            start = now + 4_500_000L,
            isActiveWindow = false
        )
        val keptFutureCode = ReminderDecisionEngine.stableNotificationId(keptFuture.id)

        // Zero-lead notification that just started 30 seconds ago (still valid today)
        val ongoingStartId = "calendar_57_2026-11-21"
        val ongoingNotifId = ReminderDecisionEngine.stableNotificationId(ongoingStartId)
        val ongoingEntry = ReminderDecisionEngine.PostedEntry(
            key = "$ongoingStartId@${now - 30_000L}",
            notificationId = ongoingNotifId,
            eventStartMillis = now - 30_000L,
            expiresAtMillis = now + 570_000L
        )

        val planKeep = ReminderDecisionEngine.computeReconcilePlan(
            items = listOf(keptFuture),
            previouslyScheduledCodes = setOf(deletedFutureCode, keptFutureCode),
            previouslyPosted = listOf(ongoingEntry),
            dismissedKeys = emptySet(),
            validTodayOccurrenceIds = setOf(ongoingStartId),
            nowMillis = now
        )
        assertEquals(setOf(deletedFutureCode), planKeep.alarmCodesToCancel)
        assertTrue(planKeep.activeNotifIdsToDismiss.isEmpty())

        // Once ongoingStartId is deleted/completed, its active notification must be dismissed
        val planDelete = ReminderDecisionEngine.computeReconcilePlan(
            items = listOf(keptFuture),
            previouslyScheduledCodes = setOf(keptFutureCode),
            previouslyPosted = listOf(ongoingEntry),
            dismissedKeys = emptySet(),
            validTodayOccurrenceIds = emptySet(),
            nowMillis = now
        )
        assertEquals(setOf(ongoingNotifId), planDelete.activeNotifIdsToDismiss)
    }
}
