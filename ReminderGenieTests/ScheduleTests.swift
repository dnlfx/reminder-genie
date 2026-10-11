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

    @Test func averageDaysBetweenReplacements() {
        let dates = [date(2026, 9, 1), date(2026, 9, 15), date(2026, 10, 1)]
        #expect(HistoryStats.averageDaysBetween(dates, calendar: calendar) == 15)
        #expect(HistoryStats.averageDaysBetween([date(2026, 9, 1)], calendar: calendar) == nil)
    }

    @Test func intervalLabels() {
        #expect(IntervalUnit.days.label(for: 1) == "day")
        #expect(IntervalUnit.months.label(for: 3) == "months")
    }
}
