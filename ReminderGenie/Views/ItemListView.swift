import SwiftData
import SwiftUI

struct ItemListView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var notifications: NotificationManager
    @Query(sort: \TrackedItem.createdAt) private var items: [TrackedItem]
    @AppStorage(AppSettings.dueSoonDaysKey) private var dueSoonDays = 3

    @State private var path: [TrackedItem] = []
    @State private var showingAdd = false
    @State private var showingSettings = false
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
                    Button("Add", systemImage: "plus") { showingAdd = true }
                }
            }
            .sheet(isPresented: $showingAdd) { AddItemFlow() }
            .sheet(isPresented: $showingSettings) { SettingsView() }
        }
        .onChange(of: scenePhase, initial: true) { _, phase in
            guard phase == .active else { return }
            now = .now
            notifications.refreshAll()
        }
        .onChange(of: notifications.pendingItemID, initial: true) { _, id in
            // Tapping a notification opens that item.
            guard let id else { return }
            if let item = items.first(where: { $0.id == id }) {
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

    private var list: some View {
        List {
            ForEach(sections, id: \.title) { section in
                Section(section.title) {
                    ForEach(section.items) { item in
                        NavigationLink(value: item) {
                            ItemRowView(item: item, now: now, dueSoonDays: dueSoonDays)
                        }
                        .swipeActions(edge: .leading) {
                            Button("Replaced", systemImage: "checkmark") {
                                withAnimation { notifications.markReplaced(item) }
                            }
                            .tint(.green)
                        }
                        .swipeActions(edge: .trailing) {
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                withAnimation { notifications.delete(item) }
                            }
                        }
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label("Nothing to track yet", systemImage: "sparkles")
        } description: {
            Text("Add the things you replace on a schedule, like contact lenses, filters or a toothbrush, and Reminder Genie will tell you when it's time.")
        } actions: {
            Button("Add your first item") { showingAdd = true }
                .buttonStyle(.borderedProminent)
        }
    }

    private struct ListSection {
        var title: String
        var items: [TrackedItem]
    }

    private var sections: [ListSection] {
        let active = items.filter { !$0.isPaused }.sorted { $0.dueDate < $1.dueDate }
        let paused = items.filter(\.isPaused).sorted { $0.name < $1.name }

        func matching(_ statuses: Set<ItemStatus>) -> [TrackedItem] {
            active.filter { statuses.contains($0.schedule.status(at: now, dueSoonDays: dueSoonDays)) }
        }

        return [
            ListSection(title: "Overdue", items: matching([.overdue])),
            ListSection(title: "Due soon", items: matching([.dueToday, .dueSoon])),
            ListSection(title: "Upcoming", items: matching([.ok])),
            ListSection(title: "Paused", items: paused),
        ].filter { !$0.items.isEmpty }
    }
}

struct ItemRowView: View {
    let item: TrackedItem
    let now: Date
    let dueSoonDays: Int

    var body: some View {
        let schedule = item.schedule
        let status = schedule.status(at: now, dueSoonDays: dueSoonDays)
        let tint = item.isPaused ? Color.gray : (status == .ok ? item.color.color : status.tint)

        HStack(spacing: 14) {
            ProgressRing(progress: schedule.progress(at: now), tint: tint, symbolName: item.symbolName)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name)
                    .font(.headline)
                Group {
                    if item.isPaused {
                        Text("Paused")
                    } else if item.isSnoozed, let until = item.snoozedUntil {
                        Text("Snoozed until \(until.formatted(date: .omitted, time: .shortened))")
                    } else {
                        Text("\(DueText.short(daysRemaining: schedule.daysRemaining(at: now))) · \(item.dueDate.formatted(.dateTime.month(.abbreviated).day()))")
                    }
                }
                .font(.subheadline)
                .foregroundStyle(status == .overdue && !item.isPaused ? Color.red : Color.secondary)
            }
            Spacer()
            if item.tracksSpares {
                Label("\(item.spareCount)", systemImage: "shippingbox")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(item.isLowOnSpares ? Color.orange.opacity(0.2) : Color.secondary.opacity(0.12), in: Capsule())
                    .foregroundStyle(item.isLowOnSpares ? Color.orange : Color.secondary)
                    .accessibilityLabel("\(item.spareCount) spares left")
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
