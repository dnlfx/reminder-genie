import XCTest

/// The launch smoke test, automated. Runs on a simulator or a connected iPhone with ⌘U.
///
/// Each test launches the app with `-ui-testing`, which swaps in an empty in-memory database
/// and loads sample data (see `UITestSupport` in the app), so your real items are never touched.
final class SmokeTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // MARK: Tests

    @MainActor
    func testAddingAnItemFromATemplate() throws {
        let app = launch(.empty)
        app.buttons["Add Your First Item"].tap()
        // Scoped to the Add sheet's list; the empty screen behind it has a Toothbrush shortcut too.
        app.collectionViews.staticTexts["Toothbrush"].tap()
        app.navigationBars["New Item"].buttons["Add"].tap()
        allowNotificationsIfAsked()

        let row = itemRow("Toothbrush", in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5), "The new item should appear in the list")
        XCTAssertTrue(row.label.contains("Due in"), row.label)
    }

    @MainActor
    func testSwipeToReplaceThenUndo() throws {
        let app = launch(.standard)
        let row = itemRow("Contact lenses", in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        XCTAssertTrue(row.label.contains("Overdue by 2 days"), row.label)

        row.swipeRight()
        // A fast swipe logs it straight away; a slower one only reveals the button.
        let replacedButton = app.buttons["Replaced"]
        if replacedButton.waitForExistence(timeout: 1) { replacedButton.tap() }

        let undo = app.buttons["Undo"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5), "The Undo toast should appear")
        XCTAssertTrue(waitFor(row, labelContaining: "Due in 14 days"), row.label)

        undo.tap()
        XCTAssertTrue(waitFor(row, labelContaining: "Overdue by 2 days"), "Undo should restore the countdown: \(row.label)")
    }

    @MainActor
    func testSnoozeIsOnlyOfferedOnceAnItemIsNearlyDue() throws {
        let app = launch(.standard)

        itemRow("Water filter", in: app).tap()
        XCTAssertTrue(app.buttons["Mark as Replaced"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Snooze"].exists, "An item due in two months shouldn't offer Snooze")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        itemRow("Contact lenses", in: app).tap()
        XCTAssertTrue(app.buttons["Snooze"].waitForExistence(timeout: 5), "An overdue item should offer Snooze")
    }

    @MainActor
    func testDeletingTheLatestHistoryEntryRewindsTheCountdown() throws {
        let app = launch(.standard)
        itemRow("Toothbrush", in: app).tap()

        let countdown = app.descendants(matching: .any).matching(identifier: "countdown").firstMatch
        XCTAssertTrue(countdown.waitForExistence(timeout: 5))
        let before = try XCTUnwrap(daysLeft(countdown.label), countdown.label)

        // Entries are newest first, so the first one started the current cycle.
        // History sits below the fold, and the list only creates rows once they scroll into view.
        let latest = app.descendants(matching: .any).matching(identifier: "history-entry").firstMatch
        for _ in 0..<6 where !(latest.exists && latest.isHittable) { app.swipeUp() }
        XCTAssertTrue(latest.waitForExistence(timeout: 5), "Couldn't find the history entries")
        latest.swipeLeft()
        let delete = app.buttons["Delete"]
        if delete.waitForExistence(timeout: 1) { delete.tap() }

        // The countdown now runs from the entry 60 days ago instead of 10 days ago.
        for _ in 0..<5 where !countdown.isHittable { app.swipeDown() }
        let deadline = Date.now.addingTimeInterval(5)
        var after = daysLeft(countdown.label)
        while (after ?? before) > before - 40, Date.now < deadline {
            RunLoop.current.run(until: Date.now.addingTimeInterval(0.25))
            after = daysLeft(countdown.label)
        }
        let rewound = try XCTUnwrap(after, countdown.label)
        XCTAssertLessThan(rewound, before - 40, "Countdown didn't rewind: \(countdown.label)")
    }

    /// The full loop: a real reminder arrives, and "Replaced it" on the notification updates the item.
    /// Waits up to two minutes for the reminder, so it's the slowest test here.
    @MainActor
    func testReplacingFromTheReminderNotification() throws {
        let app = launch(.notification)
        allowNotificationsIfAsked()
        let row = itemRow("Test lenses", in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        XCTAssertTrue(row.label.contains("Due today"), row.label)

        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Time to replace: Test lenses"))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 120), "The due-day reminder never arrived")

        banner.press(forDuration: 1.5)
        let replacedIt = springboard.buttons["Replaced it"]
        XCTAssertTrue(replacedIt.waitForExistence(timeout: 5), "Long-pressing the reminder should show its actions")
        replacedIt.tap()

        app.activate()
        XCTAssertTrue(waitFor(row, labelContaining: "Due in 14 days", timeout: 10), "Replaced it should start a new cycle: \(row.label)")
    }

    @MainActor
    func testNewItemNeedsANameAndAValidLink() throws {
        let app = launch(.empty)
        app.buttons["Add Your First Item"].tap()
        app.collectionViews.staticTexts["Custom Item"].tap()

        let add = app.navigationBars["New Item"].buttons["Add"]
        XCTAssertTrue(add.waitForExistence(timeout: 5))
        XCTAssertFalse(add.isEnabled, "Add should be disabled without a name")

        let name = app.textFields["Name, e.g. Contact lenses"]
        name.tap()
        name.typeText("Furnace filter")
        XCTAssertTrue(add.isEnabled, "Add should be enabled once there's a name")

        // Put the keyboard away and scroll the form itself; the link field is near the bottom
        // and isn't created until it scrolls into view on smaller iPhones.
        name.typeText("\n")
        let link = app.textFields["Reorder link (optional)"]
        let form = app.collectionViews.firstMatch
        for _ in 0..<6 where !(link.exists && link.isHittable) { form.swipeUp() }
        XCTAssertTrue(link.waitForExistence(timeout: 2), "The reorder link field should be reachable")
        link.tap()
        link.typeText("not a link")
        XCTAssertFalse(add.isEnabled, "Add should be disabled with a broken reorder link")
        XCTAssertTrue(app.staticTexts["That doesn't look like a web address. Try something like amazon.com/your-item."].exists)

        link.typeText(XCUIKeyboardKey.delete.rawValue.repeated(10))
        link.typeText("example.com/filters")
        XCTAssertTrue(add.isEnabled)
        add.tap()
        allowNotificationsIfAsked()
        XCTAssertTrue(itemRow("Furnace filter", in: app).waitForExistence(timeout: 5))
    }

    @MainActor
    func testPausingAndResumingFromTheContextMenu() throws {
        let app = launch(.standard)
        let row = itemRow("Water filter", in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))

        row.press(forDuration: 1.0)
        app.buttons["Pause Reminders"].tap()
        XCTAssertTrue(waitFor(row, labelContaining: "Reminders paused"), row.label)
        XCTAssertTrue(app.staticTexts["Paused"].exists, "A Paused section should appear")

        row.press(forDuration: 1.0)
        app.buttons["Resume Reminders"].tap()
        XCTAssertTrue(waitFor(row, labelContaining: "Due in"), row.label)
    }

    @MainActor
    func testMarkingReplacedFromTheDetailScreenUsesASpare() throws {
        let app = launch(.standard)
        itemRow("Contact lenses", in: app).tap()

        let countdown = app.descendants(matching: .any).matching(identifier: "countdown").firstMatch
        XCTAssertTrue(countdown.waitForExistence(timeout: 5))
        XCTAssertTrue(countdown.label.contains("Overdue by 2 days"), countdown.label)

        app.buttons["Mark as Replaced"].tap()
        XCTAssertTrue(app.staticTexts["Spares after this"].waitForExistence(timeout: 5))
        app.navigationBars["Replace Contact lenses"].buttons["Save"].tap()

        XCTAssertTrue(waitFor(countdown, labelContaining: "Due in 14 days"), countdown.label)
        // The stepper reads "Spares on hand" with the count in its label or its value, depending on iOS version.
        let sparesRow = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "Spares on hand")).firstMatch
        for _ in 0..<4 where !sparesRow.exists { app.swipeUp() }
        let twoLeft = app.descendants(matching: .any).matching(NSPredicate(
            format: "label BEGINSWITH %@ AND (label CONTAINS %@ OR value == %@)", "Spares on hand", "2", "2"
        )).firstMatch
        XCTAssertTrue(twoLeft.waitForExistence(timeout: 3), "One of the 3 spares should be used: \(sparesRow.label)")
    }

    @MainActor
    func testDeletingAnItemAsksFirst() throws {
        let app = launch(.standard)
        let row = itemRow("Water filter", in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))

        row.swipeLeft()
        // A long swipe triggers Delete straight away; a short one only reveals the buttons.
        let confirm = confirmationButton("Delete", in: app)
        if !confirm.waitForExistence(timeout: 1) {
            app.collectionViews.buttons["Delete"].tap()
        }
        XCTAssertTrue(confirm.waitForExistence(timeout: 3), "Deleting should ask for confirmation")
        XCTAssertTrue(row.exists, "Nothing is deleted until it's confirmed")

        confirm.tap()
        XCTAssertTrue(row.waitForNonExistence(timeout: 5), "The item should be gone")
        XCTAssertTrue(itemRow("Contact lenses", in: app).exists, "Other items stay")
    }

    // MARK: Helpers

    /// The confirm button of a confirmation dialog, which iOS shows as a sheet, alert or popover.
    @MainActor
    private func confirmationButton(_ title: String, in app: XCUIApplication) -> XCUIElement {
        let inDialog = NSPredicate(format: "elementType IN %@", [XCUIElement.ElementType.sheet.rawValue, XCUIElement.ElementType.alert.rawValue, XCUIElement.ElementType.popover.rawValue])
        return app.descendants(matching: .any).matching(inDialog).buttons[title].firstMatch
    }

    /// Matches `UITestSupport.Scenario` in the app.
    enum Scenario: String {
        case empty, standard, notification
    }

    @MainActor
    private func launch(_ scenario: Scenario) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launchEnvironment["UITEST_SCENARIO"] = scenario.rawValue
        app.launch()
        return app
    }

    @MainActor
    private func itemRow(_ name: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "item-\(name)").firstMatch
    }

    /// Taps Allow on the notification permission alert, if iOS shows it.
    @MainActor
    private func allowNotificationsIfAsked() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allow = springboard.alerts.buttons["Allow"]
        if allow.waitForExistence(timeout: 3) { allow.tap() }
    }

    @MainActor
    private func waitFor(_ element: XCUIElement, labelContaining text: String, timeout: TimeInterval = 5) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", text), object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }
}

private extension String {
    func repeated(_ count: Int) -> String { String(repeating: self, count: count) }
}

/// The number in "Due in 82 days".
private func daysLeft(_ label: String) -> Int? {
    guard label.hasPrefix("Due in") else { return nil }
    return label.split(separator: " ").compactMap { Int($0) }.first
}
