#if DEBUG
import Foundation
import SwiftData
import UserNotifications

/// Hooks for the UI tests in ReminderGenieUITests. Debug builds only.
///
/// Launched with `-ui-testing`, the app uses an empty in-memory database and fresh settings,
/// then loads the sample data named by the `UITEST_SCENARIO` environment variable.
enum UITestSupport {
    static let isActive = ProcessInfo.processInfo.arguments.contains("-ui-testing")

    enum Scenario: String {
        /// No items.
        case empty
        /// One overdue, one upcoming and one item with two history entries.
        case standard
        /// One item whose due reminder fires about a minute after launch.
        case notification
    }

    @MainActor
    static func prepare(container: ModelContainer) {
        if let domain = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: domain)
        }
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()

        let raw = ProcessInfo.processInfo.environment["UITEST_SCENARIO"] ?? Scenario.empty.rawValue
        let scenario = Scenario(rawValue: raw) ?? .empty
        let context = container.mainContext
        let calendar = Calendar.current
        // One fixed "now", so a log and the cycle it started share the exact same date.
        let now = Date.now
        func daysAgo(_ days: Int) -> Date { calendar.date(byAdding: .day, value: -days, to: now) ?? now }

        switch scenario {
        case .empty:
            break

        case .standard:
            let lenses = item("Contact lenses", "eye", .blue, 14, .days, start: daysAgo(16), in: context)
            lenses.tracksSpares = true
            lenses.spareCount = 3
            lenses.lowStockThreshold = 1
            log(lenses, on: daysAgo(16), in: context)

            let filter = item("Water filter", "drop", .teal, 2, .months, start: daysAgo(5), in: context)
            log(filter, on: daysAgo(5), in: context)

            let toothbrush = item("Toothbrush", "mouth", .green, 3, .months, start: daysAgo(10), in: context)
            log(toothbrush, on: daysAgo(60), in: context)
            log(toothbrush, on: daysAgo(10), in: context)

        case .notification:
            // Due today, with the reminder set for the next minute that's at least 15 seconds away.
            let fire = now.addingTimeInterval(75)
            let parts = calendar.dateComponents([.hour, .minute], from: fire)
            let start = calendar.date(byAdding: .day, value: -14, to: fire) ?? now
            let lenses = item("Test lenses", "eye", .blue, 14, .days, start: start, in: context)
            lenses.reminderHour = parts.hour ?? 9
            lenses.reminderMinute = parts.minute ?? 0
            lenses.leadDays = 0
            lenses.remindDailyWhenOverdue = false
            log(lenses, on: start, in: context)
        }
        try? context.save()

        if scenario == .notification {
            Task {
                await NotificationManager.shared.requestAuthorizationIfNeeded()
                NotificationManager.shared.refreshAll()
            }
        }
    }

    @MainActor
    private static func item(
        _ name: String, _ symbol: String, _ color: ItemColor,
        _ value: Int, _ unit: IntervalUnit, start: Date, in context: ModelContext
    ) -> TrackedItem {
        let item = TrackedItem(name: name, symbolName: symbol, colorName: color.rawValue, intervalValue: value, intervalUnit: unit, startDate: start)
        context.insert(item)
        return item
    }

    @MainActor
    private static func log(_ item: TrackedItem, on date: Date, in context: ModelContext) {
        let log = ReplacementLog(date: date)
        context.insert(log)
        log.item = item
    }
}
#endif
