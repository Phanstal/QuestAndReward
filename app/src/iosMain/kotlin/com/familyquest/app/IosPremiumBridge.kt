package com.familyquest.app

import com.familyquest.app.ui.PremiumStatus
import com.familyquest.app.ui.PremiumUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IosPremiumRequestHandler {
    fun purchase()

    fun restorePurchases()
}

class IosPremiumBridge {
    private val mutableState = MutableStateFlow(PremiumUiState())
    internal val state: StateFlow<PremiumUiState> = mutableState.asStateFlow()

    fun setChecking(priceLabel: String) {
        mutableState.value = mutableState.value.copy(
            status = PremiumStatus.CHECKING,
            priceLabel = priceLabel.ifBlank { PremiumUiState.DEFAULT_PREMIUM_PRICE },
            isBusy = false,
            errorMessage = null,
        )
    }

    fun setFree(priceLabel: String, errorMessage: String?) {
        mutableState.value = PremiumUiState(
            status = PremiumStatus.FREE,
            priceLabel = priceLabel.ifBlank { PremiumUiState.DEFAULT_PREMIUM_PRICE },
            errorMessage = errorMessage,
        )
    }

    fun setPremium(priceLabel: String) {
        mutableState.value = PremiumUiState(
            status = PremiumStatus.PREMIUM,
            priceLabel = priceLabel.ifBlank { PremiumUiState.DEFAULT_PREMIUM_PRICE },
        )
    }

    fun setBusy(isBusy: Boolean) {
        mutableState.value = mutableState.value.copy(isBusy = isBusy, errorMessage = null)
    }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(isBusy = false, errorMessage = message)
    }
}
