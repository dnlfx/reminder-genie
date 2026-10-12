import SwiftUI
import UIKit

/// A floating confirmation with an Undo button, shown after a replacement is logged from the list.
struct UndoToast: View {
    var title: String
    var subtitle: String
    var onUndo: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.title2)
                .foregroundStyle(.green)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Button("Undo", action: onUndo)
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
        }
        .padding(.leading, 16)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .background(.regularMaterial, in: Capsule())
        .shadow(color: .black.opacity(0.12), radius: 16, y: 6)
        .padding(.horizontal)
        .padding(.bottom, 8)
        .accessibilityElement(children: .combine)
        .accessibilityAction(named: "Undo", onUndo)
    }
}

/// Shown at the top of the list when notifications are turned off, since the app is useless without them.
struct NotificationsOffBanner: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "bell.slash.fill")
                .font(.title3)
                .foregroundStyle(.orange)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text("Notifications are off")
                    .font(.subheadline.weight(.semibold))
                Text("You won't get reminders until you allow notifications for Reminder Genie.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Turn On in Settings") {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                        openURL(url)
                    }
                }
                .font(.footnote.weight(.semibold))
                .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 4)
    }
}

/// The headline at the top of the list: what needs attention right now.
struct SummaryCard: View {
    var overdue: Int
    var dueSoon: Int
    var next: (name: String, days: Int)?

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: symbol)
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(tint.gradient, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(headline)
                    .font(.headline)
                if let detail {
                    Text(detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }

    private var symbol: String {
        if overdue > 0 { return "exclamationmark.circle" }
        if dueSoon > 0 { return "clock" }
        return "checkmark"
    }

    private var tint: Color {
        if overdue > 0 { return .red }
        if dueSoon > 0 { return .orange }
        return .green
    }

    private var headline: String {
        if overdue > 0 { return "\(pluralized(overdue, "item")) overdue" }
        if dueSoon > 0 { return "\(pluralized(dueSoon, "item")) due soon" }
        return "You're all caught up"
    }

    private var detail: String? {
        if overdue > 0 && dueSoon > 0 { return "Plus \(dueSoon) due soon" }
        guard let next else { return nil }
        if overdue > 0 { return nil }
        return "Next up: \(next.name), \(DueText.short(daysRemaining: next.days).lowercased())"
    }
}
