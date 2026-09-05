package com.familyquest.domain.event

enum class AggregateType {
    PROFILE,
    TASK,
    REWARD,
    REDEMPTION,
    UNKNOWN,
}

enum class EventType {
    PROFILE_CREATED,
    INITIAL_BALANCE_GRANTED,
    TASK_CREATED,
    TASK_UPDATED,
    TASK_DELETED,
    TASK_COMPLETED,
    TASK_COMPLETION_REVOKED,
    REWARD_CREATED,
    REWARD_UPDATED,
    REWARD_DELETED,
    REWARD_REDEEMED,
    WISH_GOAL_UPDATED,
    INVENTORY_ITEM_SOLD,
    INVENTORY_ITEM_USED,
    DATA_RESET,
    BACKUP_RESTORED,
    UNKNOWN,
}

sealed interface EventValue {
    data class Text(val value: String) : EventValue
    data class Integer(val value: Long) : EventValue
    data class Decimal(val value: Double) : EventValue
    data class Flag(val value: Boolean) : EventValue
    data class Object(val values: Map<String, EventValue>) : EventValue
    data class Array(val values: List<EventValue>) : EventValue
    object Null : EventValue
}

typealias EventPayload = Map<String, EventValue>

interface EventPayloadCodec {
    fun encodePayload(payload: EventPayload): String
    fun decodePayload(content: String): EventPayload
}

data class DomainEvent(
    val schemaVersion: Int = 1,
    val eventId: String,
    val deviceId: String,
    val traceId: String? = null,
    val idempotencyKey: String? = null,
    val actorId: String?,
    val aggregateType: AggregateType = AggregateType.UNKNOWN,
    val aggregateId: String,
    val eventType: EventType = EventType.UNKNOWN,
    val occurredAt: Long,
    val logicalCounter: Int,
    val payload: EventPayload,
)

object EventIds {
    fun create(timestamp: Long, logicalCounter: Int, deviceId: String, randomId: String): String {
        return buildString {
            append(timestamp.toString().padStart(13, '0'))
            append('-')
            append(logicalCounter.toString().padStart(4, '0'))
            append('-')
            append(deviceId.take(8))
            append('-')
            append(randomId)
        }
    }
}
