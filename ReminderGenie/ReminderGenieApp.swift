import SwiftData
import SwiftUI

@main
struct ReminderGenieApp: App {
    init() {
        // Without the real database, leave the reminders and badge already scheduled alone.
        if AppModelContainer.loadErrorMessage == nil {
            NotificationManager.shared.configure(container: AppModelContainer.shared)
        }
        #if DEBUG
        if UITestSupport.isActive {
            UITestSupport.prepare(container: AppModelContainer.shared)
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            if let message = AppModelContainer.loadErrorMessage {
                StoreErrorView(message: message)
            } else {
                ItemListView()
                    .environmentObject(NotificationManager.shared)
            }
        }
        .modelContainer(AppModelContainer.shared)
    }
}

/// The one SwiftData container, shared by the UI, notification actions and App Intents (Siri, Shortcuts).
enum AppModelContainer {
    static var shared: ModelContainer { loaded.container }

    /// Set when the database couldn't be opened. The app then shows `StoreErrorView` on an empty
    /// in-memory store rather than crashing on every launch, and leaves the file on disk untouched.
    static var loadErrorMessage: String? { loaded.errorMessage }

    private static let loaded: (container: ModelContainer, errorMessage: String?) = {
        let schema = Schema(versionedSchema: ReminderGenieSchemaV1.self)
        var inMemory = false
        #if DEBUG
        // UI tests start from a clean slate every launch and never touch real data.
        inMemory = UITestSupport.isActive
        #endif
        do {
            let container = try ModelContainer(
                for: schema,
                migrationPlan: ReminderGenieMigrationPlan.self,
                configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
            )
            return (container, nil)
        } catch {
            Log.data.fault("Couldn't open the database: \(String(describing: error), privacy: .public)")
            let memory = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
            do {
                return (try ModelContainer(for: schema, configurations: memory), error.localizedDescription)
            } catch {
                fatalError("Could not create even an in-memory database: \(error)")
            }
        }
    }()
}

enum AppSettings {
    static let defaultHourKey = "defaultReminderHour"
    static let defaultMinuteKey = "defaultReminderMinute"
    static let dueSoonDaysKey = "dueSoonDays"
}
