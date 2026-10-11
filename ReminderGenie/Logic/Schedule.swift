import Foundation

/// The unit an item's replacement interval is measured in.
enum IntervalUnit: String, Codable, CaseIterable, Identifiable, Sendable {
    case days, weeks, months, years

    var id: String { rawValue }

    var calendarComponent: Calendar.Component {
        switch self {
        case .days: .day
        case .weeks: .weekOfYear
        case .months: .month
        case .years: .year
        }
    }

    func label(for value: Int) -> String {
        let singular: String
        switch self {
        case .days: singular = "day"
        case .weeks: singular = "week"
        case .months: singular = "month"
        case .years: singular = "year"
        }
        return value == 1 ? singular : singular + "s"
    }
}

/// Where an item sits relative to its due date.
enum ItemStatus: Int, Comparable, Sendable {
    case overdue, dueToday, dueSoon, ok

    static func < (lhs: ItemStatus, rhs: ItemStatus) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// Pure date math for a recurring item. Kept free of SwiftData/SwiftUI so it can be unit tested.
struct Schedule: Sendable {
    var startDate: Date
    var intervalValue: Int
    var intervalUnit: IntervalUnit
    var calendar: Calendar = .current

    /// The moment the current item is due to be replaced.
    var dueDate: Date {
        calendar.date(byAdding: intervalUnit.calendarComponent, value: max(1, intervalValue), to: startDate) ?? startDate
    }

    /// Fraction of the interval that has elapsed, clamped to 0...1.
    func progress(at now: Date) -> Double {
        let total = dueDate.timeIntervalSince(startDate)
        guard total > 0 else { return 1 }
        return min(1, max(0, now.timeIntervalSince(startDate) / total))
    }

    /// Whole calendar days until the due date. Negative when overdue, 0 on the due day.
    func daysRemaining(at now: Date) -> Int {
        let from = calendar.startOfDay(for: now)
        let to = calendar.startOfDay(for: dueDate)
        return calendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    func status(at now: Date, dueSoonDays: Int) -> ItemStatus {
        let days = daysRemaining(at: now)
        if days < 0 { return .overdue }
        if days == 0 { return .dueToday }
        if days <= dueSoonDays { return .dueSoon }
        return .ok
    }

    /// The due day at the user's preferred reminder time.
    func reminderDate(hour: Int, minute: Int) -> Date {
        calendar.date(bySettingHour: hour, minute: minute, second: 0, of: dueDate) ?? dueDate
    }
}

/// One notification the app intends to schedule for an item.
struct PlannedReminder: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case headsUp
        case due
        case overdue(day: Int)
    }

    var kind: Kind
    var fireDate: Date

    var identifierSuffix: String {
        switch kind {
        case .headsUp: "lead"
        case .due: "due"
        case .overdue(let day): "nag-\(day)"
        }
    }
}

enum ReminderPlanner {
    /// How many daily follow-ups to send after the due date when an item is still not replaced.
    static let overdueFollowUps = 3

    /// Every notification id that could exist for an item, so they can all be cancelled at once.
    static func allIdentifierSuffixes() -> [String] {
        ["lead", "due"] + (1...overdueFollowUps).map { "nag-\($0)" }
    }

    /// Works out which notifications should be pending for an item right now.
    static func plan(
        schedule: Schedule,
        hour: Int,
        minute: Int,
        leadDays: Int,
        remindDailyWhenOverdue: Bool,
        snoozedUntil: Date?,
        now: Date
    ) -> [PlannedReminder] {
        let calendar = schedule.calendar
        var result: [PlannedReminder] = []

        // A snooze replaces the regular due reminder and follow-ups start from it.
        let base: Date
        if let snoozedUntil, snoozedUntil > now {
            base = snoozedUntil
        } else {
            base = schedule.reminderDate(hour: hour, minute: minute)
            if leadDays > 0,
               let lead = calendar.date(byAdding: .day, value: -leadDays, to: base),
               lead > now, lead > schedule.startDate {
                result.append(PlannedReminder(kind: .headsUp, fireDate: lead))
            }
        }

        if base > now {
            result.append(PlannedReminder(kind: .due, fireDate: base))
        }

        if remindDailyWhenOverdue {
            for day in 1...overdueFollowUps {
                if let nag = calendar.date(byAdding: .day, value: day, to: base), nag > now {
                    result.append(PlannedReminder(kind: .overdue(day: day), fireDate: nag))
                }
            }
        }
        return result
    }
}

/// Stats derived from an item's replacement history.
enum HistoryStats {
    /// Average number of days between consecutive replacements, or nil with fewer than two entries.
    static func averageDaysBetween(_ dates: [Date], calendar: Calendar = .current) -> Double? {
        let sorted = dates.sorted()
        guard sorted.count >= 2 else { return nil }
        var total = 0
        for (a, b) in zip(sorted, sorted.dropFirst()) {
            total += calendar.dateComponents([.day], from: calendar.startOfDay(for: a), to: calendar.startOfDay(for: b)).day ?? 0
        }
        return Double(total) / Double(sorted.count - 1)
    }
}
