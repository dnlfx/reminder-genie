import Foundation
import SwiftData

/// Something the user replaces or does on a schedule, such as contact lenses or a water filter.
@Model
final class TrackedItem {
    var id: UUID = UUID()
    var name: String = ""
    var symbolName: String = "clock"
    var colorName: String = ItemColor.blue.rawValue
    var intervalValue: Int = 14
    var intervalUnitRaw: String = IntervalUnit.days.rawValue
    /// When the current one was started (put in, opened, last done).
    var startDate: Date = Date.now
    var reminderHour: Int = 9
    var reminderMinute: Int = 0
    /// Send a heads-up this many days before the due date. 0 turns it off.
    var leadDays: Int = 1
    var remindDailyWhenOverdue: Bool = true
    var notes: String = ""
    var tracksSpares: Bool = false
    var spareCount: Int = 0
    var lowStockThreshold: Int = 1
    /// Where to buy more, as typed by the user. See `reorderURL`.
    var reorderLink: String = ""
    var snoozedUntil: Date?
    var isPaused: Bool = false
    var createdAt: Date = Date.now

    @Relationship(deleteRule: .cascade, inverse: \ReplacementLog.item)
    var logs: [ReplacementLog]? = []

    init(name: String, symbolName: String, colorName: String, intervalValue: Int, intervalUnit: IntervalUnit, startDate: Date) {
        self.name = name
        self.symbolName = symbolName
        self.colorName = colorName
        self.intervalValue = intervalValue
        self.intervalUnitRaw = intervalUnit.rawValue
        self.startDate = startDate
    }

    var intervalUnit: IntervalUnit {
        get { IntervalUnit(rawValue: intervalUnitRaw) ?? .days }
        set { intervalUnitRaw = newValue.rawValue }
    }

    var color: ItemColor { ItemColor(rawValue: colorName) ?? .blue }

    var schedule: Schedule {
        Schedule(startDate: startDate, intervalValue: intervalValue, intervalUnit: intervalUnit)
    }

    var dueDate: Date { schedule.dueDate }

    var intervalDescription: String {
        "Every \(intervalValue) \(intervalUnit.label(for: intervalValue))"
    }

    var sortedLogs: [ReplacementLog] {
        (logs ?? []).sorted { $0.date > $1.date }
    }

    var isSnoozed: Bool {
        guard let snoozedUntil else { return false }
        return snoozedUntil > .now
    }

    var isLowOnSpares: Bool { tracksSpares && spareCount <= lowStockThreshold }

    /// The reorder link as a URL, adding https:// when the user typed a bare domain.
    var reorderURL: URL? { ReorderLink.url(from: reorderLink) }

    var reminderInput: ReminderInput {
        ReminderInput(
            id: id,
            schedule: schedule,
            hour: reminderHour,
            minute: reminderMinute,
            leadDays: leadDays,
            remindDailyWhenOverdue: remindDailyWhenOverdue,
            snoozedUntil: snoozedUntil,
            isPaused: isPaused
        )
    }

    /// Removes a history entry. Removing the entry that started the current cycle rewinds the
    /// countdown to the entry before it, so a mistaken log can be taken back after Undo has gone.
    func removeLog(_ log: ReplacementLog) {
        let remaining = sortedLogs.filter { $0.persistentModelID != log.persistentModelID }
        if log.date == startDate, let previous = remaining.first {
            startDate = previous.date
            snoozedUntil = nil
        }
        logs?.removeAll { $0.persistentModelID == log.persistentModelID }
        modelContext?.delete(log)
    }

    /// Records a replacement: starts a new cycle, logs it, clears any snooze and uses up a spare.
    @discardableResult
    func markReplaced(on date: Date = .now, note: String = "") -> ReplacementLog {
        // An entry older than the current cycle only fills in history; it doesn't rewind the countdown.
        if date >= startDate {
            startDate = date
            snoozedUntil = nil
            if tracksSpares && spareCount > 0 {
                spareCount -= 1
            }
        }
        let log = ReplacementLog(date: date, note: note)
        modelContext?.insert(log)
        // Setting one side of the relationship lets SwiftData maintain the inverse.
        log.item = self
        return log
    }
}

enum ReorderLink {
    static func url(from text: String) -> URL? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.contains(" ") else { return nil }
        let withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed
        guard let url = URL(string: withScheme), let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme), url.host() != nil else { return nil }
        return url
    }
}

/// One past replacement of an item.
@Model
final class ReplacementLog {
    var date: Date = Date.now
    var note: String = ""
    var item: TrackedItem?

    init(date: Date, note: String = "") {
        self.date = date
        self.note = note
    }
}

/// The colors an item can be tagged with. Stored by name so the model stays UI-free.
enum ItemColor: String, CaseIterable, Identifiable, Codable, Sendable {
    case blue, teal, green, yellow, orange, red, pink, purple, indigo, gray
    var id: String { rawValue }
    var displayName: String { rawValue.capitalized }
}
