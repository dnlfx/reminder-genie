import AppIntents
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
    @State private var sentTest = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent {
                        Text(statusText)
                            .foregroundStyle(statusColor)
                    } label: {
                        Label("Notifications", systemImage: "bell.badge")
                    }
                    switch notifications.authorizationStatus {
                    case .notDetermined:
                        Button("Turn On Notifications") {
                            Task { await notifications.requestAuthorizationIfNeeded() }
                        }
                    case .denied:
                        Button("Open iOS Settings") {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                                openURL(url)
                            }
                        }
                    default:
                        Button {
                            Task {
                                await notifications.sendTestNotification()
                                sentTest = true
                            }
                        } label: {
                            Label(sentTest ? "Test sent. Check in a few seconds" : "Send a Test Notification", systemImage: sentTest ? "checkmark" : "paperplane")
                        }
                        .disabled(sentTest)
                    }
                } header: {
                    Text("Notifications")
                } footer: {
                    Text("Reminders are scheduled on this iPhone, so they work offline. Long-press a reminder to log a replacement or snooze without opening the app.")
                }

                Section {
                    DatePicker("Default reminder time", selection: defaultTime, displayedComponents: .hourAndMinute)
                    Stepper("\"Due soon\" within \(pluralized(dueSoonDays, "day"))", value: $dueSoonDays, in: 1...14)
                } header: {
                    Text("Defaults")
                } footer: {
                    Text("The default time is used for new items. Each item can have its own time.")
                }

                Section {
                    SiriTipView(intent: MarkReplacedIntent())
                    ShortcutsLink()
                        .frame(maxWidth: .infinity)
                } header: {
                    Text("Siri & Shortcuts")
                } footer: {
                    Text("Say “Log a replacement in Reminder Genie” or “What's due in Reminder Genie”.")
                }

                Section {
                    LabeledContent("Version", value: versionText)
                    Label("Your data stays on this iPhone. No account, no tracking.", systemImage: "lock.shield")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("About")
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

    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }

    private var statusText: String {
        switch notifications.authorizationStatus {
        case .authorized, .provisional, .ephemeral: return "On"
        case .denied: return "Off"
        case .notDetermined: return "Not set up"
        @unknown default: return "Unknown"
        }
    }

    private var statusColor: Color {
        switch notifications.authorizationStatus {
        case .authorized, .provisional, .ephemeral: return .green
        case .denied: return .red
        default: return .secondary
        }
    }
}
