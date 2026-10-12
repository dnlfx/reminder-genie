import AppIntents
import SwiftData
import SwiftUI

struct ItemListView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var notifications: NotificationManager
    @Query(sort: \TrackedItem.createdAt) private var items: [TrackedItem]
    @AppStorage(AppSettings.dueSoonDaysKey) private var dueSoonDays = 3
    @AppStorage(AppSettings.defaultHourKey) private var defaultHour = 9
    @AppStorage(AppSettings.defaultMinuteKey) private var defaultMinute = 0

    @State private var path: [TrackedItem] = []
    @State private var showingAdd = false
    @State private var showingSettings = false
    @State private var editingItem: TrackedItem?
    @State private var starterDraft: StarterDraft?
    @State private var pendingDelete: TrackedItem?
    @State private var lastReplacement: ReplacementUndo?
    @State private var search = ""
    @State private var now = Date.now

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if items.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .navigationTitle("Reminder Genie")
            .navigationDestination(for: TrackedItem.self) { item in
                ItemDetailView(item: item)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Settings", systemImage: "gearshape") { showingSettings = true }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Add Item", systemImage: "plus") { showingAdd = true }
                }
            }
            .sheet(isPresented: $showingAdd) { AddItemFlow() }
            .sheet(isPresented: $showingSettings) { SettingsView() }
            .sheet(item: $editingItem) { item in
                NavigationStack { ItemEditorView(mode: .edit(item)) }
            }
            .sheet(item: $starterDraft) { starter in
                NavigationStack { ItemEditorView(mode: .create(starter.draft)) }
            }
            .confirmationDialog(
                "Delete \(pendingDelete?.name ?? "item")?",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                titleVisibility: .visible,
                presenting: pendingDelete
            ) { item in
                Button("Delete", role: .destructive) {
                    if lastReplacement?.item.persistentModelID == item.persistentModelID { lastReplacement = nil }
                    withAnimation { notifications.delete(item) }
                }
            } message: { _ in
                Text("Its history and reminders will be removed too. This can't be undone.")
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let undo = lastReplacement, !undo.item.isDeleted {
                UndoToast(
                    title: "\(undo.itemName) replaced",
                    subtitle: "Next due \(undo.nextDueDate.formatted(.dateTime.weekday(.wide).month(.abbreviated).day()))"
                ) {
                    withAnimation(.snappy) {
                        notifications.undo(undo)
                        lastReplacement = nil
                    }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .task(id: lastReplacement?.id) {
            guard lastReplacement != nil else { return }
            try? await Task.sleep(for: .seconds(6))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) { lastReplacement = nil }
        }
        .sensoryFeedback(.success, trigger: lastReplacement?.id) { _, new in new != nil }
        .onChange(of: scenePhase, initial: true) { _, phase in
            guard phase == .active else { return }
            now = .now
            notifications.refreshAll()
            ReminderGenieShortcuts.updateAppShortcutParameters()
        }
        .onChange(of: notifications.pendingItemID, initial: true) { _, id in
            // Tapping a notification opens that item.
            guard let id else { return }
            if let item = items.first(where: { $0.id == id }) {
                showingAdd = false
                showingSettings = false
                editingItem = nil
                starterDraft = nil
                pendingDelete = nil
                path = [item]
            }
            notifications.pendingItemID = nil
        }
        .task {
            // Keep countdowns fresh if the app is left open across midnight.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                now = .now
            }
        }
    }

    // MARK: List

    private var list: some View {
        // Grouping does date math for every item, so do it once per update.
        let sections = self.sections
        return List {
            if search.isEmpty {
                if notifications.authorizationStatus == .denied {
                    Section { NotificationsOffBanner() }
                }
                Section {
                    SummaryCard(overdue: overdueCount, dueSoon: dueSoonCount, next: nextUp)
                }
            }

            ForEach(sections) { section in
                Section {
                    ForEach(section.items) { item in
                        NavigationLink(value: item) {
                            ItemRowView(item: item, now: now, dueSoonDays: dueSoonDays)
                        }
                        .accessibilityIdentifier("item-\(item.name)")
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            if !item.isPaused {
                                Button("Replaced", systemImage: "checkmark") { replace(item) }
                                    .tint(.green)
                            }
                        }
                        .swipeActions(edge: .trailing) {
                            // Not role: .destructive, which would animate the row away before the user confirms.
                            Button("Delete", systemImage: "trash") { pendingDelete = item }
                                .tint(.red)
                            Button("Edit", systemImage: "pencil") { editingItem = item }
                                .tint(.gray)
                        }
                        .contextMenu { contextMenu(for: item) }
                    }
                } header: {
                    SectionHeader(section: section)
                }
            }
        }
        .searchable(text: $search, prompt: "Search items")
        .overlay {
            if !search.isEmpty && sections.isEmpty {
                ContentUnavailableView.search(text: search)
            }
        }
        .animation(.default, value: items.map(\.dueDate))
    }

    @ViewBuilder
    private func contextMenu(for item: TrackedItem) -> some View {
        if !item.isPaused {
            Button("Mark as Replaced", systemImage: "checkmark.circle") { replace(item) }
            // Same rule as the detail screen: nothing to snooze until it's nearly due.
            if status(item) != .ok {
                Menu("Snooze Reminder", systemImage: "moon.zzz") {
                    SnoozeOptions(item: item)
                }
            }
        }
        Button(item.isPaused ? "Resume Reminders" : "Pause Reminders", systemImage: item.isPaused ? "play.circle" : "pause.circle") {
            withAnimation { notifications.setPaused(item, !item.isPaused) }
        }
        Button("Edit", systemImage: "pencil") { editingItem = item }
        if let url = item.reorderURL {
            Link(destination: url) { Label("Reorder", systemImage: "cart") }
        }
        Divider()
        Button("Delete", systemImage: "trash", role: .destructive) { pendingDelete = item }
    }

    private func replace(_ item: TrackedItem) {
        withAnimation(.snappy) {
            lastReplacement = notifications.markReplaced(item)
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        ScrollView {
            VStack(spacing: 28) {
                ContentUnavailableView {
                    Label("Nothing to track yet", systemImage: "sparkles")
                } description: {
                    Text("Add the things you replace on a schedule, like contact lenses, filters or a toothbrush, and Reminder Genie will tell you when it's time.")
                } actions: {
                    Button("Add Your First Item") { showingAdd = true }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("Popular")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    ForEach(ItemTemplate.starters) { template in
                        Button {
                            starterDraft = StarterDraft(draft: ItemDraft(template: template, defaultHour: defaultHour, defaultMinute: defaultMinute))
                        } label: {
                            HStack(spacing: 12) {
                                TemplateIcon(template: template)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(template.name)
                                        .foregroundStyle(.primary)
                                    Text(template.intervalDescription)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "plus.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(.tint)
                            }
                            .padding(12)
                            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Adds this item")
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical, 24)
        }
        .background(Color(.systemGroupedBackground))
    }

    // MARK: Sections

    struct ListSection: Identifiable {
        enum Kind: String { case overdue, dueSoon, upcoming, paused }
        var kind: Kind
        var items: [TrackedItem]
        var id: Kind { kind }

        var title: String {
            switch kind {
            case .overdue: "Overdue"
            case .dueSoon: "Due Soon"
            case .upcoming: "Upcoming"
            case .paused: "Paused"
            }
        }

        var symbol: String {
            switch kind {
            case .overdue: "exclamationmark.circle.fill"
            case .dueSoon: "clock.fill"
            case .upcoming: "calendar"
            case .paused: "pause.circle.fill"
            }
        }

        var tint: Color {
            switch kind {
            case .overdue: .red
            case .dueSoon: .orange
            case .upcoming, .paused: .secondary
            }
        }
    }

    private struct SectionHeader: View {
        let section: ListSection

        var body: some View {
            HStack(spacing: 6) {
                Image(systemName: section.symbol)
                    .foregroundStyle(section.tint)
                    .accessibilityHidden(true)
                Text(section.title)
                Spacer()
                Text("\(section.items.count)")
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .accessibilityLabel(pluralized(section.items.count, "item"))
            }
        }
    }

    private var visibleItems: [TrackedItem] {
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return items }
        return items.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.notes.localizedCaseInsensitiveContains(query) }
    }

    private func status(_ item: TrackedItem) -> ItemStatus {
        item.schedule.status(at: now, dueSoonDays: dueSoonDays)
    }

    private var activeItems: [TrackedItem] {
        items.filter { !$0.isPaused }
    }

    private var overdueCount: Int { activeItems.filter { status($0) == .overdue }.count }
    private var dueSoonCount: Int { activeItems.filter { [ItemStatus.dueToday, .dueSoon].contains(status($0)) }.count }

    private var nextUp: (name: String, days: Int)? {
        guard let item = activeItems.filter({ status($0) != .overdue }).min(by: { $0.dueDate < $1.dueDate }) else { return nil }
        return (item.name, item.schedule.daysRemaining(at: now))
    }

    private var sections: [ListSection] {
        let active = visibleItems.filter { !$0.isPaused }.sorted { $0.dueDate < $1.dueDate }
        let paused = visibleItems.filter(\.isPaused).sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }

        func matching(_ statuses: Set<ItemStatus>) -> [TrackedItem] {
            active.filter { statuses.contains(status($0)) }
        }

        return [
            ListSection(kind: .overdue, items: matching([.overdue])),
            ListSection(kind: .dueSoon, items: matching([.dueToday, .dueSoon])),
            ListSection(kind: .upcoming, items: matching([.ok])),
            ListSection(kind: .paused, items: paused),
        ].filter { !$0.items.isEmpty }
    }
}

/// Wraps a draft so it can drive `.sheet(item:)`.
struct StarterDraft: Identifiable {
    let id = UUID()
    var draft: ItemDraft
}

/// The snooze choices, shared by the list's context menu and the detail screen.
struct SnoozeOptions: View {
    let item: TrackedItem
    @EnvironmentObject private var notifications: NotificationManager

    var body: some View {
        Button("1 Hour", systemImage: "clock") { notifications.snooze(item, for: 60 * 60) }
        Button("Tomorrow", systemImage: "sunrise") { notifications.snooze(item, for: 24 * 60 * 60) }
        Button("3 Days", systemImage: "calendar") { notifications.snooze(item, for: 3 * 24 * 60 * 60) }
        Button("1 Week", systemImage: "calendar.badge.clock") { notifications.snooze(item, for: 7 * 24 * 60 * 60) }
    }
}

struct ItemRowView: View {
    let item: TrackedItem
    let now: Date
    let dueSoonDays: Int
    @ScaledMetric(relativeTo: .headline) private var ringSize: CGFloat = 44

    var body: some View {
        let schedule = item.schedule
        let status = schedule.status(at: now, dueSoonDays: dueSoonDays)
        let days = schedule.daysRemaining(at: now)

        HStack(spacing: 14) {
            ProgressRing(progress: item.isPaused ? 0 : schedule.progress(at: now), tint: item.tint(for: status), symbolName: item.symbolName, size: ringSize)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name)
                    .font(.headline)
                    .lineLimit(2)
                Group {
                    if item.isPaused {
                        Text("Reminders paused")
                    } else if item.isSnoozed, let until = item.snoozedUntil {
                        Text(DueText.snoozed(until: until))
                    } else {
                        Text("\(DueText.short(daysRemaining: days)) · \(item.dueDate.formatted(.dateTime.month(.abbreviated).day()))")
                    }
                }
                .font(.subheadline)
                .foregroundStyle(subtitleColor(status))
            }
            Spacer(minLength: 8)
            if item.tracksSpares {
                StatusPill(
                    text: item.spareCount == 0 ? "Out" : "\(item.spareCount)",
                    systemImage: "shippingbox",
                    tint: item.spareCount == 0 ? .red : (item.isLowOnSpares ? .orange : .secondary)
                )
                .accessibilityLabel(item.spareCount == 0 ? "No spares left" : "\(pluralized(item.spareCount, "spare")) left")
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private func subtitleColor(_ status: ItemStatus) -> Color {
        guard !item.isPaused, !item.isSnoozed else { return .secondary }
        switch status {
        case .overdue: return .red
        case .dueToday: return .orange
        default: return .secondary
        }
    }
}
