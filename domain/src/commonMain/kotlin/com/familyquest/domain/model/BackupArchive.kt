package com.familyquest.domain.model

data class BackupArchive(
    val formatVersion: Int,
    val exportedAt: Long,
    val selectedProfileId: String?,
    val wishGoalRewardId: String?,
    val wishGoalDeposit: Int = 0,
    val wishGoalLastReminderDate: String? = null,
    val profiles: List<BackupProfileRecord>,
    val tasks: List<BackupTaskRecord>,
    val rewards: List<BackupRewardRecord>,
    val completions: List<BackupCompletionRecord>,
    val ledgerEntries: List<BackupLedgerRecord>,
    val redemptions: List<BackupRedemptionRecord>,
    val events: List<BackupEventRecord>,
    val processedCommands: List<BackupProcessedCommandRecord>,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 3
    }
}

data class BackupProfileRecord(
    val id: String,
    val name: String,
    val accentIndex: Int,
    val createdAt: Long,
    val archived: Boolean,
)

data class BackupTaskRecord(
    val id: String,
    val title: String,
    val notes: String,
    val category: String,
    val rewardPoints: Int,
    val assigneeId: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val deletedAt: Long?,
    val recurrence: String,
    val emoji: String,
    val deadlineMinutes: Int?,
    val weekDaysMask: Int,
    val monthDay: Int?,
    val monthlyTargetCount: Int,
)

data class BackupRewardRecord(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val stock: Int?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val emoji: String,
)

data class BackupCompletionRecord(
    val id: String,
    val taskId: String,
    val occurrenceKey: String,
    val profileId: String,
    val rewardSnapshot: Int,
    val completedAt: Long,
    val revokedAt: Long?,
    val titleSnapshot: String,
    val emojiSnapshot: String,
    val recurrenceSnapshot: String,
)

data class BackupLedgerRecord(
    val id: String,
    val profileId: String,
    val delta: Int,
    val reason: String,
    val referenceId: String,
    val label: String,
    val createdAt: Long,
)

data class BackupRedemptionRecord(
    val id: String,
    val rewardId: String,
    val rewardName: String,
    val profileId: String,
    val costSnapshot: Int,
    val status: String,
    val createdAt: Long,
    val emojiSnapshot: String,
    val inventoryState: String,
    val soldAt: Long?,
)

data class BackupEventRecord(
    val eventId: String,
    val schemaVersion: Int,
    val deviceId: String,
    val traceId: String?,
    val idempotencyKey: String?,
    val actorId: String?,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val occurredAt: Long,
    val logicalCounter: Int,
    val payload: String,
    val synced: Boolean,
)

data class BackupProcessedCommandRecord(
    val idempotencyKey: String,
    val operation: String,
    val traceId: String,
    val result: String,
    val rejectionReason: String?,
    val processedAt: Long,
)
