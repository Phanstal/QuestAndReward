import SwiftUI
import QuestAndReward

struct ContentView: View {
    @ObservedObject var subscriptionManager: SubscriptionManager

    var body: some View {
        ComposeView(subscriptionManager: subscriptionManager)
            .ignoresSafeArea(.keyboard)
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    let subscriptionManager: SubscriptionManager

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            premiumBridge: subscriptionManager.bridge,
            premiumRequestHandler: subscriptionManager
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}
