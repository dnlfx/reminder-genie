import Foundation
import OSLog
import SwiftData
import UserNotifications

/// Failures that would otherwise be silent, readable in Console.app when chasing a bug report.
enum Log {
    private static let subsystem = Bundle.main.bundleIdentifier ?? "ReminderGenie"
    static let notifications = Logger(subsystem: subsystem, category: "notifications")
    static let data = Logger(subsystem: subsystem, category: "data")
}

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
        // Only the status crosses back to the main actor; the settings object isn't Sendable.
        authorizationStatus = await withCheckedContinuation { continuation in
            center.getNotificationSettings { @Sendable settings in
                continuation.resume(returning: settings.authorizationStatus)
            }
        }
    }

    func requestAuthorizationIfNeeded() async {
        await refreshAuthorizationStatus()
        guard authorizationStatus == .notDetermined else { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            center.requestAuthorization(options: [.alert, .sound, .badge]) { @Sendable _, error in
                if let error { Log.notifications.error("Couldn't request permission: \(error.localizedDescription, privacy: .public)") }
                continuation.resume()
            }
        }
        await refreshAuthorizationStatus()
    }

    // MARK: Scheduling

    /// Call after anything about an item changes. Clears its delivered reminders, since they
    /// describe the old state, then rebuilds every pending reminder and the badge.
    func reschedule(_ item: TrackedItem) {
        center.removeDeliveredNotifications(withIdentifiers: identifiers(for: item.id))
        rebuildSchedule()
    }

    private func cancel(_ item: TrackedItem) {
        let ids = identifiers(for: item.id)
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    /// Rebuilds every item's notifications and the app badge. Called whenever the app becomes active.
    func refreshAll() {
        rebuildSchedule()
        Task { await refreshAuthorizationStatus() }
    }

    /// Replaces all pending item reminders in one pass. Doing it for every item at once keeps the
    /// soonest reminders within iOS's pending limit and lets each one carry the badge count for
    /// the moment it's delivered, so the badge stays right while the app is closed.
    private func rebuildSchedule() {
        let items = fetchAllItems()
        center.removePendingNotificationRequests(withIdentifiers: items.flatMap { identifiers(for: $0.id) })

        let now = Date.now
        let active = items.filter { !$0.isPaused }
        let schedules = active.map(\.schedule)
        let planned = active
            .flatMap { item in plan(for: item, now: now).map { (item: item, reminder: $0) } }
            .sorted { $0.reminder.fireDate < $1.reminder.fireDate }
            .prefix(ReminderPlanner.pendingLimit)

        for (item, reminder) in planned {
            let content = makeContent(for: reminder, of: item)
            content.badge = NSNumber(value: schedules.filter { $0.needsReplacing(at: reminder.fireDate) }.count)
            let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: reminder.fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            let request = UNNotificationRequest(
                identifier: identifier(item.id, reminder.identifierSuffix),
                content: content,
                trigger: trigger
            )
            // @Sendable so the handler isn't treated as main-actor code when iOS calls it on a background queue.
            center.add(request) { @Sendable error in
                if let error { Log.notifications.error("Couldn't schedule a reminder: \(error.localizedDescription, privacy: .public)") }
            }
        }
        updateBadge(items: items)
    }

    private func plan(for item: TrackedItem, now: Date) -> [PlannedReminder] {
        ReminderPlanner.plan(
            schedule: item.schedule,
            hour: item.reminderHour,
            minute: item.reminderMinute,
            leadDays: item.leadDays,
            remindDailyWhenOverdue: item.remindDailyWhenOverdue,
            snoozedUntil: item.snoozedUntil,
            now: now
        )
    }

    private func makeContent(for reminder: PlannedReminder, of item: TrackedItem) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.sound = .default
        content.categoryIdentifier = ID.itemCategory
        content.userInfo = [ID.itemKey: item.id.uuidString]
        content.threadIdentifier = item.id.uuidString

        switch reminder.kind {
        case .headsUp:
            // Counted from the fire date, since a snoozed heads-up fires later than planned.
            let days = item.schedule.daysRemaining(at: reminder.fireDate)
            content.title = switch days {
            case ...0: "\(item.name) is due today"
            case 1: "\(item.name) is due tomorrow"
            default: "\(item.name) is due in \(days) days"
            }
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
        return content
    }

    /// Sets the badge to the number of items due today or overdue.
    func updateBadge(items: [TrackedItem]? = nil) {
        let all = items ?? fetchAllItems()
        let now = Date.now
        let count = all.filter { !$0.isPaused && $0.schedule.needsReplacing(at: now) }.count
        center.setBadgeCount(count) { @Sendable error in
            if let error { Log.notifications.error("Couldn't set the badge: \(error.localizedDescription, privacy: .public)") }
        }
    }

    private func fetchAllItems() -> [TrackedItem] {
        guard let context = container?.mainContext else { return [] }
        do {
            return try context.fetch(FetchDescriptor<TrackedItem>())
        } catch {
            Log.data.error("Couldn't load items: \(error.localizedDescription, privacy: .public)")
            return []
        }
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
        save(item.modelContext)
        reschedule(item)
        notifyLowStock(item)
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
        save(item.modelContext)
        let lowStockID = identifier(item.id, "low-stock")
        center.removePendingNotificationRequests(withIdentifiers: [lowStockID])
        center.removeDeliveredNotifications(withIdentifiers: [lowStockID])
        reschedule(item)
    }

    /// Removes a history entry. Deleting the entry that started the current cycle rewinds the
    /// countdown to the entry before it, so a mistaken log can be taken back after Undo has gone.
    func deleteLog(_ log: ReplacementLog, from item: TrackedItem) {
        let remaining = item.sortedLogs.filter { $0.persistentModelID != log.persistentModelID }
        if log.date == item.startDate, let previous = remaining.first {
            item.startDate = previous.date
            item.snoozedUntil = nil
        }
        item.logs?.removeAll { $0.persistentModelID == log.persistentModelID }
        item.modelContext?.delete(log)
        save(item.modelContext)
        reschedule(item)
    }

    func snooze(_ item: TrackedItem, for interval: TimeInterval) {
        item.snoozedUntil = Date.now.addingTimeInterval(interval)
        save(item.modelContext)
        reschedule(item)
    }

    func setPaused(_ item: TrackedItem, _ paused: Bool) {
        item.isPaused = paused
        if paused { item.snoozedUntil = nil }
        save(item.modelContext)
        reschedule(item)
    }

    /// The next notification that will fire for an item, for display in the detail screen.
    func nextReminderDate(for item: TrackedItem, now: Date = .now) -> Date? {
        guard !item.isPaused else { return nil }
        return plan(for: item, now: now).map(\.fireDate).min()
    }

    /// Sends a sample reminder a few seconds from now so people can check sounds and banners.
    func sendTestNotification() async {
        await requestAuthorizationIfNeeded()
        let content = UNMutableNotificationContent()
        content.title = "Reminder Genie is all set"
        content.body = "This is what a reminder looks like. Long-press real ones to log a replacement or snooze."
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)
        center.add(UNNotificationRequest(identifier: "test-notification", content: content, trigger: trigger)) { @Sendable error in
            if let error { Log.notifications.error("Couldn't send the test notification: \(error.localizedDescription, privacy: .public)") }
        }
    }

    func delete(_ item: TrackedItem) {
        cancel(item)
        item.modelContext?.delete(item)
        save(container?.mainContext)
        // Other items' pending reminders carry badge counts that included this one.
        rebuildSchedule()
    }

    // MARK: Private

    private func save(_ context: ModelContext?) {
        guard let context, context.hasChanges else { return }
        do {
            try context.save()
        } catch {
            Log.data.error("Couldn't save: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func identifier(_ id: UUID, _ suffix: String) -> String {
        "\(id.uuidString)-\(suffix)"
    }

    /// Every reminder id an item can have, so they can all be cleared at once.
    private func identifiers(for id: UUID) -> [String] {
        ReminderPlanner.allIdentifierSuffixes().map { identifier(id, $0) }
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

// iOS calls these on the main thread and expects the completion handler there too. These used to be
// nonisolated async methods, which in Swift 6 finish (and call the handler) on a background thread,
// crashing the app when "Replaced it" was tapped. Synchronous main-actor methods keep it all on main.
extension NotificationManager: @preconcurrency UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let raw = response.notification.request.content.userInfo[ID.itemKey] as? String,
           let id = UUID(uuidString: raw) {
            handle(action: response.actionIdentifier, itemID: id)
        }
        completionHandler()
    }
}
