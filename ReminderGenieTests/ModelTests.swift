import Foundation
import SwiftData
import Testing
import UIKit
@testable import ReminderGenie

/// Items, their history, the editor draft and the built-in templates, on an in-memory database.
@MainActor
struct ModelTests {
    private let container: ModelContainer
    private var context: ModelContext { container.mainContext }

    init() throws {
        let schema = Schema(versionedSchema: ReminderGenieSchemaV1.self)
        container = try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: true))
    }

    private func makeItem(start: Date, days: Int = 14, spares: Int? = nil) -> TrackedItem {
        let item = TrackedItem(name: "Lenses", symbolName: "eye", colorName: ItemColor.blue.rawValue, intervalValue: days, intervalUnit: .days, startDate: start)
        if let spares {
            item.tracksSpares = true
            item.spareCount = spares
        }
        context.insert(item)
        return item
    }

    private func daysAgo(_ days: Int, from now: Date = .now) -> Date {
        Calendar.current.date(byAdding: .day, value: -days, to: now)!
    }

    // MARK: Replacing

    @Test func replacingStartsANewCycleAndUsesASpare() {
        let item = makeItem(start: daysAgo(15), spares: 2)
        item.snoozedUntil = .now.addingTimeInterval(3600)
        let now = Date.now
        item.markReplaced(on: now)
        #expect(item.startDate == now)
        #expect(item.spareCount == 1)
        #expect(item.snoozedUntil == nil)
        #expect(item.logs?.count == 1)
    }

    @Test func replacingWithNoSparesLeftStaysAtZero() {
        let item = makeItem(start: daysAgo(15), spares: 0)
        item.markReplaced()
        #expect(item.spareCount == 0)
        #expect(item.isLowOnSpares)
    }

    @Test func backdatedEntryOnlyFillsInHistory() {
        let start = daysAgo(3)
        let item = makeItem(start: start, spares: 2)
        item.markReplaced(on: daysAgo(20))
        #expect(item.startDate == start)
        #expect(item.spareCount == 2)
        #expect(item.logs?.count == 1)
    }

    @Test func lowOnSparesAtTheThreshold() {
        let item = makeItem(start: .now, spares: 2)
        item.lowStockThreshold = 1
        #expect(!item.isLowOnSpares)
        item.spareCount = 1
        #expect(item.isLowOnSpares)
        item.tracksSpares = false
        #expect(!item.isLowOnSpares)
    }

    // MARK: History

    @Test func removingTheLatestEntryRewindsTheCountdown() {
        let now = Date.now
        let item = makeItem(start: daysAgo(30, from: now))
        item.markReplaced(on: daysAgo(30, from: now))
        let latest = item.markReplaced(on: daysAgo(2, from: now))
        #expect(item.startDate == daysAgo(2, from: now))

        item.removeLog(latest)
        #expect(item.startDate == daysAgo(30, from: now))
        #expect(item.logs?.count == 1)
    }

    @Test func removingAnOlderEntryLeavesTheCountdownAlone() {
        let now = Date.now
        let item = makeItem(start: daysAgo(30, from: now))
        let older = item.markReplaced(on: daysAgo(30, from: now))
        item.markReplaced(on: daysAgo(2, from: now))

        item.removeLog(older)
        #expect(item.startDate == daysAgo(2, from: now))
        #expect(item.logs?.count == 1)
    }

    @Test func removingTheOnlyEntryKeepsTheCurrentCycle() {
        let start = daysAgo(5)
        let item = makeItem(start: start)
        let only = item.markReplaced(on: start)
        item.removeLog(only)
        #expect(item.startDate == start)
        #expect(item.logs?.isEmpty == true)
    }

    // MARK: Editor draft

    @Test func draftNeedsANameAndAValidLink() {
        var draft = ItemDraft()
        #expect(!draft.isValid)
        draft.name = "   "
        #expect(!draft.isValid)
        draft.name = "Filter"
        #expect(draft.isValid)
        draft.reorderLink = "not a link"
        #expect(!draft.isValid)
        draft.reorderLink = "amazon.com/filters"
        #expect(draft.isValid)
    }

    @Test func draftRoundTripsThroughAnItem() {
        let item = makeItem(start: daysAgo(4), spares: 3)
        item.notes = "Acuvue, -2.25"
        item.reorderLink = "example.com"
        item.reminderHour = 20
        item.reminderMinute = 30
        let draft = ItemDraft(item: item)
        #expect(draft.name == "Lenses")
        #expect(draft.spareCount == 3)
        #expect(draft.reminderHourMinute.hour == 20)
        #expect(draft.reminderHourMinute.minute == 30)

        var edited = draft
        edited.name = "  Daily lenses  "
        edited.apply(to: item)
        #expect(item.name == "Daily lenses")
        #expect(item.notes == "Acuvue, -2.25")
    }

    @Test func changingTheStartDateInTheEditorClearsTheSnooze() {
        let item = makeItem(start: daysAgo(20))
        item.snoozedUntil = .now.addingTimeInterval(3600)

        ItemDraft(item: item).apply(to: item)
        #expect(item.snoozedUntil != nil, "Saving without changing the date keeps the snooze")

        var draft = ItemDraft(item: item)
        draft.startDate = daysAgo(1)
        draft.apply(to: item)
        #expect(item.snoozedUntil == nil)
    }

    @Test func templateDraftUsesTheDefaultReminderTime() {
        let template = ItemTemplate.all.first { $0.name == "Toothbrush" }!
        let draft = ItemDraft(template: template, defaultHour: 7, defaultMinute: 45)
        #expect(draft.name == "Toothbrush")
        #expect(draft.intervalUnit == .months)
        #expect(draft.reminderHourMinute.hour == 7)
        #expect(draft.reminderHourMinute.minute == 45)
    }

    // MARK: Templates and icons

    @Test func everyIconExistsInSFSymbols() {
        let names = Set(ItemTemplate.all.map(\.symbolName) + ItemTemplate.symbols.map(\.name))
        let missing = names.filter { UIImage(systemName: $0) == nil }.sorted()
        #expect(missing.isEmpty, "Missing SF Symbols: \(missing)")
    }

    @Test func templatesAreUniqueAndSensible() {
        let names = ItemTemplate.all.map(\.name)
        #expect(Set(names).count == names.count, "Template names are their ids, so they must be unique")
        for template in ItemTemplate.all {
            #expect(template.intervalUnit.allowedRange.contains(template.intervalValue), "\(template.name)")
        }
        #expect(ItemTemplate.starters.count == 4, "Every starter name should match a template")
    }
}
