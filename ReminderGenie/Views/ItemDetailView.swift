import Charts
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
    @State private var feedbackTrigger = 0

    var body: some View {
        let now = Date.now
        let schedule = item.schedule
        let status = schedule.status(at: now, dueSoonDays: dueSoonDays)
        let days = schedule.daysRemaining(at: now)

        List {
            Section {
                header(schedule: schedule, status: status, days: days, now: now)
            }
            .listRowBackground(Color.clear)

            Section("Schedule") {
                LabeledContent("Repeats", value: item.intervalDescription)
                LabeledContent("Current one started", value: item.startDate.formatted(date: .abbreviated, time: .omitted))
                LabeledContent("Due", value: item.dueDate.formatted(date: .abbreviated, time: .omitted))
                if let next = notifications.nextReminderDate(for: item, now: now) {
                    LabeledContent("Next reminder", value: DueText.reminder(next))
                } else {
                    LabeledContent("Next reminder", value: item.isPaused ? "Paused" : "None scheduled")
                }
                if item.leadDays > 0 {
                    LabeledContent("Heads-up", value: "\(pluralized(item.leadDays, "day")) before")
                }
            }

            if item.tracksSpares || item.reorderURL != nil {
                inventorySection
            }

            if !item.notes.isEmpty {
                Section("Notes") {
                    Text(item.notes)
                        .textSelection(.enabled)
                }
            }

            HistorySection(item: item)

            Section {
                Button("Delete Item", role: .destructive) { confirmingDelete = true }
                    .frame(maxWidth: .infinity)
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
            ReplaceSheet(item: item) { feedbackTrigger += 1 }
                .presentationDetents([.medium, .large])
        }
        .sensoryFeedback(.success, trigger: feedbackTrigger)
        .confirmationDialog("Delete \(item.name)?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                // Leave the screen first so the view never reads a deleted model.
                let manager = notifications
                dismiss()
                Task {
                    try? await Task.sleep(for: .milliseconds(400))
                    manager.delete(item)
                }
            }
        } message: {
            Text("Its history and reminders will be removed too. This can't be undone.")
        }
        .onChange(of: item.spareCount) { _, _ in notifications.reschedule(item) }
    }

    // MARK: Header

    @ViewBuilder
    private func header(schedule: Schedule, status: ItemStatus, days: Int, now: Date) -> some View {
        let tint = item.tint(for: status)

        VStack(spacing: 18) {
            ZStack {
                ProgressRing(progress: item.isPaused ? 0 : schedule.progress(at: now), tint: tint, symbolName: nil, lineWidth: 16, size: 190)
                VStack(spacing: 2) {
                    Image(systemName: item.symbolName)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(tint)
                    if days == 0 {
                        Text("Today")
                            .font(.system(size: 40, weight: .bold, design: .rounded))
                    } else {
                        Text("\(abs(days))")
                            .font(.system(size: 56, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .contentTransition(.numericText())
                    }
                    Text(ringCaption(days: days))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                .foregroundStyle(status == .overdue && !item.isPaused ? Color.red : Color.primary)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilitySummary(days: days))

            VStack(spacing: 6) {
                Text("Due \(item.dueDate.formatted(date: .complete, time: .omitted))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                HStack(spacing: 6) {
                    if item.isPaused {
                        StatusPill(text: "Reminders paused", systemImage: "pause.circle.fill")
                    } else if item.isSnoozed, let until = item.snoozedUntil {
                        StatusPill(text: DueText.snoozed(until: until), systemImage: "moon.zzz.fill", tint: .indigo)
                    }
                }
            }

            VStack(spacing: 10) {
                Button {
                    showingReplaceSheet = true
                } label: {
                    Label("Mark as Replaced", systemImage: "checkmark.circle.fill")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .tint(item.color.color)

                HStack(spacing: 10) {
                    if !item.isPaused && status != .ok {
                        Menu {
                            SnoozeOptions(item: item)
                        } label: {
                            Label("Snooze", systemImage: "moon.zzz")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                    Button {
                        withAnimation { notifications.setPaused(item, !item.isPaused) }
                    } label: {
                        Label(item.isPaused ? "Resume" : "Pause", systemImage: item.isPaused ? "play.fill" : "pause.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .controlSize(.large)
                .tint(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    private func ringCaption(days: Int) -> String {
        if item.isPaused { return "paused" }
        switch days {
        case ..<0: return days == -1 ? "day overdue" : "days overdue"
        case 0: return "due"
        case 1: return "day left"
        default: return "days left"
        }
    }

    private func accessibilitySummary(days: Int) -> String {
        let due = DueText.short(daysRemaining: days)
        return item.isPaused ? "\(due). Reminders paused." : due
    }

    // MARK: Inventory

    private var inventorySection: some View {
        Section {
            if item.tracksSpares {
                Stepper(value: $item.spareCount, in: 0...999) {
                    LabeledContent("Spares on hand", value: "\(item.spareCount)")
                }
                if item.isLowOnSpares {
                    Label(item.spareCount == 0 ? "You're out of spares. Time to reorder." : "Running low. Time to reorder?", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(item.spareCount == 0 ? Color.red : Color.orange)
                }
            }
            if let url = item.reorderURL {
                Link(destination: url) {
                    HStack {
                        Label("Reorder", systemImage: "cart")
                        Spacer()
                        Text(url.host() ?? "")
                            .foregroundStyle(.secondary)
                        Image(systemName: "arrow.up.forward")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                }
            }
        } header: {
            Text("Inventory")
        } footer: {
            if item.tracksSpares {
                Text("One spare is used each time you mark this replaced. You'll get a nudge at \(item.lowStockThreshold) or fewer.")
            }
        }
    }
}

// MARK: - History

private struct HistorySection: View {
    let item: TrackedItem

    var body: some View {
        let logs = item.sortedLogs
        let gaps = HistoryStats.gaps(logs.map(\.date), intervalValue: item.intervalValue, intervalUnit: item.intervalUnit)

        Section {
            if logs.isEmpty {
                Text("No replacements logged yet.")
                    .foregroundStyle(.secondary)
            }

            if !gaps.isEmpty {
                StatsRow(logCount: logs.count, gaps: gaps)
            }

            if gaps.count >= 2 {
                HistoryChart(gaps: gaps, tint: item.color.color)
            }

            ForEach(Array(logs.enumerated()), id: \.element.persistentModelID) { index, log in
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(log.date.formatted(date: .abbreviated, time: .shortened))
                        if !log.note.isEmpty {
                            Text(log.note)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    if index + 1 < logs.count {
                        let gap = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: logs[index + 1].date), to: Calendar.current.startOfDay(for: log.date)).day ?? 0
                        Text(pluralized(gap, "day"))
                            .font(.subheadline)
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
                .swipeActions {
                    Button("Delete", role: .destructive) {
                        item.logs?.removeAll { $0.persistentModelID == log.persistentModelID }
                        item.modelContext?.delete(log)
                        try? item.modelContext?.save()
                    }
                }
            }
        } header: {
            Text("History")
        } footer: {
            if !logs.isEmpty {
                Text("Swipe an entry to delete it. To log one you forgot, tap Mark as Replaced and change the date.")
            }
        }
    }
}

private struct StatsRow: View {
    let logCount: Int
    let gaps: [HistoryStats.Gap]

    var body: some View {
        HStack(spacing: 0) {
            stat(value: "\(logCount)", label: "Logged")
            Divider()
            stat(value: average, label: "Avg. days")
            Divider()
            stat(value: onTime, label: "On time")
        }
        .padding(.vertical, 4)
    }

    private var average: String {
        let total = gaps.reduce(0) { $0 + $1.days }
        return (Double(total) / Double(gaps.count)).formatted(.number.precision(.fractionLength(0...1)))
    }

    private var onTime: String {
        (HistoryStats.onTimeRate(gaps) ?? 0).formatted(.percent.precision(.fractionLength(0)))
    }

    private func stat(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.title3.weight(.semibold))
                .fontDesign(.rounded)
                .monospacedDigit()
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}

/// Days each cycle actually lasted, against the scheduled interval.
private struct HistoryChart: View {
    let gaps: [HistoryStats.Gap]
    let tint: Color

    var body: some View {
        // Show the most recent cycles so the bars stay readable.
        let recent = Array(gaps.suffix(12))
        let target = recent.last?.targetDays ?? 0

        Chart {
            ForEach(Array(recent.enumerated()), id: \.offset) { index, gap in
                BarMark(
                    x: .value("Cycle", String(index)),
                    y: .value("Days", gap.days),
                    width: .ratio(0.6)
                )
                .foregroundStyle(gap.isOnTime ? tint.gradient : Color.red.gradient)
                .cornerRadius(4)
                .accessibilityLabel(Text(gap.replacedOn.formatted(date: .abbreviated, time: .omitted)))
                .accessibilityValue(Text("\(pluralized(gap.days, "day")), \(gap.isOnTime ? "on time" : "late")"))
            }
            RuleMark(y: .value("Target", target))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3]))
                .foregroundStyle(.secondary)
                .annotation(position: .top, alignment: .leading) {
                    Text("Every \(pluralized(target, "day"))")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
        }
        .chartXAxis(.hidden)
        .chartYAxis {
            AxisMarks(position: .leading, values: .automatic(desiredCount: 3))
        }
        .frame(height: 140)
        .padding(.vertical, 6)
    }
}

// MARK: - Replace sheet

/// Logs a replacement, optionally backdated, with an optional note.
struct ReplaceSheet: View {
    let item: TrackedItem
    var onReplaced: () -> Void = {}
    @EnvironmentObject private var notifications: NotificationManager
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date.now
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Replaced on", selection: $date, in: ...Date.now)
                    TextField("Note (optional)", text: $note, axis: .vertical)
                } footer: {
                    Text("Forgot to log it? Set the date it actually happened and the countdown starts from there.")
                }
                Section {
                    if date >= item.startDate {
                        LabeledContent("Next due") {
                            Text(Schedule(startDate: date, intervalValue: item.intervalValue, intervalUnit: item.intervalUnit).dueDate.formatted(date: .abbreviated, time: .omitted))
                        }
                        if item.tracksSpares {
                            LabeledContent("Spares after this", value: "\(max(0, item.spareCount - 1))")
                        }
                    } else {
                        Label("This is before the current cycle, so it's added to history without changing the countdown.", systemImage: "clock.arrow.circlepath")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
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
                    Button("Save") {
                        notifications.markReplaced(item, on: date, note: note.trimmingCharacters(in: .whitespacesAndNewlines))
                        onReplaced()
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }
}
