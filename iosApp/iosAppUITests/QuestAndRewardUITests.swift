import StoreKitTest
import XCTest

final class QuestAndRewardUITests: XCTestCase {
    private var session: SKTestSession!

    override func setUpWithError() throws {
        continueAfterFailure = false
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
        app.staticTexts["Got it"].tap()
        XCTAssertTrue(app.staticTexts["Today's Quests"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Morning Exercise"].exists)

        app.staticTexts["📆 Weekly"].tap()
        XCTAssertTrue(app.staticTexts["Weekly Cleanup"].waitForExistence(timeout: 5))

        app.staticTexts["Store"].tap()
        XCTAssertTrue(app.staticTexts["Reward Store"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Specialty Coffee"].exists)
        XCTAssertTrue(app.staticTexts["Locked"].exists)

        app.staticTexts["Upgrade to Premium to create your own rewards!"].tap()
        XCTAssertTrue(app.staticTexts["Start Free Trial"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "$1.99/month"))
                .firstMatch.exists
        )
        app.staticTexts["Start Free Trial"].tap()
        XCTAssertTrue(app.staticTexts["Add New Reward"].waitForExistence(timeout: 20))

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
}
