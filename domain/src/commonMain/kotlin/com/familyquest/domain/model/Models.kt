package com.familyquest.domain.model

data class Profile(
    val id: String,
    val name: String,
    val accentIndex: Int,
    val createdAt: Long,
)

data class CommandMetadata(
    val idempotencyKey: String,
    val traceId: String,
)

data class HabitTask(
    val id: String,
    val title: String,
    val notes: String,
    val rewardPoints: Int,
    val assigneeId: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val category: TaskCategory = TaskCategory.DAILY,
    val recurrence: TaskRecurrence = TaskRecurrence.ONCE,
    val emoji: String = "✅",
    val deadlineMinutes: Int? = null,
    val weekDays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val monthlyTargetCount: Int = 1,
    val currentPeriodCompletionCount: Int = 0,
    val currentCompletionId: String? = null,
    val currentCompletionReward: Int? = null,
)

enum class TaskRecurrence {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

enum class TaskCategory {
    DAILY,
    HOME,
    HEALTH,
    GROWTH,
}

data class Reward(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val stock: Int?,
    val active: Boolean,
    val createdAt: Long,
    val emoji: String = "🎁",
    val redeemedCount: Int = 0,
)

data class InventoryItem(
    val id: String,
    val rewardId: String,
    val title: String,
    val emoji: String,
    val cost: Int,
    val acquiredAt: Long,
)

data class TaskCompletion(
    val id: String,
    val taskId: String,
    val occurrenceKey: String,
    val profileId: String,
    val rewardSnapshot: Int,
    val titleSnapshot: String,
    val emojiSnapshot: String,
    val recurrenceSnapshot: TaskRecurrence,
    val completedAt: Long,
    val revokedAt: Long?,
)

enum class LedgerReason {
    INITIAL_BALANCE,
    TASK_COMPLETED,
    TASK_REOPENED,
    REWARD_REDEEMED,
    WISH_GOAL_DEPOSITED,
    WISH_GOAL_CLEARED,
    WISH_GOAL_REDEEM_BONUS,
    INVENTORY_ITEM_SOLD,
    DATA_RESET,
    EXPERIENCE_RESET,
}

data class WishGoal(
    val profileId: String,
    val rewardId: String,
    val deposit: Int,
    val lastReminderDate: String? = null,
)

data class LedgerEntry(
    val id: String,
    val profileId: String,
    val delta: Int,
    val reason: LedgerReason,
    val referenceId: String,
    val label: String,
    val createdAt: Long,
)

data class ProgressStats(
    val experiencePoints: Int = 0,
    val completedTaskCount: Int = 0,
    val redemptionCount: Int = 0,
)

data class ProfileDataSnapshot(
    val profileId: String,
    val tasks: List<HabitTask>,
    val completions: List<TaskCompletion>,
    val balance: Int,
    val progressStats: ProgressStats,
    val inventory: List<InventoryItem>,
)

data class PlayerProgress(
    val level: Int,
    val totalExperience: Int,
    val currentLevelExperience: Int,
    val experienceForNextLevel: Int,
)

enum class AchievementId {
    FIRST_QUEST,
    QUEST_TRIO,
    TREASURE_HUNTER,
    FIRST_REWARD,
}

data class Achievement(
    val id: AchievementId,
    val current: Int,
    val target: Int,
    val unlocked: Boolean,
)

data class GameProgress(
    val player: PlayerProgress,
    val achievements: List<Achievement>,
)

enum class RedemptionStatus {
    ACCEPTED,
    REJECTED_INSUFFICIENT_BALANCE,
}

data class Redemption(
    val id: String,
    val rewardId: String,
    val rewardName: String,
    val profileId: String,
    val costSnapshot: Int,
    val status: RedemptionStatus,
    val createdAt: Long,
)

sealed interface OperationResult {
    object Success : OperationResult
    object NoChange : OperationResult
    data class Rejected(val reason: RejectionReason) : OperationResult
}

enum class RejectionReason {
    INSUFFICIENT_BALANCE,
    OUT_OF_STOCK,
    INVENTORY_FULL,
    INVENTORY_ITEM_NOT_FOUND,
    PROFILE_NOT_FOUND,
    TASK_NOT_FOUND,
    TASK_RECURRENCE_LOCKED,
    REWARD_NOT_FOUND,
    BACKUP_INVALID,
    BACKUP_RESTORE_FAILED,
    INVALID_INPUT,
}
