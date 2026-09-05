package com.familyquest.domain.rules

import com.familyquest.domain.model.RejectionReason

object RewardRules {
    fun validateRedemption(balance: Int, cost: Int, stock: Int?): RejectionReason? {
        if (stock != null && stock <= 0) return RejectionReason.OUT_OF_STOCK
        if (balance < cost) return RejectionReason.INSUFFICIENT_BALANCE
        return null
    }
}

