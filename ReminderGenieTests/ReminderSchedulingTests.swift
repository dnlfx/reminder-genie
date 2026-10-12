import Foundation
import Testing
@testable import ReminderGenie

/// The whole-app reminder schedule: ordering, the iOS pending limit, pausing and badge counts.
struct ReminderSchedulingTests {
    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        return calendar
    }()

    private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 8) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
    }

    private func input(start: Date, every days: Int, leadDays: Int = 1, paused: Bool = false, snoozedUntil: Date? = nil) -> ReminderInput {
        ReminderInput(
            id: UUID(),
            schedule: Schedule(startDate: start, intervalValue: days, intervalUnit: .days, calendar: calendar),
            hour: 9,
            minute: 0,
            leadDays: leadDays,
            remindDailyWhenOverdue: true,
            snoozedUntil: snoozedUntil,
            isPaused: paused
        )
    }

    @Test func remindersComeSoonestFirstAcrossItems() {
        let now = date(2026, 10, 1)
        let later = input(start: now, every: 20)
        let sooner = input(start: now, every: 5)
        let result = ReminderPlanner.schedule([later, sooner], now: now)
        #expect(result.map(\.reminder.fireDate) == result.map(\.reminder.fireDate).sorted())
        #expect(result.first?.itemID == sooner.id)
    }

    @Test func pausedItemsGetNoReminders() {
        let now = date(2026, 10, 1)
        let paused = input(start: now, every: 5, paused: true)
        #expect(ReminderPlanner.schedule([paused], now: now).isEmpty)
        #expect(ReminderPlanner.plan(paused, now: now).isEmpty)
    }

    @Test func staysUnderThePendingLimitKeepingTheSoonest() {
        let now = date(2026, 10, 1)
        // 20 items × 5 reminders = 100, well over the limit.
        let inputs = (1...20).map { input(start: now, every: $0 + 2) }
        let result = ReminderPlanner.schedule(inputs, now: now)
        #expect(result.count == ReminderPlanner.pendingLimit)

        let everything = inputs.flatMap { ReminderPlanner.plan($0, now: now) }.map(\.fireDate).sorted()
        #expect(result.last!.reminder.fireDate == everything[ReminderPlanner.pendingLimit - 1])
    }

    @Test func eachReminderCarriesTheBadgeForWhenItArrives() {
        let now = date(2026, 10, 1)
        let first = input(start: now, every: 3, leadDays: 0)   // due Oct 4
        let second = input(start: now, every: 6, leadDays: 0)  // due Oct 7
        let result = ReminderPlanner.schedule([first, second], now: now)

        func badge(_ id: UUID, _ suffix: String) -> Int? {
            result.first { $0.itemID == id && $0.reminder.identifierSuffix == suffix }?.badge
        }
        #expect(badge(first.id, "due") == 1)     // only the first is due on Oct 4
        #expect(badge(first.id, "nag-3") == 2)   // Oct 7: the second is due too
        #expect(badge(second.id, "due") == 2)
    }

    @Test func badgeCountsDueTodayAndOverdueButNotPaused() {
        let now = date(2026, 10, 10)
        let overdue = input(start: date(2026, 9, 1), every: 14)
        let dueToday = input(start: date(2026, 10, 5), every: 5)
        let upcoming = input(start: date(2026, 10, 9), every: 30)
        let pausedOverdue = input(start: date(2026, 9, 1), every: 14, paused: true)
        #expect(ReminderPlanner.badgeCount([overdue, dueToday, upcoming, pausedOverdue], at: now) == 2)
    }

    @Test func headsUpIsSkippedWhenItWouldFireBeforeTheCycleStarted() {
        // A 1-day interval with a 3-day heads-up would put the heads-up before the start date.
        let now = date(2026, 10, 1)
        let plan = ReminderPlanner.plan(input(start: now, every: 1, leadDays: 3), now: now)
        #expect(!plan.contains { $0.kind == .headsUp })
        #expect(plan.contains { $0.kind == .due })
    }

    @Test func noFollowUpsWhenTurnedOff() {
        let now = date(2026, 10, 1)
        var item = input(start: now, every: 5)
        item.remindDailyWhenOverdue = false
        #expect(ReminderPlanner.plan(item, now: now).map(\.identifierSuffix) == ["lead", "due"])
    }
}
