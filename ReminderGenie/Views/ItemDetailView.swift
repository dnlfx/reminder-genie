import SwiftData
import SwiftUI

struct ItemDetailView: View {
    @Bindable var item: TrackedItem
    @EnvironmentObject private var notifications: NotificationManager
    @Environment(\.dismiss) private var dismiss
    @AppStorage(AppSettings.dueSoonDaysKey) private var dueSoonDays = 3

    @State private var showingEditor = false
    @State private var showingReplaceSheet = false
    @State private var confirmingDelete = false

    var body: some View {
        let now = Date.now
        let schedule = item.schedule
        let status = schedule.status(at: now, dueSoonDays: dueSoonDays)
        let days = schedule.daysRemaining(at: now)

        List {
            Section {
                VStack(spacing: 16) {
                    ZStack {
                        ProgressRing(
                            progress: schedule.progress(at: now),
                            tint: status == .ok ? item.color.color : status.tint,
                            symbolName: item.symbolName,
                            lineWidth: 14,
                            size: 170
                        )
                        .opacity(0.9)
                    }
                    VStack(spacing: 4) {
                        Text(countdownHeadline(days: days))
                            .font(.title2.bold())
                            .foregroundStyle(status == .overdue ? Color.red : Color.primary)
                        Text("Due \(item.dueDate.formatted(date: .complete, time: .omitted))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if item.isSnoozed, let until = item.snoozedUntil {
                            Label("Snoozed until \(until.formatted(date: .abbreviated, time: .shortened))", systemImage: "moon.zzz")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        if item.isPaused {
                            Label("Reminders paused", systemImage: "pause.circle")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Button {
                        showingReplaceSheet = true
                    } label: {
                        Label("I replaced it", systemImage: "checkmark.circle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .tint(item.color.color)

                    if status != .ok && !item.isPaused {
                        Menu {
                            Button("1 hour") { notifications.snooze(item, for: 60 * 60) }
                            Button("Tomorrow") { notifications.snooze(item, for: 24 * 60 * 60) }
                            Button("3 days") { notifications.snooze(item, for: 3 * 24 * 60 * 60) }
                        } label: {
                            Label("Snooze reminder", systemImage: "moon.zzz")
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }

            Section("Schedule") {
                LabeledContent("Repeats", value: item.intervalDescription)
                LabeledContent("Current one started", value: item.startDate.formatted(date: .abbreviated, time: .omitted))
                LabeledContent("Reminder time", value: reminderTimeText)
                if item.leadDays > 0 {
                    LabeledContent("Heads-up", value: "\(item.leadDays) \(item.leadDays == 1 ? "day" : "days") before")
                }
            }

            if item.tracksSpares {
                Section {
                    Stepper(value: $item.spareCount, in: 0...999) {
                        LabeledContent("Spares on hand", value: "\(item.spareCount)")
                    }
                    if item.isLowOnSpares {
                        Label(item.spareCount == 0 ? "You're out of spares. Time to reorder." : "Running low. Time to reorder?", systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                    }
                } header: {
                    Text("Inventory")
                } footer: {
                    Text("One spare is used each time you mark this replaced. You'll get a nudge at \(item.lowStockThreshold) or fewer.")
                }
            }

            if !item.notes.isEmpty {
                Section("Notes") {
                    Text(item.notes)
                }
            }

            historySection

            Section {
                Button("Delete item", role: .destructive) { confirmingDelete = true }
            }
        }
        .navigationTitle(item.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Edit") { showingEditor = true }
        }
        .sheet(isPresented: $showingEditor) {
            NavigationStack {
                ItemEditorView(mode: .edit(item))
            }
        }
        .sheet(isPresented: $showingReplaceSheet) {
            ReplaceSheet(item: item)
                .presentationDetents([.medium])
        }
        .confirmationDialog("Delete \(item.name)?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                // Leave the screen first so the view never reads a deleted model.
                dismiss()
                Task {
                    try? await Task.sleep(for: .milliseconds(400))
                    notifications.delete(item)
                }
            }
        } message: {
            Text("Its history and reminders will be removed too.")
        }
        .onChange(of: item.spareCount) { _, _ in notifications.reschedule(item) }
    }

    @ViewBuilder
    private var historySection: some View {
        let logs = item.sortedLogs
        Section {
            if logs.isEmpty {
                Text("No replacements logged yet.")
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(logs.enumerated()), id: \.element.persistentModelID) { index, log in
                VStack(alignment: .leading, spacing: 2) {
                    Text(log.date.formatted(date: .abbreviated, time: .shortened))
                    if index + 1 < logs.count {
                        let gap = Calendar.current.dateComponents([.day], from: logs[index + 1].date, to: log.date).day ?? 0
                        Text("After \(gap) \(gap == 1 ? "day" : "days")")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if !log.note.isEmpty {
                        Text(log.note)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .swipeActions {
                    Button("Delete", role: .destructive) {
                        item.logs?.removeAll { $0.persistentModelID == log.persistentModelID }
                        item.modelContext?.delete(log)
                    }
                }
            }
        } header: {
            Text("History")
        } footer: {
            if let average = HistoryStats.averageDaysBetween(logs.map(\.date)) {
                Text("On average you replace this every \(average.formatted(.number.precision(.fractionLength(0...1)))) days.")
            }
        }
    }

    private var reminderTimeText: String {
        ItemDraft.time(hour: item.reminderHour, minute: item.reminderMinute).formatted(date: .omitted, time: .shortened)
    }

    private func countdownHeadline(days: Int) -> String {
        switch days {
        case ..<0: "Overdue by \(-days) \(days == -1 ? "day" : "days")"
        case 0: "Due today"
        case 1: "1 day left"
        default: "\(days) days left"
        }
    }
}

/// Logs a replacement, optionally backdated, with an optional note.
struct ReplaceSheet: View {
    let item: TrackedItem
    @EnvironmentObject private var notifications: NotificationManager
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date.now
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                DatePicker("Replaced on", selection: $date, in: ...Date.now)
                TextField("Note (optional)", text: $note, axis: .vertical)
                Section {
                    LabeledContent("Next due") {
                        Text(Schedule(startDate: date, intervalValue: item.intervalValue, intervalUnit: item.intervalUnit).dueDate.formatted(date: .abbreviated, time: .omitted))
                    }
                    if item.tracksSpares {
                        LabeledContent("Spares after this", value: "\(max(0, item.spareCount - 1))")
                    }
                }
            }
            .navigationTitle("Replace \(item.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        notifications.markReplaced(item, on: date, note: note.trimmingCharacters(in: .whitespacesAndNewlines))
                        dismiss()
                    }
                }
            }
        }
    }
}
