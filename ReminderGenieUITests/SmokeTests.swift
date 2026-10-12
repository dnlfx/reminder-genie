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
        app.staticTexts["Toothbrush"].tap()
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
        let latest = app.descendants(matching: .any).matching(identifier: "history-entry").firstMatch
        XCTAssertTrue(latest.waitForExistence(timeout: 5))
        for _ in 0..<5 where !latest.isHittable { app.swipeUp() }
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

    // MARK: Helpers

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

/// The number in "Due in 82 days".
private func daysLeft(_ label: String) -> Int? {
    guard label.hasPrefix("Due in") else { return nil }
    return label.split(separator: " ").compactMap { Int($0) }.first
}
