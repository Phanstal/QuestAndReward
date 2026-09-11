package com.familyquest.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyquest.data.db.FamilyQuestDatabase
import com.familyquest.data.db.EventEntity
import com.familyquest.domain.event.EventPayload
import com.familyquest.domain.event.EventPayloadCodec
import com.familyquest.domain.event.EventType
import com.familyquest.domain.event.EventValue
import com.familyquest.domain.model.CommandMetadata
import com.familyquest.domain.model.OperationResult
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.TaskRecurrence
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomFamilyQuestRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: FamilyQuestDatabase
    private lateinit var repository: RoomFamilyQuestRepository
    private lateinit var eventCodec: RecordingEventCodec
    private var command = 0

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("family-quest-preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("family-quest-device", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, FamilyQuestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventCodec = RecordingEventCodec()
        repository = RoomFamilyQuestRepository(
            eventPayloadCodec = eventCodec,
            database = database,
            preferences = AndroidPreferencesStore(context, "family-quest-preferences"),
            deviceIdentity = DeviceIdentity(AndroidPreferencesStore(context, "family-quest-device")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyDatabaseReceivesCompleteSamplesAndZeroBalance() = runTest {
        assertEquals(OperationResult.Success, repository.ensureSeedData(metadata()))
        val profile = repository.profiles.first().single()

        assertEquals("Family", profile.name)
        val tasks = repository.observeTasks(profile.id).first()
        assertEquals(3, tasks.size)
        assertEquals(
            listOf(
                "seed-daily-exercise",
            ),
            tasks.filter { it.recurrence == TaskRecurrence.DAILY }.map { it.id },
        )
        assertEquals(3, repository.rewards.first().size)
        assertEquals(120, repository.observeBalance(profile.id).first())
        assertEquals(null, repository.wishGoal.first()?.rewardId)
        assertEquals(null, repository.wishGoal.first()?.deposit)
        assertTrue(repository.observeInventory(profile.id).first().isEmpty())
        assertEquals(1, tasks.count { it.recurrence == TaskRecurrence.ONCE })
        assertEquals(8 * 60, tasks.single { it.id == "seed-daily-exercise" }.deadlineMinutes)
        assertEquals(setOf(6), tasks.single { it.id == "seed-weekly-clean" }.weekDays)
        assertEquals(null, tasks.single { it.id == "seed-custom-goals" }.monthDay)
        assertEquals(1, tasks.single { it.id == "seed-custom-goals" }.monthlyTargetCount)
        assertTrue(tasks.none { it.recurrence == TaskRecurrence.YEARLY })
    }

    @Test
    fun nonEmptyDatabaseWithoutProfilesDoesNotReceiveSamples() = runTest {
        database.dao().insertEvent(
            EventEntity(
                eventId = "existing-event",
                schemaVersion = 1,
                deviceId = "old-device",
                traceId = null,
                idempotencyKey = null,
                actorId = null,
                aggregateType = "UNKNOWN",
                aggregateId = "legacy",
                eventType = "UNKNOWN",
                occurredAt = 1,
                logicalCounter = 0,
                payload = "{}",
            ),
        )

        assertEquals(OperationResult.Success, repository.ensureSeedData(metadata()))
        assertTrue(repository.profiles.first().isEmpty())
        assertTrue(repository.rewards.first().isEmpty())
    }

    @Test
    fun taskScheduleIsNormalizedPersistedAndAddedToTaskEvents() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id

        assertEquals(
            OperationResult.Success,
            repository.addTask(
                "周末任务",
                "",
                30,
                TaskRecurrence.WEEKLY,
                "✅",
                profileId,
                metadata(),
                deadlineMinutes = 22 * 60,
                weekDays = setOf(6, 0),
                monthDay = 15,
            ),
        )
        val task = repository.observeTasks(profileId).first().single { it.title == "周末任务" }
        assertEquals(22 * 60, task.deadlineMinutes)
        assertEquals(setOf(0, 6), task.weekDays)
        assertEquals(null, task.monthDay)
        assertEquals(EventValue.Integer((22 * 60).toLong()), eventCodec.encodedPayloads.last()["deadlineMinutes"])
        assertEquals(
            EventValue.Array(listOf(EventValue.Integer(0L), EventValue.Integer(6L))),
            eventCodec.encodedPayloads.last()["weekDays"],
        )
        assertEquals(EventValue.Null, eventCodec.encodedPayloads.last()["monthDay"])

        assertEquals(
            OperationResult.Success,
            repository.updateTask(
                task.id,
                task.title,
                task.notes,
                task.rewardPoints,
                TaskRecurrence.MONTHLY,
                task.emoji,
                1_777_000_000_000,
                TimeZone.UTC,
                metadata(),
                deadlineMinutes = 60,
                weekDays = setOf(1),
                monthDay = 31,
                monthlyTargetCount = 8,
            ),
        )
        val monthly = repository.observeTasks(profileId).first().single { it.id == task.id }
        assertEquals(null, monthly.deadlineMinutes)
        assertTrue(monthly.weekDays.isEmpty())
        assertEquals(31, monthly.monthDay)
        assertEquals(8, monthly.monthlyTargetCount)
        assertEquals(EventValue.Integer(8L), eventCodec.encodedPayloads.last()["monthlyTargetCount"])
        assertEquals(
            OperationResult.Rejected(RejectionReason.INVALID_INPUT),
            repository.addTask(
                "非法周任务",
                "",
                30,
                TaskRecurrence.WEEKLY,
                "✅",
                profileId,
                metadata(),
                weekDays = setOf(7),
            ),
        )
        assertEquals(
            OperationResult.Rejected(RejectionReason.INVALID_INPUT),
            repository.addTask(
                "非法月目标",
                "",
                30,
                TaskRecurrence.MONTHLY,
                "✅",
                profileId,
                metadata(),
                monthlyTargetCount = 9,
            ),
        )
    }

    @Test
    fun monthlyTaskRewardsUpToItsTargetAndStartsAgainNextMonth() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        assertEquals(
            OperationResult.Success,
            repository.addTask(
                "每月八次",
                "",
                5,
                TaskRecurrence.MONTHLY,
                "🌙",
                profileId,
                metadata(),
                monthlyTargetCount = 8,
            ),
        )
        assertEquals(EventValue.Integer(8L), eventCodec.encodedPayloads.last()["monthlyTargetCount"])
        val task = repository.observeTasks(profileId).first().single { it.title == "每月八次" }
        val zone = TimeZone.UTC
        val august = Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds()
        val september = Instant.parse("2026-09-01T04:00:00Z").toEpochMilliseconds()
        val firstMetadata = metadata()

        assertEquals(OperationResult.Success, repository.completeTask(task.id, august, zone, firstMetadata))
        assertEquals(OperationResult.NoChange, repository.completeTask(task.id, august, zone, firstMetadata))
        repeat(7) {
            assertEquals(OperationResult.Success, repository.completeTask(task.id, august, zone, metadata()))
        }
        assertEquals(OperationResult.NoChange, repository.completeTask(task.id, august, zone, metadata()))
        assertEquals(160, repository.observeBalance(profileId).first())
        assertEquals(
            setOf(
                "monthly:2026-08",
                "monthly:2026-08#2",
                "monthly:2026-08#3",
                "monthly:2026-08#4",
                "monthly:2026-08#5",
                "monthly:2026-08#6",
                "monthly:2026-08#7",
                "monthly:2026-08#8",
            ),
            repository.observeCompletions(profileId).first()
                .filter { it.taskId == task.id }
                .mapTo(mutableSetOf()) { it.occurrenceKey },
        )

        assertEquals(OperationResult.Success, repository.completeTask(task.id, september, zone, metadata()))
        assertEquals(165, repository.observeBalance(profileId).first())
        val completionPayload = eventCodec.encodedPayloads.last()
        assertEquals(EventValue.Text("monthly:2026-09"), completionPayload["periodKey"])
        assertEquals(EventValue.Integer(1L), completionPayload["occurrenceIndex"])
        assertEquals(EventValue.Integer(8L), completionPayload["targetCount"])
    }

    @Test
    fun recurringOccurrenceRewardsOnceAndResetsNextPeriod() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        val taskId = "seed-daily-exercise"
        val zone = TimeZone.of("Asia/Shanghai")
        val first = Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds()
        val next = Instant.parse("2026-08-26T04:00:00Z").toEpochMilliseconds()

        assertEquals(OperationResult.Success, repository.completeTask(taskId, first, zone, metadata()))
        assertEquals(OperationResult.NoChange, repository.completeTask(taskId, first, zone, metadata()))
        assertEquals(140, repository.observeBalance(profileId).first())
        assertEquals(1, repository.observeCompletions(profileId).first().size)

        assertEquals(OperationResult.Success, repository.completeTask(taskId, next, zone, metadata()))
        assertEquals(160, repository.observeBalance(profileId).first())
        assertEquals(2, repository.observeCompletions(profileId).first().size)
    }

    @Test
    fun oneTimeRevokeCanMakeBalanceNegativeAndRestoresTask() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addTask("一次任务", "", 30, TaskRecurrence.ONCE, "✅", profileId, metadata())
        val task = repository.observeTasks(profileId).first().first { it.title == "一次任务" }
        repository.addReward("小奖励", "", 20, null, "🎁", metadata())
        val reward = repository.rewards.first().first { it.name == "小奖励" }

        repository.completeTask(
            task.id,
            Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds(),
                TimeZone.UTC,
            metadata(),
        )
        repository.redeemReward(reward.id, profileId, metadata())
        val completion = repository.observeCompletions(profileId).first().single { it.taskId == task.id }
        assertEquals(OperationResult.Success, repository.revokeCompletion(completion.id, metadata()))

        assertEquals(100, repository.observeBalance(profileId).first())
        assertEquals(false, repository.observeTasks(profileId).first().single { it.id == task.id }.isCompleted)
        assertTrue(repository.observeCompletions(profileId).first().none { it.id == completion.id })
        assertEquals(1, repository.rewards.first().single { it.id == reward.id }.redeemedCount)
    }

    @Test
    fun oneTimeRevokeDoesNotRestoreAnExplicitlyDeletedTask() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addTask("待删除任务", "", 30, TaskRecurrence.ONCE, "✅", profileId, metadata())
        val task = repository.observeTasks(profileId).first().first { it.title == "待删除任务" }
        repository.completeTask(task.id, 1_777_000_000_000, TimeZone.UTC, metadata())
        val completion = repository.observeCompletions(profileId).first().single { it.taskId == task.id }

        assertEquals(OperationResult.Success, repository.deleteTask(task.id, metadata()))
        assertEquals(OperationResult.Success, repository.revokeCompletion(completion.id, metadata()))

        assertTrue(repository.observeTasks(profileId).first().none { it.id == task.id })
        assertTrue(database.dao().taskIncludingDeleted(task.id)?.deletedAt != null)
        assertEquals(120, repository.observeBalance(profileId).first())
    }

    @Test
    fun recurringCompletionCanBeRevokedAndCompletedAgainInSameOccurrence() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        val completedAt = Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds()
        repository.completeTask(
            "seed-daily-exercise",
            completedAt,
                TimeZone.UTC,
            metadata(),
        )
        val completion = repository.observeCompletions(profileId).first().single()

        assertEquals(OperationResult.Success, repository.revokeCompletion(completion.id, metadata()))
        assertEquals(120, repository.observeBalance(profileId).first())
        assertTrue(repository.observeCompletions(profileId).first().isEmpty())

        assertEquals(
            OperationResult.Success,
            repository.completeTask("seed-daily-exercise", completedAt, TimeZone.UTC, metadata()),
        )
        assertEquals(140, repository.observeBalance(profileId).first())
        assertEquals(completion.id, repository.observeCompletions(profileId).first().single().id)
    }

    @Test
    fun completedOccurrenceBlocksRecurrenceChangeButAllowsOtherEdits() = runTest {
        repository.ensureSeedData(metadata())
        val taskId = "seed-daily-exercise"
        val completedAt = Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds()
        val sameOccurrence = Instant.parse("2026-08-25T05:00:00Z").toEpochMilliseconds()
        val nextDayInSameWeek = Instant.parse("2026-08-26T04:00:00Z").toEpochMilliseconds()
        val nextOccurrence = Instant.parse("2026-08-31T04:00:00Z").toEpochMilliseconds()
        val zone = TimeZone.UTC
        repository.completeTask(taskId, completedAt, zone, metadata())

        assertEquals(
            OperationResult.Success,
            repository.updateTask(
                taskId,
                "更新标题",
                "更新说明",
                25,
                TaskRecurrence.DAILY,
                "🏃",
                sameOccurrence,
                zone,
                metadata(),
            ),
        )
        assertEquals("更新标题", repository.observeTasks("family-default").first().single { it.id == taskId }.title)
        assertEquals(
            OperationResult.Rejected(RejectionReason.TASK_RECURRENCE_LOCKED),
            repository.updateTask(
                taskId,
                "更新标题",
                "更新说明",
                25,
                TaskRecurrence.WEEKLY,
                "🏃",
                sameOccurrence,
                zone,
                metadata(),
            ),
        )
        assertEquals(
            OperationResult.Rejected(RejectionReason.TASK_RECURRENCE_LOCKED),
            repository.updateTask(
                taskId,
                "更新标题",
                "更新说明",
                25,
                TaskRecurrence.WEEKLY,
                "🏃",
                nextDayInSameWeek,
                zone,
                metadata(),
            ),
        )
        assertEquals(
            OperationResult.Success,
            repository.updateTask(
                taskId,
                "更新标题",
                "更新说明",
                25,
                TaskRecurrence.WEEKLY,
                "🏃",
                nextOccurrence,
                zone,
                metadata(),
            ),
        )
    }

    @Test
    fun completedRecurringTaskCanBecomeANewOneTimeOccurrence() = runTest {
        repository.ensureSeedData(metadata())
        val taskId = "seed-daily-exercise"
        val completedAt = Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds()
        val zone = TimeZone.UTC
        repository.completeTask(taskId, completedAt, zone, metadata())

        assertEquals(
            OperationResult.Success,
            repository.updateTask(
                taskId,
                "晨间锻炼",
                "",
                20,
                TaskRecurrence.ONCE,
                "💪",
                completedAt,
                zone,
                metadata(),
            ),
        )
        assertEquals(
            OperationResult.Success,
            repository.completeTask(taskId, completedAt, zone, metadata()),
        )
        assertEquals(
            OperationResult.NoChange,
            repository.completeTask(taskId, completedAt, zone, metadata()),
        )
        assertEquals(160, repository.observeBalance("family-default").first())
        assertEquals(2, repository.observeCompletions("family-default").first().size)
    }

    @Test
    fun purchaseIgnoresLegacyCapacityAndSaleRefundsSeventyPercent() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addTask("赚金币", "", 100, TaskRecurrence.ONCE, "✅", profileId, metadata())
        val task = repository.observeTasks(profileId).first().first { it.title == "赚金币" }
        repository.completeTask(task.id, 1_777_000_000_000, TimeZone.UTC, metadata())
        repository.addReward("测试奖励", "", 25, null, "🎁", metadata())
        val reward = repository.rewards.first().first { it.name == "测试奖励" }

        repeat(8) {
            assertEquals(OperationResult.Success, repository.redeemReward(reward.id, profileId, metadata()))
        }
        val firstItem = repository.observeInventory(profileId).first().first()
        assertEquals(20, repository.observeBalance(profileId).first())
        assertEquals(8, repository.observeInventory(profileId).first().size)
        assertEquals(
            OperationResult.Rejected(RejectionReason.INSUFFICIENT_BALANCE),
            repository.redeemReward(reward.id, profileId, metadata()),
        )

        assertEquals(OperationResult.Success, repository.sellInventoryItem(firstItem.id, profileId, metadata()))
        assertEquals(37, repository.observeBalance(profileId).first())
        assertEquals(7, repository.observeInventory(profileId).first().size)
        assertTrue(repository.observeInventory(profileId).first().none { it.id == firstItem.id })
    }

    @Test
    fun ownedInventoryCanBeUsedOnceWithoutRefund() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addTask("赚金币", "", 100, TaskRecurrence.ONCE, "✅", profileId, metadata())
        val task = repository.observeTasks(profileId).first().first { it.title == "赚金币" }
        repository.completeTask(task.id, 1_777_000_000_000, TimeZone.UTC, metadata())
        repository.addReward("测试奖励", "", 50, null, "🎁", metadata())
        val reward = repository.rewards.first().first { it.name == "测试奖励" }
        repository.redeemReward(reward.id, profileId, metadata())
        val item = repository.observeInventory(profileId).first().single()
        val useMetadata = metadata()
        val eventsBeforeUse = repository.pendingEvents().size

        assertEquals(OperationResult.Success, repository.useInventoryItem(item.id, profileId, useMetadata))
        assertEquals(170, repository.observeBalance(profileId).first())
        assertTrue(repository.observeInventory(profileId).first().isEmpty())
        assertEquals(eventsBeforeUse + 1, repository.pendingEvents().size)
        assertEquals(EventType.INVENTORY_ITEM_USED, repository.pendingEvents().last().eventType)

        assertEquals(OperationResult.NoChange, repository.useInventoryItem(item.id, profileId, useMetadata))
        assertEquals(eventsBeforeUse + 1, repository.pendingEvents().size)
        assertEquals(
            OperationResult.Rejected(RejectionReason.INVENTORY_ITEM_NOT_FOUND),
            repository.sellInventoryItem(item.id, profileId, metadata()),
        )
    }

    @Test
    fun resetRestoresSeedTasksAndClearsProgressWithoutDeletingShopOrEvents() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addTask("临时任务", "", 250, TaskRecurrence.ONCE, "✅", profileId, metadata())
        val task = repository.observeTasks(profileId).first().first { it.title == "临时任务" }
        repository.completeTask(task.id, 1_777_000_000_000, TimeZone.UTC, metadata())
        repository.addReward("保留奖励", "重置后仍在", 50, null, "🎁", metadata())
        val reward = repository.rewards.first().first { it.name == "保留奖励" }
        repository.redeemReward(reward.id, profileId, metadata())
        repository.deleteReward("seed-reward-coffee", metadata())
        assertEquals(null, repository.wishGoal.first())
        val eventsBeforeReset = repository.pendingEvents().size
        val resetMetadata = metadata()

        assertEquals(OperationResult.Success, repository.resetData(profileId, resetMetadata))
        val resetTasks = repository.observeTasks(profileId).first()
        assertEquals(3, resetTasks.size)
        assertEquals(
            listOf(
                "seed-daily-exercise",
            ),
            resetTasks.filter { it.recurrence == TaskRecurrence.DAILY }.map { it.id },
        )
        assertTrue(resetTasks.none { it.title == "临时任务" })
        assertEquals(8 * 60, resetTasks.single { it.id == "seed-daily-exercise" }.deadlineMinutes)
        assertEquals(setOf(6), resetTasks.single { it.id == "seed-weekly-clean" }.weekDays)
        assertEquals(null, resetTasks.single { it.id == "seed-custom-goals" }.monthDay)
        assertEquals(0, repository.observeBalance(profileId).first())
        assertEquals(0, repository.observeProgressStats(profileId).first().experiencePoints)
        assertEquals(0, repository.observeProgressStats(profileId).first().completedTaskCount)
        assertTrue(repository.observeCompletions(profileId).first().isEmpty())
        assertTrue(repository.observeInventory(profileId).first().isEmpty())
        assertTrue(repository.rewards.first().any { it.id == reward.id })
        assertTrue(repository.rewards.first().any { it.id == "seed-reward-coffee" && it.active })
        assertEquals(null, repository.wishGoal.first()?.rewardId)
        assertEquals(null, repository.wishGoal.first()?.deposit)
        assertTrue(repository.pendingEvents().size > eventsBeforeReset)

        val eventsAfterReset = repository.pendingEvents().size
        assertEquals(OperationResult.NoChange, repository.resetData(profileId, resetMetadata))
        assertEquals(eventsAfterReset, repository.pendingEvents().size)
    }

    @Test
    fun resetUsesStableProfileScopedSeedTasksWithoutMovingAnotherProfilesTasks() = runTest {
        repository.ensureSeedData(metadata())
        val defaultProfileId = repository.profiles.first().single().id
        val defaultTaskIds = repository.observeTasks(defaultProfileId).first().mapTo(mutableSetOf()) { it.id }
        repository.addProfile("第二角色", metadata())
        val secondProfileId = repository.profiles.first().single { it.id != defaultProfileId }.id

        assertEquals(OperationResult.Success, repository.resetData(secondProfileId, metadata()))
        val secondTaskIds = repository.observeTasks(secondProfileId).first().mapTo(mutableSetOf()) { it.id }

        assertEquals(defaultTaskIds, repository.observeTasks(defaultProfileId).first().mapTo(mutableSetOf()) { it.id })
        assertEquals(3, secondTaskIds.size)
        assertTrue(secondTaskIds.none { it in defaultTaskIds })
        assertTrue(secondTaskIds.all { it.startsWith("$secondProfileId:") })

        assertEquals(OperationResult.Success, repository.resetData(secondProfileId, metadata()))
        assertEquals(secondTaskIds, repository.observeTasks(secondProfileId).first().mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun profileDataSnapshotReturnsAllBackupInputsTogether() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.completeTask(
            "seed-daily-exercise",
            Instant.parse("2026-08-25T04:00:00Z").toEpochMilliseconds(),
                TimeZone.UTC,
            metadata(),
        )
        repository.addReward("快照奖励", "", 10, null, "🎁", metadata())
        val reward = repository.rewards.first().single { it.name == "快照奖励" }
        repository.redeemReward(reward.id, profileId, metadata())

        val snapshot = requireNotNull(repository.profileDataSnapshot(profileId))

        assertEquals(profileId, snapshot.profileId)
        assertEquals(3, snapshot.tasks.size)
        assertEquals(130, snapshot.balance)
        assertEquals(140, snapshot.progressStats.experiencePoints)
        assertEquals(1, snapshot.completions.size)
        assertEquals(1, snapshot.inventory.size)
        assertEquals(null, repository.profileDataSnapshot("missing-profile"))
    }

    @Test
    fun wishGoalPersistsAcrossRepositoryRecreationAndClearsWhenRewardIsDeleted() = runTest {
        repository.ensureSeedData(metadata())
        val rewardId = "seed-reward-coffee"

        assertEquals(OperationResult.Success, repository.setWishGoal(rewardId, metadata()))
        assertEquals(rewardId, repository.wishGoalRewardId.first())

        val recreated = RoomFamilyQuestRepository(
            eventPayloadCodec = eventCodec,
            database = database,
            preferences = AndroidPreferencesStore(context, "family-quest-preferences"),
            deviceIdentity = DeviceIdentity(AndroidPreferencesStore(context, "family-quest-device")),
        )
        assertEquals(rewardId, recreated.wishGoalRewardId.first())
        assertEquals(OperationResult.Success, recreated.deleteReward(rewardId, metadata()))
        assertEquals(null, recreated.wishGoalRewardId.first())
    }

    @Test
    fun v055CatalogMigrationFillsOnlyOneMissingWishAndPreservesExistingGoal() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addReward("Existing Goal", "Keep this selection", 900, null, "🎯", metadata())
        val existingGoal = repository.rewards.first().single { it.name == "Existing Goal" }
        repository.setWishGoal(existingGoal.id, metadata())
        restoreBeforeDefaultWishMigration()

        repository.ensureSeedData(metadata())

        assertEquals(existingGoal.id, repository.wishGoal.first()?.rewardId)
        assertEquals(profileId, repository.wishGoal.first()?.profileId)
    }

    @Test
    fun v055CatalogMigrationDoesNotRecreateCoffeeAfterUserCancelsIt() = runTest {
        repository.ensureSeedData(metadata())
        repository.setWishGoal(null, metadata())
        restoreBeforeDefaultWishMigration()

        repository.ensureSeedData(metadata())
        assertEquals(null, repository.wishGoal.first()?.rewardId)

        assertEquals(OperationResult.NoChange, repository.setWishGoal(null, metadata()))
        val archive = repository.backupArchive(exportedAt = 1234)
        assertEquals(OperationResult.Success, repository.restoreBackup(archive, metadata()))
        repository.ensureSeedData(metadata())
        assertEquals(null, repository.wishGoal.first())
    }

    @Test
    fun defaultWishMigrationDoesNotRestoreDeletedTasksOrOverwriteRewardEdits() = runTest {
        repository.ensureSeedData(metadata())
        repository.setWishGoal(null, metadata())
        repository.deleteTask("seed-daily-exercise", metadata())
        restoreBeforeDefaultWishMigration()

        repository.ensureSeedData(metadata())

        val profileId = repository.profiles.first().single().id
        assertTrue(repository.observeTasks(profileId).first().none { it.id == "seed-daily-exercise" })
        assertEquals(null, repository.wishGoal.first()?.rewardId)
    }

    private suspend fun restoreBeforeDefaultWishMigration() {
        val archive = repository.backupArchive(exportedAt = 1234)
        assertEquals(
            OperationResult.Success,
            repository.restoreBackup(
                archive.copy(processedCommands = archive.processedCommands.filterNot {
                    it.idempotencyKey == "bootstrap-seed-v2:catalog-v055"
                }),
                metadata(),
            ),
        )
    }

    @Test
    fun pinningIsFreeAndRedemptionHasNoBonus() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.addReward("Small treat", "", 50, null, "🎁", metadata())
        val reward = repository.rewards.first().single { it.name == "Small treat" }

        assertEquals(OperationResult.Success, repository.setWishGoal(reward.id, metadata()))
        assertEquals(120, repository.observeBalance(profileId).first())
        assertEquals(0, repository.wishGoal.first()?.deposit)

        assertEquals(OperationResult.Success, repository.redeemReward(reward.id, profileId, metadata()))
        assertEquals(70, repository.observeBalance(profileId).first())
        assertEquals(null, repository.wishGoal.first())
        assertEquals(1, repository.observeInventory(profileId).first().count { it.rewardId == reward.id })

        repository.addReward("Expensive goal", "", 2_000, null, "🎧", metadata())
        val expensive = repository.rewards.first().single { it.name == "Expensive goal" }
        assertEquals(OperationResult.Success, repository.setWishGoal(expensive.id, metadata()))
        assertEquals(70, repository.observeBalance(profileId).first())
        assertEquals(OperationResult.Success, repository.setWishGoal(null, metadata()))
        assertEquals(70, repository.observeBalance(profileId).first())
    }

    @Test
    fun wishGoalReminderIsIdempotentPerNaturalDay() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        val rewardId = repository.rewards.first().single { it.name == "Specialty Coffee" }.id

        assertEquals(OperationResult.Success, repository.setWishGoal(rewardId, metadata()))
        assertEquals(
            OperationResult.Success,
            repository.markWishGoalReminderShown(profileId, "2026-09-03", metadata()),
        )
        assertEquals(
            OperationResult.NoChange,
            repository.markWishGoalReminderShown(profileId, "2026-09-03", metadata()),
        )
        assertEquals(
            OperationResult.Success,
            repository.markWishGoalReminderShown(profileId, "2026-09-04", metadata()),
        )
        assertEquals("2026-09-04", repository.wishGoal.first()?.lastReminderDate)
    }

    @Test
    fun completeBackupRestoresAllTablesAndPreferences() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        repository.completeTask(
            "seed-custom-goals",
            Instant.parse("2026-09-02T12:00:00Z").toEpochMilliseconds(),
                TimeZone.UTC,
            metadata(),
        )
        repository.addReward("Backup Reward", "", 50, null, "🎁", metadata())
        val rewardId = repository.rewards.first().single { it.name == "Backup Reward" }.id
        repository.setWishGoal(rewardId, metadata())
        repository.redeemReward(rewardId, profileId, metadata())
        val archive = repository.backupArchive(exportedAt = 1234)
        assertTrue(archive.profiles.isNotEmpty())
        assertTrue(archive.tasks.isNotEmpty())
        assertTrue(archive.rewards.isNotEmpty())
        assertTrue(archive.completions.isNotEmpty())
        assertTrue(archive.ledgerEntries.isNotEmpty())
        assertTrue(archive.redemptions.isNotEmpty())
        assertTrue(archive.events.isNotEmpty())
        assertTrue(archive.processedCommands.isNotEmpty())

        repository.addReward("临时奖励", "不应保留", 1, null, "🧪", metadata())
        assertTrue(repository.rewards.first().any { it.name == "临时奖励" })

        assertEquals(OperationResult.Success, repository.restoreBackup(archive, metadata()))
        assertTrue(repository.rewards.first().none { it.name == "临时奖励" })
        assertEquals(null, repository.wishGoalRewardId.first())
        assertEquals(profileId, repository.selectedProfileId.value)
        assertEquals(archive.tasks.size, database.dao().allTasks().size)
        assertEquals(archive.completions.size, database.dao().allCompletions().size)
        assertEquals(archive.ledgerEntries.size, database.dao().allLedgerEntries().size)
        assertEquals(archive.redemptions.size, database.dao().allRedemptions().size)
        assertEquals(archive.events.size + 1, database.dao().allEvents().size)
        assertEquals(archive.processedCommands.size + 1, database.dao().allProcessedCommands().size)
    }

    @Test
    fun invalidBackupIsRejectedWithoutWritesAndRestoreConflictRollsBackAtomically() = runTest {
        repository.ensureSeedData(metadata())
        val archive = repository.backupArchive(exportedAt = 1234)
        repository.addReward("现场奖励", "必须保留", 1, null, "🧪", metadata())
        val liveRewardIds = repository.rewards.first().mapTo(mutableSetOf()) { it.id }

        val invalidArchive = archive.copy(selectedProfileId = "missing-profile")
        assertEquals(
            OperationResult.Rejected(RejectionReason.BACKUP_INVALID),
            repository.restoreBackup(invalidArchive, metadata()),
        )
        assertEquals(liveRewardIds, repository.rewards.first().mapTo(mutableSetOf()) { it.id })

        val restoreMetadata = metadata()
        val conflictingArchive = archive.copy(
            events = archive.events.toMutableList().also { events ->
                events[0] = events[0].copy(idempotencyKey = restoreMetadata.idempotencyKey)
            },
        )
        assertEquals(
            OperationResult.Rejected(RejectionReason.BACKUP_RESTORE_FAILED),
            repository.restoreBackup(conflictingArchive, restoreMetadata),
        )
        assertEquals(liveRewardIds, repository.rewards.first().mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun invalidEventPayloadIsRejectedBeforeRestoreWrites() = runTest {
        repository.ensureSeedData(metadata())
        val archive = repository.backupArchive(exportedAt = 1234)
        repository.addReward("现场奖励", "必须保留", 1, null, "🧪", metadata())
        val liveRewardIds = repository.rewards.first().mapTo(mutableSetOf()) { it.id }
        val invalidArchive = archive.copy(
            events = archive.events.toMutableList().also { events ->
                events[0] = events[0].copy(payload = "not-json")
            },
        )

        assertEquals(
            OperationResult.Rejected(RejectionReason.BACKUP_INVALID),
            repository.restoreBackup(invalidArchive, metadata()),
        )
        assertEquals(liveRewardIds, repository.rewards.first().mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun outstandingLegacyDepositIsRefundedOnceOnStartupAndBackupRestore() = runTest {
        repository.ensureSeedData(metadata())
        val profileId = repository.profiles.first().single().id
        database.dao().upsertWishGoal(com.familyquest.data.db.WishGoalEntity(profileId, "seed-reward-coffee", 50))
        val before = repository.observeBalance(profileId).first()
        repository.ensureSeedData(metadata())
        assertEquals(before + 50, repository.observeBalance(profileId).first())
        assertEquals(0, repository.wishGoal.first()?.deposit)
        repository.ensureSeedData(metadata())
        assertEquals(before + 50, repository.observeBalance(profileId).first())
        val archive = repository.backupArchive(1234).copy(wishGoalDeposit = 25)
        assertEquals(OperationResult.Success, repository.restoreBackup(archive, metadata()))
        assertEquals(before + 75, repository.observeBalance(profileId).first())
        assertEquals(0, repository.wishGoal.first()?.deposit)
    }

    private fun metadata(): CommandMetadata {
        command += 1
        return CommandMetadata("test-command-$command", "test-trace-$command")
    }
}

private class RecordingEventCodec : EventPayloadCodec {
    val encodedPayloads = mutableListOf<EventPayload>()

    override fun encodePayload(payload: EventPayload): String {
        encodedPayloads += payload
        return "{}"
    }

    override fun decodePayload(content: String): EventPayload {
        require(content == "{}") { "Invalid event payload" }
        return emptyMap()
    }
}
