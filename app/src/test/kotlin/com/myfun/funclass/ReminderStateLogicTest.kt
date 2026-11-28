package com.myfun.funclass

import com.myfun.funclass.core.model.AlarmItemType
import com.myfun.funclass.core.model.CourseDndWindow
import com.myfun.funclass.core.model.ScheduledAlarmItem
import com.myfun.funclass.reminder.FluidCloudNotifier
import com.myfun.funclass.reminder.ReminderDecisionEngine
import com.myfun.funclass.reminder.ReminderKeepAliveService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
            assertTrue((id + ReminderDecisionEngine.DND_ENABLE_OFFSET) in 130_000 until 150_000)
            assertFalse((id + ReminderDecisionEngine.DND_ENABLE_OFFSET) in 100_000 until 120_000)
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

    @Test
    fun dndReconcilePlan_schedulesAtClassStartEvenInsideReminderLeadWindow() {
        val now = 1_000_000L
        val classStart = now + 15 * 60_000L // reminder already fired 15m ago (30m lead)
        val classEnd = classStart + 100 * 60_000L
        val window = CourseDndWindow(
            id = "course_1_10_2026-11-28",
            title = "高等数学",
            startEpochMillis = classStart,
            endEpochMillis = classEnd
        )

        val plan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = listOf(window),
            previouslyScheduledEnableCodes = emptySet(),
            activeDndSessions = emptyList(),
            handledStartKeys = emptySet(),
            nowMillis = now
        )

        assertTrue(plan.ongoingToActivate.isEmpty())
        assertEquals(listOf(window), plan.futureToSchedule)
        assertFalse(plan.shouldRestoreDndNow)
    }

    @Test
    fun dndReconcilePlan_activatesOngoingOnceAndSuppressesRepeatAfterManualUnmute() {
        val classStart = 1_000_000L
        val now = classStart + 10 * 60_000L
        val classEnd = classStart + 100 * 60_000L
        val window = CourseDndWindow(
            id = "course_1_10_2026-11-28",
            title = "操作系统",
            startEpochMillis = classStart,
            endEpochMillis = classEnd
        )

        val initialPlan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = listOf(window),
            previouslyScheduledEnableCodes = setOf(ReminderDecisionEngine.stableNotificationId(window.id)),
            activeDndSessions = emptyList(),
            handledStartKeys = emptySet(),
            nowMillis = now
        )
        assertEquals(listOf(window), initialPlan.ongoingToActivate)
        assertTrue(initialPlan.futureToSchedule.isEmpty())
        assertEquals(
            setOf(ReminderDecisionEngine.stableNotificationId(window.id)),
            initialPlan.enableCodesToCancel
        )

        // User manually turned off DND mid-class, then reopened app -> must NOT re-mute
        val handledKey = ReminderDecisionEngine.dndStartKey(window)
        val reopenPlan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = listOf(window),
            previouslyScheduledEnableCodes = emptySet(),
            activeDndSessions = listOf(
                ReminderDecisionEngine.DndSessionEntry(
                    key = handledKey,
                    notificationId = ReminderDecisionEngine.stableNotificationId(window.id),
                    startEpochMillis = classStart,
                    endEpochMillis = classEnd
                )
            ),
            handledStartKeys = setOf(handledKey),
            nowMillis = now + 60_000L
        )
        assertTrue(reopenPlan.ongoingToActivate.isEmpty())
        assertTrue(reopenPlan.sessionsToAbort.isEmpty())
        assertFalse(reopenPlan.shouldRestoreDndNow)
    }

    @Test
    fun dndReconcilePlan_abortsDeletedOrUpdatedSessionsSafely() {
        val start1 = 1_000_000L
        val end1 = start1 + 90 * 60_000L
        val now = start1 + 20 * 60_000L
        val w1 = CourseDndWindow("course_1_10_2026-11-28", "大学物理", start1, end1)
        val s1 = ReminderDecisionEngine.DndSessionEntry(
            key = ReminderDecisionEngine.dndStartKey(w1),
            notificationId = ReminderDecisionEngine.stableNotificationId(w1.id),
            startEpochMillis = start1,
            endEpochMillis = end1
        )

        // 1. Extended end time while in class -> update restore alarm, do not abort or re-mute
        val w1Extended = w1.copy(endEpochMillis = end1 + 30 * 60_000L)
        val extendPlan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = listOf(w1Extended),
            previouslyScheduledEnableCodes = emptySet(),
            activeDndSessions = listOf(s1),
            handledStartKeys = setOf(s1.key),
            nowMillis = now
        )
        assertEquals(listOf(w1Extended), extendPlan.restoreAlarmsToUpdate)
        assertTrue(extendPlan.sessionsToAbort.isEmpty())
        assertFalse(extendPlan.shouldRestoreDndNow)

        // 2. Deleted while overlapping course w2 is still active -> abort s1, keep DND
        val w2 = CourseDndWindow("course_2_20_2026-11-28", "实验课", start1 + 10 * 60_000L, end1 + 40 * 60_000L)
        val overlapAbortPlan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = listOf(w2),
            previouslyScheduledEnableCodes = emptySet(),
            activeDndSessions = listOf(s1),
            handledStartKeys = setOf(s1.key, ReminderDecisionEngine.dndStartKey(w2)),
            nowMillis = now
        )
        assertEquals(listOf(s1), overlapAbortPlan.sessionsToAbort)
        assertFalse(overlapAbortPlan.shouldRestoreDndNow)

        // 3. Deleted or autoMute disabled with no remaining ongoing course -> restore immediately
        val fullAbortPlan = ReminderDecisionEngine.computeDndReconcilePlan(
            windows = emptyList(),
            previouslyScheduledEnableCodes = emptySet(),
            activeDndSessions = listOf(s1),
            handledStartKeys = setOf(s1.key),
            nowMillis = now
        )
        assertEquals(listOf(s1), fullAbortPlan.sessionsToAbort)
        assertTrue(fullAbortPlan.shouldRestoreDndNow)
    }

    @Test
    fun shouldKeepDndAtRestore_handlesBackToBackOverlappingAndBreaks() {
        val c1Start = 1_000_000L
        val c1End = c1Start + 90 * 60_000L
        val c1 = CourseDndWindow("c1", "第一节", c1Start, c1End)

        // Single course ending -> restore
        assertNull(
            ReminderDecisionEngine.shouldKeepDndAtRestore(
                windows = listOf(c1),
                autoMuteEnabled = true,
                nowMillis = c1End,
                scheduledEndMillis = c1End
            )
        )

        // Back-to-back class starting exactly at c1End -> keep DND even if restore fires 500ms early
        val c2BackToBack = CourseDndWindow("c2", "第二节", c1End, c1End + 90 * 60_000L)
        assertEquals(
            c2BackToBack,
            ReminderDecisionEngine.shouldKeepDndAtRestore(
                windows = listOf(c1, c2BackToBack),
                autoMuteEnabled = true,
                nowMillis = c1End - 500L,
                scheduledEndMillis = c1End
            )
        )

        // Overlapping class -> keep DND
        val c2Overlap = CourseDndWindow("c2", "重叠课", c1Start + 30 * 60_000L, c1End + 30 * 60_000L)
        assertEquals(
            c2Overlap,
            ReminderDecisionEngine.shouldKeepDndAtRestore(
                windows = listOf(c1, c2Overlap),
                autoMuteEnabled = true,
                nowMillis = c1End + 500L,
                scheduledEndMillis = c1End
            )
        )

        // 10-minute break -> restore DND during break
        val c2Break = CourseDndWindow("c2", "课间后", c1End + 10 * 60_000L, c1End + 100 * 60_000L)
        assertNull(
            ReminderDecisionEngine.shouldKeepDndAtRestore(
                windows = listOf(c1, c2Break),
                autoMuteEnabled = true,
                nowMillis = c1End,
                scheduledEndMillis = c1End
            )
        )

        // Auto-mute turned off -> restore DND
        assertNull(
            ReminderDecisionEngine.shouldKeepDndAtRestore(
                windows = listOf(c1, c2BackToBack),
                autoMuteEnabled = false,
                nowMillis = c1End,
                scheduledEndMillis = c1End
            )
        )
    }

    @Test
    fun keepAliveIds_doNotCollideWithReminderOrTestIds() {
        val reserved = setOf(
            ReminderKeepAliveService.KEEP_ALIVE_NOTIFICATION_ID,
            ReminderKeepAliveService.KEEP_ALIVE_STOP_REQUEST_CODE,
            ReminderKeepAliveService.KEEP_ALIVE_WAKE_REQUEST_CODE
        )
        assertEquals(3, reserved.size)
        assertFalse(FluidCloudNotifier.TEST_NOTIFICATION_ID in reserved)
        val sampleIds = (1..200).map { ReminderDecisionEngine.stableNotificationId("course_${it}_1_2026-11-28") }
        for (id in sampleIds) {
            assertFalse(id in reserved)
            assertFalse((id + ReminderDecisionEngine.DISMISS_ACTION_OFFSET) in reserved)
            assertFalse((id + ReminderDecisionEngine.AUTO_DISMISS_OFFSET) in reserved)
            assertFalse((id + ReminderDecisionEngine.DND_RESTORE_OFFSET) in reserved)
            assertFalse((id + ReminderDecisionEngine.DND_ENABLE_OFFSET) in reserved)
        }
    }

    @Test
    fun keepAlivePlan_runsOnlyDuringActiveDayAndSchedulesNextDayWake() {
        val now = 10_000_000L
        val todayEnd = now + 8 * 3600_000L
        val todayEarly = mkItem(
            id = "course_1_1_2026-11-28",
            trigger = now + 15 * 60_000L,
            start = now + 30 * 60_000L,
            isActiveWindow = false
        ).copy(title = "线性代数", eventEndEpochMillis = now + 120 * 60_000L)
        val todayLate = mkItem(
            id = "course_2_2_2026-11-28",
            trigger = now + 3 * 3600_000L,
            start = now + 3 * 3600_000L + 15 * 60_000L,
            isActiveWindow = false
        ).copy(title = "数据结构", eventEndEpochMillis = now + 5 * 3600_000L)
        val tomorrowFirst = mkItem(
            id = "course_3_1_2026-11-29",
            trigger = todayEnd + 8 * 3600_000L,
            start = todayEnd + 8 * 3600_000L + 15 * 60_000L,
            isActiveWindow = false
        ).copy(title = "计算机网络", eventEndEpochMillis = todayEnd + 10 * 3600_000L)

        // 1. Disabled -> never runs
        val disabledPlan = ReminderDecisionEngine.computeKeepAlivePlan(
            items = listOf(todayEarly, todayLate, tomorrowFirst),
            remindersEnabled = true,
            keepAliveEnabled = false,
            nowMillis = now,
            todayEndMillis = todayEnd
        )
        assertFalse(disabledPlan.shouldRunNow)
        assertNull(disabledPlan.stopAtMillis)
        assertNull(disabledPlan.nextStartAtMillis)

        // 2. Today still has classes -> run now and stop after last class of today
        val activeDayPlan = ReminderDecisionEngine.computeKeepAlivePlan(
            items = listOf(todayEarly, todayLate, tomorrowFirst),
            remindersEnabled = true,
            keepAliveEnabled = true,
            nowMillis = now,
            todayEndMillis = todayEnd
        )
        assertTrue(activeDayPlan.shouldRunNow)
        assertEquals(todayLate.eventEndEpochMillis, activeDayPlan.stopAtMillis)
        assertEquals("线性代数", activeDayPlan.nextItemTitle)
        assertNull(activeDayPlan.nextStartAtMillis)

        // 3. After today's last class -> stop now and schedule next-day pre-start wake
        val eveningNow = todayLate.eventEndEpochMillis + 60_000L
        val eveningPlan = ReminderDecisionEngine.computeKeepAlivePlan(
            items = listOf(tomorrowFirst),
            remindersEnabled = true,
            keepAliveEnabled = true,
            nowMillis = eveningNow,
            todayEndMillis = todayEnd
        )
        assertFalse(eveningPlan.shouldRunNow)
        assertNull(eveningPlan.stopAtMillis)
        assertEquals(
            tomorrowFirst.triggerEpochMillis - ReminderDecisionEngine.KEEP_ALIVE_PRE_START_MILLIS,
            eveningPlan.nextStartAtMillis
        )
        assertEquals("计算机网络", eveningPlan.nextItemTitle)

        // 4. No upcoming items -> stay idle with no wake alarm
        val emptyPlan = ReminderDecisionEngine.computeKeepAlivePlan(
            items = emptyList(),
            remindersEnabled = true,
            keepAliveEnabled = true,
            nowMillis = eveningNow,
            todayEndMillis = todayEnd
        )
        assertFalse(emptyPlan.shouldRunNow)
        assertNull(emptyPlan.nextStartAtMillis)
    }
}
