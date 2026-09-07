import SwiftUI
import QuestAndReward

@main
struct QuestAndRewardApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var subscriptionManager: SubscriptionManager

    init() {
        let bridge = IosPremiumBridge()
        _subscriptionManager = StateObject(
            wrappedValue: SubscriptionManager(bridge: bridge)
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView(subscriptionManager: subscriptionManager)
                .ignoresSafeArea()
                .onChange(of: scenePhase) { phase in
                    if phase == .active {
                        subscriptionManager.refresh()
                    }
                }
        }
    }
}
