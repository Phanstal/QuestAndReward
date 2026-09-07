import StoreKitTest
import XCTest

final class QuestAndRewardUITests: XCTestCase {
    private var session: SKTestSession!

    override func setUpWithError() throws {
        continueAfterFailure = false
        executionTimeAllowance = 600
        session = try SKTestSession(configurationFileNamed: "QuestAndReward")
        session.disableDialogs = true
        session.resetToDefaultState()
        session.clearTransactions()
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

    func testFirstRunFreeLockSubscriptionAndRelaunch() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Level Up Your Life"].waitForExistence(timeout: 20))
        app.staticTexts["Start Exploring"].tap()
        XCTAssertTrue(app.staticTexts["Skip"].waitForExistence(timeout: 5))
        app.staticTexts["Skip"].tap()

        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Today's Goal"))
                .firstMatch.waitForExistence(timeout: 20)
        )
        XCTAssertTrue(app.staticTexts["Specialty Coffee"].firstMatch.exists)
        capture("Default Coffee reminder")
        app.staticTexts["Got it"].tap()
        XCTAssertTrue(app.staticTexts["Today's Quests"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Morning Exercise"].exists)

        app.staticTexts["📆 Weekly"].tap()
        XCTAssertTrue(app.staticTexts["Weekly Cleanup"].waitForExistence(timeout: 5))

        app.staticTexts["Store"].tap()
        XCTAssertTrue(app.staticTexts["Reward Store"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Specialty Coffee"].exists)
        XCTAssertTrue(app.staticTexts["Locked"].exists)
        capture("Free store lock")

        app.staticTexts["Upgrade to Premium to create your own rewards!"].tap()
        XCTAssertTrue(app.staticTexts["Start Free Trial"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "$1.99/month"))
                .firstMatch.exists
        )
        app.staticTexts["Start Free Trial"].tap()
        XCTAssertTrue(app.staticTexts["Add New Reward"].waitForExistence(timeout: 20))
        capture("StoreKit premium store")

        exercisePremiumFeatures(app)

        app.staticTexts["Rewards"].tap()
        XCTAssertTrue(app.staticTexts["My Rewards"].waitForExistence(timeout: 5))
        app.staticTexts["Stats"].tap()
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Quest Harvest Board"))
                .firstMatch.waitForExistence(timeout: 5)
        )

        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["Today's Quests"].waitForExistence(timeout: 20))
        app.staticTexts["Store"].tap()
        XCTAssertTrue(app.staticTexts["Add New Reward"].waitForExistence(timeout: 20))
    }

    private func exercisePremiumFeatures(_ app: XCUIApplication) {
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
        tapIdentifier(app, "buy-reward-Verified Reward", scroll: true)
        tap(app, "Rewards")
        XCTAssertTrue(app.staticTexts["Verified Reward"].waitForExistence(timeout: 10))
        tapLabel(app, "Sell Verified Reward for 7 coins")
        capture("Sell confirmation")
        tap(app, "Confirm Sell")

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
        if scroll {
            for _ in 0..<8 {
                if element.exists && element.isHittable { break }
                app.swipeUp()
            }
        }
        XCTAssertTrue(element.waitForExistence(timeout: 10), app.debugDescription)
        element.tap()
    }

    private func fill(_ app: XCUIApplication, identifier: String, value: String) {
        let field = app.textFields[identifier]
        if !field.isHittable { app.swipeUp() }
        XCTAssertTrue(field.waitForExistence(timeout: 10), app.debugDescription)
        field.tap()
        let oldValue = field.value as? String ?? ""
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: oldValue.count) + value)
    }
}
