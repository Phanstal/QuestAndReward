import StoreKitTest
import XCTest
import QuestAndReward
@testable import QuestAndRewardIOS

final class SubscriptionManagerTests: XCTestCase {
    private var session: SKTestSession!

    override func setUpWithError() throws {
        session = try SKTestSession(configurationFileNamed: "QuestAndReward")
        session.resetToDefaultState()
        session.clearTransactions()
        // Apply overrides after reset so automated purchases never require a dialog.
        session.disableDialogs = true
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
        NSLog("StoreKit acceptance: loading product before expiration test")
        await manager.refreshStoreState()
        NSLog("StoreKit acceptance: purchasing before expiration test")
        await manager.purchaseSubscription()
        NSLog("StoreKit acceptance: purchase returned, expiring subscription")
        XCTAssertEqual(manager.status, .premium)

        try session.expireSubscription(productIdentifier: SubscriptionManager.productID)
        await manager.refreshEntitlement()
        NSLog("StoreKit acceptance: expired entitlement refreshed")
        XCTAssertEqual(manager.status, .free)

        let transaction = try buyTestProduct()
        NSLog("StoreKit acceptance: repurchase returned")
        await manager.refreshEntitlement()
        XCTAssertEqual(manager.status, .premium)

        try session.refundTransaction(identifier: transaction.identifier)
        await manager.refreshEntitlement()
        NSLog("StoreKit acceptance: revoked entitlement refreshed")
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
    func testTransactionListenerUnlocksAndRevocationDowngradesWithoutRelaunch() async throws {
        let manager = SubscriptionManager(bridge: IosPremiumBridge())
        await manager.refreshStoreState()
        let transaction = try buyTestProduct()
        await assertEventually { manager.status == .premium }
        try session.refundTransaction(identifier: transaction.identifier)
        await assertEventually { manager.status == .free }
    }

    @MainActor
    private func assertEventually(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        let deadline = Date().addingTimeInterval(10)
        while !condition() && Date() < deadline {
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        XCTAssertTrue(condition(), file: file, line: line)
    }

    @MainActor
    func testCancelledPurchaseDoesNotUnlockOrReportSuccess() async {
        let manager = makeManager()
        await manager.refreshStoreState()
        await manager.handlePurchaseResult(.userCancelled)
        XCTAssertEqual(manager.status, .free)
        XCTAssertFalse(manager.isBusy)
        XCTAssertNil(manager.errorMessage)
    }

    @MainActor
    func testEntitlementRulesRejectUnverifiedExpiredRevokedAndWrongProducts() {
        let now = Date(timeIntervalSince1970: 1_000)
        let future = Date(timeIntervalSince1970: 2_000)

        XCTAssertFalse(SubscriptionManager.grantsPremium(
            productID: SubscriptionManager.productID, isVerified: true,
            expirationDate: nil, revocationDate: nil, isUpgraded: false, now: now
        ))
        XCTAssertFalse(SubscriptionManager.grantsPremium(
            productID: SubscriptionManager.productID, isVerified: true,
            expirationDate: future, revocationDate: nil, isUpgraded: true, now: now
        ))

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
    private func buyTestProduct() throws -> SKTestTransaction {
        try session.buyProduct(productIdentifier: SubscriptionManager.productID)
        return try XCTUnwrap(session.allTransactions().last)
    }

    @MainActor
    private func makeManager() -> SubscriptionManager {
        SubscriptionManager(bridge: IosPremiumBridge(), startAutomatically: false)
    }
}
