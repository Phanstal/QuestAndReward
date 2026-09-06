package com.familyquest.sync

import com.familyquest.application.BackupCodec
import com.familyquest.application.BackupDecodeException
import com.familyquest.application.BackupInventoryItem
import com.familyquest.application.BackupSnapshot
import com.familyquest.application.BackupTask
import com.familyquest.application.SnapshotExporter
import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.BackupCompletionRecord
import com.familyquest.domain.model.BackupEventRecord
import com.familyquest.domain.model.BackupLedgerRecord
import com.familyquest.domain.model.BackupProcessedCommandRecord
import com.familyquest.domain.model.BackupProfileRecord
import com.familyquest.domain.model.BackupRedemptionRecord
import com.familyquest.domain.model.BackupRewardRecord
import com.familyquest.domain.model.BackupTaskRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
class SnapshotCodec : SnapshotExporter, BackupCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    override fun export(snapshot: BackupSnapshot): String = json.encodeToString(snapshot.toLegacyDocument())

    override fun encode(archive: BackupArchive): String {
        val content = json.encodeToString(archive.toDocument())
        require(content.encodeToByteArray().size <= MAX_BACKUP_BYTES) {
            "Backup exceeds the maximum supported size"
        }
        return content
    }

    override fun decode(content: String): BackupArchive {
        if (content.encodeToByteArray().size > MAX_BACKUP_BYTES) throw BackupDecodeException()
        try {
            val envelope = json.parseToJsonElement(content).jsonObject
            if (envelope["format"]?.jsonPrimitive?.content != BACKUP_FORMAT) {
                throw BackupDecodeException(unsupportedVersion = true)
            }
            val document = json.decodeFromString<BackupArchiveDocument>(content)
            if (document.formatVersion !in 2..BackupArchive.CURRENT_FORMAT_VERSION) {
                throw BackupDecodeException(unsupportedVersion = true)
            }
            return document.toDomain()
        } catch (exception: BackupDecodeException) {
            throw exception
        } catch (exception: SerializationException) {
            throw BackupDecodeException(cause = exception)
        } catch (exception: IllegalArgumentException) {
            throw BackupDecodeException(cause = exception)
        }
    }

    private companion object {
        const val BACKUP_FORMAT = "wish-force-backup"
        const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
    }
}

@Serializable
private data class BackupArchiveDocument(
    val format: String,
    val formatVersion: Int,
    val exportedAt: Long,
    val selectedProfileId: String?,
    val wishGoalRewardId: String?,
    val wishGoalDeposit: Int = 0,
    val wishGoalLastReminderDate: String? = null,
    val profiles: List<BackupProfileRecordDocument>,
    val tasks: List<BackupTaskRecordDocument>,
    val rewards: List<BackupRewardRecordDocument>,
    val completions: List<BackupCompletionRecordDocument>,
    val ledgerEntries: List<BackupLedgerRecordDocument>,
    val redemptions: List<BackupRedemptionRecordDocument>,
    val events: List<BackupEventRecordDocument>,
    val processedCommands: List<BackupProcessedCommandRecordDocument>,
)

@Serializable
private data class BackupProfileRecordDocument(
    val id: String,
    val name: String,
    val accentIndex: Int,
    val createdAt: Long,
    val archived: Boolean,
)

@Serializable
private data class BackupTaskRecordDocument(
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

@Serializable
private data class BackupRewardRecordDocument(
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

@Serializable
private data class BackupCompletionRecordDocument(
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

@Serializable
private data class BackupLedgerRecordDocument(
    val id: String,
    val profileId: String,
    val delta: Int,
    val reason: String,
    val referenceId: String,
    val label: String,
    val createdAt: Long,
)

@Serializable
private data class BackupRedemptionRecordDocument(
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

@Serializable
private data class BackupEventRecordDocument(
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

@Serializable
private data class BackupProcessedCommandRecordDocument(
    val idempotencyKey: String,
    val operation: String,
    val traceId: String,
    val result: String,
    val rejectionReason: String?,
    val processedAt: Long,
)

@Serializable
private data class LegacyBackupSnapshotDocument(
    val tasks: List<LegacyBackupTaskDocument>,
    val coins: Int,
    val totalEarned: Int,
    val completedToday: Int,
    val inventory: List<LegacyBackupInventoryItemDocument>,
)

@Serializable
private data class LegacyBackupTaskDocument(
    val id: String,
    val title: String,
    val type: String,
    val coins: Int,
    val completed: Boolean,
    val emoji: String,
    val deadlineMinutes: Int?,
    val weekDays: List<Int>,
    val monthDay: Int?,
    val completions: Int,
)

@Serializable
private data class LegacyBackupInventoryItemDocument(
    val id: String,
    val title: String,
    val emoji: String,
    val cost: Int,
)

private fun BackupArchive.toDocument() = BackupArchiveDocument(
    format = "wish-force-backup",
    formatVersion = formatVersion,
    exportedAt = exportedAt,
    selectedProfileId = selectedProfileId,
    wishGoalRewardId = wishGoalRewardId,
    wishGoalDeposit = wishGoalDeposit,
    wishGoalLastReminderDate = wishGoalLastReminderDate,
    profiles = profiles.map { BackupProfileRecordDocument(it.id, it.name, it.accentIndex, it.createdAt, it.archived) },
    tasks = tasks.map {
        BackupTaskRecordDocument(
            it.id, it.title, it.notes, it.category, it.rewardPoints, it.assigneeId, it.isCompleted,
            it.createdAt, it.updatedAt, it.completedAt, it.deletedAt, it.recurrence, it.emoji,
            it.deadlineMinutes, it.weekDaysMask, it.monthDay, it.monthlyTargetCount,
        )
    },
    rewards = rewards.map {
        BackupRewardRecordDocument(
            it.id, it.name, it.description, it.cost, it.stock, it.active, it.createdAt, it.updatedAt, it.emoji,
        )
    },
    completions = completions.map {
        BackupCompletionRecordDocument(
            it.id, it.taskId, it.occurrenceKey, it.profileId, it.rewardSnapshot, it.completedAt,
            it.revokedAt, it.titleSnapshot, it.emojiSnapshot, it.recurrenceSnapshot,
        )
    },
    ledgerEntries = ledgerEntries.map {
        BackupLedgerRecordDocument(it.id, it.profileId, it.delta, it.reason, it.referenceId, it.label, it.createdAt)
    },
    redemptions = redemptions.map {
        BackupRedemptionRecordDocument(
            it.id, it.rewardId, it.rewardName, it.profileId, it.costSnapshot, it.status, it.createdAt,
            it.emojiSnapshot, it.inventoryState, it.soldAt,
        )
    },
    events = events.map {
        BackupEventRecordDocument(
            it.eventId, it.schemaVersion, it.deviceId, it.traceId, it.idempotencyKey, it.actorId,
            it.aggregateType, it.aggregateId, it.eventType, it.occurredAt, it.logicalCounter, it.payload, it.synced,
        )
    },
    processedCommands = processedCommands.map {
        BackupProcessedCommandRecordDocument(
            it.idempotencyKey, it.operation, it.traceId, it.result, it.rejectionReason, it.processedAt,
        )
    },
)

private fun BackupArchiveDocument.toDomain() = BackupArchive(
    formatVersion = formatVersion,
    exportedAt = exportedAt,
    selectedProfileId = selectedProfileId,
    wishGoalRewardId = wishGoalRewardId,
    wishGoalDeposit = wishGoalDeposit,
    wishGoalLastReminderDate = wishGoalLastReminderDate,
    profiles = profiles.map { BackupProfileRecord(it.id, it.name, it.accentIndex, it.createdAt, it.archived) },
    tasks = tasks.map {
        BackupTaskRecord(
            it.id, it.title, it.notes, it.category, it.rewardPoints, it.assigneeId, it.isCompleted,
            it.createdAt, it.updatedAt, it.completedAt, it.deletedAt, it.recurrence, it.emoji,
            it.deadlineMinutes, it.weekDaysMask, it.monthDay, it.monthlyTargetCount,
        )
    },
    rewards = rewards.map {
        BackupRewardRecord(
            it.id, it.name, it.description, it.cost, it.stock, it.active, it.createdAt, it.updatedAt, it.emoji,
        )
    },
    completions = completions.map {
        BackupCompletionRecord(
            it.id, it.taskId, it.occurrenceKey, it.profileId, it.rewardSnapshot, it.completedAt,
            it.revokedAt, it.titleSnapshot, it.emojiSnapshot, it.recurrenceSnapshot,
        )
    },
    ledgerEntries = ledgerEntries.map {
        BackupLedgerRecord(it.id, it.profileId, it.delta, it.reason, it.referenceId, it.label, it.createdAt)
    },
    redemptions = redemptions.map {
        BackupRedemptionRecord(
            it.id, it.rewardId, it.rewardName, it.profileId, it.costSnapshot, it.status, it.createdAt,
            it.emojiSnapshot, it.inventoryState, it.soldAt,
        )
    },
    events = events.map {
        BackupEventRecord(
            it.eventId, it.schemaVersion, it.deviceId, it.traceId, it.idempotencyKey, it.actorId,
            it.aggregateType, it.aggregateId, it.eventType, it.occurredAt, it.logicalCounter, it.payload, it.synced,
        )
    },
    processedCommands = processedCommands.map {
        BackupProcessedCommandRecord(
            it.idempotencyKey, it.operation, it.traceId, it.result, it.rejectionReason, it.processedAt,
        )
    },
)

private fun BackupSnapshot.toLegacyDocument() = LegacyBackupSnapshotDocument(
    tasks = tasks.map(BackupTask::toLegacyDocument),
    coins = coins,
    totalEarned = totalEarned,
    completedToday = completedToday,
    inventory = inventory.map(BackupInventoryItem::toLegacyDocument),
)

private fun BackupTask.toLegacyDocument() = LegacyBackupTaskDocument(
    id, title, type, coins, completed, emoji, deadlineMinutes, weekDays, monthDay, completions,
)

private fun BackupInventoryItem.toLegacyDocument() = LegacyBackupInventoryItemDocument(id, title, emoji, cost)
