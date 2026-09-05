package com.familyquest.application

import com.familyquest.domain.event.DomainEvent
import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.BackupProfileRecord
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
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.repository.FamilyQuestRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class FamilyQuestServiceTest {
    private val metadata = CommandMetadata("command-1", "trace-1")

    @Test
    fun `maps domain rejection to friendly error and preserves trace id`() = runTest {
        val repository = FakeRepository().apply {
            nextResult = OperationResult.Rejected(RejectionReason.INSUFFICIENT_BALANCE)
        }
        val service = FamilyQuestService(repository, PlainEventExporter())

        val result = service.redeemReward("reward-1", "profile-1", metadata)

        assertTrue(result is ApplicationResult.Failure)
        result as ApplicationResult.Failure
        assertEquals(ErrorCodeEnum.REWARD_INSUFFICIENT_BALANCE, result.errorCode)
        assertEquals("trace-1", result.traceId)
        assertEquals(metadata, repository.lastMetadata)
    }

    @Test
    fun `maps inventory rejections to friendly errors`() = runTest {
        val repository = FakeRepository()
        val service = FamilyQuestService(repository, PlainEventExporter())

        repository.nextResult = OperationResult.Rejected(RejectionReason.INVENTORY_FULL)
        val fullResult = service.redeemReward("reward-1", "profile-1", metadata)
        assertEquals(ErrorCodeEnum.INVENTORY_FULL, (fullResult as ApplicationResult.Failure).errorCode)

        repository.nextResult = OperationResult.Rejected(RejectionReason.INVENTORY_ITEM_NOT_FOUND)
        val missingResult = service.sellInventoryItem("item-1", "profile-1", metadata)
        assertEquals(
            ErrorCodeEnum.INVENTORY_ITEM_NOT_FOUND,
            (missingResult as ApplicationResult.Failure).errorCode,
        )

        repository.nextResult = OperationResult.Rejected(RejectionReason.TASK_RECURRENCE_LOCKED)
        val lockedResult = service.updateTask(
            "task-1",
            "Task",
            "",
            20,
            TaskRecurrence.WEEKLY,
            "✅",
            metadata = metadata,
        )
        assertEquals(
            ErrorCodeEnum.TASK_RECURRENCE_LOCKED,
            (lockedResult as ApplicationResult.Failure).errorCode,
        )
    }

    @Test
    fun `times out a stalled database command without leaking exception`() = runTest {
        val repository = FakeRepository().apply { commandDelayMillis = 1_000 }
        val service = FamilyQuestService(
            repository = repository,
            eventExporter = PlainEventExporter(),
            commandTimeoutMillis = 100,
        )

        val result = service.addProfile("测试", metadata)

        assertTrue(result is ApplicationResult.Failure)
        assertEquals(ErrorCodeEnum.COMMON_TIMEOUT, (result as ApplicationResult.Failure).errorCode)
    }

    @Test
    fun `normalizes exporter failure at application boundary`() = runTest {
        val service = FamilyQuestService(
            repository = FakeRepository(),
            eventExporter = EventExporter { error("broken destination") },
        )

        val result = service.exportPendingEvents(metadata)

        assertTrue(result is ApplicationResult.Failure)
        assertEquals(ErrorCodeEnum.COMMON_UNEXPECTED, (result as ApplicationResult.Failure).errorCode)
        assertEquals("trace-1", result.traceId)
    }

    @Test
    fun `reports ready after bootstrap succeeds`() = runTest {
        val service = FamilyQuestService(FakeRepository(), PlainEventExporter())

        service.ensureSeedData()

        assertEquals(ApplicationHealth.Ready, service.health.value)
    }

    @Test
    fun `projects recurring completion only for current occurrence`() = runTest {
        val now = Instant.parse("2026-08-25T04:00:00Z")
        val repository = FakeRepository().apply {
            tasks = listOf(task(TaskRecurrence.DAILY))
            completions = listOf(completion("daily:2026-08-24"))
        }
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            timeProvider = FakeTimeProvider(now, TimeZone.UTC),
        )

        assertFalse(service.observeTasks("profile-1").first().single().isCompleted)
        assertEquals(null, service.observeTasks("profile-1").first().single().currentCompletionId)

        repository.completions = listOf(completion("daily:2026-08-25"))
        val currentTask = service.observeTasks("profile-1").first().single()
        assertTrue(currentTask.isCompleted)
        assertEquals("completion-1", currentTask.currentCompletionId)
        assertEquals(20, currentTask.currentCompletionReward)
    }

    @Test
    fun `monthly projection reports progress and completes only at its target`() = runTest {
        val now = Instant.parse("2026-08-25T04:00:00Z")
        val repository = FakeRepository().apply {
            tasks = listOf(task(TaskRecurrence.MONTHLY).copy(monthlyTargetCount = 3))
            completions = listOf(
                completion("monthly:2026-08", id = "completion-1"),
                completion("monthly:2026-08#2", id = "completion-2"),
            )
        }
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            timeProvider = FakeTimeProvider(now, TimeZone.UTC),
        )

        val partial = service.observeTasks("profile-1").first().single()
        assertEquals(2, partial.currentPeriodCompletionCount)
        assertFalse(partial.isCompleted)

        repository.completions = repository.completions +
            completion("monthly:2026-08#3", id = "completion-3")
        val completed = service.observeTasks("profile-1").first().single()
        assertEquals(3, completed.currentPeriodCompletionCount)
        assertTrue(completed.isCompleted)
        assertEquals("completion-3", completed.currentCompletionId)
    }

    @Test
    fun `completion command receives business clock and device zone`() = runTest {
        val now = Instant.parse("2026-08-25T12:34:56Z")
        val zone = TimeZone.of("Asia/Shanghai")
        val repository = FakeRepository()
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            timeProvider = FakeTimeProvider(now, zone),
        )

        service.completeTask("task-1", metadata)

        assertEquals(now.toEpochMilliseconds(), repository.completedAt)
        assertEquals(zone, repository.completedZone)
        assertEquals(metadata, repository.lastMetadata)

        service.updateTask(
            "task-1",
            "Task",
            "",
            20,
            TaskRecurrence.WEEKLY,
            "✅",
            deadlineMinutes = 22 * 60,
            weekDays = setOf(0, 6),
            metadata = metadata,
        )
        assertEquals(now.toEpochMilliseconds(), repository.updatedAt)
        assertEquals(zone, repository.updatedZone)
        assertEquals(22 * 60, repository.taskDeadlineMinutes)
        assertEquals(setOf(0, 6), repository.taskWeekDays)
        assertEquals(null, repository.taskMonthDay)
        assertEquals(metadata, repository.lastMetadata)

        service.addTask(
            title = "月任务",
            notes = "",
            rewardPoints = 30,
            recurrence = TaskRecurrence.MONTHLY,
            emoji = "📅",
            assigneeId = "profile-1",
            monthDay = 31,
            monthlyTargetCount = 8,
            metadata = metadata,
        )
        assertEquals(null, repository.taskDeadlineMinutes)
        assertTrue(repository.taskWeekDays.isEmpty())
        assertEquals(31, repository.taskMonthDay)
        assertEquals(8, repository.taskMonthlyTargetCount)
    }

    @Test
    fun `task mutation reports whether the command changed durable state`() = runTest {
        val repository = FakeRepository()
        val service = FamilyQuestService(repository, PlainEventExporter())

        repository.nextResult = OperationResult.Success
        val completed = service.completeTask("task-1", metadata)
        assertTrue((completed as ApplicationResult.Success).value)

        repository.nextResult = OperationResult.NoChange
        val duplicateCompletion = service.completeTask("task-1", metadata)
        assertFalse((duplicateCompletion as ApplicationResult.Success).value)

        val duplicateRevoke = service.revokeCompletion("completion-1", metadata)
        assertFalse((duplicateRevoke as ApplicationResult.Success).value)
    }

    @Test
    fun `recent completion projection sorts deterministically and is capped at twenty`() = runTest {
        val repository = FakeRepository().apply {
            completions = (1..25).sortedBy { index -> (index * 7) % 25 }.map { index ->
                completion(
                    occurrenceKey = "daily:2026-08-${index.toString().padStart(2, '0')}",
                    id = "completion-${index.toString().padStart(2, '0')}",
                    completedAt = (index / 2).toLong(),
                )
            }
        }
        val service = FamilyQuestService(repository, PlainEventExporter())

        val recent = service.observeRecentCompletions("profile-1").first()

        assertEquals(
            (25 downTo 6).map { index -> "completion-${index.toString().padStart(2, '0')}" },
            recent.map { it.id },
        )
    }

    @Test
    fun `task leaderboards aggregate snapshots exclude revoked and cap both lists`() = runTest {
        val repository = FakeRepository().apply {
            tasks = (1..6).map { index ->
                task(TaskRecurrence.DAILY).copy(
                    id = "task-$index",
                    title = if (index == 1) "当前标题" else "任务 $index",
                )
            }
            completions = (1..6).map { index ->
                completion(
                    occurrenceKey = "daily:2026-08-$index",
                    id = "completion-$index",
                    taskId = "task-$index",
                    reward = index,
                    completedAt = index.toLong(),
                )
            } + listOf(
                completion("daily:extra-1", "completion-extra-1", "task-1", 20, 20, title = "最新标题"),
                completion("daily:extra-2", "completion-extra-2", "task-1", 30, 30, title = "最新标题"),
                completion("daily:revoked", "completion-revoked", "task-revoked", 10_000, 40, revokedAt = 41),
                completion("daily:deleted", "completion-deleted", "task-deleted", 10_000, 50),
            )
        }
        val service = FamilyQuestService(repository, PlainEventExporter())

        val leaderboards = service.observeTaskLeaderboards("profile-1").first()

        assertEquals(5, leaderboards.mostCompleted.size)
        assertEquals(5, leaderboards.highestEarning.size)
        assertEquals("task-1", leaderboards.mostCompleted.first().taskId)
        assertEquals(3, leaderboards.mostCompleted.first().completionCount)
        assertEquals(51, leaderboards.mostCompleted.first().totalEarned)
        assertEquals("当前标题", leaderboards.mostCompleted.first().title)
        assertEquals("task-1", leaderboards.highestEarning.first().taskId)
        assertTrue(leaderboards.mostCompleted.none { it.taskId == "task-revoked" })
        assertTrue(leaderboards.highestEarning.none { it.taskId == "task-deleted" })
    }

    @Test
    fun `inventory commands preserve ids profile and metadata`() = runTest {
        val repository = FakeRepository()
        val service = FamilyQuestService(repository, PlainEventExporter())

        service.sellInventoryItem("item-1", "profile-1", metadata)
        assertEquals("item-1", repository.soldItemId)
        assertEquals("profile-1", repository.soldProfileId)
        assertEquals(metadata, repository.lastMetadata)

        service.useInventoryItem("item-2", "profile-1", metadata)
        assertEquals("item-2", repository.usedItemId)
        assertEquals("profile-1", repository.usedProfileId)
        assertEquals(metadata, repository.lastMetadata)

        service.resetData("profile-1", metadata)
        assertEquals("profile-1", repository.resetProfileId)
        assertEquals(metadata, repository.lastMetadata)
    }

    @Test
    fun `backup uses one atomic snapshot even when observation flows fail`() = runTest {
        val now = Instant.parse("2026-08-25T04:00:00Z")
        val repository = FakeRepository().apply {
            tasks = listOf(
                task(TaskRecurrence.ONCE).copy(
                    title = "一次任务",
                    rewardPoints = 30,
                    emoji = "🎯",
                ),
                task(TaskRecurrence.WEEKLY).copy(
                    id = "task-2",
                    title = "周末任务",
                    deadlineMinutes = 480,
                    weekDays = setOf(0, 6),
                ),
                task(TaskRecurrence.MONTHLY).copy(
                    id = "task-3",
                    title = "月度任务",
                    monthDay = 15,
                ),
            )
            completions = listOf(
                completion("once").copy(completedAt = now.toEpochMilliseconds()),
                completion(
                    occurrenceKey = "once-revoked",
                    id = "completion-revoked",
                    completedAt = now.minus(60, DateTimeUnit.SECOND).toEpochMilliseconds(),
                    revokedAt = now.toEpochMilliseconds(),
                ),
            )
            balance = 75
            progressStats = ProgressStats(experiencePoints = 245, completedTaskCount = 1)
            inventory = listOf(
                InventoryItem("item-1", "reward-1", "电影之夜", "🎬", 150, now.toEpochMilliseconds()),
            )
            failObservationFlows = true
        }
        val exporter = RecordingSnapshotExporter()
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            timeProvider = FakeTimeProvider(now, TimeZone.UTC),
            snapshotExporter = exporter,
        )

        val result = service.exportBackup("profile-1", metadata)

        assertEquals("backup-json", (result as ApplicationResult.Success).value)
        assertEquals(1, repository.snapshotReadCount)
        assertEquals(
            BackupSnapshot(
                tasks = listOf(
                    BackupTask(
                        "task-1",
                        "一次任务",
                        "custom",
                        30,
                        true,
                        "🎯",
                        null,
                        emptyList(),
                        null,
                        completions = 1,
                    ),
                    BackupTask(
                        "task-2",
                        "周末任务",
                        "weekly",
                        20,
                        false,
                        "✅",
                        480,
                        listOf(0, 6),
                        null,
                    ),
                    BackupTask(
                        "task-3",
                        "月度任务",
                        "monthly",
                        20,
                        false,
                        "✅",
                        null,
                        emptyList(),
                        15,
                    ),
                ),
                coins = 75,
                totalEarned = 245,
                completedToday = 1,
                inventory = listOf(BackupInventoryItem("item-1", "电影之夜", "🎬", 150)),
            ),
            exporter.snapshot,
        )
    }

    @Test
    fun `backup fails closed when atomic snapshot cannot be read`() = runTest {
        val repository = FakeRepository().apply {
            snapshotFailure = IllegalStateException("database unavailable")
        }
        val exporter = RecordingSnapshotExporter()
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            snapshotExporter = exporter,
        )

        val result = service.exportBackup("profile-1", metadata)

        assertEquals(ErrorCodeEnum.COMMON_UNEXPECTED, (result as ApplicationResult.Failure).errorCode)
        assertEquals(null, exporter.snapshot)
        assertEquals(1, repository.snapshotReadCount)
    }

    @Test
    fun `complete backup round trip uses repository archive and injected codec`() = runTest {
        val archive = minimalBackupArchive()
        val repository = FakeRepository().apply { backupArchiveValue = archive }
        val codec = FakeBackupCodec(archive)
        val now = Instant.parse("2026-09-02T12:00:00Z")
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            timeProvider = FakeTimeProvider(now, TimeZone.UTC),
            backupCodec = codec,
        )

        val result = service.exportBackup("profile-1", metadata)
        val exported = (result as ApplicationResult.Success).value

        assertEquals("full-backup", exported)
        assertEquals(now.toEpochMilliseconds(), repository.backupExportedAt)
        assertEquals(archive, codec.encodedArchive)
        assertEquals(0, repository.snapshotReadCount)

        val restoreMetadata = CommandMetadata("restore-command", "restore-trace")
        val imported = service.importBackup(exported, restoreMetadata)
        assertTrue(imported is ApplicationResult.Success)
        assertEquals(archive, repository.restoredArchive)
        assertEquals(restoreMetadata, repository.restoreMetadata)
    }

    @Test
    fun `complete backup import validates then restores archive`() = runTest {
        val archive = minimalBackupArchive()
        val repository = FakeRepository()
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            backupCodec = FakeBackupCodec(archive),
        )

        val result = service.importBackup("full-backup", metadata)

        assertTrue(result is ApplicationResult.Success)
        assertEquals(archive, repository.restoredArchive)
        assertEquals(metadata, repository.restoreMetadata)
    }

    @Test
    fun `backup import distinguishes unsupported malformed and invalid archives`() = runTest {
        val archive = minimalBackupArchive()
        val repository = FakeRepository()

        val unsupported = FamilyQuestService(
            repository,
            PlainEventExporter(),
            backupCodec = FakeBackupCodec(
                archive,
                decodeFailure = BackupDecodeException(unsupportedVersion = true),
            ),
        ).importBackup("legacy", metadata)
        assertEquals(
            ErrorCodeEnum.BACKUP_UNSUPPORTED_VERSION,
            (unsupported as ApplicationResult.Failure).errorCode,
        )

        val malformed = FamilyQuestService(
            repository,
            PlainEventExporter(),
            backupCodec = FakeBackupCodec(archive, decodeFailure = BackupDecodeException()),
        ).importBackup("bad-json", metadata)
        assertEquals(ErrorCodeEnum.BACKUP_INVALID, (malformed as ApplicationResult.Failure).errorCode)

        val invalid = FamilyQuestService(
            repository,
            PlainEventExporter(),
            backupCodec = FakeBackupCodec(archive.copy(selectedProfileId = "missing")),
        ).importBackup("full-backup", metadata)
        assertEquals(ErrorCodeEnum.BACKUP_INVALID, (invalid as ApplicationResult.Failure).errorCode)
        assertEquals(null, repository.restoredArchive)
    }

    @Test
    fun `backup restore rejection maps to dedicated error`() = runTest {
        val archive = minimalBackupArchive()
        val repository = FakeRepository().apply {
            nextResult = OperationResult.Rejected(RejectionReason.BACKUP_RESTORE_FAILED)
        }
        val service = FamilyQuestService(
            repository,
            PlainEventExporter(),
            backupCodec = FakeBackupCodec(archive),
        )

        val result = service.importBackup("full-backup", metadata)

        assertEquals(ErrorCodeEnum.BACKUP_RESTORE_FAILED, (result as ApplicationResult.Failure).errorCode)
    }

    @Test
    fun `commerce overview ignores progress failures and has no capacity limit`() = runTest {
        val repository = FakeRepository().apply {
            rewardValues = listOf(
                Reward("reward-1", "可购买", "", 50, null, true, 0),
                Reward("reward-2", "售罄", "", 20, 0, true, 0),
            )
            balance = 100
            failProgressFlow = true
            inventory = (1..13).map { index ->
                InventoryItem("item-$index", "reward-1", "可购买", "🎁", 50, index.toLong())
            }
        }
        val service = FamilyQuestService(repository, PlainEventExporter())

        val overview = service.observeCommerce("profile-1").first()

        assertEquals(70, overview.refundPercent)
        assertEquals(null, overview.rewardOptions[0].rejectionReason)
        assertEquals(RejectionReason.OUT_OF_STOCK, overview.rewardOptions[1].rejectionReason)
        assertEquals(13, overview.inventoryOptions.size)
        assertTrue(overview.inventoryOptions.all { it.saleRefund == 35 })
    }
}

private class PlainEventExporter : EventExporter {
    override fun export(events: List<DomainEvent>): String = ""
}

private class RecordingSnapshotExporter : SnapshotExporter {
    var snapshot: BackupSnapshot? = null

    override fun export(snapshot: BackupSnapshot): String {
        this.snapshot = snapshot
        return "backup-json"
    }
}

private class FakeBackupCodec(
    private val decodedArchive: BackupArchive,
    private val decodeFailure: BackupDecodeException? = null,
) : BackupCodec {
    var encodedArchive: BackupArchive? = null

    override fun encode(archive: BackupArchive): String {
        encodedArchive = archive
        return "full-backup"
    }

    override fun decode(content: String): BackupArchive {
        decodeFailure?.let { throw it }
        return decodedArchive
    }
}

private class FakeTimeProvider(
    private val instant: Instant,
    private val zone: TimeZone,
) : BusinessTimeProvider {
    override fun now(): Instant = instant
    override fun timeZone(): TimeZone = zone
    override fun ticks(): Flow<Instant> = flowOf(instant)
}

private class FakeRepository : FamilyQuestRepository {
    override val profiles: Flow<List<Profile>> = flowOf(emptyList())
    override val rewards: Flow<List<Reward>> get() = observed(rewardValues)
    override val selectedProfileId = MutableStateFlow<String?>(null)
    override val pendingEventCount: Flow<Int> = flowOf(0)

    var nextResult: OperationResult = OperationResult.Success
    var commandDelayMillis: Long = 0
    var lastMetadata: CommandMetadata? = null
    var tasks: List<HabitTask> = emptyList()
    var rewardValues: List<Reward> = emptyList()
    var completions: List<TaskCompletion> = emptyList()
    var inventory: List<InventoryItem> = emptyList()
    var balance: Int = 0
    var progressStats: ProgressStats = ProgressStats()
    var completedAt: Long? = null
    var completedZone: TimeZone? = null
    var updatedAt: Long? = null
    var updatedZone: TimeZone? = null
    var taskDeadlineMinutes: Int? = null
    var taskWeekDays: Set<Int> = emptySet()
    var taskMonthDay: Int? = null
    var taskMonthlyTargetCount: Int = 1
    var soldItemId: String? = null
    var soldProfileId: String? = null
    var usedItemId: String? = null
    var usedProfileId: String? = null
    var resetProfileId: String? = null
    var failObservationFlows: Boolean = false
    var failProgressFlow: Boolean = false
    var snapshotFailure: Exception? = null
    var snapshotReadCount: Int = 0
    var backupArchiveValue: BackupArchive = minimalBackupArchive()
    var backupExportedAt: Long? = null
    var restoredArchive: BackupArchive? = null
    var restoreMetadata: CommandMetadata? = null

    override fun observeTasks(profileId: String): Flow<List<HabitTask>> = observed(tasks)
    override fun observeBalance(profileId: String): Flow<Int> = observed(balance)
    override fun observeLedger(profileId: String): Flow<List<LedgerEntry>> = flowOf(emptyList())
    override fun observeProgressStats(profileId: String): Flow<ProgressStats> =
        if (failProgressFlow) flow { error("progress observation failed") } else observed(progressStats)
    override fun observeCompletions(profileId: String): Flow<List<TaskCompletion>> = observed(completions)
    override fun observeInventory(profileId: String): Flow<List<InventoryItem>> = observed(inventory)

    override suspend fun profileDataSnapshot(profileId: String): ProfileDataSnapshot? {
        snapshotReadCount += 1
        snapshotFailure?.let { throw it }
        return ProfileDataSnapshot(profileId, tasks, completions, balance, progressStats, inventory)
    }

    override suspend fun backupArchive(exportedAt: Long): BackupArchive {
        backupExportedAt = exportedAt
        return backupArchiveValue
    }

    override suspend fun restoreBackup(
        archive: BackupArchive,
        metadata: CommandMetadata,
    ): OperationResult {
        restoredArchive = archive
        restoreMetadata = metadata
        return command(metadata)
    }

    override suspend fun ensureSeedData(metadata: CommandMetadata): OperationResult = command(metadata)
    override suspend fun selectProfile(profileId: String, metadata: CommandMetadata): OperationResult = command(metadata)
    override suspend fun addProfile(name: String, metadata: CommandMetadata): OperationResult = command(metadata)

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
    ): OperationResult {
        taskDeadlineMinutes = deadlineMinutes
        taskWeekDays = weekDays
        taskMonthDay = monthDay
        taskMonthlyTargetCount = monthlyTargetCount
        return command(metadata)
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
    ): OperationResult {
        this.updatedAt = updatedAt
        updatedZone = timeZone
        taskDeadlineMinutes = deadlineMinutes
        taskWeekDays = weekDays
        taskMonthDay = monthDay
        taskMonthlyTargetCount = monthlyTargetCount
        return command(metadata)
    }

    override suspend fun deleteTask(taskId: String, metadata: CommandMetadata): OperationResult = command(metadata)

    override suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
    ): OperationResult {
        this.completedAt = completedAt
        completedZone = timeZone
        return command(metadata)
    }

    override suspend fun revokeCompletion(
        completionId: String,
        metadata: CommandMetadata,
    ): OperationResult = command(metadata)

    override suspend fun addReward(
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ): OperationResult = command(metadata)

    override suspend fun updateReward(
        rewardId: String,
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ): OperationResult = command(metadata)

    override suspend fun deleteReward(rewardId: String, metadata: CommandMetadata): OperationResult = command(metadata)

    override suspend fun redeemReward(
        rewardId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult = command(metadata)

    override suspend fun sellInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult {
        soldItemId = itemId
        soldProfileId = profileId
        return command(metadata)
    }

    override suspend fun useInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ): OperationResult {
        usedItemId = itemId
        usedProfileId = profileId
        return command(metadata)
    }

    override suspend fun resetData(profileId: String, metadata: CommandMetadata): OperationResult {
        resetProfileId = profileId
        return command(metadata)
    }

    override suspend fun pendingEvents(): List<DomainEvent> = emptyList()

    private suspend fun command(metadata: CommandMetadata): OperationResult {
        lastMetadata = metadata
        if (commandDelayMillis > 0) delay(commandDelayMillis)
        return nextResult
    }

    private fun <T> observed(value: T): Flow<T> {
        return if (failObservationFlows) flow { error("observation failed") } else flowOf(value)
    }
}

private fun task(recurrence: TaskRecurrence) = HabitTask(
    id = "task-1",
    title = "Task",
    notes = "",
    rewardPoints = 20,
    assigneeId = "profile-1",
    isCompleted = false,
    createdAt = 0,
    updatedAt = 0,
    completedAt = null,
    recurrence = recurrence,
)

private fun completion(
    occurrenceKey: String,
    id: String = "completion-1",
    taskId: String = "task-1",
    reward: Int = 20,
    completedAt: Long = 0,
    title: String = "Task",
    revokedAt: Long? = null,
) = TaskCompletion(
    id = id,
    taskId = taskId,
    occurrenceKey = occurrenceKey,
    profileId = "profile-1",
    rewardSnapshot = reward,
    titleSnapshot = title,
    emojiSnapshot = "✅",
    recurrenceSnapshot = TaskRecurrence.DAILY,
    completedAt = completedAt,
    revokedAt = revokedAt,
)

private fun minimalBackupArchive() = BackupArchive(
    formatVersion = BackupArchive.CURRENT_FORMAT_VERSION,
    exportedAt = 1,
    selectedProfileId = "profile-1",
    wishGoalRewardId = null,
    profiles = listOf(
        BackupProfileRecord(
            id = "profile-1",
            name = "家庭",
            accentIndex = 0,
            createdAt = 1,
            archived = false,
        ),
    ),
    tasks = emptyList(),
    rewards = emptyList(),
    completions = emptyList(),
    ledgerEntries = emptyList(),
    redemptions = emptyList(),
    events = emptyList(),
    processedCommands = emptyList(),
)
