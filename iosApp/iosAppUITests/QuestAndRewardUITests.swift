import StoreKitTest
import XCTest

final class QuestAndRewardUITests: XCTestCase {
    private var session: SKTestSession!

    override func setUpWithError() throws {
        continueAfterFailure = false
        executionTimeAllowance = 600
        session = try SKTestSession(configurationFileNamed: "QuestAndReward")
        session.resetToDefaultState()
        session.clearTransactions()
        session.disableDialogs = true
    }

    override func tearDown() {
        if testRun?.hasSucceeded == false {
            let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            attachment.name = "QuestAndReward failure"
            attachment.lifetime = .keepAlways
            add(attachment)
            let hierarchy = XCTAttachment(string: XCUIApplication().debugDescription)
            hierarchy.name = "QuestAndReward accessibility hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
        }
        session.clearTransactions()
        session = nil
        super.tearDown()
    }

    func testFirstRunFreeLockSubscriptionAndRelaunch() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Level Up Your Life"].waitForExistence(timeout: 20))
        tap(app, "Start Exploring")
        XCTAssertTrue(app.staticTexts["Skip"].waitForExistence(timeout: 5))
        tap(app, "Skip")

        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Today's Goal"))
                .firstMatch.waitForExistence(timeout: 20)
        )
        XCTAssertTrue(app.staticTexts["Specialty Coffee"].firstMatch.exists)
        capture("Default Coffee reminder")
        tap(app, "Got it")
        XCTAssertTrue(app.staticTexts["Today's Quests"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Morning Exercise"].exists)

        tap(app, "📆 Weekly")
        XCTAssertTrue(app.staticTexts["Weekly Cleanup"].waitForExistence(timeout: 5))

        tap(app, "Store")
        XCTAssertTrue(app.staticTexts["Reward Store"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Specialty Coffee"].exists)
        XCTAssertTrue(app.staticTexts["Locked"].exists)
        capture("Free store lock")

        tap(app, "Upgrade to Premium to create your own rewards!")
        XCTAssertTrue(app.staticTexts["Start Free Trial"].waitForExistence(timeout: 60), app.debugDescription)
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "$1.99/month"))
                .firstMatch.exists
        )
        tap(app, "Start Free Trial")
        assertPremiumStore(app)
        capture("StoreKit premium store")

        try exercisePremiumFeatures(app)

        tap(app, "Rewards")
        XCTAssertTrue(app.staticTexts["My Rewards"].waitForExistence(timeout: 5))
        tap(app, "Stats")
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Quest Harvest Board"))
                .firstMatch.waitForExistence(timeout: 5)
        )

        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["Today's Quests"].waitForExistence(timeout: 20))
        tap(app, "Store")
        assertPremiumStore(app)
    }

    private func exercisePremiumFeatures(_ app: XCUIApplication) throws {
        tap(app, "Quests")
        tap(app, "📅 Daily")
        tap(app, "Set Your First Daily Quest")
        fill(app, identifier: "task-title", value: "Acceptance Quest")
        fill(app, identifier: "task-reward", value: "500")
        capture("Premium quest editor")
        tap(app, "Add", scroll: true)
        XCTAssertTrue(app.staticTexts["Acceptance Quest"].waitForExistence(timeout: 10))
        tap(app, "Acceptance Quest")
        fill(app, identifier: "task-title", value: "Verified Quest")
        tap(app, "Save", scroll: true)
        tapIdentifier(app, "complete-task-Verified Quest")
        XCTAssertTrue(app.staticTexts["1/2"].waitForExistence(timeout: 10))
        capture("Quest progress")

        tap(app, "Store")
        XCTAssertTrue(app.staticTexts["620"].waitForExistence(timeout: 10))
        let subscription = try XCTUnwrap(session.allTransactions().last)
        try session.refundTransaction(identifier: subscription.identifier)
        XCTAssertTrue(app.staticTexts["Locked"].waitForExistence(timeout: 20))
        let lockedCoffee = app.descendants(matching: .any)["buy-reward-Specialty Coffee"].firstMatch
        XCTAssertFalse(lockedCoffee.isEnabled)
        XCTAssertTrue(app.staticTexts["620"].exists)
        capture("Free high-balance Coffee lock")
        try session.buyProduct(productIdentifier: "quest_reward_monthly")
        assertPremiumStore(app)
        tapIdentifier(app, "buy-reward-Specialty Coffee")
        tap(app, "Rewards")
        XCTAssertTrue(app.staticTexts["Specialty Coffee"].waitForExistence(timeout: 10))
        capture("Purchased Coffee inventory")
        tapLabel(app, "Use Specialty Coffee")
        tap(app, "🎉 Confirm Use")

        tap(app, "Store")
        tap(app, "Add New Reward", scroll: true)
        fill(app, identifier: "reward-name", value: "Acceptance Reward")
        fill(app, identifier: "reward-cost", value: "10")
        capture("Premium reward editor")
        tap(app, "Create", scroll: true)
        tapLabel(app, "Edit Acceptance Reward", scroll: true)
        fill(app, identifier: "reward-name", value: "Verified Reward")
        tap(app, "Save", scroll: true)
        tapLabel(app, "Set Verified Reward as wish goal", scroll: true)
        if app.staticTexts["Got it"].waitForExistence(timeout: 3) { tap(app, "Got it") }
        XCTAssertTrue(app.staticTexts["🔒 Deposit: 1🪙 · +100 on redeem"].waitForExistence(timeout: 10))
        tapIdentifier(app, "buy-reward-Verified Reward", scroll: true)
        tap(app, "Rewards")
        XCTAssertTrue(app.staticTexts["Verified Reward"].waitForExistence(timeout: 10))
        tapLabel(app, "Sell Verified Reward for 7 coins")
        capture("Sell confirmation")
        tap(app, "Confirm Sell")

        tap(app, "Quests")
        tap(app, "🗓️ Monthly")
        let addMonthly = app.staticTexts.matching(NSPredicate(
            format: "label IN %@", ["Set Your First Monthly Quest", "Add Monthly Quest"]
        )).firstMatch
        interact(app, element: addMonthly, scroll: true)
        for count in 1...8 {
            XCTAssertTrue(app.descendants(matching: .any)["task-monthly-target-\(count)"].firstMatch.exists)
        }
        tapIdentifier(app, "task-monthly-target-2")
        fill(app, identifier: "task-title", value: "Monthly Acceptance")
        fill(app, identifier: "task-reward", value: "1")
        tap(app, "Add", scroll: true)
        tapIdentifier(app, "complete-task-Monthly Acceptance", scroll: true)
        XCTAssertTrue(app.staticTexts["This month 1/2 · 2 times/month"].waitForExistence(timeout: 10))
        tapIdentifier(app, "complete-task-Monthly Acceptance")
        XCTAssertTrue(app.staticTexts["This month 2/2 · 2 times/month"].waitForExistence(timeout: 10))
        capture("Monthly occurrence progress")
        tap(app, "Monthly Acceptance")
        tap(app, "🗑️ Delete", scroll: true)
        tap(app, "📅 Daily")

        tap(app, "Stats")
        tap(app, "📥 Export Backup", scroll: true)
        XCTAssertTrue(app.staticTexts["Backup exported."].waitForExistence(timeout: 10))
        tap(app, "Reset All Data", scroll: true)
        capture("Reset confirmation")
        tap(app, "⚠️ No, Keep My Data")
        tap(app, "Reset All Data", scroll: true)
        tap(app, "Reset Anyway")
        if app.staticTexts["Got it"].waitForExistence(timeout: 5) { tap(app, "Got it") }
        tap(app, "📤 Import Data", scroll: true)
        let backupFile = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "quest-backup-")).firstMatch
        XCTAssertTrue(backupFile.waitForExistence(timeout: 15), app.debugDescription)
        backupFile.tap()
        tap(app, "Quests")
        XCTAssertTrue(app.staticTexts["Verified Quest"].waitForExistence(timeout: 10))
        tap(app, "Verified Quest")
        tap(app, "🗑️ Delete")
        XCTAssertFalse(app.staticTexts["Edit Quest"].exists)
        tap(app, "Store")
        tapLabel(app, "Edit Verified Reward", scroll: true)
        tap(app, "🗑️ Delete")
        XCTAssertFalse(app.staticTexts["Edit Reward"].exists)
    }

    private func assertPremiumStore(_ app: XCUIApplication) {
        // The add row is lazily composed below the catalog, not a first-screen state signal.
        let editCoffee = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Edit Specialty Coffee")).firstMatch
        XCTAssertTrue(editCoffee.waitForExistence(timeout: 90), app.debugDescription)
    }

    private func tap(_ app: XCUIApplication, _ text: String, scroll: Bool = false) {
        interact(app, element: app.staticTexts[text].firstMatch, scroll: scroll)
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func tapLabel(_ app: XCUIApplication, _ label: String, scroll: Bool = false) {
        interact(app, element: app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch, scroll: scroll)
    }

    private func tapIdentifier(_ app: XCUIApplication, _ identifier: String, scroll: Bool = false) {
        interact(app, element: app.descendants(matching: .any)[identifier].firstMatch, scroll: scroll)
    }

    private func interact(_ app: XCUIApplication, element: XCUIElement, scroll: Bool) {
        func isVisible() -> Bool {
            guard element.exists, !element.frame.isEmpty, app.frame.contains(element.frame) else { return false }
            let keyboard = app.keyboards.firstMatch
            return !keyboard.exists || element.frame.maxY <= keyboard.frame.minY
        }
        if scroll && !isVisible() {
            dismissKeyboard(app)
            for _ in 0..<8 {
                if isVisible() { break }
                app.swipeUp()
            }
        }
        XCTAssertTrue(element.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(isVisible(), app.debugDescription)
        if element.isHittable {
            element.tap()
        } else {
            // Compose virtual accessibility nodes can have a valid visible frame
            // without a hit point on iOS 26. Keep the real touch and business assertions.
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func fill(_ app: XCUIApplication, identifier: String, value: String) {
        let field = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        // A clipped Compose TextView can exist while its editable area is outside
        // the drawer viewport. Reveal it above the keyboard before selecting it.
        for _ in 0..<8 {
            let keyboard = app.keyboards.firstMatch
            let assistant = app.otherElements["SystemInputAssistantView"]
            let bottom = min(
                keyboard.exists ? keyboard.frame.minY : app.frame.maxY,
                assistant.exists ? assistant.frame.minY : app.frame.maxY
            )
            if field.exists && field.frame.height >= 48 &&
                field.frame.minY >= app.frame.minY && field.frame.maxY < bottom {
                break
            }
            let origin = app.coordinate(withNormalizedOffset: .zero)
            origin.withOffset(CGVector(dx: app.frame.midX, dy: bottom - 30))
                .press(forDuration: 0.1, thenDragTo:
                    origin.withOffset(CGVector(dx: app.frame.midX, dy: bottom * 0.4)))
        }
        XCTAssertTrue(field.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertGreaterThanOrEqual(field.frame.height, 48, app.debugDescription)
        XCTAssertTrue(app.frame.contains(field.frame), app.debugDescription)
        if app.keyboards.firstMatch.exists {
            XCTAssertLessThan(field.frame.maxY, app.keyboards.firstMatch.frame.minY, app.debugDescription)
        }
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10), app.debugDescription)
        let typingIntroduction = app.otherElements["UIContinuousPathIntroductionView"]
        if typingIntroduction.exists {
            let continueButton = typingIntroduction.buttons["Continue"]
            XCTAssertTrue(continueButton.exists, app.debugDescription)
            continueButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            let dismissed = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "exists == false"), object: typingIntroduction
            )
            XCTAssertEqual(XCTWaiter.wait(for: [dismissed], timeout: 10), .completed, app.debugDescription)
        }
        // Compose's virtual TextView exposes its text in label; value can be
        // an empty string even when the field contains the default amount.
        let oldValue = field.label
        app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: oldValue.count) + value)
        let updatedText = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                field.label == value || field.value as? String == value
            },
            object: field
        )
        XCTAssertEqual(XCTWaiter.wait(for: [updatedText], timeout: 10), .completed, app.debugDescription)
    }

    private func dismissKeyboard(_ app: XCUIApplication) {
        guard app.keyboards.firstMatch.exists else { return }
        for _ in 0..<3 {
            let next = app.keyboards.buttons["next"]
            if next.exists {
                next.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            } else { break }
        }
        app.typeText("\n")
        let hidden = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: app.keyboards.firstMatch
        )
        XCTAssertEqual(XCTWaiter.wait(for: [hidden], timeout: 10), .completed, app.debugDescription)
    }
}
