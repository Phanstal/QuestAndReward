package com.familyquest.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyQuestDao {
    @Query("SELECT * FROM profiles WHERE archived = 0 ORDER BY createdAt")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE archived = 0 ORDER BY createdAt")
    suspend fun profiles(): List<ProfileEntity>

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    suspend fun allProfiles(): List<ProfileEntity>

    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM profiles) + " +
            "(SELECT COUNT(*) FROM tasks) + " +
            "(SELECT COUNT(*) FROM rewards) + " +
            "(SELECT COUNT(*) FROM completions) + " +
            "(SELECT COUNT(*) FROM ledger_entries) + " +
            "(SELECT COUNT(*) FROM redemptions) + " +
            "(SELECT COUNT(*) FROM events) + " +
            "(SELECT COUNT(*) FROM processed_commands) + " +
            "(SELECT COUNT(*) FROM wish_goals)",
    )
    suspend fun storedRowCount(): Int

    @Query("SELECT * FROM profiles WHERE id = :profileId AND archived = 0")
    suspend fun profile(profileId: String): ProfileEntity?

    @Query("SELECT * FROM wish_goals WHERE profileId = :profileId")
    fun observeWishGoal(profileId: String): Flow<WishGoalEntity?>

    @Query("SELECT * FROM wish_goals WHERE profileId = :profileId")
    suspend fun wishGoal(profileId: String): WishGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWishGoal(goal: WishGoalEntity)

    @Query("DELETE FROM wish_goals WHERE profileId = :profileId")
    suspend fun deleteWishGoal(profileId: String)

    @Query("DELETE FROM wish_goals WHERE rewardId = :rewardId")
    suspend fun deleteWishGoalsForReward(rewardId: String)

    @Query("SELECT * FROM wish_goals WHERE rewardId = :rewardId")
    suspend fun wishGoalsForReward(rewardId: String): List<WishGoalEntity>

    @Query("UPDATE wish_goals SET lastReminderDate = :reminderDate WHERE profileId = :profileId")
    suspend fun updateWishGoalReminderDate(profileId: String, reminderDate: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Query("SELECT * FROM tasks WHERE assigneeId = :profileId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeTasks(profileId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE assigneeId = :profileId AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun taskSnapshotRows(profileId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId AND deletedAt IS NULL")
    suspend fun task(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun taskIncludingDeleted(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE assigneeId = :profileId")
    suspend fun tasksIncludingDeleted(profileId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdAt, id")
    suspend fun allTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query(
        "SELECT rewards.*, COUNT(redemptions.id) AS redeemedCount FROM rewards " +
            "LEFT JOIN redemptions ON redemptions.rewardId = rewards.id AND redemptions.status = 'ACCEPTED' " +
            "WHERE rewards.active = 1 GROUP BY rewards.id ORDER BY rewards.createdAt",
    )
    fun observeRewards(): Flow<List<RewardWithCountRow>>

    @Query("SELECT * FROM rewards WHERE id = :rewardId AND active = 1")
    suspend fun reward(rewardId: String): RewardEntity?

    @Query("SELECT * FROM rewards ORDER BY createdAt, id")
    suspend fun allRewards(): List<RewardEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReward(reward: RewardEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRewards(rewards: List<RewardEntity>)

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Query(
        "SELECT COALESCE(SUM(delta), 0) FROM ledger_entries " +
            "WHERE profileId = :profileId AND reason <> 'EXPERIENCE_RESET'",
    )
    fun observeBalance(profileId: String): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(delta), 0) FROM ledger_entries " +
            "WHERE profileId = :profileId AND reason <> 'EXPERIENCE_RESET'",
    )
    suspend fun balance(profileId: String): Int

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE profileId = :profileId")
    suspend fun ledgerCount(profileId: String): Int

    @Query("SELECT * FROM ledger_entries WHERE profileId = :profileId ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeLedger(profileId: String): Flow<List<LedgerEntryEntity>>

    @Query(
        "SELECT " +
            "COALESCE((SELECT SUM(delta) FROM ledger_entries " +
            "WHERE profileId = :profileId " +
            "AND reason IN ('TASK_COMPLETED', 'TASK_REOPENED', 'EXPERIENCE_RESET', 'INITIAL_BALANCE')), 0) " +
            "AS experiencePoints, " +
            "(SELECT COUNT(*) FROM completions WHERE profileId = :profileId " +
            "AND revokedAt IS NULL) AS completedTaskCount, " +
            "(SELECT COUNT(*) FROM redemptions WHERE profileId = :profileId " +
            "AND status = 'ACCEPTED') AS redemptionCount",
    )
    fun observeProgressStats(profileId: String): Flow<ProgressStatsRow>

    @Query(
        "SELECT " +
            "COALESCE((SELECT SUM(delta) FROM ledger_entries " +
            "WHERE profileId = :profileId " +
            "AND reason IN ('TASK_COMPLETED', 'TASK_REOPENED', 'EXPERIENCE_RESET', 'INITIAL_BALANCE')), 0) " +
            "AS experiencePoints, " +
            "(SELECT COUNT(*) FROM completions WHERE profileId = :profileId " +
            "AND revokedAt IS NULL) AS completedTaskCount, " +
            "(SELECT COUNT(*) FROM redemptions WHERE profileId = :profileId " +
            "AND status = 'ACCEPTED') AS redemptionCount",
    )
    suspend fun progressSnapshotRow(profileId: String): ProgressStatsRow

    @Query(
        "SELECT COALESCE(SUM(delta), 0) FROM ledger_entries " +
            "WHERE profileId = :profileId " +
            "AND reason IN ('TASK_COMPLETED', 'TASK_REOPENED', 'EXPERIENCE_RESET', 'INITIAL_BALANCE')",
    )
    suspend fun experiencePoints(profileId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity)

    @Query("SELECT * FROM ledger_entries ORDER BY createdAt, id")
    suspend fun allLedgerEntries(): List<LedgerEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntries(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM completions WHERE taskId = :taskId AND occurrenceKey = :occurrenceKey")
    suspend fun completion(taskId: String, occurrenceKey: String): CompletionEntity?

    @Query("SELECT * FROM completions WHERE id = :completionId")
    suspend fun completion(completionId: String): CompletionEntity?

    @Query("SELECT * FROM completions WHERE taskId = :taskId AND revokedAt IS NULL")
    suspend fun activeCompletionsForTask(taskId: String): List<CompletionEntity>

    @Query("SELECT * FROM completions ORDER BY completedAt, id")
    suspend fun allCompletions(): List<CompletionEntity>

    @Query(
        "SELECT * FROM completions WHERE profileId = :profileId AND revokedAt IS NULL " +
            "ORDER BY completedAt DESC, id DESC",
    )
    fun observeCompletions(profileId: String): Flow<List<CompletionEntity>>

    @Query(
        "SELECT * FROM completions WHERE profileId = :profileId AND revokedAt IS NULL " +
            "ORDER BY completedAt DESC, id DESC",
    )
    suspend fun completionSnapshotRows(profileId: String): List<CompletionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompletion(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompletions(completions: List<CompletionEntity>)

    @Update
    suspend fun updateCompletion(completion: CompletionEntity)

    @Query("UPDATE completions SET revokedAt = :revokedAt WHERE profileId = :profileId AND revokedAt IS NULL")
    suspend fun revokeActiveCompletions(profileId: String, revokedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRedemption(redemption: RedemptionEntity)

    @Query("SELECT * FROM redemptions ORDER BY createdAt, id")
    suspend fun allRedemptions(): List<RedemptionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRedemptions(redemptions: List<RedemptionEntity>)

    @Update
    suspend fun updateRedemption(redemption: RedemptionEntity)

    @Query(
        "SELECT * FROM redemptions WHERE profileId = :profileId " +
            "AND status = 'ACCEPTED' AND inventoryState = 'OWNED' " +
            "ORDER BY createdAt, id",
    )
    fun observeInventory(profileId: String): Flow<List<RedemptionEntity>>

    @Query(
        "SELECT * FROM redemptions WHERE profileId = :profileId " +
            "AND status = 'ACCEPTED' AND inventoryState = 'OWNED' " +
            "ORDER BY createdAt, id",
    )
    suspend fun inventorySnapshotRows(profileId: String): List<RedemptionEntity>

    @Query(
        "SELECT * FROM redemptions WHERE id = :itemId AND profileId = :profileId " +
            "AND status = 'ACCEPTED' AND inventoryState = 'OWNED'",
    )
    suspend fun ownedInventoryItem(itemId: String, profileId: String): RedemptionEntity?

    @Query(
        "UPDATE redemptions SET status = 'RESET', inventoryState = 'RESET', soldAt = :resetAt " +
            "WHERE profileId = :profileId AND status = 'ACCEPTED'",
    )
    suspend fun resetRedemptions(profileId: String, resetAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT COUNT(*) FROM events WHERE synced = 0")
    fun observePendingEventCount(): Flow<Int>

    @Query("SELECT * FROM events WHERE synced = 0 ORDER BY occurredAt, logicalCounter, eventId")
    suspend fun pendingEvents(): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY occurredAt, logicalCounter, eventId")
    suspend fun allEvents(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("SELECT * FROM processed_commands WHERE idempotencyKey = :idempotencyKey")
    suspend fun processedCommand(idempotencyKey: String): ProcessedCommandEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProcessedCommand(command: ProcessedCommandEntity)

    @Query("SELECT * FROM processed_commands ORDER BY processedAt, idempotencyKey")
    suspend fun allProcessedCommands(): List<ProcessedCommandEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProcessedCommands(commands: List<ProcessedCommandEntity>)

    @Query("DELETE FROM processed_commands")
    suspend fun clearProcessedCommands()

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM redemptions")
    suspend fun clearRedemptions()

    @Query("DELETE FROM ledger_entries")
    suspend fun clearLedgerEntries()

    @Query("DELETE FROM completions")
    suspend fun clearCompletions()

    @Query("DELETE FROM rewards")
    suspend fun clearRewards()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    @Query("DELETE FROM wish_goals")
    suspend fun clearWishGoals()
}
