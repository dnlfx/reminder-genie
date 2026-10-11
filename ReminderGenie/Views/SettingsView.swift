import SwiftUI
import UIKit
import UserNotifications

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @EnvironmentObject private var notifications: NotificationManager
    @AppStorage(AppSettings.defaultHourKey) private var defaultHour = 9
    @AppStorage(AppSettings.defaultMinuteKey) private var defaultMinute = 0
    @AppStorage(AppSettings.dueSoonDaysKey) private var dueSoonDays = 3

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Status", value: statusText)
                    switch notifications.authorizationStatus {
                    case .notDetermined:
                        Button("Turn on notifications") {
                            Task { await notifications.requestAuthorizationIfNeeded() }
                        }
                    case .denied:
                        Button("Open iOS Settings") {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                                openURL(url)
                            }
                        }
                    default:
                        EmptyView()
                    }
                } header: {
                    Text("Notifications")
                } footer: {
                    Text("Reminders are scheduled on this iPhone, so they work offline and nothing leaves your device.")
                }

                Section {
                    DatePicker("Default reminder time", selection: defaultTime, displayedComponents: .hourAndMinute)
                    Stepper("\"Due soon\" within \(dueSoonDays) \(dueSoonDays == 1 ? "day" : "days")", value: $dueSoonDays, in: 1...14)
                } header: {
                    Text("Defaults")
                } footer: {
                    Text("The default time is used for new items. Each item can have its own time.")
                }

                Section("About") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await notifications.refreshAuthorizationStatus() }
        }
    }

    private var defaultTime: Binding<Date> {
        Binding {
            ItemDraft.time(hour: defaultHour, minute: defaultMinute)
        } set: { newValue in
            let parts = Calendar.current.dateComponents([.hour, .minute], from: newValue)
            defaultHour = parts.hour ?? 9
            defaultMinute = parts.minute ?? 0
        }
    }

    private var statusText: String {
        switch notifications.authorizationStatus {
        case .authorized, .provisional, .ephemeral: return "On"
        case .denied: return "Off"
        case .notDetermined: return "Not set up"
        @unknown default: return "Unknown"
        }
    }
}
