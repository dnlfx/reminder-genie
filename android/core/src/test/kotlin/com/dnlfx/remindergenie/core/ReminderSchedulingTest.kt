package com.dnlfx.remindergenie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The whole-app reminder schedule: ordering, the alarm cap, pausing and badge counts. */
class ReminderSchedulingTest {
    private var nextId = 0

    private fun input(
        start: Instant,
        every: Int,
        leadDays: Int = 1,
        paused: Boolean = false,
        snoozedUntil: Instant? = null,
        followUps: Boolean = true,
    ) = ReminderInput(
        id = "item-${nextId++}",
        schedule = Schedule(start, every, IntervalUnit.DAYS, LA),
        hour = 9,
        minute = 0,
        leadDays = leadDays,
        remindDailyWhenOverdue = followUps,
        snoozedUntil = snoozedUntil,
        isPaused = paused,
    )

    @Test fun remindersComeSoonestFirstAcrossItems() {
        val now = date(2026, 10, 1)
        val later = input(now, 20)
        val sooner = input(now, 5)
        val result = ReminderPlanner.schedule(listOf(later, sooner), now)
        assertEquals(result.map { it.reminder.fireDate }.sorted(), result.map { it.reminder.fireDate })
        assertEquals(sooner.id, result.first().itemId)
    }

    @Test fun pausedItemsGetNoReminders() {
        val now = date(2026, 10, 1)
        val paused = input(now, 5, paused = true)
        assertTrue(ReminderPlanner.schedule(listOf(paused), now).isEmpty())
        assertTrue(ReminderPlanner.plan(paused, now).isEmpty())
    }

    @Test fun staysUnderTheLimitKeepingTheSoonest() {
        val now = date(2026, 10, 1)
        // 20 items × 5 reminders = 100, over a limit of 60.
        val inputs = (1..20).map { input(now, it + 2) }
        val result = ReminderPlanner.schedule(inputs, now, limit = 60)
        assertEquals(60, result.size)

        val everything = inputs.flatMap { ReminderPlanner.plan(it, now) }.map { it.fireDate }.sorted()
        assertEquals(everything[59], result.last().reminder.fireDate)
    }

    @Test fun eachReminderCarriesTheBadgeForWhenItArrives() {
        val now = date(2026, 10, 1)
        val first = input(now, 3, leadDays = 0) // due Oct 4
        val second = input(now, 6, leadDays = 0) // due Oct 7
        val result = ReminderPlanner.schedule(listOf(first, second), now)

        fun badge(id: String, suffix: String) =
            result.first { it.itemId == id && it.reminder.identifierSuffix == suffix }.badge
        assertEquals(1, badge(first.id, "due")) // only the first is due on Oct 4
        assertEquals(2, badge(first.id, "nag-3")) // Oct 7: the second is due too
        assertEquals(2, badge(second.id, "due"))
    }

    @Test fun badgeCountsDueTodayAndOverdueButNotPaused() {
        val now = date(2026, 10, 10)
        val overdue = input(date(2026, 9, 1), 14)
        val dueToday = input(date(2026, 10, 5), 5)
        val upcoming = input(date(2026, 10, 9), 30)
        val pausedOverdue = input(date(2026, 9, 1), 14, paused = true)
        assertEquals(2, ReminderPlanner.badgeCount(listOf(overdue, dueToday, upcoming, pausedOverdue), now))
    }

    @Test fun headsUpIsSkippedWhenItWouldFireBeforeTheCycleStarted() {
        // A 1-day interval with a 3-day heads-up would put the heads-up before the start date.
        val now = date(2026, 10, 1)
        val plan = ReminderPlanner.plan(input(now, 1, leadDays = 3), now)
        assertFalse(plan.any { it.kind == PlannedReminder.Kind.HeadsUp })
        assertTrue(plan.any { it.kind == PlannedReminder.Kind.Due })
    }

    @Test fun noFollowUpsWhenTurnedOff() {
        val now = date(2026, 10, 1)
        val plan = ReminderPlanner.plan(input(now, 5, followUps = false), now)
        assertEquals(listOf("lead", "due"), plan.map { it.identifierSuffix })
    }
}
