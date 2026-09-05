package com.familyquest.sync

import com.familyquest.application.EventExporter
import com.familyquest.domain.event.AggregateType
import com.familyquest.domain.event.DomainEvent
import com.familyquest.domain.event.EventPayload
import com.familyquest.domain.event.EventPayloadCodec
import com.familyquest.domain.event.EventType
import com.familyquest.domain.event.EventValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

data class EventDocument(
    val relativePath: String,
    val content: String,
)

class EventCodec : EventExporter, EventPayloadCodec {
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(event: DomainEvent): String = json.encodeToString(event.toDocument())

    fun decode(content: String): DomainEvent {
        return json.decodeFromString<EventDocumentDto>(content).toDomain()
    }

    fun encodeDocuments(events: List<DomainEvent>): List<EventDocument> {
        return events.map { event ->
            EventDocument(
                relativePath = "events/${safeSegment(event.deviceId)}/${safeSegment(event.eventId)}.json",
                content = encode(event) + "\n",
            )
        }
    }

    fun encodeNdjson(events: List<DomainEvent>): String {
        return events.joinToString(separator = "\n", postfix = if (events.isEmpty()) "" else "\n") { encode(it) }
    }

    override fun export(events: List<DomainEvent>): String = encodeNdjson(events)

    fun decodeNdjson(content: String): List<DomainEvent> {
        return content.lineSequence()
            .filter { it.isNotBlank() }
            .map(::decode)
            .toList()
    }

    override fun encodePayload(payload: EventPayload): String {
        return json.encodeToString(JsonObject.serializer(), payload.toJsonObject())
    }

    override fun decodePayload(content: String): EventPayload {
        return json.decodeFromString(JsonObject.serializer(), content).toEventPayload()
    }

    private fun safeSegment(value: String): String {
        return value.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
        }.joinToString("").ifEmpty { "_" }
    }
}

@Serializable
private data class EventDocumentDto(
    val schemaVersion: Int = 1,
    val eventId: String,
    val deviceId: String,
    val traceId: String? = null,
    val idempotencyKey: String? = null,
    val actorId: String? = null,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val occurredAt: Long,
    val logicalCounter: Int,
    val payload: JsonObject,
)

private fun DomainEvent.toDocument() = EventDocumentDto(
    schemaVersion = schemaVersion,
    eventId = eventId,
    deviceId = deviceId,
    traceId = traceId,
    idempotencyKey = idempotencyKey,
    actorId = actorId,
    aggregateType = aggregateType.name,
    aggregateId = aggregateId,
    eventType = eventType.name,
    occurredAt = occurredAt,
    logicalCounter = logicalCounter,
    payload = payload.toJsonObject(),
)

private fun EventDocumentDto.toDomain() = DomainEvent(
    schemaVersion = schemaVersion,
    eventId = eventId,
    deviceId = deviceId,
    traceId = traceId,
    idempotencyKey = idempotencyKey,
    actorId = actorId,
    aggregateType = enumValues<AggregateType>().firstOrNull { it.name == aggregateType }
        ?: AggregateType.UNKNOWN,
    aggregateId = aggregateId,
    eventType = enumValues<EventType>().firstOrNull { it.name == eventType }
        ?: EventType.UNKNOWN,
    occurredAt = occurredAt,
    logicalCounter = logicalCounter,
    payload = payload.toEventPayload(),
)

private fun EventPayload.toJsonObject(): JsonObject {
    return JsonObject(mapValues { (_, value) -> value.toJsonElement() })
}

private fun EventValue.toJsonElement(): JsonElement {
    return when (this) {
        is EventValue.Text -> JsonPrimitive(value)
        is EventValue.Integer -> JsonPrimitive(value)
        is EventValue.Decimal -> JsonPrimitive(value)
        is EventValue.Flag -> JsonPrimitive(value)
        is EventValue.Object -> JsonObject(values.mapValues { (_, value) -> value.toJsonElement() })
        is EventValue.Array -> JsonArray(values.map(EventValue::toJsonElement))
        EventValue.Null -> JsonNull
    }
}

private fun JsonObject.toEventPayload(): EventPayload {
    return mapValues { (_, value) -> value.toEventValue() }
}

private fun JsonElement.toEventValue(): EventValue {
    return when (this) {
        JsonNull -> EventValue.Null
        is JsonObject -> EventValue.Object(toEventPayload())
        is JsonArray -> EventValue.Array(map(JsonElement::toEventValue))
        is JsonPrimitive -> when {
            isString -> EventValue.Text(content)
            booleanOrNull != null -> EventValue.Flag(requireNotNull(booleanOrNull))
            longOrNull != null -> EventValue.Integer(requireNotNull(longOrNull))
            doubleOrNull != null -> EventValue.Decimal(requireNotNull(doubleOrNull))
            else -> EventValue.Text(contentOrNull.orEmpty())
        }
    }
}
