package com.familyquest.data

import androidx.room.withTransaction
import com.familyquest.data.db.CompletionEntity
import com.familyquest.data.db.EventEntity
import com.familyquest.data.db.FamilyQuestDatabase
import com.familyquest.data.db.LedgerEntryEntity
import com.familyquest.data.db.ProcessedCommandEntity
import com.familyquest.data.db.ProgressStatsRow
import com.familyquest.data.db.ProfileEntity
import com.familyquest.data.db.RedemptionEntity
import com.familyquest.data.db.RewardEntity
import com.familyquest.data.db.RewardWithCountRow
import com.familyquest.data.db.TaskEntity
import com.familyquest.data.db.WishGoalEntity
import com.familyquest.domain.event.AggregateType
import com.familyquest.domain.event.DomainEvent
import com.familyquest.domain.event.EventPayload
import com.familyquest.domain.event.EventPayloadCodec
import com.familyquest.domain.event.EventIds
import com.familyquest.domain.event.EventType
import com.familyquest.domain.event.EventValue
import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.BackupCompletionRecord
import com.familyquest.domain.model.BackupEventRecord
import com.familyquest.domain.model.BackupLedgerRecord
import com.familyquest.domain.model.BackupProcessedCommandRecord
import com.familyquest.domain.model.BackupProfileRecord
import com.familyquest.domain.model.BackupRedemptionRecord
import com.familyquest.domain.model.BackupRewardRecord
import com.familyquest.domain.model.BackupTaskRecord
import com.familyquest.domain.model.CommandMetadata
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.InventoryItem
import com.familyquest.domain.model.LedgerEntry
import com.familyquest.domain.model.LedgerReason
import com.familyquest.domain.model.OperationResult
import com.familyquest.domain.model.Profile
import com.familyquest.domain.model.ProfileDataSnapshot
import com.familyquest.domain.model.ProgressStats
import com.familyquest.domain.model.RedemptionStatus
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.TaskCategory
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.model.WishGoal
import com.familyquest.domain.repository.FamilyQuestRepository
import com.familyquest.domain.rules.BackupArchiveRules
import com.familyquest.domain.rules.InventoryRules
import com.familyquest.domain.rules.RewardRules
import com.familyquest.domain.rules.TaskScheduleRules
import com.familyquest.domain.rules.TaskRecurrenceRules
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class RoomFamilyQuestRepository(
    private val eventPayloadCodec: EventPayloadCodec,
    private val database: FamilyQuestDatabase,
    private val preferences: PreferencesStore,
    private val deviceIdentity: DeviceIdentity,
) : FamilyQuestRepository {
    private val dao = database.dao()
    private val mutableSelectedProfileId = MutableStateFlow(preferences.getString(KEY_SELECTED_PROFILE))

    override val selectedProfileId: StateFlow<String?> = mutableSelectedProfileId
    override val wishGoal: Flow<WishGoal?> = mutableSelectedProfileId.flatMapLatest { profileId ->
        if (profileId == null) flowOf(null) else dao.observeWishGoal(profileId).map { it?.toDomain() }
    }
    override val wishGoalRewardId: Flow<String?> = wishGoal.map { it?.rewardId }
    override val profiles: Flow<List<Profile>> = dao.observeProfiles().map { rows -> rows.map(ProfileEntity::toDomain) }
    override val rewards: Flow<List<Reward>> = dao.observeRewards().map { rows -> rows.map(RewardWithCountRow::toDomain) }
    override val pendingEventCount: Flow<Int> = dao.observePendingEventCount()

    override fun observeTasks(profileId: String): Flow<List<HabitTask>> {
        return dao.observeTasks(profileId).map { rows -> rows.map(TaskEntity::toDomain) }
    }

    override fun observeBalance(profileId: String): Flow<Int> = dao.observeBalance(profileId)

    override fun observeLedger(profileId: String): Flow<List<LedgerEntry>> {
        return dao.observeLedger(profileId).map { rows -> rows.map(LedgerEntryEntity::toDomain) }
    }

    override fun observeProgressStats(profileId: String): Flow<ProgressStats> {
        return dao.observeProgressStats(profileId).map(ProgressStatsRow::toDomain)
    }

    override fun observeCompletions(profileId: String): Flow<List<TaskCompletion>> {
        return dao.observeCompletions(profileId).map { rows -> rows.map(CompletionEntity::toDomain) }
    }

    override fun observeInventory(profileId: String): Flow<List<InventoryItem>> {
        return dao.observeInventory(profileId).map { rows -> rows.map(RedemptionEntity::toInventoryItem) }
    }

    override suspend fun profileDataSnapshot(profileId: String): ProfileDataSnapshot? {
        return database.withTransaction {
            if (dao.profile(profileId) == null) return@withTransaction null
            ProfileDataSnapshot(
                profileId = profileId,
                tasks = dao.taskSnapshotRows(profileId).map(TaskEntity::toDomain),
                completions = dao.completionSnapshotRows(profileId).map(CompletionEntity::toDomain),
                balance = dao.balance(profileId),
                progressStats = dao.progressSnapshotRow(profileId).toDomain(),
                inventory = dao.inventorySnapshotRows(profileId).map(RedemptionEntity::toInventoryItem),
            )
        }
    }

    override suspend fun ensureSeedData(metadata: CommandMetadata): OperationResult {
        val existingProfiles = dao.profiles()
        if (existingProfiles.isEmpty() && dao.storedRowCount() == 0) return seedFreshDatabase(metadata)
        if (existingProfiles.isEmpty()) return OperationResult.Success
        if (mutableSelectedProfileId.value !in existingProfiles.map { it.id }) {
            selectProfile(existingProfiles.first().id, metadata)
        }
        migrateLegacyWishGoal(mutableSelectedProfileId.value)
        migrateSeedCatalog(metadata)
        return OperationResult.Success
    }

    private suspend fun seedFreshDatabase(metadata: CommandMetadata): OperationResult {
        val result = processCommand(metadata, OP_SEED_DATABASE) {
            val now = Clock.System.now().toEpochMilliseconds()
            dao.insertProfile(ProfileEntity(SEED_PROFILE_ID, "Family", 0, now))
            dao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = newId(),
                    profileId = SEED_PROFILE_ID,
                    delta = INITIAL_BALANCE,
                    reason = LedgerReason.INITIAL_BALANCE.name,
                    referenceId = SEED_PROFILE_ID,
                    label = "Welcome balance",
                    createdAt = now,
                ),
            )
            recordEvent(
                metadata = metadata.forSeed("initial-balance"),
                actorId = SEED_PROFILE_ID,
                aggregateType = AggregateType.PROFILE,
                aggregateId = SEED_PROFILE_ID,
                eventType = EventType.INITIAL_BALANCE_GRANTED,
                payload = mapOf(
                    "profileId" to EventValue.Text(SEED_PROFILE_ID),
                    "amount" to EventValue.Integer(INITIAL_BALANCE.toLong()),
                ),
            )
            recordEvent(
                metadata = metadata.forSeed("profile"),
                actorId = SEED_PROFILE_ID,
                aggregateType = AggregateType.PROFILE,
                aggregateId = SEED_PROFILE_ID,
                eventType = EventType.PROFILE_CREATED,
                payload = mapOf(
                    "name" to EventValue.Text("Family"),
                    "accentIndex" to EventValue.Integer(0),
                ),
            )
            SEED_TASKS.forEachIndexed { index, seed ->
                val orderedAt = now + SEED_TASKS.size - index
                val task = TaskEntity(
                    id = seed.id,
                    title = seed.title,
                    notes = "",
                    category = TaskCategory.DAILY.name,
                    rewardPoints = seed.reward,
                    assigneeId = SEED_PROFILE_ID,
                    isCompleted = false,
                    createdAt = orderedAt,
                    updatedAt = orderedAt,
                    completedAt = null,
                    recurrence = seed.recurrence.name,
                    emoji = seed.emoji,
                    deadlineMinutes = seed.deadlineMinutes,
                    weekDaysMask = seed.weekDays.toMask(),
                    monthDay = seed.monthDay,
                    monthlyTargetCount = seed.monthlyTargetCount,
                )
                dao.insertTask(task)
                recordEvent(
                    metadata = metadata.forSeed(task.id),
                    actorId = SEED_PROFILE_ID,
                    aggregateType = AggregateType.TASK,
                    aggregateId = task.id,
                    eventType = EventType.TASK_CREATED,
                    payload = taskPayload(task),
                )
            }
            SEED_REWARDS.forEachIndexed { index, seed ->
                val reward = RewardEntity(
                    id = seed.id,
                    name = seed.name,
                    description = seed.description,
                    cost = seed.cost,
                    stock = null,
                    active = true,
                    createdAt = now + SEED_TASKS.size + index + 1,
                    updatedAt = now + SEED_TASKS.size + index + 1,
                    emoji = seed.emoji,
                )
                dao.insertReward(reward)
                recordEvent(
                    metadata = metadata.forSeed(reward.id),
                    actorId = SEED_PROFILE_ID,
                    aggregateType = AggregateType.REWARD,
                    aggregateId = reward.id,
                    eventType = EventType.REWARD_CREATED,
                    payload = rewardPayload(reward),
                )
            }
            OperationResult.Success
        }
        if (result == OperationResult.Success || result == OperationResult.NoChange) {
            persistSelectedProfile(SEED_PROFILE_ID)
        }
        return result
    }

    override suspend fun selectProfile(
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult {
        if (!metadata.isValid()) return invalidInput()
        if (dao.profile(profileId) == null) {
            return OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
        }
        persistSelectedProfile(profileId)
        return OperationResult.Success
    }

    override suspend fun setWishGoal(
        rewardId: String?,
        metadata: CommandMetadata,
    ): OperationResult {
        return processCommand(metadata, OP_SET_WISH_GOAL) {
            val profileId = mutableSelectedProfileId.value
                ?: return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
            if (dao.profile(profileId) == null) {
                return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
            }
            val current = dao.wishGoal(profileId)
            if (rewardId == null) {
                if (current == null) return@processCommand OperationResult.NoChange
                dao.deleteWishGoal(profileId)
                dao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id = newId(),
                        profileId = profileId,
                        delta = 0,
                        reason = LedgerReason.WISH_GOAL_CLEARED.name,
                        referenceId = current.rewardId,
                        label = "Wish goal cleared",
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
                recordEvent(
                    metadata = metadata,
                    actorId = profileId,
                    aggregateType = AggregateType.PROFILE,
                    aggregateId = profileId,
                    eventType = EventType.WISH_GOAL_UPDATED,
                    payload = mapOf(
                        "profileId" to EventValue.Text(profileId),
                        "rewardId" to EventValue.Null,
                        "deposit" to EventValue.Integer(0),
                    ),
                )
                return@processCommand OperationResult.Success
            }
            val reward = dao.reward(rewardId)
                ?: return@processCommand OperationResult.Rejected(RejectionReason.REWARD_NOT_FOUND)
            if (!reward.active) return@processCommand OperationResult.Rejected(RejectionReason.REWARD_NOT_FOUND)
            if (current?.rewardId == rewardId) return@processCommand OperationResult.NoChange
            if (current != null) {
                dao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id = newId(),
                        profileId = profileId,
                        delta = 0,
                        reason = LedgerReason.WISH_GOAL_CLEARED.name,
                        referenceId = current.rewardId,
                        label = "Wish goal replaced",
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            }
            val deposit = reward.cost / 10
            val now = Clock.System.now().toEpochMilliseconds()
            dao.upsertWishGoal(WishGoalEntity(profileId, rewardId, deposit))
            dao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = newId(),
                    profileId = profileId,
                    delta = -deposit,
                    reason = LedgerReason.WISH_GOAL_DEPOSITED.name,
                    referenceId = rewardId,
                    label = "Wish goal deposit: ${reward.name}",
                    createdAt = now,
                ),
            )
            recordEvent(
                metadata = metadata,
                actorId = profileId,
                aggregateType = AggregateType.PROFILE,
                aggregateId = profileId,
                eventType = EventType.WISH_GOAL_UPDATED,
                payload = mapOf(
                    "profileId" to EventValue.Text(profileId),
                    "rewardId" to EventValue.Text(rewardId),
                    "deposit" to EventValue.Integer(deposit.toLong()),
                    "previousRewardId" to (current?.rewardId?.let(EventValue::Text) ?: EventValue.Null),
                ),
            )
            OperationResult.Success
        }
    }

    override suspend fun markWishGoalReminderShown(
        profileId: String,
        reminderDate: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_MARK_WISH_GOAL_REMINDER) {
        if (profileId.isBlank() || reminderDate.isBlank()) return@processCommand invalidInput()
        if (dao.wishGoal(profileId) == null) return@processCommand OperationResult.NoChange
        if (dao.wishGoal(profileId)?.lastReminderDate == reminderDate) {
            return@processCommand OperationResult.NoChange
        }
        dao.updateWishGoalReminderDate(profileId, reminderDate)
        OperationResult.Success
    }

    override suspend fun addProfile(name: String, metadata: CommandMetadata): OperationResult {
        val result = processCommand(metadata, OP_ADD_PROFILE) {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) return@processCommand invalidInput()
            val profileId = newId()
            val now = Clock.System.now().toEpochMilliseconds()
            val accent = dao.profiles().size % 5
            dao.insertProfile(ProfileEntity(profileId, cleanName, accent, now))
            recordEvent(
                metadata = metadata,
                actorId = profileId,
                aggregateType = AggregateType.PROFILE,
                aggregateId = profileId,
                eventType = EventType.PROFILE_CREATED,
                payload = mapOf(
                    "name" to EventValue.Text(cleanName),
                    "accentIndex" to EventValue.Integer(accent.toLong()),
                ),
            )
            OperationResult.Success
        }
        if (result == OperationResult.Success && mutableSelectedProfileId.value == null) {
            dao.profiles().firstOrNull()?.let { selectProfile(it.id, metadata) }
        }
        return result
    }

    override suspend fun addTask(
        title: String,
        notes: String,
        rewardPoints: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        assigneeId: String,
        metadata: CommandMetadata,
        deadlineMinutes: Int?,
        weekDays: Set<Int>,
        monthDay: Int?,
        monthlyTargetCount: Int,
    ): OperationResult = processCommand(metadata, OP_ADD_TASK) {
        val cleanTitle = title.trim()
        val cleanEmoji = emoji.trim()
        if (cleanTitle.isEmpty() || cleanEmoji.isEmpty() || rewardPoints < 1 || assigneeId.isBlank()) {
            return@processCommand invalidInput()
        }
        if (dao.profile(assigneeId) == null) {
            return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
        }
        val schedule = TaskScheduleRules.normalize(
            recurrence,
            deadlineMinutes,
            weekDays,
            monthDay,
            monthlyTargetCount,
        )
            ?: return@processCommand invalidInput()
        val id = newId()
            val now = Clock.System.now().toEpochMilliseconds()
        val task = TaskEntity(
            id = id,
            title = cleanTitle,
            notes = notes.trim(),
            category = TaskCategory.DAILY.name,
            rewardPoints = rewardPoints,
            assigneeId = assigneeId,
            isCompleted = false,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            recurrence = recurrence.name,
            emoji = cleanEmoji,
            deadlineMinutes = schedule.deadlineMinutes,
            weekDaysMask = schedule.weekDays.toMask(),
            monthDay = schedule.monthDay,
            monthlyTargetCount = schedule.monthlyTargetCount,
        )
        dao.insertTask(task)
        recordEvent(
            metadata = metadata,
            actorId = assigneeId,
            aggregateType = AggregateType.TASK,
            aggregateId = id,
            eventType = EventType.TASK_CREATED,
            payload = taskPayload(task),
        )
        OperationResult.Success
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        notes: String,
        rewardPoints: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        updatedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
        deadlineMinutes: Int?,
        weekDays: Set<Int>,
        monthDay: Int?,
        monthlyTargetCount: Int,
    ): OperationResult = processCommand(metadata, OP_UPDATE_TASK) {
        val cleanTitle = title.trim()
        val cleanEmoji = emoji.trim()
        if (cleanTitle.isEmpty() || cleanEmoji.isEmpty() || rewardPoints < 1) return@processCommand invalidInput()
        val old = dao.task(taskId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.TASK_NOT_FOUND)
        val schedule = TaskScheduleRules.normalize(
            recurrence,
            deadlineMinutes,
            weekDays,
            monthDay,
            monthlyTargetCount,
        )
            ?: return@processCommand invalidInput()
        val oldRecurrence = old.recurrence.toRecurrence()
        if (recurrence != oldRecurrence && recurrence != TaskRecurrence.ONCE) {
            val currentOccurrenceKey = TaskRecurrenceRules.occurrenceKey(
                recurrence,
                Instant.fromEpochMilliseconds(updatedAt),
                timeZone,
            )
            val conflictsWithCurrentOccurrence = dao.activeCompletionsForTask(taskId).any { completion ->
                TaskRecurrenceRules.occurrenceKey(
                    recurrence,
                    Instant.fromEpochMilliseconds(completion.completedAt),
                    timeZone,
                ) == currentOccurrenceKey
            }
            if (conflictsWithCurrentOccurrence) {
                return@processCommand OperationResult.Rejected(RejectionReason.TASK_RECURRENCE_LOCKED)
            }
        }
        val updated = old.copy(
            title = cleanTitle,
            notes = notes.trim(),
            rewardPoints = rewardPoints,
            recurrence = recurrence.name,
            emoji = cleanEmoji,
            deadlineMinutes = schedule.deadlineMinutes,
            weekDaysMask = schedule.weekDays.toMask(),
            monthDay = schedule.monthDay,
            monthlyTargetCount = schedule.monthlyTargetCount,
            updatedAt = updatedAt,
        )
        dao.updateTask(updated)
        recordEvent(
            metadata = metadata,
            actorId = updated.assigneeId,
            aggregateType = AggregateType.TASK,
            aggregateId = taskId,
            eventType = EventType.TASK_UPDATED,
            payload = taskPayload(updated),
        )
        OperationResult.Success
    }

    override suspend fun deleteTask(
        taskId: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_DELETE_TASK) {
        val task = dao.task(taskId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.TASK_NOT_FOUND)
            val now = Clock.System.now().toEpochMilliseconds()
        dao.updateTask(task.copy(deletedAt = now, updatedAt = now))
        recordEvent(
            metadata = metadata,
            actorId = task.assigneeId,
            aggregateType = AggregateType.TASK,
            aggregateId = taskId,
            eventType = EventType.TASK_DELETED,
            payload = mapOf("deletedAt" to EventValue.Integer(now)),
        )
        OperationResult.Success
    }

    override suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_COMPLETE_TASK) {
        val task = dao.task(taskId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.TASK_NOT_FOUND)
        val recurrence = task.recurrence.toRecurrence()
        val completionInstant = Instant.fromEpochMilliseconds(completedAt)
        val periodKey = TaskRecurrenceRules.occurrenceKey(
            recurrence,
            completionInstant,
            timeZone,
        )
        val targetCount = if (recurrence == TaskRecurrence.MONTHLY) task.monthlyTargetCount else 1
        if (targetCount !in TaskScheduleRules.MIN_MONTHLY_TARGET_COUNT..TaskScheduleRules.MAX_MONTHLY_TARGET_COUNT) {
            return@processCommand invalidInput()
        }
        val activeKeys = dao.activeCompletionsForTask(taskId)
            .asSequence()
            .map { it.occurrenceKey }
            .filter { TaskRecurrenceRules.belongsToOccurrence(it, periodKey) }
            .toSet()
        if (activeKeys.size >= targetCount) return@processCommand OperationResult.NoChange
        val occurrenceIndex = (1..targetCount).first { index ->
            TaskRecurrenceRules.completionOccurrenceKey(recurrence, completionInstant, timeZone, index) !in activeKeys
        }
        val occurrenceKey = TaskRecurrenceRules.completionOccurrenceKey(
            recurrence,
            completionInstant,
            timeZone,
            occurrenceIndex,
        )
        val existing = dao.completion(taskId, occurrenceKey)

        val completion = existing?.copy(
            profileId = task.assigneeId,
            rewardSnapshot = task.rewardPoints,
            titleSnapshot = task.title,
            emojiSnapshot = task.emoji,
            recurrenceSnapshot = recurrence.name,
            completedAt = completedAt,
            revokedAt = null,
        ) ?: CompletionEntity(
            id = newId(),
            taskId = taskId,
            occurrenceKey = occurrenceKey,
            profileId = task.assigneeId,
            rewardSnapshot = task.rewardPoints,
            completedAt = completedAt,
            revokedAt = null,
            titleSnapshot = task.title,
            emojiSnapshot = task.emoji,
            recurrenceSnapshot = recurrence.name,
        )
        if (existing == null) dao.insertCompletion(completion) else dao.updateCompletion(completion)
        dao.insertLedgerEntry(
            LedgerEntryEntity(
                id = newId(),
                profileId = task.assigneeId,
                delta = task.rewardPoints,
                reason = LedgerReason.TASK_COMPLETED.name,
                referenceId = completion.id,
                label = task.title,
                createdAt = completedAt,
            ),
        )
        if (recurrence == TaskRecurrence.ONCE) {
            dao.updateTask(task.copy(isCompleted = true, completedAt = completedAt, updatedAt = completedAt))
        }
        recordEvent(
            metadata = metadata,
            actorId = task.assigneeId,
            aggregateType = AggregateType.TASK,
            aggregateId = taskId,
            eventType = EventType.TASK_COMPLETED,
            payload = mapOf(
                "completionId" to EventValue.Text(completion.id),
                "occurrenceKey" to EventValue.Text(occurrenceKey),
                "periodKey" to EventValue.Text(periodKey),
                "occurrenceIndex" to EventValue.Integer(occurrenceIndex.toLong()),
                "targetCount" to EventValue.Integer(targetCount.toLong()),
                "recurrence" to EventValue.Text(recurrence.name),
                "reward" to EventValue.Integer(task.rewardPoints.toLong()),
            ),
        )
        OperationResult.Success
    }

    override suspend fun revokeCompletion(
        completionId: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_REVOKE_COMPLETION) {
        val completion = dao.completion(completionId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.TASK_NOT_FOUND)
        if (completion.revokedAt != null) return@processCommand OperationResult.NoChange
        val task = dao.taskIncludingDeleted(completion.taskId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.TASK_NOT_FOUND)
            val now = Clock.System.now().toEpochMilliseconds()
        dao.updateCompletion(completion.copy(revokedAt = now))
        dao.insertLedgerEntry(
            LedgerEntryEntity(
                id = newId(),
                profileId = completion.profileId,
                delta = -completion.rewardSnapshot,
                reason = LedgerReason.TASK_REOPENED.name,
                referenceId = completion.id,
                label = completion.titleSnapshot,
                createdAt = now,
            ),
        )
        if (completion.recurrenceSnapshot.toRecurrence() == TaskRecurrence.ONCE) {
            dao.updateTask(
                task.copy(
                    isCompleted = false,
                    completedAt = null,
                    updatedAt = now,
                ),
            )
        }
        recordEvent(
            metadata = metadata,
            actorId = completion.profileId,
            aggregateType = AggregateType.TASK,
            aggregateId = completion.taskId,
            eventType = EventType.TASK_COMPLETION_REVOKED,
            payload = mapOf(
                "completionId" to EventValue.Text(completion.id),
                "occurrenceKey" to EventValue.Text(completion.occurrenceKey),
            ),
        )
        OperationResult.Success
    }

    override suspend fun addReward(
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_ADD_REWARD) {
        val cleanName = name.trim()
        val cleanEmoji = emoji.trim()
        if (cleanName.isEmpty() || cleanEmoji.isEmpty() || cost < 1 || (stock != null && stock < 0)) {
            return@processCommand invalidInput()
        }
        val id = newId()
            val now = Clock.System.now().toEpochMilliseconds()
        val reward = RewardEntity(id, cleanName, description.trim(), cost, stock, true, now, now, cleanEmoji)
        dao.insertReward(reward)
        recordEvent(
            metadata = metadata,
            actorId = mutableSelectedProfileId.value,
            aggregateType = AggregateType.REWARD,
            aggregateId = id,
            eventType = EventType.REWARD_CREATED,
            payload = rewardPayload(reward),
        )
        OperationResult.Success
    }

    override suspend fun updateReward(
        rewardId: String,
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_UPDATE_REWARD) {
        val cleanName = name.trim()
        val cleanEmoji = emoji.trim()
        if (cleanName.isEmpty() || cleanEmoji.isEmpty() || cost < 1 || (stock != null && stock < 0)) {
            return@processCommand invalidInput()
        }
        val old = dao.reward(rewardId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.REWARD_NOT_FOUND)
        val updated = old.copy(
            name = cleanName,
            description = description.trim(),
            cost = cost,
            stock = stock,
            emoji = cleanEmoji,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        dao.updateReward(updated)
        recordEvent(
            metadata = metadata,
            actorId = mutableSelectedProfileId.value,
            aggregateType = AggregateType.REWARD,
            aggregateId = rewardId,
            eventType = EventType.REWARD_UPDATED,
            payload = rewardPayload(updated),
        )
        OperationResult.Success
    }

    override suspend fun deleteReward(
        rewardId: String,
        metadata: CommandMetadata,
    ): OperationResult {
        val result = processCommand(metadata, OP_DELETE_REWARD) {
            val reward = dao.reward(rewardId)
                ?: return@processCommand OperationResult.Rejected(RejectionReason.REWARD_NOT_FOUND)
            val now = Clock.System.now().toEpochMilliseconds()
            dao.updateReward(reward.copy(active = false, updatedAt = now))
            dao.wishGoalsForReward(rewardId).forEach { goal ->
                dao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id = newId(),
                        profileId = goal.profileId,
                        delta = 0,
                        reason = LedgerReason.WISH_GOAL_CLEARED.name,
                        referenceId = rewardId,
                        label = "Wish goal cleared: reward deleted",
                        createdAt = now,
                    ),
                )
                recordEvent(
                    metadata = metadata.forSeed("wish-clear-${goal.profileId}"),
                    actorId = goal.profileId,
                    aggregateType = AggregateType.PROFILE,
                    aggregateId = goal.profileId,
                    eventType = EventType.WISH_GOAL_UPDATED,
                    payload = mapOf(
                        "profileId" to EventValue.Text(goal.profileId),
                        "rewardId" to EventValue.Null,
                        "deposit" to EventValue.Integer(0),
                        "reason" to EventValue.Text("REWARD_DELETED"),
                    ),
                )
            }
            dao.deleteWishGoalsForReward(rewardId)
            recordEvent(
                metadata = metadata,
                actorId = mutableSelectedProfileId.value,
                aggregateType = AggregateType.REWARD,
                aggregateId = rewardId,
                eventType = EventType.REWARD_DELETED,
                payload = mapOf("deletedAt" to EventValue.Integer(now)),
            )
            OperationResult.Success
        }
        return result
    }

    override suspend fun redeemReward(
        rewardId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_REDEEM_REWARD) {
        if (dao.profile(profileId) == null) {
            return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
        }
        val reward = dao.reward(rewardId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.REWARD_NOT_FOUND)
        val wishGoal = dao.wishGoal(profileId)?.takeIf { it.rewardId == rewardId }
        val effectiveCost = (reward.cost - (wishGoal?.deposit ?: 0)).coerceAtLeast(0)
        val rejection = RewardRules.validateRedemption(
            balance = dao.balance(profileId),
            cost = effectiveCost,
            stock = reward.stock,
        )
        if (rejection != null) return@processCommand OperationResult.Rejected(rejection)

            val now = Clock.System.now().toEpochMilliseconds()
        val redemptionId = newId()
        dao.insertRedemption(
            RedemptionEntity(
                id = redemptionId,
                rewardId = rewardId,
                rewardName = reward.name,
                profileId = profileId,
                costSnapshot = reward.cost,
                status = RedemptionStatus.ACCEPTED.name,
                createdAt = now,
                emojiSnapshot = reward.emoji,
                inventoryState = INVENTORY_OWNED,
                soldAt = null,
            ),
        )
        dao.insertLedgerEntry(
            LedgerEntryEntity(
                id = newId(),
                profileId = profileId,
                delta = -effectiveCost,
                reason = LedgerReason.REWARD_REDEEMED.name,
                referenceId = redemptionId,
                label = reward.name,
                createdAt = now,
            ),
        )
        if (wishGoal != null) {
            dao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = newId(),
                    profileId = profileId,
                    delta = 0,
                    reason = LedgerReason.WISH_GOAL_CLEARED.name,
                    referenceId = redemptionId,
                    label = "Wish goal cleared on redemption",
                    createdAt = now,
                ),
            )
            dao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = newId(),
                    profileId = profileId,
                    delta = WISH_GOAL_REDEEM_BONUS,
                    reason = LedgerReason.WISH_GOAL_REDEEM_BONUS.name,
                    referenceId = redemptionId,
                    label = "Wish goal redemption bonus",
                    createdAt = now,
                ),
            )
            dao.deleteWishGoal(profileId)
        }
        if (reward.stock != null) {
            dao.updateReward(reward.copy(stock = reward.stock - 1, updatedAt = now))
        }
        recordEvent(
            metadata = metadata,
            actorId = profileId,
            aggregateType = AggregateType.REDEMPTION,
            aggregateId = redemptionId,
            eventType = EventType.REWARD_REDEEMED,
            payload = mapOf(
                "rewardId" to EventValue.Text(reward.id),
                "rewardName" to EventValue.Text(reward.name),
                "cost" to EventValue.Integer(reward.cost.toLong()),
                "effectiveCost" to EventValue.Integer(effectiveCost.toLong()),
                "wishDeposit" to EventValue.Integer((wishGoal?.deposit ?: 0).toLong()),
                "wishBonus" to EventValue.Integer(if (wishGoal == null) 0 else WISH_GOAL_REDEEM_BONUS.toLong()),
                "profileId" to EventValue.Text(profileId),
                "emoji" to EventValue.Text(reward.emoji),
                "inventoryState" to EventValue.Text(INVENTORY_OWNED),
            ),
        )
        OperationResult.Success
    }

    override suspend fun sellInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_SELL_INVENTORY_ITEM) {
        if (dao.profile(profileId) == null) {
            return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
        }
        val item = dao.ownedInventoryItem(itemId, profileId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.INVENTORY_ITEM_NOT_FOUND)
            val now = Clock.System.now().toEpochMilliseconds()
        val refund = InventoryRules.saleRefund(item.costSnapshot)
        dao.updateRedemption(item.copy(inventoryState = INVENTORY_SOLD, soldAt = now))
        dao.insertLedgerEntry(
            LedgerEntryEntity(
                id = newId(),
                profileId = profileId,
                delta = refund,
                reason = LedgerReason.INVENTORY_ITEM_SOLD.name,
                referenceId = item.id,
                label = item.rewardName,
                createdAt = now,
            ),
        )
        recordEvent(
            metadata = metadata,
            actorId = profileId,
            aggregateType = AggregateType.REDEMPTION,
            aggregateId = item.id,
            eventType = EventType.INVENTORY_ITEM_SOLD,
            payload = mapOf(
                "itemId" to EventValue.Text(item.id),
                "rewardId" to EventValue.Text(item.rewardId),
                "title" to EventValue.Text(item.rewardName),
                "emoji" to EventValue.Text(item.emojiSnapshot),
                "cost" to EventValue.Integer(item.costSnapshot.toLong()),
                "refund" to EventValue.Integer(refund.toLong()),
                "profileId" to EventValue.Text(profileId),
                "soldAt" to EventValue.Integer(now),
            ),
        )
        OperationResult.Success
    }

    override suspend fun useInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_USE_INVENTORY_ITEM) {
        if (dao.profile(profileId) == null) {
            return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
        }
        val item = dao.ownedInventoryItem(itemId, profileId)
            ?: return@processCommand OperationResult.Rejected(RejectionReason.INVENTORY_ITEM_NOT_FOUND)
            val now = Clock.System.now().toEpochMilliseconds()
        dao.updateRedemption(item.copy(inventoryState = INVENTORY_USED, soldAt = null))
        recordEvent(
            metadata = metadata,
            actorId = profileId,
            aggregateType = AggregateType.REDEMPTION,
            aggregateId = item.id,
            eventType = EventType.INVENTORY_ITEM_USED,
            payload = mapOf(
                "itemId" to EventValue.Text(item.id),
                "rewardId" to EventValue.Text(item.rewardId),
                "title" to EventValue.Text(item.rewardName),
                "emoji" to EventValue.Text(item.emojiSnapshot),
                "cost" to EventValue.Integer(item.costSnapshot.toLong()),
                "profileId" to EventValue.Text(profileId),
                "usedAt" to EventValue.Integer(now),
                "inventoryState" to EventValue.Text(INVENTORY_USED),
            ),
        )
        OperationResult.Success
    }

    override suspend fun resetData(
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult = processCommand(metadata, OP_RESET_DATA) {
        if (dao.profile(profileId) == null) {
            return@processCommand OperationResult.Rejected(RejectionReason.PROFILE_NOT_FOUND)
        }
        val resetSeeds = SEED_TASKS.map { seed ->
            val taskId = seedTaskId(profileId, seed.id)
            Triple(seed, taskId, dao.taskIncludingDeleted(taskId))
        }
        if (resetSeeds.any { (_, _, existing) -> existing != null && existing.assigneeId != profileId }) {
            return@processCommand invalidInput()
        }
            val now = Clock.System.now().toEpochMilliseconds()
        val balanceBeforeReset = dao.balance(profileId)
        val experienceBeforeReset = dao.experiencePoints(profileId)

        if (dao.wishGoal(profileId) != null) {
            dao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = newId(),
                    profileId = profileId,
                    delta = 0,
                    reason = LedgerReason.WISH_GOAL_CLEARED.name,
                    referenceId = metadata.idempotencyKey,
                    label = "Wish goal cleared on reset",
                    createdAt = now,
                ),
            )
            dao.deleteWishGoal(profileId)
            recordEvent(
                metadata = metadata,
                actorId = profileId,
                aggregateType = AggregateType.PROFILE,
                aggregateId = profileId,
                eventType = EventType.WISH_GOAL_UPDATED,
                payload = mapOf(
                    "profileId" to EventValue.Text(profileId),
                    "rewardId" to EventValue.Null,
                    "deposit" to EventValue.Integer(0),
                    "reason" to EventValue.Text("DATA_RESET"),
                ),
            )
        }

        dao.revokeActiveCompletions(profileId, now)
        dao.resetRedemptions(profileId, now)

        val seedIds = resetSeeds.mapTo(mutableSetOf()) { it.second }
        dao.tasksIncludingDeleted(profileId).forEach { task ->
            if (task.id !in seedIds && task.deletedAt == null) {
                dao.updateTask(task.copy(deletedAt = now, updatedAt = now, isCompleted = false, completedAt = null))
            }
        }
        resetSeeds.forEachIndexed { index, (seed, taskId, existing) ->
            val resetTask = TaskEntity(
                id = taskId,
                title = seed.title,
                notes = "",
                category = TaskCategory.DAILY.name,
                rewardPoints = seed.reward,
                assigneeId = profileId,
                isCompleted = false,
                createdAt = now + SEED_TASKS.size - index,
                updatedAt = now,
                completedAt = null,
                deletedAt = null,
                recurrence = seed.recurrence.name,
                emoji = seed.emoji,
                deadlineMinutes = seed.deadlineMinutes,
                weekDaysMask = seed.weekDays.toMask(),
                monthDay = seed.monthDay,
                monthlyTargetCount = seed.monthlyTargetCount,
            )
            if (existing == null) dao.insertTask(resetTask) else dao.updateTask(resetTask)
        }

        dao.insertLedgerEntry(
            LedgerEntryEntity(
                id = newId(),
                profileId = profileId,
                delta = -balanceBeforeReset,
                reason = LedgerReason.DATA_RESET.name,
                referenceId = metadata.idempotencyKey,
                    label = "Reset balance",
                createdAt = now,
            ),
        )
        dao.insertLedgerEntry(
            LedgerEntryEntity(
                id = newId(),
                profileId = profileId,
                delta = -experienceBeforeReset,
                reason = LedgerReason.EXPERIENCE_RESET.name,
                referenceId = metadata.idempotencyKey,
                    label = "Reset experience",
                createdAt = now,
            ),
        )
        recordEvent(
            metadata = metadata,
            actorId = profileId,
            aggregateType = AggregateType.PROFILE,
            aggregateId = profileId,
            eventType = EventType.DATA_RESET,
            payload = mapOf(
                "profileId" to EventValue.Text(profileId),
                "resetAt" to EventValue.Integer(now),
                "balanceBeforeReset" to EventValue.Integer(balanceBeforeReset.toLong()),
                "experienceBeforeReset" to EventValue.Integer(experienceBeforeReset.toLong()),
                "seedCatalogVersion" to EventValue.Text(SEED_CATALOG_VERSION),
            ),
        )
        OperationResult.Success
    }

    override suspend fun backupArchive(exportedAt: Long): BackupArchive {
        return database.withTransaction {
            val rewards = dao.allRewards()
            val activeRewardIds = rewards.asSequence().filter { it.active }.mapTo(mutableSetOf()) { it.id }
            val selectedProfileId = mutableSelectedProfileId.value
            val selectedWishGoal = selectedProfileId?.let { dao.wishGoal(it) }
            BackupArchive(
                formatVersion = BackupArchive.CURRENT_FORMAT_VERSION,
                exportedAt = exportedAt,
                selectedProfileId = mutableSelectedProfileId.value,
                wishGoalRewardId = selectedWishGoal?.rewardId?.takeIf { it in activeRewardIds },
                wishGoalDeposit = selectedWishGoal?.deposit ?: 0,
                wishGoalLastReminderDate = selectedWishGoal?.lastReminderDate,
                profiles = dao.allProfiles().map(ProfileEntity::toBackupRecord),
                tasks = dao.allTasks().map(TaskEntity::toBackupRecord),
                rewards = rewards.map(RewardEntity::toBackupRecord),
                completions = dao.allCompletions().map(CompletionEntity::toBackupRecord),
                ledgerEntries = dao.allLedgerEntries().map(LedgerEntryEntity::toBackupRecord),
                redemptions = dao.allRedemptions().map(RedemptionEntity::toBackupRecord),
                events = dao.allEvents().map(EventEntity::toBackupRecord),
                processedCommands = dao.allProcessedCommands().map(ProcessedCommandEntity::toBackupRecord),
            )
        }
    }

    override suspend fun restoreBackup(
        archive: BackupArchive,
        metadata: CommandMetadata,
    ): OperationResult {
        if (!BackupArchiveRules.isValid(archive)) {
            return OperationResult.Rejected(RejectionReason.BACKUP_INVALID)
        }
        if (!archive.events.all(::hasValidPayload)) {
            return OperationResult.Rejected(RejectionReason.BACKUP_INVALID)
        }
        val selectedProfileId = archive.selectedProfileId
            ?: archive.profiles.first { !it.archived }.id
        val result = try {
            processCommand(metadata, OP_RESTORE_BACKUP) {
                dao.clearProcessedCommands()
                dao.clearEvents()
                dao.clearRedemptions()
                dao.clearWishGoals()
                dao.clearLedgerEntries()
                dao.clearCompletions()
                dao.clearRewards()
                dao.clearTasks()
                dao.clearProfiles()

                dao.insertProfiles(archive.profiles.map(BackupProfileRecord::toEntity))
                val wishGoalRewardId = archive.wishGoalRewardId
                if (wishGoalRewardId != null) {
                    dao.upsertWishGoal(
                        WishGoalEntity(
                            profileId = selectedProfileId,
                            rewardId = wishGoalRewardId,
                            deposit = archive.wishGoalDeposit.coerceAtLeast(0),
                            lastReminderDate = archive.wishGoalLastReminderDate,
                        ),
                    )
                }
                dao.insertTasks(archive.tasks.map(BackupTaskRecord::toEntity))
                dao.insertRewards(archive.rewards.map(BackupRewardRecord::toEntity))
                dao.insertCompletions(archive.completions.map(BackupCompletionRecord::toEntity))
                dao.insertLedgerEntries(archive.ledgerEntries.map(BackupLedgerRecord::toEntity))
                dao.insertRedemptions(archive.redemptions.map(BackupRedemptionRecord::toEntity))
                dao.insertEvents(archive.events.map(BackupEventRecord::toEntity))
                dao.insertProcessedCommands(archive.processedCommands.map(BackupProcessedCommandRecord::toEntity))
                recordEvent(
                    metadata = metadata,
                    actorId = selectedProfileId,
                    aggregateType = AggregateType.PROFILE,
                    aggregateId = selectedProfileId,
                    eventType = EventType.BACKUP_RESTORED,
                    payload = mapOf(
                        "formatVersion" to EventValue.Integer(archive.formatVersion.toLong()),
                        "backupExportedAt" to EventValue.Integer(archive.exportedAt),
                        "restoredProfileCount" to EventValue.Integer(archive.profiles.size.toLong()),
                        "restoredTaskCount" to EventValue.Integer(archive.tasks.size.toLong()),
                    ),
                )
                OperationResult.Success
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            OperationResult.Rejected(RejectionReason.BACKUP_RESTORE_FAILED)
        }
        if (result == OperationResult.Success || result == OperationResult.NoChange) {
            persistSelectedProfile(selectedProfileId)
        }
        return result
    }

    override suspend fun pendingEvents(): List<DomainEvent> = dao.pendingEvents().map { it.toDomain() }

    private suspend fun processCommand(
        metadata: CommandMetadata,
        operation: String,
        block: suspend () -> OperationResult,
    ): OperationResult {
        if (!metadata.isValid()) return invalidInput()
        return database.withTransaction {
            val processed = dao.processedCommand(metadata.idempotencyKey)
            if (processed != null) {
                return@withTransaction if (processed.operation == operation) {
                    processed.toOperationResult()
                } else {
                    invalidInput()
                }
            }

            val result = block()
            dao.insertProcessedCommand(
                ProcessedCommandEntity(
                    idempotencyKey = metadata.idempotencyKey,
                    operation = operation,
                    traceId = metadata.traceId,
                    result = when (result) {
                        OperationResult.Success -> RESULT_SUCCESS
                        OperationResult.NoChange -> RESULT_NO_CHANGE
                        is OperationResult.Rejected -> RESULT_REJECTED
                    },
                    rejectionReason = (result as? OperationResult.Rejected)?.reason?.name,
                    processedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            result
        }
    }

    private suspend fun recordEvent(
        metadata: CommandMetadata,
        actorId: String?,
        aggregateType: AggregateType,
        aggregateId: String,
        eventType: EventType,
        payload: EventPayload,
    ) {
        val (timestamp, counter) = deviceIdentity.nextClock(Clock.System.now().toEpochMilliseconds())
        val eventId = EventIds.create(timestamp, counter, deviceIdentity.id, newId().take(8))
        dao.insertEvent(
            EventEntity(
                eventId = eventId,
                schemaVersion = EVENT_SCHEMA_VERSION,
                deviceId = deviceIdentity.id,
                traceId = metadata.traceId,
                idempotencyKey = metadata.idempotencyKey,
                actorId = actorId,
                aggregateType = aggregateType.name,
                aggregateId = aggregateId,
                eventType = eventType.name,
                occurredAt = timestamp,
                logicalCounter = counter,
                payload = eventPayloadCodec.encodePayload(payload),
            ),
        )
    }

    private fun taskPayload(task: TaskEntity): EventPayload = mapOf(
        "title" to EventValue.Text(task.title),
        "notes" to EventValue.Text(task.notes),
        "category" to EventValue.Text(task.category),
        "rewardPoints" to EventValue.Integer(task.rewardPoints.toLong()),
        "assigneeId" to EventValue.Text(task.assigneeId),
        "recurrence" to EventValue.Text(task.recurrence),
        "emoji" to EventValue.Text(task.emoji),
        "deadlineMinutes" to task.deadlineMinutes.toEventValue(),
        "weekDays" to EventValue.Array(task.weekDaysMask.toWeekDays().map { EventValue.Integer(it.toLong()) }),
        "monthDay" to task.monthDay.toEventValue(),
        "monthlyTargetCount" to EventValue.Integer(task.monthlyTargetCount.toLong()),
    )

    private fun rewardPayload(reward: RewardEntity): EventPayload = mapOf(
        "name" to EventValue.Text(reward.name),
        "description" to EventValue.Text(reward.description),
        "cost" to EventValue.Integer(reward.cost.toLong()),
        "stock" to (reward.stock?.let { EventValue.Integer(it.toLong()) } ?: EventValue.Null),
        "emoji" to EventValue.Text(reward.emoji),
    )

    private fun EventEntity.toDomain() = DomainEvent(
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
        payload = eventPayloadCodec.decodePayload(payload),
    )

    private fun ProcessedCommandEntity.toOperationResult(): OperationResult {
        return when (result) {
            RESULT_SUCCESS, RESULT_NO_CHANGE -> OperationResult.NoChange
            RESULT_REJECTED -> OperationResult.Rejected(
                RejectionReason.valueOf(requireNotNull(rejectionReason)),
            )
            else -> error("Unknown processed command result: $result")
        }
    }

    private fun CommandMetadata.isValid(): Boolean {
        return idempotencyKey.isNotBlank() && idempotencyKey.length <= MAX_METADATA_LENGTH &&
            traceId.isNotBlank() && traceId.length <= MAX_METADATA_LENGTH
    }

    private fun invalidInput() = OperationResult.Rejected(RejectionReason.INVALID_INPUT)
    private fun hasValidPayload(event: BackupEventRecord): Boolean {
        return try {
            eventPayloadCodec.decodePayload(event.payload)
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            false
        }
    }

    private suspend fun migrateLegacyWishGoal(profileId: String?) {
        val rewardId = preferences.getString(KEY_WISH_GOAL_REWARD) ?: return
        if (profileId != null && dao.wishGoal(profileId) == null && dao.reward(rewardId)?.active == true) {
            dao.upsertWishGoal(WishGoalEntity(profileId, rewardId, 0))
        }
        preferences.remove(KEY_WISH_GOAL_REWARD)
    }

    private suspend fun migrateSeedCatalog(metadata: CommandMetadata) {
        if (mutableSelectedProfileId.value != SEED_PROFILE_ID) return
        processCommand(metadata.forSeed("catalog-v051"), OP_SEED_DATABASE) {
            val profileId = SEED_PROFILE_ID
            val now = Clock.System.now().toEpochMilliseconds()
            val legacyTasks = LEGACY_TASKS.associateBy { it.id }
            val canonicalIds = SEED_TASKS.mapTo(mutableSetOf()) { it.id }
            SEED_TASKS.forEachIndexed { index, seed ->
                val current = dao.taskIncludingDeleted(seed.id)
                if (current == null) {
                    val task = seed.toEntity(profileId, now + SEED_TASKS.size - index)
                    dao.insertTask(task)
                    recordEvent(metadata.forSeed(seed.id), profileId, AggregateType.TASK, seed.id, EventType.TASK_CREATED, taskPayload(task))
                } else if (current.matchesSeed(legacyTasks[seed.id]) || current.deletedAt != null) {
                    val task = seed.toEntity(profileId, current.createdAt).copy(
                        isCompleted = current.isCompleted,
                        completedAt = current.completedAt,
                        updatedAt = now,
                        deletedAt = null,
                    )
                    dao.updateTask(task)
                    recordEvent(metadata.forSeed("update-${seed.id}"), profileId, AggregateType.TASK, seed.id, EventType.TASK_UPDATED, taskPayload(task))
                }
            }
            dao.tasksIncludingDeleted(profileId).forEach { task ->
                val legacy = legacyTasks[task.id]
                if (task.id !in canonicalIds && task.deletedAt == null && task.matchesSeed(legacy)) {
                    dao.updateTask(task.copy(deletedAt = now, updatedAt = now, isCompleted = false, completedAt = null))
                    recordEvent(
                        metadata.forSeed("delete-${task.id}"), profileId, AggregateType.TASK, task.id,
                        EventType.TASK_DELETED, mapOf("deletedAt" to EventValue.Integer(now)),
                    )
                }
            }

            val legacyRewards = LEGACY_REWARDS.associateBy { it.id }
            val existingRewards = dao.allRewards().associateBy { it.id }
            val canonicalRewardIds = SEED_REWARDS.mapTo(mutableSetOf()) { it.id }
            SEED_REWARDS.forEachIndexed { index, seed ->
                val current = existingRewards[seed.id]
                if (current == null) {
                    val reward = seed.toEntity(now + SEED_TASKS.size + index + 1)
                    dao.insertReward(reward)
                    recordEvent(metadata.forSeed(seed.id), profileId, AggregateType.REWARD, seed.id, EventType.REWARD_CREATED, rewardPayload(reward))
                } else if (current.matchesSeed(legacyRewards[seed.id])) {
                    val reward = seed.toEntity(current.createdAt).copy(updatedAt = now, active = true)
                    dao.updateReward(reward)
                    recordEvent(metadata.forSeed("update-${seed.id}"), profileId, AggregateType.REWARD, seed.id, EventType.REWARD_UPDATED, rewardPayload(reward))
                }
            }
            dao.allRewards().forEach { reward ->
                val legacy = legacyRewards[reward.id]
                if (reward.id !in canonicalRewardIds && reward.active && reward.matchesSeed(legacy)) {
                    dao.updateReward(reward.copy(active = false, updatedAt = now))
                    recordEvent(
                        metadata.forSeed("delete-${reward.id}"), profileId, AggregateType.REWARD, reward.id,
                        EventType.REWARD_DELETED, mapOf("deletedAt" to EventValue.Integer(now)),
                    )
                }
            }
            if (dao.ledgerCount(profileId) == 0) {
                dao.insertLedgerEntry(
                    LedgerEntryEntity(newId(), profileId, INITIAL_BALANCE, LedgerReason.INITIAL_BALANCE.name, profileId, "Welcome balance", now),
                )
                recordEvent(
                    metadata.forSeed("initial-balance"), profileId, AggregateType.PROFILE, profileId,
                    EventType.INITIAL_BALANCE_GRANTED,
                    mapOf("profileId" to EventValue.Text(profileId), "amount" to EventValue.Integer(INITIAL_BALANCE.toLong())),
                )
            }
            OperationResult.Success
        }
    }

    private fun persistSelectedProfile(profileId: String) {
        preferences.putString(KEY_SELECTED_PROFILE, profileId)
        mutableSelectedProfileId.value = profileId
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()
    private fun CommandMetadata.forSeed(suffix: String) = copy(idempotencyKey = "$idempotencyKey:$suffix")
    private fun seedTaskId(profileId: String, seedId: String): String {
        return if (profileId == SEED_PROFILE_ID) seedId else "$profileId:$seedId"
    }

    private companion object {
        const val KEY_SELECTED_PROFILE = "selected-profile-id"
        const val KEY_WISH_GOAL_REWARD = "wish-goal-reward-id"
        const val EVENT_SCHEMA_VERSION = 1
        const val MAX_METADATA_LENGTH = 128
        const val RESULT_SUCCESS = "SUCCESS"
        const val RESULT_NO_CHANGE = "NO_CHANGE"
        const val RESULT_REJECTED = "REJECTED"
        const val OP_ADD_PROFILE = "ADD_PROFILE"
        const val OP_ADD_TASK = "ADD_TASK"
        const val OP_UPDATE_TASK = "UPDATE_TASK"
        const val OP_DELETE_TASK = "DELETE_TASK"
        const val OP_COMPLETE_TASK = "COMPLETE_TASK"
        const val OP_REVOKE_COMPLETION = "REVOKE_COMPLETION"
        const val OP_SEED_DATABASE = "SEED_DATABASE"
        const val OP_ADD_REWARD = "ADD_REWARD"
        const val OP_UPDATE_REWARD = "UPDATE_REWARD"
        const val OP_DELETE_REWARD = "DELETE_REWARD"
        const val OP_SET_WISH_GOAL = "SET_WISH_GOAL"
        const val OP_MARK_WISH_GOAL_REMINDER = "MARK_WISH_GOAL_REMINDER"
        const val OP_REDEEM_REWARD = "REDEEM_REWARD"
        const val OP_SELL_INVENTORY_ITEM = "SELL_INVENTORY_ITEM"
        const val OP_USE_INVENTORY_ITEM = "USE_INVENTORY_ITEM"
        const val OP_RESET_DATA = "RESET_DATA"
        const val OP_RESTORE_BACKUP = "RESTORE_BACKUP"
        const val SEED_PROFILE_ID = "family-default"
        const val SEED_CATALOG_VERSION = "v0.51"
        const val INITIAL_BALANCE = 120
        const val WISH_GOAL_REDEEM_BONUS = 100
        const val INVENTORY_OWNED = "OWNED"
        const val INVENTORY_SOLD = "SOLD"
        const val INVENTORY_USED = "USED"

        val SEED_TASKS = listOf(
            SeedTask("seed-daily-exercise", "Morning Exercise", 20, "🏃", TaskRecurrence.DAILY, deadlineMinutes = 8 * 60),
            SeedTask("seed-weekly-clean", "Weekly Cleanup", 100, "🧹", TaskRecurrence.WEEKLY, weekDays = setOf(6)),
            SeedTask("seed-custom-goals", "Read 2 books per month", 120, "📚", TaskRecurrence.ONCE),
        )

        val SEED_REWARDS = listOf(
            SeedReward("seed-reward-coffee", "Specialty Coffee", "A perfect brew to reward your focus", 500, "☕"),
            SeedReward("seed-reward-electronics", "Electronics", "Upgrade your gear for the next level", 2000, "🎧"),
            SeedReward("seed-reward-journey", "Long Journey", "That destination you've always dreamed of", 8000, "✈️"),
        )

        val LEGACY_TASKS = listOf(
            SeedTask("seed-daily-exercise", "\u6668\u95f4\u953b\u70bc", 20, "💪", TaskRecurrence.DAILY, deadlineMinutes = 8 * 60),
            SeedTask("seed-daily-reading", "\u9605\u8bfb 20 \u5206\u949f", 15, "📚", TaskRecurrence.DAILY, deadlineMinutes = 22 * 60),
            SeedTask("seed-daily-water", "\u559d\u591f 8 \u676f\u6c34", 10, "💧", TaskRecurrence.DAILY),
            SeedTask("seed-daily-screen", "8 \u70b9\u524d\u4e0d\u770b\u5c4f\u5e55", 25, "🌅", TaskRecurrence.DAILY, deadlineMinutes = 8 * 60),
            SeedTask("seed-weekly-clean", "\u5f7b\u5e95\u6253\u626b\u623f\u95f4", 100, "🧹", TaskRecurrence.WEEKLY, weekDays = setOf(6)),
            SeedTask("seed-weekly-budget", "\u56de\u987e\u672c\u5468\u5f00\u9500", 80, "💰", TaskRecurrence.WEEKLY, weekDays = setOf(0)),
            SeedTask("seed-weekly-friend", "\u8054\u7cfb\u4e45\u672a\u8054\u7cfb\u7684\u670b\u53cb", 50, "📞", TaskRecurrence.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5)),
            SeedTask("seed-monthly-budget", "\u6708\u5ea6\u9884\u7b97\u56de\u987e", 120, "📊", TaskRecurrence.MONTHLY, monthDay = 1),
            SeedTask("seed-monthly-checkup", "\u5e74\u5ea6\u4f53\u68c0\u9884\u7ea6", 200, "🏥", TaskRecurrence.MONTHLY, monthDay = 15),
            SeedTask("seed-custom-goals", "\u56de\u987e\u4eba\u751f\u76ee\u6807", 300, "🎯", TaskRecurrence.ONCE),
        )

        val LEGACY_REWARDS = listOf(
            SeedReward("seed-reward-coffee", "\u7cbe\u54c1\u5496\u5561", "\u72d2\u52b3\u81ea\u5df1\u4e00\u676f\u7f8e\u5473\u62ff\u94c1", 50, "☕"),
            SeedReward("seed-reward-movie", "\u7535\u5f71\u4e4b\u591c", "\u914d\u7740\u7206\u7c73\u82b1\u770b\u4e00\u90e8\u597d\u7247", 150, "🎬"),
            SeedReward("seed-reward-dinner", "\u7f8e\u98df\u5927\u9910", "\u53bb\u6700\u7231\u7684\u9910\u5385\u597d\u597d\u4eab\u7528", 350, "🍽️"),
            SeedReward("seed-reward-spa", "SPA \u62a4\u7406", "\u4eab\u53d7\u4e00\u6574\u5929\u7684\u653e\u677e\u65f6\u5149", 600, "🧖"),
            SeedReward("seed-reward-trip", "\u77ed\u9014\u65c5\u884c", "\u63a2\u7d22\u9644\u8fd1\u7684\u65b0\u5730\u65b9", 800, "🚗"),
            SeedReward("seed-reward-travel", "\u51fa\u5883\u65c5\u6e38", "\u68a6\u60f3\u76ee\u7684\u5730\u5728\u7b49\u7740\u4f60", 3000, "✈️"),
        )
    }
}

private data class SeedTask(
    val id: String,
    val title: String,
    val reward: Int,
    val emoji: String,
    val recurrence: TaskRecurrence,
    val deadlineMinutes: Int? = null,
    val weekDays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val monthlyTargetCount: Int = 1,
)

private data class SeedReward(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val emoji: String,
)

private fun SeedTask.toEntity(profileId: String, timestamp: Long) = TaskEntity(
    id = id,
    title = title,
    notes = "",
    category = TaskCategory.DAILY.name,
    rewardPoints = reward,
    assigneeId = profileId,
    isCompleted = false,
    createdAt = timestamp,
    updatedAt = timestamp,
    completedAt = null,
    deletedAt = null,
    recurrence = recurrence.name,
    emoji = emoji,
    deadlineMinutes = deadlineMinutes,
    weekDaysMask = weekDays.toMask(),
    monthDay = monthDay,
    monthlyTargetCount = monthlyTargetCount,
)

private fun SeedReward.toEntity(timestamp: Long) = RewardEntity(
    id = id,
    name = name,
    description = description,
    cost = cost,
    stock = null,
    active = true,
    createdAt = timestamp,
    updatedAt = timestamp,
    emoji = emoji,
)

private fun TaskEntity.matchesSeed(seed: SeedTask?): Boolean {
    return seed != null && title == seed.title && rewardPoints == seed.reward &&
        recurrence == seed.recurrence.name && emoji == seed.emoji &&
        deadlineMinutes == seed.deadlineMinutes && weekDaysMask == seed.weekDays.toMask() &&
        monthDay == seed.monthDay && monthlyTargetCount == seed.monthlyTargetCount
}

private fun RewardEntity.matchesSeed(seed: SeedReward?): Boolean {
    return seed != null && name == seed.name && description == seed.description &&
        cost == seed.cost && emoji == seed.emoji
}

private fun WishGoalEntity.toDomain() = WishGoal(profileId, rewardId, deposit, lastReminderDate)

private fun ProfileEntity.toBackupRecord() = BackupProfileRecord(
    id = id,
    name = name,
    accentIndex = accentIndex,
    createdAt = createdAt,
    archived = archived,
)

private fun BackupProfileRecord.toEntity() = ProfileEntity(id, name, accentIndex, createdAt, archived)

private fun TaskEntity.toBackupRecord() = BackupTaskRecord(
    id = id,
    title = title,
    notes = notes,
    category = category,
    rewardPoints = rewardPoints,
    assigneeId = assigneeId,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    deletedAt = deletedAt,
    recurrence = recurrence,
    emoji = emoji,
    deadlineMinutes = deadlineMinutes,
    weekDaysMask = weekDaysMask,
    monthDay = monthDay,
    monthlyTargetCount = monthlyTargetCount,
)

private fun BackupTaskRecord.toEntity() = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    category = category,
    rewardPoints = rewardPoints,
    assigneeId = assigneeId,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    deletedAt = deletedAt,
    recurrence = recurrence,
    emoji = emoji,
    deadlineMinutes = deadlineMinutes,
    weekDaysMask = weekDaysMask,
    monthDay = monthDay,
    monthlyTargetCount = monthlyTargetCount,
)

private fun RewardEntity.toBackupRecord() = BackupRewardRecord(
    id = id,
    name = name,
    description = description,
    cost = cost,
    stock = stock,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
    emoji = emoji,
)

private fun BackupRewardRecord.toEntity() = RewardEntity(
    id = id,
    name = name,
    description = description,
    cost = cost,
    stock = stock,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
    emoji = emoji,
)

private fun CompletionEntity.toBackupRecord() = BackupCompletionRecord(
    id = id,
    taskId = taskId,
    occurrenceKey = occurrenceKey,
    profileId = profileId,
    rewardSnapshot = rewardSnapshot,
    completedAt = completedAt,
    revokedAt = revokedAt,
    titleSnapshot = titleSnapshot,
    emojiSnapshot = emojiSnapshot,
    recurrenceSnapshot = recurrenceSnapshot,
)

private fun BackupCompletionRecord.toEntity() = CompletionEntity(
    id = id,
    taskId = taskId,
    occurrenceKey = occurrenceKey,
    profileId = profileId,
    rewardSnapshot = rewardSnapshot,
    completedAt = completedAt,
    revokedAt = revokedAt,
    titleSnapshot = titleSnapshot,
    emojiSnapshot = emojiSnapshot,
    recurrenceSnapshot = recurrenceSnapshot,
)

private fun LedgerEntryEntity.toBackupRecord() = BackupLedgerRecord(
    id = id,
    profileId = profileId,
    delta = delta,
    reason = reason,
    referenceId = referenceId,
    label = label,
    createdAt = createdAt,
)

private fun BackupLedgerRecord.toEntity() = LedgerEntryEntity(
    id = id,
    profileId = profileId,
    delta = delta,
    reason = reason,
    referenceId = referenceId,
    label = label,
    createdAt = createdAt,
)

private fun RedemptionEntity.toBackupRecord() = BackupRedemptionRecord(
    id = id,
    rewardId = rewardId,
    rewardName = rewardName,
    profileId = profileId,
    costSnapshot = costSnapshot,
    status = status,
    createdAt = createdAt,
    emojiSnapshot = emojiSnapshot,
    inventoryState = inventoryState,
    soldAt = soldAt,
)

private fun BackupRedemptionRecord.toEntity() = RedemptionEntity(
    id = id,
    rewardId = rewardId,
    rewardName = rewardName,
    profileId = profileId,
    costSnapshot = costSnapshot,
    status = status,
    createdAt = createdAt,
    emojiSnapshot = emojiSnapshot,
    inventoryState = inventoryState,
    soldAt = soldAt,
)

private fun EventEntity.toBackupRecord() = BackupEventRecord(
    eventId = eventId,
    schemaVersion = schemaVersion,
    deviceId = deviceId,
    traceId = traceId,
    idempotencyKey = idempotencyKey,
    actorId = actorId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    occurredAt = occurredAt,
    logicalCounter = logicalCounter,
    payload = payload,
    synced = synced,
)

private fun BackupEventRecord.toEntity() = EventEntity(
    eventId = eventId,
    schemaVersion = schemaVersion,
    deviceId = deviceId,
    traceId = traceId,
    idempotencyKey = idempotencyKey,
    actorId = actorId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    occurredAt = occurredAt,
    logicalCounter = logicalCounter,
    payload = payload,
    synced = synced,
)

private fun ProcessedCommandEntity.toBackupRecord() = BackupProcessedCommandRecord(
    idempotencyKey = idempotencyKey,
    operation = operation,
    traceId = traceId,
    result = result,
    rejectionReason = rejectionReason,
    processedAt = processedAt,
)

private fun BackupProcessedCommandRecord.toEntity() = ProcessedCommandEntity(
    idempotencyKey = idempotencyKey,
    operation = operation,
    traceId = traceId,
    result = result,
    rejectionReason = rejectionReason,
    processedAt = processedAt,
)

private fun ProfileEntity.toDomain() = Profile(id, name, accentIndex, createdAt)

private fun TaskEntity.toDomain() = HabitTask(
    id = id,
    title = title,
    notes = notes,
    rewardPoints = rewardPoints,
    assigneeId = assigneeId,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    category = enumValues<TaskCategory>().firstOrNull { it.name == category } ?: TaskCategory.DAILY,
    recurrence = recurrence.toRecurrence(),
    emoji = emoji,
    deadlineMinutes = deadlineMinutes,
    weekDays = weekDaysMask.toWeekDays(),
    monthDay = monthDay,
    monthlyTargetCount = monthlyTargetCount,
)

private fun RewardWithCountRow.toDomain() = Reward(
    id = id,
    name = name,
    description = description,
    cost = cost,
    stock = stock,
    active = active,
    createdAt = createdAt,
    emoji = emoji,
    redeemedCount = redeemedCount,
)

private fun CompletionEntity.toDomain() = TaskCompletion(
    id = id,
    taskId = taskId,
    occurrenceKey = occurrenceKey,
    profileId = profileId,
    rewardSnapshot = rewardSnapshot,
    titleSnapshot = titleSnapshot,
    emojiSnapshot = emojiSnapshot,
    recurrenceSnapshot = recurrenceSnapshot.toRecurrence(),
    completedAt = completedAt,
    revokedAt = revokedAt,
)

private fun RedemptionEntity.toInventoryItem() = InventoryItem(
    id = id,
    rewardId = rewardId,
    title = rewardName,
    emoji = emojiSnapshot,
    cost = costSnapshot,
    acquiredAt = createdAt,
)

private fun String.toRecurrence(): TaskRecurrence {
    return enumValues<TaskRecurrence>().firstOrNull { it.name == this } ?: TaskRecurrence.ONCE
}

private fun Set<Int>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl day) }

private fun Int.toWeekDays(): Set<Int> = (0..6).filterTo(linkedSetOf()) { day -> this and (1 shl day) != 0 }

private fun Int?.toEventValue(): EventValue = this?.let { EventValue.Integer(it.toLong()) } ?: EventValue.Null

private fun LedgerEntryEntity.toDomain() = LedgerEntry(
    id = id,
    profileId = profileId,
    delta = delta,
    reason = LedgerReason.valueOf(reason),
    referenceId = referenceId,
    label = label,
    createdAt = createdAt,
)

private fun ProgressStatsRow.toDomain() = ProgressStats(
    experiencePoints = experiencePoints,
    completedTaskCount = completedTaskCount,
    redemptionCount = redemptionCount,
)
