package com.familyquest.application

import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.CommandMetadata
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.InventoryItem
import com.familyquest.domain.model.OperationResult
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.ProgressStats
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.TaskCategory
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.model.WishGoal
import com.familyquest.domain.repository.FamilyQuestRepository
import com.familyquest.domain.rules.BackupArchiveRules
import com.familyquest.domain.rules.DefaultProgressionPolicy
import com.familyquest.domain.rules.InventoryRules
import com.familyquest.domain.rules.ProgressionPolicy
import com.familyquest.domain.rules.RewardRules
import com.familyquest.domain.rules.TaskRecurrenceRules
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

private const val TASK_LEADERBOARD_LIMIT = 5

data class ProgressOverview(
    val gameProgress: com.familyquest.domain.model.GameProgress,
    val stats: ProgressStats,
)

data class TaskLeaderboardEntry(
    val taskId: String,
    val title: String,
    val emoji: String,
    val completionCount: Int,
    val totalEarned: Int,
    val lastCompletedAt: Long,
)

data class TaskLeaderboards(
    val mostCompleted: List<TaskLeaderboardEntry>,
    val highestEarning: List<TaskLeaderboardEntry>,
)

data class CommerceOverview(
    val balance: Int,
    val rewardOptions: List<RewardPurchaseOption>,
    val inventoryOptions: List<InventoryOption>,
    val refundPercent: Int,
)

data class RewardPurchaseOption(
    val reward: Reward,
    val rejectionReason: RejectionReason?,
    val effectiveCost: Int = reward.cost,
    val wishDeposit: Int = 0,
) {
    val canPurchase: Boolean get() = rejectionReason == null
}

data class InventoryOption(
    val item: InventoryItem,
    val saleRefund: Int,
)

class FamilyQuestService(
    private val repository: FamilyQuestRepository,
    private val eventExporter: EventExporter,
    private val commandTimeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MILLIS,
    private val progressionPolicy: ProgressionPolicy = DefaultProgressionPolicy,
    private val timeProvider: BusinessTimeProvider = SystemBusinessTimeProvider,
    private val snapshotExporter: SnapshotExporter = SnapshotExporter {
        error("Snapshot exporter is not configured")
    },
    private val backupCodec: BackupCodec? = null,
) {
    private val mutableHealth = MutableStateFlow<ApplicationHealth>(ApplicationHealth.Starting)
    val health: StateFlow<ApplicationHealth> = mutableHealth.asStateFlow()

    val profiles = repository.profiles.withFallback(emptyList())
    val rewards = repository.rewards.withFallback(emptyList())
    val selectedProfileId = repository.selectedProfileId
    val wishGoalRewardId = repository.wishGoalRewardId.withFallback(null)
    val wishGoal = repository.wishGoal.withFallback(null)
    val pendingEventCount = repository.pendingEventCount.withFallback(0)

    fun observeTasks(profileId: String) = combine(
        repository.observeTasks(profileId),
        repository.observeCompletions(profileId),
        timeProvider.ticks(),
    ) { tasks, completions, now ->
        projectTasks(tasks, completions, now, timeProvider.timeZone())
    }.withFallback(emptyList())
    fun observeBalance(profileId: String) = repository.observeBalance(profileId).withFallback(0)
    fun observeLedger(profileId: String) = repository.observeLedger(profileId).withFallback(emptyList())
    fun observeInventory(profileId: String) = repository.observeInventory(profileId).withFallback(emptyList())
    fun observeProgress(profileId: String) = repository.observeProgressStats(profileId)
        .map { stats -> ProgressOverview(progressionPolicy.project(stats), stats) }
        .withFallback(ProgressOverview(progressionPolicy.project(ProgressStats()), ProgressStats()))

    fun observeRecentCompletions(profileId: String) = repository.observeCompletions(profileId)
        .map { completions ->
            completions
                .asSequence()
                .filter { it.revokedAt == null }
                .sortedWith(
                    compareByDescending<TaskCompletion> { it.completedAt }
                        .thenByDescending { it.id },
                )
                .take(RECENT_COMPLETION_LIMIT)
                .toList()
        }
        .withFallback(emptyList())

    fun observeCompletedToday(profileId: String) = combine(
        repository.observeCompletions(profileId),
        timeProvider.ticks(),
    ) { completions, now ->
        completions.count {
            it.revokedAt == null && TaskRecurrenceRules.isCompletedToday(it.completedAt, now, timeProvider.timeZone())
        }
    }.withFallback(0)

    fun observeTaskLeaderboards(profileId: String) = combine(
        repository.observeTasks(profileId),
        repository.observeCompletions(profileId),
    ) { tasks, completions ->
        projectTaskLeaderboards(tasks.associateBy { it.id }, completions)
    }
        .withFallback(TaskLeaderboards(emptyList(), emptyList()))

    fun observeCommerce(profileId: String): Flow<CommerceOverview> = combine(
        repository.rewards,
        repository.observeBalance(profileId),
        repository.observeInventory(profileId),
        repository.wishGoal,
    ) { rewards, balance, inventory, wishGoal ->
        val profileWishGoal = wishGoal?.takeIf { it.profileId == profileId }
        CommerceOverview(
            balance = balance,
            rewardOptions = rewards.map { reward ->
                val deposit = profileWishGoal?.takeIf { it.rewardId == reward.id }?.deposit ?: 0
                val effectiveCost = (reward.cost - deposit).coerceAtLeast(0)
                RewardPurchaseOption(
                    reward = reward,
                    rejectionReason = RewardRules.validateRedemption(
                        balance = balance,
                        cost = effectiveCost,
                        stock = reward.stock,
                    ),
                    effectiveCost = effectiveCost,
                    wishDeposit = deposit,
                )
            },
            inventoryOptions = inventory.map { item ->
                InventoryOption(item, InventoryRules.saleRefund(item.cost))
            },
            refundPercent = InventoryRules.SALE_REFUND_PERCENT,
        )
    }.withFallback(emptyCommerceOverview())

    suspend fun ensureSeedData(): ApplicationResult<Unit> {
        val metadata = CommandMetadata(
            idempotencyKey = SEED_COMMAND_ID,
            traceId = newId(),
        )
        return execute(metadata) {
            repository.ensureSeedData(metadata).requireSuccess(metadata.traceId)
        }.also { result ->
            mutableHealth.value = when (result) {
                is ApplicationResult.Success -> ApplicationHealth.Ready
                is ApplicationResult.Failure -> ApplicationHealth.Degraded(result.errorCode, result.traceId)
            }
        }
    }

    suspend fun selectProfile(
        profileId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.selectProfile(profileId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun setWishGoal(
        rewardId: String?,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.setWishGoal(rewardId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun markWishGoalReminderShown(
        profileId: String,
        reminderDate: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.markWishGoalReminderShown(profileId, reminderDate, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun addProfile(
        name: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.addProfile(name, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun addTask(
        title: String,
        notes: String,
        rewardPoints: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        assigneeId: String,
        deadlineMinutes: Int? = null,
        weekDays: Set<Int> = emptySet(),
        monthDay: Int? = null,
        monthlyTargetCount: Int = 1,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
            repository.addTask(
                title,
                notes,
                rewardPoints,
                recurrence,
                emoji,
                assigneeId,
                metadata,
                deadlineMinutes,
                weekDays,
                monthDay,
                monthlyTargetCount,
            )
            .requireSuccess(metadata.traceId)
    }

    suspend fun updateTask(
        taskId: String,
        title: String,
        notes: String,
        rewardPoints: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        deadlineMinutes: Int? = null,
        weekDays: Set<Int> = emptySet(),
        monthDay: Int? = null,
        monthlyTargetCount: Int = 1,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        val updatedAt = timeProvider.now().toEpochMilliseconds()
        val timeZone = timeProvider.timeZone()
        repository.updateTask(
            taskId,
            title,
            notes,
            rewardPoints,
            recurrence,
            emoji,
            updatedAt,
            timeZone,
            metadata,
            deadlineMinutes,
            weekDays,
            monthDay,
            monthlyTargetCount,
        )
            .requireSuccess(metadata.traceId)
    }

    suspend fun deleteTask(
        taskId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.deleteTask(taskId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun completeTask(
        taskId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Boolean> = execute(metadata) {
        repository.completeTask(taskId, timeProvider.now().toEpochMilliseconds(), timeProvider.timeZone(), metadata)
            .requireMutation(metadata.traceId)
    }

    suspend fun revokeCompletion(
        completionId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Boolean> = execute(metadata) {
        repository.revokeCompletion(completionId, metadata).requireMutation(metadata.traceId)
    }

    suspend fun addReward(
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.addReward(name, description, cost, stock, emoji, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun updateReward(
        rewardId: String,
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.updateReward(rewardId, name, description, cost, stock, emoji, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun deleteReward(
        rewardId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.deleteReward(rewardId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun redeemReward(
        rewardId: String,
        profileId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.redeemReward(rewardId, profileId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun sellInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.sellInventoryItem(itemId, profileId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun useInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.useInventoryItem(itemId, profileId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun resetData(
        profileId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        repository.resetData(profileId, metadata)
            .requireSuccess(metadata.traceId)
    }

    suspend fun exportPendingEvents(
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<String> = execute(metadata) {
        eventExporter.export(repository.pendingEvents())
    }

    suspend fun exportBackup(
        profileId: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<String> = execute(metadata) {
        backupCodec?.let { codec ->
            return@execute codec.encode(
                repository.backupArchive(timeProvider.now().toEpochMilliseconds()),
            )
        }
        val source = repository.profileDataSnapshot(profileId)
            ?: throw BizException(ErrorCodeEnum.PROFILE_NOT_FOUND, metadata.traceId)
        val now = timeProvider.now()
        val timeZone = timeProvider.timeZone()
        val tasks = projectTasks(source.tasks, source.completions, now, timeZone)
        val completionCountsByTaskId = source.completions
            .asSequence()
            .filter { completion -> completion.revokedAt == null }
            .groupingBy { completion -> completion.taskId }
            .eachCount()
        snapshotExporter.export(
            BackupSnapshot(
                tasks = tasks.map { task ->
                    BackupTask(
                        id = task.id,
                        title = task.title,
                        type = task.recurrence.toBackupType(),
                        coins = task.rewardPoints,
                        completed = task.isCompleted,
                        emoji = task.emoji,
                        deadlineMinutes = task.deadlineMinutes,
                        weekDays = task.weekDays.sorted(),
                        monthDay = task.monthDay,
                        completions = completionCountsByTaskId[task.id] ?: 0,
                    )
                },
                coins = source.balance,
                totalEarned = source.progressStats.experiencePoints,
                completedToday = source.completions.count { completion ->
                    completion.revokedAt == null && TaskRecurrenceRules.isCompletedToday(
                        completion.completedAt,
                        now,
                        timeZone,
                    )
                },
                inventory = source.inventory.map { item ->
                    BackupInventoryItem(
                        id = item.id,
                        title = item.title,
                        emoji = item.emoji,
                        cost = item.cost,
                    )
                },
            ),
        )
    }

    suspend fun importBackup(
        content: String,
        metadata: CommandMetadata = newCommandMetadata(),
    ): ApplicationResult<Unit> = execute(metadata) {
        val codec = backupCodec
            ?: throw BizException(ErrorCodeEnum.BACKUP_UNSUPPORTED_VERSION, metadata.traceId)
        val archive = try {
            codec.decode(content)
        } catch (exception: BackupDecodeException) {
            val code = if (exception.unsupportedVersion) {
                ErrorCodeEnum.BACKUP_UNSUPPORTED_VERSION
            } else {
                ErrorCodeEnum.BACKUP_INVALID
            }
            throw BizException(code, metadata.traceId, exception)
        }
        if (archive.formatVersion !in 2..BackupArchive.CURRENT_FORMAT_VERSION) {
            throw BizException(ErrorCodeEnum.BACKUP_UNSUPPORTED_VERSION, metadata.traceId)
        }
        if (!BackupArchiveRules.isValid(archive)) {
            throw BizException(ErrorCodeEnum.BACKUP_INVALID, metadata.traceId)
        }
        repository.restoreBackup(archive, metadata)
            .requireSuccess(metadata.traceId)
    }

    private suspend fun <T> execute(
        metadata: CommandMetadata,
        block: suspend () -> T,
    ): ApplicationResult<T> {
        return try {
            val value = withTimeout(commandTimeoutMillis) { block() }
            ApplicationResult.Success(value, metadata.traceId)
        } catch (exception: TimeoutCancellationException) {
            ApplicationResult.Failure(
                errorCode = ErrorCodeEnum.COMMON_TIMEOUT,
                userMessage = ErrorCodeEnum.COMMON_TIMEOUT.userMessage,
                traceId = metadata.traceId,
            )
        } catch (exception: BizException) {
            ApplicationResult.Failure(
                errorCode = exception.errorCode,
                userMessage = exception.errorCode.userMessage,
                traceId = exception.traceId,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val error = BizException(ErrorCodeEnum.COMMON_UNEXPECTED, metadata.traceId, exception)
            ApplicationResult.Failure(
                errorCode = error.errorCode,
                userMessage = error.errorCode.userMessage,
                traceId = error.traceId,
            )
        }
    }

    private fun OperationResult.requireSuccess(traceId: String) {
        if (this is OperationResult.Rejected) {
            val code = when (reason) {
                RejectionReason.INSUFFICIENT_BALANCE -> ErrorCodeEnum.REWARD_INSUFFICIENT_BALANCE
                RejectionReason.OUT_OF_STOCK -> ErrorCodeEnum.REWARD_OUT_OF_STOCK
                RejectionReason.INVENTORY_FULL -> ErrorCodeEnum.INVENTORY_FULL
                RejectionReason.INVENTORY_ITEM_NOT_FOUND -> ErrorCodeEnum.INVENTORY_ITEM_NOT_FOUND
                RejectionReason.PROFILE_NOT_FOUND -> ErrorCodeEnum.PROFILE_NOT_FOUND
                RejectionReason.TASK_NOT_FOUND -> ErrorCodeEnum.TASK_NOT_FOUND
                RejectionReason.TASK_RECURRENCE_LOCKED -> ErrorCodeEnum.TASK_RECURRENCE_LOCKED
                RejectionReason.REWARD_NOT_FOUND -> ErrorCodeEnum.REWARD_NOT_FOUND
                RejectionReason.BACKUP_INVALID -> ErrorCodeEnum.BACKUP_INVALID
                RejectionReason.BACKUP_RESTORE_FAILED -> ErrorCodeEnum.BACKUP_RESTORE_FAILED
                RejectionReason.INVALID_INPUT -> ErrorCodeEnum.VALIDATION_INVALID_INPUT
            }
            throw BizException(code, traceId)
        }
    }

    private fun OperationResult.requireMutation(traceId: String): Boolean {
        requireSuccess(traceId)
        return this == OperationResult.Success
    }

    private fun <T> Flow<T>.withFallback(fallback: T): Flow<T> {
        return catch { exception ->
            if (exception is CancellationException) throw exception
            if (exception !is Exception) throw exception
            val traceId = newId()
            mutableHealth.value = ApplicationHealth.Degraded(
                errorCode = ErrorCodeEnum.COMMON_UNEXPECTED,
                traceId = traceId,
            )
            emit(fallback)
        }
    }

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MILLIS = 3_000L
        private const val SEED_COMMAND_ID = "bootstrap-seed-v2"
        private const val RECENT_COMPLETION_LIMIT = 20

        fun newCommandMetadata(): CommandMetadata {
            return CommandMetadata(
                idempotencyKey = newId(),
                traceId = newId(),
            )
        }

        @OptIn(ExperimentalUuidApi::class)
        private fun newId(): String = Uuid.random().toString()
    }
}

private fun projectTasks(
    tasks: List<HabitTask>,
    completions: List<TaskCompletion>,
    now: Instant,
    timeZone: TimeZone,
): List<HabitTask> {
    val activeByTask = completions
        .filter { it.revokedAt == null }
        .groupBy { it.taskId }
    return tasks.map { task ->
        val periodKey = TaskRecurrenceRules.occurrenceKey(task.recurrence, now, timeZone)
        val currentCompletions = activeByTask[task.id]
            .orEmpty()
            .filter { TaskRecurrenceRules.belongsToOccurrence(it.occurrenceKey, periodKey) }
        val completion = currentCompletions.maxWithOrNull(
            compareBy<TaskCompletion> { it.completedAt }.thenBy { it.id },
        )
        val targetCount = if (task.recurrence == TaskRecurrence.MONTHLY) task.monthlyTargetCount else 1
        task.copy(
            isCompleted = currentCompletions.size >= targetCount,
            completedAt = completion?.completedAt,
            currentPeriodCompletionCount = currentCompletions.size,
            currentCompletionId = completion?.id,
            currentCompletionReward = completion?.rewardSnapshot,
        )
    }
}

private fun projectTaskLeaderboards(
    activeTasks: Map<String, HabitTask>,
    completions: List<TaskCompletion>,
): TaskLeaderboards {
    val entries = completions
        .asSequence()
        .filter { it.revokedAt == null && it.taskId in activeTasks }
        .groupBy { it.taskId }
        .map { (taskId, taskCompletions) ->
            val latest = taskCompletions.maxWithOrNull(
                compareBy<TaskCompletion> { it.completedAt }.thenBy { it.id },
            ) ?: error("Completion group cannot be empty")
            val task = activeTasks.getValue(taskId)
            TaskLeaderboardEntry(
                taskId = taskId,
                title = task.title,
                emoji = task.emoji,
                completionCount = taskCompletions.size,
                totalEarned = taskCompletions.sumOf { it.rewardSnapshot },
                lastCompletedAt = latest.completedAt,
            )
        }

    val tieBreak = compareByDescending<TaskLeaderboardEntry> { it.lastCompletedAt }
        .thenBy { it.taskId }
    return TaskLeaderboards(
        mostCompleted = entries.sortedWith(
            compareByDescending<TaskLeaderboardEntry> { it.completionCount }.then(tieBreak),
        ).take(TASK_LEADERBOARD_LIMIT),
        highestEarning = entries.sortedWith(
            compareByDescending<TaskLeaderboardEntry> { it.totalEarned }.then(tieBreak),
        ).take(TASK_LEADERBOARD_LIMIT),
    )
}

private fun emptyCommerceOverview() = CommerceOverview(
    balance = 0,
    rewardOptions = emptyList(),
    inventoryOptions = emptyList(),
    refundPercent = InventoryRules.SALE_REFUND_PERCENT,
)

private fun TaskRecurrence.toBackupType(): String {
    return when (this) {
        TaskRecurrence.ONCE -> "custom"
        TaskRecurrence.DAILY -> "daily"
        TaskRecurrence.WEEKLY -> "weekly"
        TaskRecurrence.MONTHLY -> "monthly"
        TaskRecurrence.YEARLY -> "yearly"
    }
}
