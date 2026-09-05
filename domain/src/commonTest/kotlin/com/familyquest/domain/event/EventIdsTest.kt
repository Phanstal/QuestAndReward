package com.familyquest.domain.event

import kotlin.test.Test
import kotlin.test.assertTrue

class EventIdsTest {
    @Test
    fun `event ids preserve timestamp and logical counter order`() {
        val first = EventIds.create(1000, 1, "device-a", "aaa")
        val second = EventIds.create(1000, 2, "device-a", "bbb")
        val later = EventIds.create(1001, 0, "device-a", "ccc")

        assertTrue(first < second)
        assertTrue(second < later)
    }
}
