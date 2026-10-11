import SwiftData
import SwiftUI

@main
struct ReminderGenieApp: App {
    let container: ModelContainer

    init() {
        do {
            container = try ModelContainer(for: TrackedItem.self, ReplacementLog.self)
        } catch {
            fatalError("Could not open the Reminder Genie database: \(error)")
        }
        NotificationManager.shared.configure(container: container)
    }

    var body: some Scene {
        WindowGroup {
            ItemListView()
                .environmentObject(NotificationManager.shared)
        }
        .modelContainer(container)
    }
}

enum AppSettings {
    static let defaultHourKey = "defaultReminderHour"
    static let defaultMinuteKey = "defaultReminderMinute"
    static let dueSoonDaysKey = "dueSoonDays"
}
