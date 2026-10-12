import SwiftUI

extension ItemColor {
    var color: Color {
        switch self {
        case .blue: .blue
        case .teal: .teal
        case .green: .green
        case .yellow: .yellow
        case .orange: .orange
        case .red: .red
        case .pink: .pink
        case .purple: .purple
        case .indigo: .indigo
        case .gray: .gray
        }
    }
}

extension ItemStatus {
    var tint: Color {
        switch self {
        case .overdue: .red
        case .dueToday: .orange
        case .dueSoon: .orange
        case .ok: .green
        }
    }
}

extension TrackedItem {
    /// The ring color: the item's own color while things are fine, a warning color as the due date nears.
    func tint(for status: ItemStatus) -> Color {
        if isPaused { return .gray }
        return status == .ok ? color.color : status.tint
    }
}

/// A circular countdown ring with the item's icon in the middle.
struct ProgressRing: View {
    var progress: Double
    var tint: Color
    var symbolName: String?
    var lineWidth: CGFloat = 5
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            Circle()
                .stroke(tint.opacity(0.18), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(tint.gradient, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.smooth, value: progress)
            if let symbolName {
                Image(systemName: symbolName)
                    .font(.system(size: size * 0.38, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// A small rounded tag, used for spares, snooze and pause states.
struct StatusPill: View {
    var text: String
    var systemImage: String
    var tint: Color = .secondary

    var body: some View {
        Label(text, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .labelStyle(.titleAndIcon)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .foregroundStyle(tint)
            .background(tint.opacity(0.14), in: Capsule())
    }
}

enum DueText {
    /// "Due in 3 days", "Due today", "Overdue by 2 days".
    static func short(daysRemaining days: Int) -> String {
        switch days {
        case ..<0: "Overdue by \(pluralized(-days, "day"))"
        case 0: "Due today"
        case 1: "Due tomorrow"
        default: "Due in \(days) days"
        }
    }

    /// "Snoozed until 3:00 PM", "Snoozed until tomorrow, 9:00 AM", "Snoozed until Oct 15".
    static func snoozed(until date: Date, calendar: Calendar = .current) -> String {
        let time = date.formatted(date: .omitted, time: .shortened)
        if calendar.isDateInToday(date) { return "Snoozed until \(time)" }
        if calendar.isDateInTomorrow(date) { return "Snoozed until tomorrow, \(time)" }
        return "Snoozed until \(date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()))"
    }

    /// "Today at 9:00 AM", "Tomorrow at 9:00 AM", "Fri, Oct 24 at 9:00 AM".
    static func reminder(_ date: Date, calendar: Calendar = .current) -> String {
        let time = date.formatted(date: .omitted, time: .shortened)
        if calendar.isDateInToday(date) { return "Today at \(time)" }
        if calendar.isDateInTomorrow(date) { return "Tomorrow at \(time)" }
        return "\(date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())) at \(time)"
    }
}
