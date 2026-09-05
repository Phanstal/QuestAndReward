package com.familyquest.domain.repository

import com.familyquest.domain.event.DomainEvent
import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.CommandMetadata
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.InventoryItem
import com.familyquest.domain.model.LedgerEntry
import com.familyquest.domain.model.OperationResult
import com.familyquest.domain.model.Profile
import com.familyquest.domain.model.ProfileDataSnapshot
import com.familyquest.domain.model.ProgressStats
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.TaskCategory
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.model.WishGoal
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface FamilyQuestRepository {
    val profiles: Flow<List<Profile>>
    val rewards: Flow<List<Reward>>
    val selectedProfileId: StateFlow<String?>
    val wishGoalRewardId: Flow<String?> get() = flowOf(null)
    val wishGoal: Flow<WishGoal?> get() = wishGoalRewardId.map { rewardId ->
        rewardId?.let { WishGoal(profileId = "", rewardId = it, deposit = 0) }
    }
    val pendingEventCount: Flow<Int>

    fun observeTasks(profileId: String): Flow<List<HabitTask>>
    fun observeBalance(profileId: String): Flow<Int>
    fun observeLedger(profileId: String): Flow<List<LedgerEntry>>
    fun observeProgressStats(profileId: String): Flow<ProgressStats>
    fun observeCompletions(profileId: String): Flow<List<TaskCompletion>>
    fun observeInventory(profileId: String): Flow<List<InventoryItem>>
    suspend fun profileDataSnapshot(profileId: String): ProfileDataSnapshot?

    suspend fun ensureSeedData(metadata: CommandMetadata): OperationResult
    suspend fun selectProfile(profileId: String, metadata: CommandMetadata): OperationResult
    suspend fun setWishGoal(rewardId: String?, metadata: CommandMetadata): OperationResult = OperationResult.NoChange
    suspend fun markWishGoalReminderShown(
        profileId: String,
        reminderDate: String,
        metadata: CommandMetadata,
    ): OperationResult = OperationResult.NoChange
    suspend fun addProfile(name: String, metadata: CommandMetadata): OperationResult
    suspend fun addTask(
        title: String,
        notes: String,
        rewardPoints: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        assigneeId: String,
        metadata: CommandMetadata,
        deadlineMinutes: Int? = null,
        weekDays: Set<Int> = emptySet(),
        monthDay: Int? = null,
        monthlyTargetCount: Int = 1,
    ): OperationResult

    suspend fun updateTask(
        taskId: String,
        title: String,
        notes: String,
        rewardPoints: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        updatedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
        deadlineMinutes: Int? = null,
        weekDays: Set<Int> = emptySet(),
        monthDay: Int? = null,
        monthlyTargetCount: Int = 1,
    ): OperationResult

    suspend fun deleteTask(taskId: String, metadata: CommandMetadata): OperationResult
    suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
    ): OperationResult
    suspend fun revokeCompletion(completionId: String, metadata: CommandMetadata): OperationResult

    suspend fun addReward(
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ): OperationResult

    suspend fun updateReward(
        rewardId: String,
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ): OperationResult

    suspend fun deleteReward(rewardId: String, metadata: CommandMetadata): OperationResult
    suspend fun redeemReward(
        rewardId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult
    suspend fun sellInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult
    suspend fun useInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult
    suspend fun resetData(profileId: String, metadata: CommandMetadata): OperationResult

    suspend fun backupArchive(exportedAt: Long): BackupArchive {
        error("Complete backup is not supported by this repository")
    }

    suspend fun restoreBackup(archive: BackupArchive, metadata: CommandMetadata): OperationResult {
        return OperationResult.Rejected(RejectionReason.BACKUP_RESTORE_FAILED)
    }

    suspend fun pendingEvents(): List<DomainEvent>
}
