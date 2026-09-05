package com.familyquest.application

private val errorCodePattern = Regex("[A-Z]+-[0-9]{4}")

enum class ErrorCodeEnum(
    val code: String,
    val userMessage: String,
) {
    COMMON_UNEXPECTED("COMMON-0001", "Something went wrong. Try again."),
    COMMON_TIMEOUT("COMMON-0002", "The operation timed out. Try again."),
    VALIDATION_INVALID_INPUT("VALIDATION-0001", "Check your input and try again."),
    PROFILE_NOT_FOUND("PROFILE-0001", "This profile no longer exists."),
    TASK_NOT_FOUND("TASK-0001", "This quest no longer exists."),
    TASK_RECURRENCE_LOCKED("TASK-0002", "This quest has a completion in the current cycle and cannot change its schedule."),
    REWARD_NOT_FOUND("REWARD-0001", "This reward no longer exists."),
    REWARD_INSUFFICIENT_BALANCE("REWARD-0002", "You do not have enough coins."),
    REWARD_OUT_OF_STOCK("REWARD-0003", "This reward is out of stock."),
    INVENTORY_FULL("INVENTORY-0001", "Your rewards bag is full. Sell an item first."),
    INVENTORY_ITEM_NOT_FOUND("INVENTORY-0002", "This reward is no longer in your bag."),
    BACKUP_INVALID("BACKUP-0001", "The backup file is invalid or damaged."),
    BACKUP_UNSUPPORTED_VERSION("BACKUP-0002", "This backup version is not supported."),
    BACKUP_RESTORE_FAILED("BACKUP-0003", "The backup could not be restored. Your data was not changed."),
    ;

    init {
        require(errorCodePattern.matches(code)) { "Invalid error code: $code" }
    }
}

class BizException(
    val errorCode: ErrorCodeEnum,
    val traceId: String,
    cause: Throwable? = null,
) : RuntimeException(errorCode.userMessage, cause)

sealed interface ApplicationResult<out T> {
    val traceId: String

    data class Success<T>(
        val value: T,
        override val traceId: String,
    ) : ApplicationResult<T>

    data class Failure(
        val errorCode: ErrorCodeEnum,
        val userMessage: String,
        override val traceId: String,
    ) : ApplicationResult<Nothing>
}

sealed interface ApplicationHealth {
    object Starting : ApplicationHealth
    object Ready : ApplicationHealth
    data class Degraded(
        val errorCode: ErrorCodeEnum,
        val traceId: String,
    ) : ApplicationHealth
}
