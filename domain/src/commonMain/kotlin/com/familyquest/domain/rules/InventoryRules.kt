package com.familyquest.domain.rules

object InventoryRules {
    const val SALE_REFUND_PERCENT = 70

    fun saleRefund(cost: Int): Int =
        (cost.toLong() * SALE_REFUND_PERCENT.toLong() / PERCENT_BASE).toInt()

    private const val PERCENT_BASE = 100L
}
