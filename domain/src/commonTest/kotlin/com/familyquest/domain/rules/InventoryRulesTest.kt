package com.familyquest.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class InventoryRulesTest {
    @Test
    fun `sale refund floors seventy percent using long arithmetic`() {
        assertEquals(70, InventoryRules.SALE_REFUND_PERCENT)
        assertEquals(0, InventoryRules.saleRefund(1))
        assertEquals(35, InventoryRules.saleRefund(50))
        assertEquals(70, InventoryRules.saleRefund(101))
        assertEquals(1_503_238_552, InventoryRules.saleRefund(Int.MAX_VALUE))
    }

}
