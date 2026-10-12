import AppIntents
import SwiftData

/// A tracked item as Siri and Shortcuts see it.
struct ItemEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Item"
    static let defaultQuery = ItemQuery()

    let id: UUID
    let name: String
    let symbolName: String

    init(item: TrackedItem) {
        id = item.id
        name = item.name
        symbolName = item.symbolName
    }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)", image: .init(systemName: symbolName))
    }
}

struct ItemQuery: EntityStringQuery {
    @MainActor
    func entities(for identifiers: [UUID]) async throws -> [ItemEntity] {
        allItems().filter { identifiers.contains($0.id) }.map(ItemEntity.init)
    }

    @MainActor
    func entities(matching string: String) async throws -> [ItemEntity] {
        allItems().filter { $0.name.localizedCaseInsensitiveContains(string) }.map(ItemEntity.init)
    }

    /// Items closest to due come first, which is usually the one being replaced.
    @MainActor
    func suggestedEntities() async throws -> [ItemEntity] {
        allItems().filter { !$0.isPaused }.sorted { $0.dueDate < $1.dueDate }.map(ItemEntity.init)
    }

    @MainActor
    private func allItems() -> [TrackedItem] {
        (try? AppModelContainer.shared.mainContext.fetch(FetchDescriptor<TrackedItem>())) ?? []
    }
}

enum ReminderGenieIntentError: Error, CustomLocalizedStringResourceConvertible {
    case itemNotFound

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .itemNotFound: "That item isn't in Reminder Genie anymore."
        }
    }
}

/// "I put in new contacts": logs a replacement without opening the app.
struct MarkReplacedIntent: AppIntent {
    static let title: LocalizedStringResource = "Log a Replacement"
    static let description = IntentDescription("Marks an item as replaced today and starts its next cycle.")

    @Parameter(title: "Item")
    var item: ItemEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Mark \(\.$item) as replaced")
    }

    init() {}

    init(item: ItemEntity) {
        self.item = item
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let manager = NotificationManager.shared
        manager.configure(container: AppModelContainer.shared)
        guard let tracked = manager.item(withID: item.id) else {
            throw ReminderGenieIntentError.itemNotFound
        }
        manager.markReplaced(tracked)
        let due = tracked.dueDate.formatted(.dateTime.weekday(.wide).month(.wide).day())
        var message = "Done. \(tracked.name) is next due \(due)."
        if tracked.tracksSpares {
            message += tracked.spareCount == 0
                ? " That was your last spare."
                : " You have \(pluralized(tracked.spareCount, "spare")) left."
        }
        return .result(dialog: "\(message)")
    }
}

/// "What's due in Reminder Genie?"
struct NextDueIntent: AppIntent {
    static let title: LocalizedStringResource = "What's Due Next"
    static let description = IntentDescription("Tells you which items are overdue or coming up next.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let items = ((try? AppModelContainer.shared.mainContext.fetch(FetchDescriptor<TrackedItem>())) ?? [])
            .filter { !$0.isPaused }
            .sorted { $0.dueDate < $1.dueDate }
        guard !items.isEmpty else {
            return .result(dialog: "You're not tracking anything yet.")
        }
        let now = Date.now
        let overdue = items.filter { $0.schedule.daysRemaining(at: now) < 0 }
        let message: String
        if overdue.count == 1 {
            message = "\(overdue[0].name) is overdue."
        } else if overdue.count > 1 {
            message = "\(overdue.count) items are overdue: \(overdue.map(\.name).formatted(.list(type: .and)))."
        } else {
            let next = items[0]
            message = "Next up is \(next.name), \(DueText.short(daysRemaining: next.schedule.daysRemaining(at: now)).lowercased())."
        }
        return .result(dialog: "\(message)")
    }
}

struct ReminderGenieShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: MarkReplacedIntent(),
            phrases: [
                "Log a replacement in \(.applicationName)",
                "Mark \(\.$item) replaced in \(.applicationName)",
                "I replaced my \(\.$item) in \(.applicationName)",
            ],
            shortTitle: "Log Replacement",
            systemImageName: "checkmark.circle"
        )
        AppShortcut(
            intent: NextDueIntent(),
            phrases: [
                "What's due in \(.applicationName)",
                "What's next in \(.applicationName)",
            ],
            shortTitle: "What's Due",
            systemImageName: "calendar.badge.clock"
        )
    }
}
