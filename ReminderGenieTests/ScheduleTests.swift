import Foundation
import Testing
@testable import ReminderGenie

struct ScheduleTests {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        return calendar
    }()

    private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 8, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    private func schedule(start: Date, _ value: Int, _ unit: IntervalUnit) -> Schedule {
        Schedule(startDate: start, intervalValue: value, intervalUnit: unit, calendar: calendar)
    }

    @Test func twoWeekLensesAreDueFourteenDaysLater() {
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        #expect(s.dueDate == date(2026, 10, 15))
        #expect(s.daysRemaining(at: date(2026, 10, 1, 22)) == 14)
        #expect(s.daysRemaining(at: date(2026, 10, 15, 23)) == 0)
        #expect(s.daysRemaining(at: date(2026, 10, 17)) == -2)
    }

    @Test func monthlyIntervalClampsToEndOfMonth() {
        let s = schedule(start: date(2026, 1, 31), 1, .months)
        #expect(calendar.component(.day, from: s.dueDate) == 28)
        #expect(calendar.component(.month, from: s.dueDate) == 2)
    }

    @Test func statusBuckets() {
        let s = schedule(start: date(2026, 10, 1), 10, .days)
        #expect(s.status(at: date(2026, 10, 2), dueSoonDays: 3) == .ok)
        #expect(s.status(at: date(2026, 10, 8), dueSoonDays: 3) == .dueSoon)
        #expect(s.status(at: date(2026, 10, 11, 20), dueSoonDays: 3) == .dueToday)
        #expect(s.status(at: date(2026, 10, 12), dueSoonDays: 3) == .overdue)
    }

    @Test func progressIsClamped() {
        let s = schedule(start: date(2026, 10, 1), 10, .days)
        #expect(s.progress(at: date(2026, 9, 1)) == 0)
        #expect(abs(s.progress(at: date(2026, 10, 6)) - 0.5) < 0.0001)
        #expect(s.progress(at: date(2026, 11, 1)) == 1)
    }

    @Test func planIncludesHeadsUpDueAndFollowUps() {
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        let plan = ReminderPlanner.plan(schedule: s, hour: 9, minute: 30, leadDays: 2, remindDailyWhenOverdue: true, snoozedUntil: nil, now: date(2026, 10, 2))
        #expect(plan.map(\.identifierSuffix) == ["lead", "due", "nag-1", "nag-2", "nag-3"])
        #expect(plan[0].fireDate == date(2026, 10, 13, 9, 30))
        #expect(plan[1].fireDate == date(2026, 10, 15, 9, 30))
        #expect(plan[4].fireDate == date(2026, 10, 18, 9, 30))
    }

    @Test func planSkipsPastRemindersWhenOverdue() {
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        let plan = ReminderPlanner.plan(schedule: s, hour: 9, minute: 0, leadDays: 2, remindDailyWhenOverdue: true, snoozedUntil: nil, now: date(2026, 10, 16, 12))
        #expect(plan.map(\.identifierSuffix) == ["nag-2", "nag-3"])
    }

    @Test func snoozeReplacesDueReminder() {
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        let snooze = date(2026, 10, 16, 18)
        let plan = ReminderPlanner.plan(schedule: s, hour: 9, minute: 0, leadDays: 2, remindDailyWhenOverdue: false, snoozedUntil: snooze, now: date(2026, 10, 16, 12))
        #expect(plan == [PlannedReminder(kind: .due, fireDate: snooze)])
    }

    @Test func snoozingBeforeTheDueDayKeepsTheDueReminder() {
        // "Remind me tomorrow" on a heads-up two days early shouldn't move the due reminder up.
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        let snooze = date(2026, 10, 14, 9)
        let plan = ReminderPlanner.plan(schedule: s, hour: 9, minute: 0, leadDays: 2, remindDailyWhenOverdue: false, snoozedUntil: snooze, now: date(2026, 10, 13, 9, 5))
        #expect(plan == [
            PlannedReminder(kind: .headsUp, fireDate: snooze),
            PlannedReminder(kind: .due, fireDate: date(2026, 10, 15, 9)),
        ])
    }

    @Test func followUpsCountFromAnExpiredSnooze() {
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        let snooze = date(2026, 10, 16, 9)
        let plan = ReminderPlanner.plan(schedule: s, hour: 9, minute: 0, leadDays: 2, remindDailyWhenOverdue: true, snoozedUntil: snooze, now: date(2026, 10, 17, 12))
        #expect(plan.map(\.identifierSuffix) == ["nag-2", "nag-3"])
        #expect(plan.first?.fireDate == date(2026, 10, 18, 9))
    }

    @Test func needsReplacingFromTheDueDay() {
        let s = schedule(start: date(2026, 10, 1), 14, .days)
        #expect(!s.needsReplacing(at: date(2026, 10, 14, 23)))
        #expect(s.needsReplacing(at: date(2026, 10, 15, 0, 1)))
        #expect(s.needsReplacing(at: date(2026, 10, 20)))
    }

    @Test func averageDaysBetweenReplacements() {
        let dates = [date(2026, 9, 1), date(2026, 9, 15), date(2026, 10, 1)]
        #expect(HistoryStats.averageDaysBetween(dates, calendar: calendar) == 15)
        #expect(HistoryStats.averageDaysBetween([date(2026, 9, 1)], calendar: calendar) == nil)
    }

    @Test func intervalLabels() {
        #expect(IntervalUnit.days.label(for: 1) == "day")
        #expect(IntervalUnit.months.label(for: 3) == "months")
    }

    @Test func gapsCompareEachCycleWithItsSchedule() {
        let dates = [date(2026, 9, 1), date(2026, 9, 15), date(2026, 10, 3)]
        let gaps = HistoryStats.gaps(dates, intervalValue: 2, intervalUnit: .weeks, calendar: calendar)
        #expect(gaps.map(\.days) == [14, 18])
        #expect(gaps.map(\.targetDays) == [14, 14])
        #expect(gaps.map(\.isOnTime) == [true, false])
        #expect(HistoryStats.onTimeRate(gaps) == 0.5)
        #expect(HistoryStats.onTimeRate([]) == nil)
    }

    @Test func monthlyGapTargetFollowsTheCalendar() {
        let gaps = HistoryStats.gaps([date(2026, 2, 1), date(2026, 3, 1)], intervalValue: 1, intervalUnit: .months, calendar: calendar)
        #expect(gaps == [HistoryStats.Gap(replacedOn: date(2026, 3, 1), days: 28, targetDays: 28)])
    }

    @Test func intervalRangesDependOnUnit() {
        #expect(IntervalUnit.days.allowedRange == 1...365)
        #expect(IntervalUnit.years.allowedRange.upperBound == 10)
    }

    @Test func pluralization() {
        #expect(pluralized(1, "day") == "1 day")
        #expect(pluralized(3, "day") == "3 days")
        #expect(pluralized(0, "spare") == "0 spares")
    }

    @Test func reorderLinks() {
        #expect(ReorderLink.url(from: "amazon.com/lenses")?.absoluteString == "https://amazon.com/lenses")
        #expect(ReorderLink.url(from: " https://example.com ")?.host() == "example.com")
        #expect(ReorderLink.url(from: "") == nil)
        #expect(ReorderLink.url(from: "not a link") == nil)
        #expect(ReorderLink.url(from: "ftp://example.com") == nil)
    }
}
