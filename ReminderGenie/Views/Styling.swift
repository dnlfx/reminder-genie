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
        case .dueSoon: .yellow
        case .ok: .green
        }
    }
}

/// A circular countdown ring with the item's icon in the middle.
struct ProgressRing: View {
    var progress: Double
    var tint: Color
    var symbolName: String
    var lineWidth: CGFloat = 5
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            Circle()
                .stroke(tint.opacity(0.2), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeOut, value: progress)
            Image(systemName: symbolName)
                .font(.system(size: size * 0.38, weight: .semibold))
                .foregroundStyle(tint)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

enum DueText {
    /// "Due in 3 days", "Due today", "Overdue by 2 days".
    static func short(daysRemaining days: Int) -> String {
        switch days {
        case ..<(-1): "Overdue by \(-days) days"
        case -1: "Overdue by 1 day"
        case 0: "Due today"
        case 1: "Due tomorrow"
        default: "Due in \(days) days"
        }
    }
}
