package com.familyquest.app.ui

enum class PremiumStatus {
    CHECKING,
    FREE,
    PREMIUM,
}

data class PremiumUiState(
    val status: PremiumStatus = PremiumStatus.CHECKING,
    val priceLabel: String = DEFAULT_PREMIUM_PRICE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val canStartFreeTrial: Boolean = true,
    val subscriptionNotice: String = "Android subscriptions in this version are a demonstration. No payment is charged.",
) {
    val isPremium: Boolean get() = status == PremiumStatus.PREMIUM

    companion object {
        const val DEFAULT_PREMIUM_PRICE = "\$1.99/month"

        fun free(): PremiumUiState = PremiumUiState(status = PremiumStatus.FREE)

        fun premium(): PremiumUiState = PremiumUiState(status = PremiumStatus.PREMIUM)
    }
}
