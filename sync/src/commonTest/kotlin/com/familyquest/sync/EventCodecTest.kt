package com.familyquest.sync

import com.familyquest.domain.event.AggregateType
import com.familyquest.domain.event.DomainEvent
import com.familyquest.domain.event.EventType
import com.familyquest.domain.event.EventValue
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCodecTest {
    private val event = DomainEvent(
        eventId = "0000000001000-0000-device-a-random",
        deviceId = "device-a",
        actorId = "profile-a",
        aggregateType = AggregateType.TASK,
        aggregateId = "task-a",
        eventType = EventType.TASK_CREATED,
        occurredAt = 1000,
        logicalCounter = 0,
        payload = mapOf(
            "title" to EventValue.Text("洗碗"),
            "deadlineMinutes" to EventValue.Integer(22 * 60L),
            "weekDays" to EventValue.Array(listOf(EventValue.Integer(1L), EventValue.Integer(5L))),
            "monthDay" to EventValue.Null,
        ),
    )

    @Test
    fun `event round trips through json`() {
        val codec = EventCodec()
        assertEquals(event, codec.decode(codec.encode(event)))
    }

    @Test
    fun `documents use immutable device-scoped paths`() {
        val document = EventCodec().encodeDocuments(listOf(event)).single()
        assertEquals("events/device-a/0000000001000-0000-device-a-random.json", document.relativePath)
    }

    @Test
    fun `future event types degrade to unknown instead of failing decode`() {
        val content = EventCodec().encode(event)
            .replace("\"TASK_CREATED\"", "\"TASK_CREATED_BY_FUTURE_CLIENT\"")

        val decoded = EventCodec().decode(content)

        assertEquals(EventType.UNKNOWN, decoded.eventType)
    }
}
