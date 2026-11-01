package com.dnlfx.remindergenie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val LA: ZoneId = ZoneId.of("America/Los_Angeles")

fun date(year: Int, month: Int, day: Int, hour: Int = 8, minute: Int = 0): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(LA).toInstant()

class ScheduleTest {
    private fun schedule(start: Instant, value: Int, unit: IntervalUnit) = Schedule(start, value, unit, LA)

    @Test fun twoWeekLensesAreDueFourteenDaysLater() {
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        assertEquals(date(2026, 10, 15), s.dueDate)
        assertEquals(14, s.daysRemaining(date(2026, 10, 1, 22)))
        assertEquals(0, s.daysRemaining(date(2026, 10, 15, 23)))
        assertEquals(-2, s.daysRemaining(date(2026, 10, 17)))
    }

    @Test fun monthlyIntervalClampsToEndOfMonth() {
        val due = schedule(date(2026, 1, 31), 1, IntervalUnit.MONTHS).dueDate.atZone(LA)
        assertEquals(28, due.dayOfMonth)
        assertEquals(2, due.monthValue)
    }

    @Test fun daysCountAcrossDaylightSavingChange() {
        // US clocks fall back on Nov 1, 2026; a 14-day cycle still lands on the same wall-clock time.
        val s = schedule(date(2026, 10, 25), 14, IntervalUnit.DAYS)
        assertEquals(date(2026, 11, 8), s.dueDate)
        assertEquals(14, s.daysRemaining(date(2026, 10, 25, 23)))
    }

    @Test fun statusBuckets() {
        val s = schedule(date(2026, 10, 1), 10, IntervalUnit.DAYS)
        assertEquals(ItemStatus.OK, s.status(date(2026, 10, 2), 3))
        assertEquals(ItemStatus.DUE_SOON, s.status(date(2026, 10, 8), 3))
        assertEquals(ItemStatus.DUE_TODAY, s.status(date(2026, 10, 11, 20), 3))
        assertEquals(ItemStatus.OVERDUE, s.status(date(2026, 10, 12), 3))
    }

    @Test fun progressIsClamped() {
        val s = schedule(date(2026, 10, 1), 10, IntervalUnit.DAYS)
        assertEquals(0.0, s.progress(date(2026, 9, 1)), 0.0)
        assertEquals(0.5, s.progress(date(2026, 10, 6)), 0.0001)
        assertEquals(1.0, s.progress(date(2026, 11, 1)), 0.0)
    }

    @Test fun planIncludesHeadsUpDueAndFollowUps() {
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        val plan = ReminderPlanner.plan(s, 9, 30, 2, true, null, date(2026, 10, 2))
        assertEquals(listOf("lead", "due", "nag-1", "nag-2", "nag-3"), plan.map { it.identifierSuffix })
        assertEquals(date(2026, 10, 13, 9, 30), plan[0].fireDate)
        assertEquals(date(2026, 10, 15, 9, 30), plan[1].fireDate)
        assertEquals(date(2026, 10, 18, 9, 30), plan[4].fireDate)
    }

    @Test fun planSkipsPastRemindersWhenOverdue() {
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        val plan = ReminderPlanner.plan(s, 9, 0, 2, true, null, date(2026, 10, 16, 12))
        assertEquals(listOf("nag-2", "nag-3"), plan.map { it.identifierSuffix })
    }

    @Test fun snoozeReplacesDueReminder() {
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        val snooze = date(2026, 10, 16, 18)
        val plan = ReminderPlanner.plan(s, 9, 0, 2, false, snooze, date(2026, 10, 16, 12))
        assertEquals(listOf(PlannedReminder(PlannedReminder.Kind.Due, snooze)), plan)
    }

    @Test fun snoozingBeforeTheDueDayKeepsTheDueReminder() {
        // "Remind me tomorrow" on a heads-up two days early shouldn't move the due reminder up.
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        val snooze = date(2026, 10, 14, 9)
        val plan = ReminderPlanner.plan(s, 9, 0, 2, false, snooze, date(2026, 10, 13, 9, 5))
        assertEquals(
            listOf(
                PlannedReminder(PlannedReminder.Kind.HeadsUp, snooze),
                PlannedReminder(PlannedReminder.Kind.Due, date(2026, 10, 15, 9)),
            ),
            plan,
        )
    }

    @Test fun followUpsCountFromAnExpiredSnooze() {
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        val snooze = date(2026, 10, 16, 9)
        val plan = ReminderPlanner.plan(s, 9, 0, 2, true, snooze, date(2026, 10, 17, 12))
        assertEquals(listOf("nag-2", "nag-3"), plan.map { it.identifierSuffix })
        assertEquals(date(2026, 10, 18, 9), plan.first().fireDate)
    }

    @Test fun needsReplacingFromTheDueDay() {
        val s = schedule(date(2026, 10, 1), 14, IntervalUnit.DAYS)
        assertFalse(s.needsReplacing(date(2026, 10, 14, 23)))
        assertTrue(s.needsReplacing(date(2026, 10, 15, 0, 1)))
        assertTrue(s.needsReplacing(date(2026, 10, 20)))
    }

    @Test fun averageDaysBetweenReplacements() {
        val dates = listOf(date(2026, 9, 1), date(2026, 9, 15), date(2026, 10, 1))
        assertEquals(15.0, HistoryStats.averageDaysBetween(dates, LA)!!, 0.0)
        assertNull(HistoryStats.averageDaysBetween(listOf(date(2026, 9, 1)), LA))
    }

    @Test fun intervalLabels() {
        assertEquals("day", IntervalUnit.DAYS.label(1))
        assertEquals("months", IntervalUnit.MONTHS.label(3))
        assertEquals("Weeks", IntervalUnit.WEEKS.displayName)
    }

    @Test fun gapsCompareEachCycleWithItsSchedule() {
        val dates = listOf(date(2026, 9, 1), date(2026, 9, 15), date(2026, 10, 3))
        val gaps = HistoryStats.gaps(dates, 2, IntervalUnit.WEEKS, LA)
        assertEquals(listOf(14, 18), gaps.map { it.days })
        assertEquals(listOf(14, 14), gaps.map { it.targetDays })
        assertEquals(listOf(true, false), gaps.map { it.isOnTime })
        assertEquals(0.5, HistoryStats.onTimeRate(gaps)!!, 0.0)
        assertNull(HistoryStats.onTimeRate(emptyList()))
    }

    @Test fun monthlyGapTargetFollowsTheCalendar() {
        val gaps = HistoryStats.gaps(listOf(date(2026, 2, 1), date(2026, 3, 1)), 1, IntervalUnit.MONTHS, LA)
        assertEquals(listOf(HistoryStats.Gap(date(2026, 3, 1), 28, 28)), gaps)
    }

    @Test fun intervalRangesDependOnUnit() {
        assertEquals(1..365, IntervalUnit.DAYS.allowedRange)
        assertEquals(10, IntervalUnit.YEARS.allowedRange.last)
    }

    @Test fun pluralization() {
        assertEquals("1 day", pluralized(1, "day"))
        assertEquals("3 days", pluralized(3, "day"))
        assertEquals("0 spares", pluralized(0, "spare"))
    }

    @Test fun reorderLinks() {
        assertEquals("https://amazon.com/lenses", ReorderLink.url("amazon.com/lenses"))
        assertEquals("example.com", ReorderLink.host(ReorderLink.url(" https://example.com ")!!))
        assertEquals("example.com", ReorderLink.host("https://www.example.com/a"))
        assertNull(ReorderLink.url(""))
        assertNull(ReorderLink.url("not a link"))
        assertNull(ReorderLink.url("ftp://example.com"))
    }

    @Test fun suffixesRoundTrip() {
        for (suffix in ReminderPlanner.allIdentifierSuffixes()) {
            val kind = PlannedReminder.kindFromSuffix(suffix)!!
            assertEquals(suffix, PlannedReminder(kind, date(2026, 1, 1)).identifierSuffix)
        }
        assertNull(PlannedReminder.kindFromSuffix("nope"))
    }

    @Test fun dueText() {
        assertEquals("Overdue by 1 day", DueText.short(-1))
        assertEquals("Due today", DueText.short(0))
        assertEquals("Due tomorrow", DueText.short(1))
        assertEquals("Due in 5 days", DueText.short(5))
        val now = date(2026, 10, 14, 10)
        val us = java.util.Locale.US
        assertEquals("Snoozed until 3:00 PM", DueText.snoozed(date(2026, 10, 14, 15), now, LA, us).replace(' ', ' '))
        assertEquals("Snoozed until tomorrow, 9:00 AM", DueText.snoozed(date(2026, 10, 15, 9), now, LA, us).replace(' ', ' '))
        assertEquals("Snoozed until Sat, Oct 17", DueText.snoozed(date(2026, 10, 17, 9), now, LA, us))
        assertEquals("Fri, Oct 16 at 9:00 AM", DueText.reminder(date(2026, 10, 16, 9), now, LA, us).replace(' ', ' '))
    }
}
