import SwiftData
import SwiftUI

struct ItemEditorView: View {
    enum Mode {
        case create(ItemDraft)
        case edit(TrackedItem)
    }

    let mode: Mode
    /// Called after saving. When nil the editor dismisses itself.
    var onFinish: (() -> Void)?

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var notifications: NotificationManager
    @State private var draft: ItemDraft

    init(mode: Mode, onFinish: (() -> Void)? = nil) {
        self.mode = mode
        self.onFinish = onFinish
        switch mode {
        case .create(let draft): _draft = State(initialValue: draft)
        case .edit(let item): _draft = State(initialValue: ItemDraft(item: item))
        }
    }

    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 12) {
                    ProgressRing(progress: 1, tint: draft.color.color, symbolName: draft.symbolName, size: 40)
                    TextField("Name, e.g. Contact lenses", text: $draft.name)
                        .font(.headline)
                }
                IconPicker(selection: $draft.symbolName, tint: draft.color.color)
                ItemColorPicker(selection: $draft.color)
            }

            Section {
                Stepper(value: $draft.intervalValue, in: 1...365) {
                    Text("Every \(draft.intervalValue) \(draft.intervalUnit.label(for: draft.intervalValue))")
                }
                Picker("Unit", selection: $draft.intervalUnit) {
                    ForEach(IntervalUnit.allCases) { unit in
                        Text(unit.rawValue.capitalized).tag(unit)
                    }
                }
                .pickerStyle(.segmented)
                DatePicker(isEditing ? "Current one started" : "Last replaced", selection: $draft.startDate, in: ...Date.now, displayedComponents: .date)
            } header: {
                Text("How often")
            } footer: {
                Text("Next due \(draft.previewDueDate.formatted(date: .complete, time: .omitted)).")
            }

            Section("Reminders") {
                DatePicker("Remind me at", selection: $draft.reminderTime, displayedComponents: .hourAndMinute)
                Stepper(value: $draft.leadDays, in: 0...30) {
                    Text(draft.leadDays == 0 ? "No heads-up" : "Heads-up \(draft.leadDays) \(draft.leadDays == 1 ? "day" : "days") before")
                }
                Toggle("Keep reminding daily if overdue", isOn: $draft.remindDailyWhenOverdue)
                if isEditing {
                    Toggle("Pause reminders", isOn: $draft.isPaused)
                }
            }

            Section {
                Toggle("Track spares", isOn: $draft.tracksSpares.animation())
                if draft.tracksSpares {
                    Stepper("Spares on hand: \(draft.spareCount)", value: $draft.spareCount, in: 0...999)
                    Stepper("Warn at \(draft.lowStockThreshold) or fewer", value: $draft.lowStockThreshold, in: 0...50)
                }
            } header: {
                Text("Inventory")
            } footer: {
                Text("Count how many you have left, like boxes of lenses or spare filters, and get a nudge to reorder.")
            }

            Section("Notes") {
                TextField("Brand, size, prescription, where to buy…", text: $draft.notes, axis: .vertical)
                    .lineLimit(3...8)
            }
        }
        .navigationTitle(isEditing ? "Edit item" : "New item")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isEditing || onFinish == nil {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save", action: save)
                    .disabled(!draft.isValid)
            }
        }
    }

    private func save() {
        let item: TrackedItem
        switch mode {
        case .edit(let existing):
            item = existing
            draft.apply(to: item)
        case .create:
            item = TrackedItem(
                name: draft.name,
                symbolName: draft.symbolName,
                colorName: draft.color.rawValue,
                intervalValue: draft.intervalValue,
                intervalUnit: draft.intervalUnit,
                startDate: draft.startDate
            )
            draft.apply(to: item)
            context.insert(item)
            // The first cycle counts as a replacement in the history.
            let log = ReplacementLog(date: draft.startDate, note: "Started tracking")
            context.insert(log)
            log.item = item
        }
        try? context.save()

        Task {
            await notifications.requestAuthorizationIfNeeded()
            notifications.reschedule(item)
            notifications.updateBadge()
        }

        if let onFinish {
            onFinish()
        } else {
            dismiss()
        }
    }
}

private struct IconPicker: View {
    @Binding var selection: String
    var tint: Color

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(ItemTemplate.symbolChoices, id: \.self) { symbol in
                    Button {
                        selection = symbol
                    } label: {
                        Image(systemName: symbol)
                            .font(.title3)
                            .frame(width: 40, height: 40)
                            .background(selection == symbol ? tint.opacity(0.25) : Color.secondary.opacity(0.1), in: Circle())
                            .foregroundStyle(selection == symbol ? tint : .secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(symbol)
                    .accessibilityAddTraits(selection == symbol ? .isSelected : [])
                }
            }
            .padding(.vertical, 4)
        }
    }
}

private struct ItemColorPicker: View {
    @Binding var selection: ItemColor

    var body: some View {
        HStack {
            ForEach(ItemColor.allCases) { color in
                Button {
                    selection = color
                } label: {
                    Circle()
                        .fill(color.color)
                        .frame(width: 26, height: 26)
                        .overlay {
                            if selection == color {
                                Circle().stroke(Color.primary, lineWidth: 2).padding(-4)
                            }
                        }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(color.rawValue)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 6)
    }
}
