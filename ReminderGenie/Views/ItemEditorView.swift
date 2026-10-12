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
    @State private var original: ItemDraft
    @State private var confirmingDiscard = false
    @FocusState private var nameFocused: Bool

    init(mode: Mode, onFinish: (() -> Void)? = nil) {
        self.mode = mode
        self.onFinish = onFinish
        let initial: ItemDraft
        switch mode {
        case .create(let draft): initial = draft
        case .edit(let item): initial = ItemDraft(item: item)
        }
        _draft = State(initialValue: initial)
        _original = State(initialValue: initial)
    }

    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    private var hasChanges: Bool { draft != original }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 12) {
                    Image(systemName: draft.symbolName)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(draft.color.color.gradient, in: Circle())
                        .accessibilityHidden(true)
                    TextField("Name, e.g. Contact lenses", text: $draft.name)
                        .font(.headline)
                        .textInputAutocapitalization(.sentences)
                        .submitLabel(.done)
                        .focused($nameFocused)
                }
                ItemColorPicker(selection: $draft.color)
                IconPicker(selection: $draft.symbolName, tint: draft.color.color)
            }

            Section {
                Stepper(value: $draft.intervalValue, in: draft.intervalUnit.allowedRange) {
                    Text("Every \(pluralized(draft.intervalValue, draft.intervalUnit.label(for: 1)))")
                        .monospacedDigit()
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

            Section {
                DatePicker("Remind me at", selection: $draft.reminderTime, displayedComponents: .hourAndMinute)
                Stepper(value: $draft.leadDays, in: 0...30) {
                    Text(draft.leadDays == 0 ? "No heads-up" : "Heads-up \(pluralized(draft.leadDays, "day")) before")
                }
                Toggle("Keep reminding daily if overdue", isOn: $draft.remindDailyWhenOverdue)
                if isEditing {
                    Toggle("Pause reminders", isOn: $draft.isPaused)
                }
            } header: {
                Text("Reminders")
            } footer: {
                Text(reminderSummary)
            }

            Section {
                Toggle("Track spares", isOn: $draft.tracksSpares.animation())
                if draft.tracksSpares {
                    Stepper("Spares on hand: \(draft.spareCount)", value: $draft.spareCount, in: 0...999)
                    Stepper("Warn at \(draft.lowStockThreshold) or fewer", value: $draft.lowStockThreshold, in: 0...50)
                }
                TextField("Reorder link (optional)", text: $draft.reorderLink)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } header: {
                Text("Inventory")
            } footer: {
                if !draft.isReorderLinkValid {
                    Text("That doesn't look like a web address. Try something like amazon.com/your-item.")
                        .foregroundStyle(.red)
                } else {
                    Text("Count how many you have left, like boxes of lenses or spare filters, and get a nudge to reorder. Add a link to reorder in one tap.")
                }
            }

            Section("Notes") {
                TextField("Brand, size, prescription, where to buy…", text: $draft.notes, axis: .vertical)
                    .lineLimit(3...8)
            }
        }
        .navigationTitle(isEditing ? "Edit Item" : "New Item")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(hasChanges && (isEditing || onFinish == nil))
        .onChange(of: draft.intervalUnit) { _, unit in
            draft.intervalValue = min(max(draft.intervalValue, unit.allowedRange.lowerBound), unit.allowedRange.upperBound)
        }
        .task {
            // A blank custom item starts with the keyboard up.
            guard !isEditing, draft.name.isEmpty else { return }
            try? await Task.sleep(for: .milliseconds(450))
            nameFocused = true
        }
        .toolbar {
            if isEditing || onFinish == nil {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        if hasChanges { confirmingDiscard = true } else { dismiss() }
                    }
                    .confirmationDialog("Discard your changes?", isPresented: $confirmingDiscard, titleVisibility: .visible) {
                        Button("Discard Changes", role: .destructive) { dismiss() }
                        Button("Keep Editing", role: .cancel) {}
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(isEditing ? "Save" : "Add", action: save)
                    .fontWeight(.semibold)
                    .disabled(!draft.isValid)
            }
        }
    }

    private var reminderSummary: String {
        let time = draft.reminderTime.formatted(date: .omitted, time: .shortened)
        var text = "You'll be reminded on the due day at \(time)"
        if draft.leadDays > 0 {
            text += ", with a heads-up \(pluralized(draft.leadDays, "day")) before"
        }
        text += draft.remindDailyWhenOverdue ? ", then daily for up to \(ReminderPlanner.overdueFollowUps) days if it's overdue." : "."
        return text
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

    private let columns = [GridItem(.adaptive(minimum: 40, maximum: 52), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 10) {
            ForEach(ItemTemplate.symbols, id: \.self) { symbol in
                let isSelected = selection == symbol.name
                Button {
                    selection = symbol.name
                } label: {
                    Image(systemName: symbol.name)
                        .font(.body.weight(.medium))
                        .frame(width: 40, height: 40)
                        .background(isSelected ? tint.opacity(0.22) : Color.secondary.opacity(0.1), in: Circle())
                        .overlay {
                            if isSelected {
                                Circle().strokeBorder(tint, lineWidth: 2)
                            }
                        }
                        .foregroundStyle(isSelected ? tint : .secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(symbol.label)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.vertical, 6)
        .sensoryFeedback(.selection, trigger: selection)
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
                        .fill(color.color.gradient)
                        .frame(width: 26, height: 26)
                        .overlay {
                            if selection == color {
                                Circle().stroke(Color.primary, lineWidth: 2).padding(-4)
                            }
                        }
                        .frame(width: 34, height: 34)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(color.displayName)
                .accessibilityAddTraits(selection == color ? .isSelected : [])
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 6)
        .sensoryFeedback(.selection, trigger: selection)
    }
}
