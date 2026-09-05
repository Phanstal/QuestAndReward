package com.familyquest.sync

import com.familyquest.domain.event.DomainEvent

/**
 * A transport must convert timeout, circuit-open and offline states to [SyncResult.Unavailable].
 * This contract prevents a future NAS client from leaking network exceptions into local features.
 */
interface SyncTransport {
    suspend fun pull(request: PullRequest): SyncResult<PullResult>
    suspend fun push(request: PushRequest): SyncResult<PushResult>
}

data class SyncRequestContext(
    val traceId: String,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val initialBackoffMillis: Long = DEFAULT_INITIAL_BACKOFF_MILLIS,
) {
    init {
        require(traceId.isNotBlank())
        require(timeoutMillis > 0)
        require(maxRetries in 0..3)
        require(initialBackoffMillis > 0)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_INITIAL_BACKOFF_MILLIS = 250L
    }
}

data class PullRequest(
    val cursor: String?,
    val context: SyncRequestContext,
)

data class PushRequest(
    val events: List<DomainEvent>,
    val idempotencyKey: String,
    val context: SyncRequestContext,
) {
    init {
        require(idempotencyKey.isNotBlank())
    }
}

sealed interface SyncResult<out T> {
    data class Success<T>(val value: T) : SyncResult<T>

    data class Unavailable(
        val reason: SyncUnavailableReason,
        val retryable: Boolean,
    ) : SyncResult<Nothing>
}

enum class SyncUnavailableReason {
    OFFLINE,
    TIMEOUT,
    CIRCUIT_OPEN,
    REMOTE_ERROR,
    INVALID_RESPONSE,
}

data class PullResult(
    val cursor: String?,
    val events: List<DomainEvent>,
)

data class PushResult(
    val cursor: String?,
    val acceptedEventIds: Set<String>,
)
