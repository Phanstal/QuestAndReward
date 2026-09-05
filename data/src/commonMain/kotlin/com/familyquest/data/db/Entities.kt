package com.familyquest.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val accentIndex: Int,
    val createdAt: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "tasks",
    indices = [Index("assigneeId")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val category: String,
    val rewardPoints: Int,
    val assigneeId: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "'ONCE'") val recurrence: String = "ONCE",
    @ColumnInfo(defaultValue = "'✅'") val emoji: String = "✅",
    val deadlineMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val weekDaysMask: Int = 0,
    val monthDay: Int? = null,
    @ColumnInfo(defaultValue = "1") val monthlyTargetCount: Int = 1,
)

data class ProgressStatsRow(
    val experiencePoints: Int,
    val completedTaskCount: Int,
    val redemptionCount: Int,
)

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val stock: Int?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'🎁'") val emoji: String = "🎁",
)

data class RewardWithCountRow(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val stock: Int?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val emoji: String,
    val redeemedCount: Int,
)

@Entity(
    tableName = "completions",
    indices = [Index(value = ["taskId", "occurrenceKey"], unique = true)],
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val occurrenceKey: String,
    val profileId: String,
    val rewardSnapshot: Int,
    val completedAt: Long,
    val revokedAt: Long?,
    @ColumnInfo(defaultValue = "''") val titleSnapshot: String = "",
    @ColumnInfo(defaultValue = "'✅'") val emojiSnapshot: String = "✅",
    @ColumnInfo(defaultValue = "'ONCE'") val recurrenceSnapshot: String = "ONCE",
)

@Entity(
    tableName = "ledger_entries",
    indices = [Index("profileId"), Index("referenceId")],
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val delta: Int,
    val reason: String,
    val referenceId: String,
    val label: String,
    val createdAt: Long,
)

@Entity(
    tableName = "redemptions",
    indices = [
        Index("profileId"),
        Index("rewardId"),
        Index(value = ["profileId", "inventoryState"]),
    ],
)
data class RedemptionEntity(
    @PrimaryKey val id: String,
    val rewardId: String,
    val rewardName: String,
    val profileId: String,
    val costSnapshot: Int,
    val status: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'🎁'") val emojiSnapshot: String = "🎁",
    @ColumnInfo(defaultValue = "'LEGACY_CONSUMED'") val inventoryState: String = "LEGACY_CONSUMED",
    val soldAt: Long? = null,
)

@Entity(
    tableName = "events",
    indices = [
        Index("synced"),
        Index(value = ["aggregateType", "aggregateId"]),
        Index(value = ["idempotencyKey"], unique = true),
    ],
)
data class EventEntity(
    @PrimaryKey val eventId: String,
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
    val synced: Boolean = false,
)

@Entity(tableName = "processed_commands")
data class ProcessedCommandEntity(
    @PrimaryKey val idempotencyKey: String,
    val operation: String,
    val traceId: String,
    val result: String,
    val rejectionReason: String?,
    val processedAt: Long,
)

@Entity(tableName = "wish_goals", primaryKeys = ["profileId"])
data class WishGoalEntity(
    val profileId: String,
    val rewardId: String,
    val deposit: Int,
    val lastReminderDate: String? = null,
)
