import StoreKitTest
import XCTest
import QuestAndReward
@testable import QuestAndRewardIOS

final class SubscriptionManagerTests: XCTestCase {
    private var session: SKTestSession!

    override func setUpWithError() throws {
        session = try SKTestSession(configurationFileNamed: "QuestAndReward")
        session.disableDialogs = true
        session.resetToDefaultState()
        session.clearTransactions()
    }

    override func tearDown() {
        session.clearTransactions()
        session = nil
        super.tearDown()
    }

    @MainActor
    func testStartsFreeAndLoadsConfiguredMonthlyPrice() async {
        let manager = makeManager()

        await manager.refreshStoreState()

        XCTAssertEqual(manager.status, .free)
        XCTAssertEqual(manager.priceLabel, "$1.99/month")
        XCTAssertFalse(manager.isBusy)
    }

    @MainActor
    func testPurchaseUnlocksAndNewManagerRestoresCurrentEntitlement() async {
        let manager = makeManager()
        await manager.refreshStoreState()

        await manager.purchaseSubscription()

        XCTAssertEqual(manager.status, .premium)
        XCTAssertNil(manager.errorMessage)

        let recreated = makeManager()
        await recreated.refreshStoreState()
        XCTAssertEqual(recreated.status, .premium)

        await recreated.restoreSubscription()
        XCTAssertEqual(recreated.status, .premium)
    }

    @MainActor
    func testExpiredAndRevokedTransactionsDoNotUnlock() async throws {
        let manager = makeManager()
        await manager.refreshStoreState()
        await manager.purchaseSubscription()
        XCTAssertEqual(manager.status, .premium)

        try session.expireSubscription(productIdentifier: SubscriptionManager.productID)
        await manager.refreshEntitlement()
        XCTAssertEqual(manager.status, .free)

        let transaction = try await session.buyProduct(identifier: SubscriptionManager.productID)
        await manager.refreshEntitlement()
        XCTAssertEqual(manager.status, .premium)

        try session.refundTransaction(identifier: transaction.identifier)
        await manager.refreshEntitlement()
        XCTAssertEqual(manager.status, .free)
    }

    @MainActor
    func testPendingAndFailedPurchasesRemainLocked() async {
        let manager = makeManager()
        await manager.refreshStoreState()

        session.askToBuyEnabled = true
        await manager.purchaseSubscription()
        XCTAssertEqual(manager.status, .free)
        XCTAssertEqual(manager.errorMessage, "The purchase is pending approval.")

        session.askToBuyEnabled = false
        session.failTransactionsEnabled = true
        await manager.purchaseSubscription()
        XCTAssertEqual(manager.status, .free)
        XCTAssertEqual(manager.errorMessage, "The purchase could not be completed.")
    }

    @MainActor
    func testEntitlementRulesRejectUnverifiedExpiredRevokedAndWrongProducts() {
        let now = Date(timeIntervalSince1970: 1_000)
        let future = Date(timeIntervalSince1970: 2_000)

        XCTAssertTrue(
            SubscriptionManager.grantsPremium(
                productID: SubscriptionManager.productID,
                isVerified: true,
                expirationDate: future,
                revocationDate: nil,
                isUpgraded: false,
                now: now
            )
        )
        XCTAssertFalse(
            SubscriptionManager.grantsPremium(
                productID: SubscriptionManager.productID,
                isVerified: false,
                expirationDate: future,
                revocationDate: nil,
                isUpgraded: false,
                now: now
            )
        )
        XCTAssertFalse(
            SubscriptionManager.grantsPremium(
                productID: "wrong-product",
                isVerified: true,
                expirationDate: future,
                revocationDate: nil,
                isUpgraded: false,
                now: now
            )
        )
        XCTAssertFalse(
            SubscriptionManager.grantsPremium(
                productID: SubscriptionManager.productID,
                isVerified: true,
                expirationDate: now,
                revocationDate: nil,
                isUpgraded: false,
                now: now
            )
        )
        XCTAssertFalse(
            SubscriptionManager.grantsPremium(
                productID: SubscriptionManager.productID,
                isVerified: true,
                expirationDate: future,
                revocationDate: now,
                isUpgraded: false,
                now: now
            )
        )
    }

    @MainActor
    private func makeManager() -> SubscriptionManager {
        SubscriptionManager(bridge: IosPremiumBridge(), startAutomatically: false)
    }
}
