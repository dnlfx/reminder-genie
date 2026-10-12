import SwiftData
import SwiftUI

@main
struct ReminderGenieApp: App {
    init() {
        NotificationManager.shared.configure(container: AppModelContainer.shared)
    }

    var body: some Scene {
        WindowGroup {
            ItemListView()
                .environmentObject(NotificationManager.shared)
        }
        .modelContainer(AppModelContainer.shared)
    }
}

/// The one SwiftData container, shared by the UI, notification actions and App Intents (Siri, Shortcuts).
enum AppModelContainer {
    static let shared: ModelContainer = {
        do {
            return try ModelContainer(for: TrackedItem.self, ReplacementLog.self)
        } catch {
            fatalError("Could not open the Reminder Genie database: \(error)")
        }
    }()
}

enum AppSettings {
    static let defaultHourKey = "defaultReminderHour"
    static let defaultMinuteKey = "defaultReminderMinute"
    static let dueSoonDaysKey = "dueSoonDays"
}
