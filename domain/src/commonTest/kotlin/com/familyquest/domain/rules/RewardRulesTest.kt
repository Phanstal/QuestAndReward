package com.familyquest.domain.rules

import com.familyquest.domain.model.RejectionReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RewardRulesTest {
    @Test
    fun `accepts affordable reward with available stock`() {
        assertNull(RewardRules.validateRedemption(balance = 20, cost = 20, stock = 1))
    }

    @Test
    fun `rejects empty stock before checking balance`() {
        assertEquals(
            RejectionReason.OUT_OF_STOCK,
            RewardRules.validateRedemption(balance = 100, cost = 20, stock = 0),
        )
    }

    @Test
    fun `rejects insufficient balance for unlimited reward`() {
        assertEquals(
            RejectionReason.INSUFFICIENT_BALANCE,
            RewardRules.validateRedemption(balance = 19, cost = 20, stock = null),
        )
    }
}
