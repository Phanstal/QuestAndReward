import Foundation
import StoreKit
import SwiftUI
import QuestAndReward

enum SubscriptionAccessState: Equatable {
    case checking
    case free
    case premium
}

@MainActor
final class SubscriptionManager: NSObject, ObservableObject, IosPremiumRequestHandler {
    static let productID = "quest_reward_monthly"
    static let fallbackPrice = "$1.99/month"

    let bridge: IosPremiumBridge

    @Published private(set) var status: SubscriptionAccessState = .checking
    @Published private(set) var priceLabel = SubscriptionManager.fallbackPrice
    @Published private(set) var isBusy = false
    @Published private(set) var errorMessage: String?

    private var product: Product?
    private var updatesTask: Task<Void, Never>?
    private var expirationTask: Task<Void, Never>?
    private var isRefreshing = false
    private var retryProductLoadAfter = Date.distantPast

    init(bridge: IosPremiumBridge, startAutomatically: Bool = true) {
        self.bridge = bridge
        super.init()
        bridge.setChecking(priceLabel: Self.fallbackPrice)
        if startAutomatically {
            updatesTask = observeTransactionUpdates()
            Task { await refreshStoreState() }
        }
    }

    deinit {
        updatesTask?.cancel()
        expirationTask?.cancel()
    }

    func purchase() {
        Task { await purchaseSubscription() }
    }

    func restorePurchases() {
        Task { await restoreSubscription() }
    }

    func refresh() {
        Task { await refreshStoreState() }
    }

    func refreshStoreState() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        await refreshEntitlement()
        var productLoadError: String?
        do {
            product = try await loadProduct()
            if product == nil {
                productLoadError = "Premium subscription is unavailable."
            } else if let product {
                priceLabel = "\(product.displayPrice)/month"
            }
        } catch {
            productLoadError = "Unable to load the premium subscription."
        }
        await refreshEntitlement(errorMessage: productLoadError)
    }

    func refreshEntitlement(errorMessage: String? = nil) async {
        var entitled = false
        var expiration: Date?
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else {
                continue
            }
            guard Self.grantsPremium(
                productID: transaction.productID,
                isVerified: true,
                expirationDate: transaction.expirationDate,
                revocationDate: transaction.revocationDate,
                isUpgraded: transaction.isUpgraded,
                now: Date()
            ) else {
                continue
            }
            entitled = true
            expiration = transaction.expirationDate
            break
        }

        let price = product.map { "\($0.displayPrice)/month" } ?? priceLabel
        status = entitled ? .premium : .free
        priceLabel = price
        self.errorMessage = errorMessage
        if entitled {
            bridge.setPremium(priceLabel: price)
        } else {
            bridge.setFree(priceLabel: price, errorMessage: errorMessage)
        }
        bridge.setBusy(isBusy: isBusy)
        if let errorMessage { bridge.reportError(message: errorMessage) }
        scheduleExpiration(expiration)
    }

    func purchaseSubscription() async {
        guard !isBusy else { return }
        setBusy(true)
        defer { setBusy(false) }
        if product == nil {
            do {
                product = try await loadProduct()
                if let product {
                    priceLabel = "\(product.displayPrice)/month"
                }
            } catch {
                reportError("Unable to load the premium subscription.")
                return
            }
        }
        guard let product else {
            reportError("Premium subscription is unavailable.")
            return
        }

        do {
            switch try await product.purchase() {
            case .success(let verification):
                guard case .verified(let transaction) = verification else {
                    reportError("The App Store could not verify this purchase.")
                    return
                }
                await transaction.finish()
                await refreshEntitlement()
            case .pending:
                reportError("The purchase is pending approval.")
            case .userCancelled:
                setBusy(false)
            @unknown default:
                reportError("The purchase could not be completed.")
            }
        } catch {
            reportError("The purchase could not be completed.")
        }
    }

    func restoreSubscription() async {
        guard !isBusy else { return }
        setBusy(true)
        defer { setBusy(false) }
        do {
            try await AppStore.sync()
            await refreshEntitlement()
        } catch {
            reportError("Purchases could not be restored.")
        }
    }

    private func observeTransactionUpdates() -> Task<Void, Never> {
        Task { [weak self] in
            for await result in Transaction.updates {
                guard let self else { return }
                guard case .verified(let transaction) = result else {
                    self.reportError("The App Store returned an unverified transaction.")
                    continue
                }
                await transaction.finish()
                await self.refreshEntitlement()
            }
        }
    }

    private enum StoreRequestError: Error { case temporarilyUnavailable, timedOut }

    private func loadProduct() async throws -> Product? {
        guard Date() >= retryProductLoadAfter else { throw StoreRequestError.temporarilyUnavailable }
        do {
            let loaded = try await withThrowingTaskGroup(of: Product?.self) { group in
                group.addTask {
                    try await Product.products(for: [SubscriptionManager.productID]).first
                }
                group.addTask {
                    try await Task.sleep(nanoseconds: 15_000_000_000)
                    throw StoreRequestError.timedOut
                }
                defer { group.cancelAll() }
                return try await group.next() ?? nil
            }
            if loaded == nil { retryProductLoadAfter = Date().addingTimeInterval(30) }
            return loaded
        } catch {
            retryProductLoadAfter = Date().addingTimeInterval(30)
            throw error
        }
    }

    private func setBusy(_ value: Bool) {
        isBusy = value
        if value {
            errorMessage = nil
        }
        bridge.setBusy(isBusy: value)
        if let errorMessage { bridge.reportError(message: errorMessage) }
    }

    private func scheduleExpiration(_ expiration: Date?) {
        expirationTask?.cancel()
        guard let expiration else { return }
        expirationTask = Task { [weak self] in
            let delay = max(0, expiration.timeIntervalSinceNow)
            do {
                try await Task.sleep(nanoseconds: UInt64(min(delay, 31_536_000) * 1_000_000_000))
            } catch { return }
            guard let self else { return }
            await self.refreshEntitlement()
        }
    }

    private func reportError(_ message: String) {
        isBusy = false
        errorMessage = message
        bridge.reportError(message: message)
    }

    static func grantsPremium(
        productID: String,
        isVerified: Bool,
        expirationDate: Date?,
        revocationDate: Date?,
        isUpgraded: Bool,
        now: Date
    ) -> Bool {
        isVerified &&
            productID == Self.productID &&
            revocationDate == nil &&
            !isUpgraded &&
            (expirationDate.map { $0 > now } ?? false)
    }
}
