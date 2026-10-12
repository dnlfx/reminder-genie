import Foundation

/// Editable copy of an item's settings, used by the editor so nothing is saved until the user taps Save.
struct ItemDraft: Equatable {
    var name: String = ""
    var symbolName: String = "clock"
    var color: ItemColor = .blue
    var intervalValue: Int = 14
    var intervalUnit: IntervalUnit = .days
    var startDate: Date = .now
    var reminderTime: Date = ItemDraft.time(hour: 9, minute: 0)
    var leadDays: Int = 1
    var remindDailyWhenOverdue: Bool = true
    var notes: String = ""
    var tracksSpares: Bool = false
    var spareCount: Int = 0
    var lowStockThreshold: Int = 1
    var reorderLink: String = ""
    var isPaused: Bool = false

    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && intervalValue > 0 && isReorderLinkValid
    }

    /// An empty link is fine; anything typed must look like a web address.
    var isReorderLinkValid: Bool {
        reorderLink.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || ReorderLink.url(from: reorderLink) != nil
    }

    var previewSchedule: Schedule {
        Schedule(startDate: startDate, intervalValue: intervalValue, intervalUnit: intervalUnit)
    }

    var previewDueDate: Date { previewSchedule.dueDate }

    var reminderHourMinute: (hour: Int, minute: Int) {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: reminderTime)
        return (parts.hour ?? 9, parts.minute ?? 0)
    }

    static func time(hour: Int, minute: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: .now) ?? .now
    }
}

extension ItemDraft {
    init(template: ItemTemplate, defaultHour: Int, defaultMinute: Int) {
        self.init()
        name = template.name
        symbolName = template.symbolName
        color = template.color
        intervalValue = template.intervalValue
        intervalUnit = template.intervalUnit
        leadDays = template.leadDays
        tracksSpares = template.tracksSpares
        reminderTime = ItemDraft.time(hour: defaultHour, minute: defaultMinute)
    }

    init(item: TrackedItem) {
        self.init()
        name = item.name
        symbolName = item.symbolName
        color = item.color
        intervalValue = item.intervalValue
        intervalUnit = item.intervalUnit
        startDate = item.startDate
        reminderTime = ItemDraft.time(hour: item.reminderHour, minute: item.reminderMinute)
        leadDays = item.leadDays
        remindDailyWhenOverdue = item.remindDailyWhenOverdue
        notes = item.notes
        tracksSpares = item.tracksSpares
        spareCount = item.spareCount
        lowStockThreshold = item.lowStockThreshold
        reorderLink = item.reorderLink
        isPaused = item.isPaused
    }

    /// Copies the draft onto an existing item.
    func apply(to item: TrackedItem) {
        let (hour, minute) = reminderHourMinute
        item.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        item.symbolName = symbolName
        item.colorName = color.rawValue
        item.intervalValue = intervalValue
        item.intervalUnit = intervalUnit
        if item.startDate != startDate {
            item.startDate = startDate
            item.snoozedUntil = nil
        }
        item.reminderHour = hour
        item.reminderMinute = minute
        item.leadDays = leadDays
        item.remindDailyWhenOverdue = remindDailyWhenOverdue
        item.notes = notes
        item.tracksSpares = tracksSpares
        item.spareCount = spareCount
        item.lowStockThreshold = lowStockThreshold
        item.reorderLink = reorderLink.trimmingCharacters(in: .whitespacesAndNewlines)
        item.isPaused = isPaused
    }
}
