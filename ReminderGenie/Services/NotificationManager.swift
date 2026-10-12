import Foundation
import SwiftData
import UserNotifications

/// Schedules local notifications for tracked items and handles the actions on them
/// ("Replaced it", "Snooze") so items can be updated straight from the lock screen.
@MainActor
final class NotificationManager: NSObject, ObservableObject {
    static let shared = NotificationManager()

    enum ID {
        static let itemCategory = "ITEM_DUE"
        static let lowStockCategory = "LOW_STOCK"
        static let markDone = "MARK_DONE"
        static let snoozeHour = "SNOOZE_1H"
        static let snoozeDay = "SNOOZE_1D"
        static let itemKey = "itemID"
    }

    /// Set when the user taps a notification; the list view navigates to that item.
    @Published var pendingItemID: UUID?
    @Published private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined

    private var container: ModelContainer?
    private let center = UNUserNotificationCenter.current()

    /// Safe to call more than once; App Intents call it in case they run before the UI.
    func configure(container: ModelContainer) {
        guard self.container == nil else { return }
        self.container = container
        center.delegate = self
        registerCategories()
    }

    // MARK: Permission

    func refreshAuthorizationStatus() async {
        authorizationStatus = await center.notificationSettings().authorizationStatus
    }

    func requestAuthorizationIfNeeded() async {
        await refreshAuthorizationStatus()
        guard authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        await refreshAuthorizationStatus()
    }

    // MARK: Scheduling

    /// Rebuilds pending notifications for one item.
    func reschedule(_ item: TrackedItem) {
        cancel(item)
        guard !item.isPaused else { return }

        let plan = ReminderPlanner.plan(
            schedule: item.schedule,
            hour: item.reminderHour,
            minute: item.reminderMinute,
            leadDays: item.leadDays,
            remindDailyWhenOverdue: item.remindDailyWhenOverdue,
            snoozedUntil: item.snoozedUntil,
            now: .now
        )

        for reminder in plan {
            let content = UNMutableNotificationContent()
            content.sound = .default
            content.categoryIdentifier = ID.itemCategory
            content.userInfo = [ID.itemKey: item.id.uuidString]
            content.threadIdentifier = item.id.uuidString

            switch reminder.kind {
            case .headsUp:
                let days = item.leadDays
                content.title = "\(item.name) is due \(days == 1 ? "tomorrow" : "in \(days) days")"
                if item.tracksSpares && item.spareCount == 0 {
                    content.body = "You're out of spares, so now's a good time to reorder."
                } else if item.isLowOnSpares {
                    content.body = "Only \(pluralized(item.spareCount, "spare")) left. Worth reordering before the due day."
                } else {
                    content.body = "Just a heads-up so you're ready."
                }
            case .due:
                content.title = "Time to replace: \(item.name)"
                content.body = "It's been \(pluralized(item.intervalValue, item.intervalUnit.label(for: 1))). Long-press and tap Replaced it once you're done."
            case .overdue(let day):
                content.title = "\(item.name) is overdue"
                content.body = "Overdue by \(pluralized(day, "day")). Don't forget to swap it out."
            }

            let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: reminder.fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            let request = UNNotificationRequest(
                identifier: identifier(item.id, reminder.identifierSuffix),
                content: content,
                trigger: trigger
            )
            center.add(request)
        }
    }

    func cancel(_ item: TrackedItem) {
        let ids = ReminderPlanner.allIdentifierSuffixes().map { identifier(item.id, $0) }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    /// Rebuilds every item's notifications and the app badge. Called whenever the app becomes active.
    func refreshAll() {
        guard let context = container?.mainContext else { return }
        let items = (try? context.fetch(FetchDescriptor<TrackedItem>())) ?? []
        center.removeAllPendingNotificationRequests()
        for item in items { reschedule(item) }
        updateBadge(items: items)
        Task { await refreshAuthorizationStatus() }
    }

    func updateBadge(items: [TrackedItem]? = nil) {
        let all = items ?? ((try? container?.mainContext.fetch(FetchDescriptor<TrackedItem>())) ?? [])
        let overdue = all.filter { !$0.isPaused && $0.schedule.daysRemaining(at: .now) < 0 }.count
        center.setBadgeCount(overdue) { _ in }
    }

    func notifyLowStock(_ item: TrackedItem) {
        guard item.isLowOnSpares else { return }
        let content = UNMutableNotificationContent()
        content.title = "Running low: \(item.name)"
        content.body = item.spareCount == 0
            ? "That was your last spare. Time to reorder."
            : "Only \(pluralized(item.spareCount, "spare")) left. Time to reorder?"
        content.sound = .default
        content.categoryIdentifier = ID.lowStockCategory
        content.userInfo = [ID.itemKey: item.id.uuidString]
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 2, repeats: false)
        center.add(UNNotificationRequest(identifier: identifier(item.id, "low-stock"), content: content, trigger: trigger))
    }

    // MARK: Item actions shared by the UI and notification actions

    /// Logs a replacement and returns what's needed to take it back.
    @discardableResult
    func markReplaced(_ item: TrackedItem, on date: Date = .now, note: String = "") -> ReplacementUndo {
        let previousStartDate = item.startDate
        let previousSnoozedUntil = item.snoozedUntil
        let previousSpareCount = item.spareCount
        let log = item.markReplaced(on: date, note: note)
        try? item.modelContext?.save()
        reschedule(item)
        notifyLowStock(item)
        updateBadge()
        return ReplacementUndo(
            item: item,
            log: log,
            itemName: item.name,
            nextDueDate: item.dueDate,
            previousStartDate: previousStartDate,
            previousSnoozedUntil: previousSnoozedUntil,
            previousSpareCount: previousSpareCount
        )
    }

    /// Reverts a replacement logged moments ago: restores the cycle and spares and drops the log entry.
    func undo(_ replacement: ReplacementUndo) {
        let item = replacement.item
        guard item.modelContext != nil, !item.isDeleted else { return }
        item.startDate = replacement.previousStartDate
        item.snoozedUntil = replacement.previousSnoozedUntil
        item.spareCount = replacement.previousSpareCount
        let log = replacement.log
        if log.modelContext != nil, !log.isDeleted {
            item.logs?.removeAll { $0.persistentModelID == log.persistentModelID }
            item.modelContext?.delete(log)
        }
        try? item.modelContext?.save()
        let lowStockID = identifier(item.id, "low-stock")
        center.removePendingNotificationRequests(withIdentifiers: [lowStockID])
        center.removeDeliveredNotifications(withIdentifiers: [lowStockID])
        reschedule(item)
        updateBadge()
    }

    func snooze(_ item: TrackedItem, for interval: TimeInterval) {
        item.snoozedUntil = Date.now.addingTimeInterval(interval)
        try? item.modelContext?.save()
        reschedule(item)
    }

    func setPaused(_ item: TrackedItem, _ paused: Bool) {
        item.isPaused = paused
        if paused { item.snoozedUntil = nil }
        try? item.modelContext?.save()
        reschedule(item)
        updateBadge()
    }

    /// The next notification that will fire for an item, for display in the detail screen.
    func nextReminderDate(for item: TrackedItem, now: Date = .now) -> Date? {
        guard !item.isPaused else { return nil }
        return ReminderPlanner.plan(
            schedule: item.schedule,
            hour: item.reminderHour,
            minute: item.reminderMinute,
            leadDays: item.leadDays,
            remindDailyWhenOverdue: item.remindDailyWhenOverdue,
            snoozedUntil: item.snoozedUntil,
            now: now
        ).map(\.fireDate).min()
    }

    /// Sends a sample reminder a few seconds from now so people can check sounds and banners.
    func sendTestNotification() async {
        await requestAuthorizationIfNeeded()
        let content = UNMutableNotificationContent()
        content.title = "Reminder Genie is all set"
        content.body = "This is what a reminder looks like. Long-press real ones to log a replacement or snooze."
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)
        try? await center.add(UNNotificationRequest(identifier: "test-notification", content: content, trigger: trigger))
    }

    func delete(_ item: TrackedItem) {
        cancel(item)
        item.modelContext?.delete(item)
        try? container?.mainContext.save()
        updateBadge()
    }

    // MARK: Private

    private func identifier(_ id: UUID, _ suffix: String) -> String {
        "\(id.uuidString)-\(suffix)"
    }

    private func registerCategories() {
        let done = UNNotificationAction(identifier: ID.markDone, title: "Replaced it", options: [], icon: UNNotificationActionIcon(systemImageName: "checkmark.circle"))
        let hour = UNNotificationAction(identifier: ID.snoozeHour, title: "Snooze 1 hour", options: [], icon: UNNotificationActionIcon(systemImageName: "clock"))
        let day = UNNotificationAction(identifier: ID.snoozeDay, title: "Remind me tomorrow", options: [], icon: UNNotificationActionIcon(systemImageName: "moon.zzz"))
        let itemCategory = UNNotificationCategory(identifier: ID.itemCategory, actions: [done, hour, day], intentIdentifiers: [])
        let lowStock = UNNotificationCategory(identifier: ID.lowStockCategory, actions: [], intentIdentifiers: [])
        center.setNotificationCategories([itemCategory, lowStock])
    }

    func item(withID id: UUID) -> TrackedItem? {
        guard let context = container?.mainContext else { return nil }
        let descriptor = FetchDescriptor<TrackedItem>(predicate: #Predicate<TrackedItem> { $0.id == id })
        return try? context.fetch(descriptor).first
    }

    fileprivate func handle(action: String, itemID: UUID) {
        guard let item = item(withID: itemID) else { return }
        switch action {
        case ID.markDone:
            markReplaced(item)
        case ID.snoozeHour:
            snooze(item, for: 60 * 60)
        case ID.snoozeDay:
            snooze(item, for: 24 * 60 * 60)
        case UNNotificationDefaultActionIdentifier:
            pendingItemID = itemID
        default:
            break
        }
    }
}

/// Everything needed to take back a replacement that was just logged.
struct ReplacementUndo: Identifiable {
    let id = UUID()
    let item: TrackedItem
    let log: ReplacementLog
    /// Copied so the confirmation can still be shown if the item is deleted meanwhile.
    let itemName: String
    let nextDueDate: Date
    let previousStartDate: Date
    let previousSnoozedUntil: Date?
    let previousSpareCount: Int
}

extension NotificationManager: UNUserNotificationCenterDelegate {
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let action = response.actionIdentifier
        guard let raw = response.notification.request.content.userInfo[ID.itemKey] as? String,
              let id = UUID(uuidString: raw) else { return }
        await MainActor.run {
            NotificationManager.shared.handle(action: action, itemID: id)
        }
    }
}
