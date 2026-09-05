package com.familyquest.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyquest.app.MainUiState
import com.familyquest.app.MainViewModel
import com.familyquest.app.ui.theme.FamilyQuestTheme
import com.familyquest.application.EventExporter
import com.familyquest.application.FamilyQuestService
import com.familyquest.application.InventoryOption
import com.familyquest.application.RewardPurchaseOption
import com.familyquest.application.TaskLeaderboardEntry
import com.familyquest.domain.event.DomainEvent
import com.familyquest.domain.model.CommandMetadata
import com.familyquest.domain.model.GameProgress
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.InventoryItem
import com.familyquest.domain.model.LedgerEntry
import com.familyquest.domain.model.OperationResult
import com.familyquest.domain.model.PlayerProgress
import com.familyquest.domain.model.Profile
import com.familyquest.domain.model.ProfileDataSnapshot
import com.familyquest.domain.model.ProgressStats
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.model.WishGoal
import com.familyquest.domain.repository.FamilyQuestRepository
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyQuestScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun taskTabsFilterDailyWeeklyAndMonthlyTasks() {
        val repository = RecordingUiRepository()
        setScreen(
            state = MainUiState(
                tasks = listOf(
                    task("daily", "每日任务", TaskRecurrence.DAILY, deadlineMinutes = 8 * 60),
                    task("weekly", "每周任务", TaskRecurrence.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5)),
                    task(
                        "monthly",
                        "每月任务",
                        TaskRecurrence.MONTHLY,
                        monthlyTargetCount = 4,
                        currentPeriodCompletionCount = 2,
                    ),
                    task("once", "自定义任务", TaskRecurrence.ONCE),
                ),
            ),
            repository = repository,
        )

        composeRule.onNodeWithText("每日任务").assertIsDisplayed()
        composeRule.onNodeWithText("⏰ 08:00").assertIsDisplayed()
        composeRule.onAllNodesWithText("每周任务").assertCountEquals(0)
        composeRule.onAllNodesWithText("每月任务").assertCountEquals(0)
        composeRule.onNodeWithText("Set Your First Daily Quest").assertIsDisplayed()
        composeRule.onAllNodesWithText("🔒").assertCountEquals(2)

        composeRule.onNodeWithText("📆 Weekly").performClick()
        composeRule.onNodeWithText("每周任务").assertIsDisplayed()
        composeRule.onNodeWithText("Weekdays").assertIsDisplayed()
        composeRule.onAllNodesWithText("每日任务").assertCountEquals(0)
        composeRule.onAllNodesWithText("每月任务").assertCountEquals(0)
        composeRule.onNodeWithText("Set Your First Weekly Quest").assertIsDisplayed()

        composeRule.onNodeWithText("🗓️ Monthly").performClick()
        composeRule.onNodeWithText("每月任务").assertIsDisplayed()
        composeRule.onNodeWithText("2/4").assertIsDisplayed()
        composeRule.onNodeWithText("This month 2/4 · 4 times/month").assertIsDisplayed()
        composeRule.onAllNodesWithText("每日任务").assertCountEquals(0)
        composeRule.onAllNodesWithText("每周任务").assertCountEquals(0)
        composeRule.onNodeWithText("Set Your First Monthly Quest").assertIsDisplayed()
    }

    @Test
    fun wishGoalProjectsEmptyRemainingAndAffordableStates() {
        val reward = Reward("reward", "电影之夜", "看一部好片", 150, null, true, 0, "🎬")
        val coffee = Reward("coffee", "精品咖啡", "喝一杯拿铁", 50, null, true, 0, "☕")
        val state = mutableStateOf(
            MainUiState(
                rewardOptions = listOf(
                    RewardPurchaseOption(reward, null),
                    RewardPurchaseOption(coffee, null),
                ),
                balance = 20,
            ),
        )
        val viewModel = MainViewModel(FamilyQuestService(RecordingUiRepository(), EventExporter { "" }))
        composeRule.setContent {
            FamilyQuestTheme(darkTheme = true) {
                FamilyQuestScreen(state.value, viewModel)
            }
        }

        composeRule.onNodeWithText("Wish Goal — Premium Feature", substring = true).assertIsDisplayed()
        unlockPremium()

        composeRule.runOnIdle { state.value = state.value.copy(wishGoalRewardId = "reward") }
        composeRule.onNodeWithText("电影之夜").assertIsDisplayed()
        composeRule.onNodeWithText("130 coins to go!").assertIsDisplayed()

        composeRule.runOnIdle { state.value = state.value.copy(balance = 150) }
        composeRule.onNodeWithText("🎉 Ready to redeem!").assertIsDisplayed()
        composeRule.onNodeWithText("Store").performClick()
        composeRule.onNodeWithContentDescription("Remove 电影之夜 wish goal").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Set 精品咖啡 as wish goal").assertIsDisplayed()

        composeRule.runOnIdle { state.value = state.value.copy(wishGoalRewardId = "coffee") }
        composeRule.onNodeWithContentDescription("Set 电影之夜 as wish goal").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove 精品咖啡 wish goal").assertIsDisplayed()

        composeRule.runOnIdle { state.value = state.value.copy(wishGoalRewardId = null) }
        composeRule.onNodeWithContentDescription("Set 电影之夜 as wish goal").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Set 精品咖啡 as wish goal").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                wishGoalRewardId = "coffee",
                rewardOptions = listOf(RewardPurchaseOption(reward, null)),
            )
        }
        composeRule.onNodeWithText("Quests").performClick()
        composeRule.onNodeWithText("No wish goal set", substring = true).assertIsDisplayed()
    }

    @Test
    fun wishGoalReminderShowsFigmaDetailsAndActions() {
        val reward = Reward("goal", "Specialty Coffee", "A perfect brew", 500, null, true, 0, "☕")
        setScreen(
            MainUiState(
                tasks = listOf(task("daily", "Morning Exercise", TaskRecurrence.DAILY)),
                rewardOptions = listOf(RewardPurchaseOption(reward, RejectionReason.INSUFFICIENT_BALANCE)),
                balance = 70,
                wishGoalRewardId = reward.id,
                wishGoal = WishGoal("family", reward.id, deposit = 50),
            ),
            RecordingUiRepository(),
        )

        composeRule.onNodeWithText("Today's Goal", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Specialty Coffee").assertCountEquals(2)
        composeRule.onNodeWithText("A perfect brew").assertIsDisplayed()
        composeRule.onNodeWithText("70 / 500 🪙").assertIsDisplayed()
        composeRule.onNodeWithText("430🪙 to go").assertIsDisplayed()
        composeRule.onNodeWithText("Got it").assertIsDisplayed()
        composeRule.onNodeWithText("Go Complete Quests →").assertIsDisplayed()
    }

    @Test
    fun weeklyEditorSavesDeadlineAndWeekendSchedule() {
        val repository = RecordingUiRepository()
        setScreen(
            state = MainUiState(
                tasks = listOf(
                    task(
                        "weekly",
                        "每周任务",
                        TaskRecurrence.WEEKLY,
                        deadlineMinutes = 21 * 60 + 25,
                        weekDays = setOf(1, 2, 3, 4, 5),
                    ),
                ),
            ),
            repository = repository,
        )

        unlockPremium()
        composeRule.onNodeWithText("📆 Weekly").performClick()
        composeRule.onNodeWithText("每周任务").performClick()
        composeRule.onNodeWithText("Edit Quest", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Weekends").performClick()
        composeRule.onNodeWithTag("task-deadline-minute-down").performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                TaskUpdateCall(
                    taskId = "weekly",
                    title = "每周任务",
                    rewardPoints = 30,
                    recurrence = TaskRecurrence.WEEKLY,
                    emoji = "✅",
                    deadlineMinutes = 21 * 60 + 30,
                    weekDays = setOf(0, 6),
                    monthDay = null,
                ),
                repository.lastTaskUpdate,
            )
        }
    }

    @Test
    fun monthlyEditorSavesTargetCount() {
        val repository = RecordingUiRepository()
        setScreen(
            state = MainUiState(
                tasks = listOf(
                    task("monthly", "每月任务", TaskRecurrence.MONTHLY, monthlyTargetCount = 2),
                ),
            ),
            repository = repository,
        )

        unlockPremium()
        composeRule.onNodeWithText("🗓️ Monthly").performClick()
        composeRule.onNodeWithText("每月任务").performClick()
        composeRule.onNodeWithText("Edit Quest", useUnmergedTree = true).assertIsDisplayed()
        (1..8).forEach { count ->
            composeRule.onNodeWithTag("task-monthly-target-$count").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("task-monthly-target-3").performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                TaskUpdateCall(
                    taskId = "monthly",
                    title = "每月任务",
                    rewardPoints = 30,
                    recurrence = TaskRecurrence.MONTHLY,
                    emoji = "✅",
                    deadlineMinutes = null,
                    weekDays = emptySet(),
                    monthDay = null,
                    monthlyTargetCount = 3,
                ),
                repository.lastTaskUpdate,
            )
        }
    }

    @Test
    fun completedRecurringTaskCanBeUndone() {
        assertCompletedTaskCanBeUndone(
            task("daily", "已完成每日任务", TaskRecurrence.DAILY, completed = true),
        )
    }

    @Test
    fun statsKeepsCustomCompletionButHidesUndoAction() {
        val completedTask = task("custom", "已完成自定义任务", TaskRecurrence.ONCE, completed = true)
        val completionId = requireNotNull(completedTask.currentCompletionId)
        val repository = RecordingUiRepository()
        setScreen(
            MainUiState(
                tasks = listOf(completedTask),
                recentCompletions = listOf(
                    TaskCompletion(
                        id = completionId,
                        taskId = completedTask.id,
                        occurrenceKey = "once",
                        profileId = "family",
                        rewardSnapshot = completedTask.rewardPoints,
                        titleSnapshot = completedTask.title,
                        emojiSnapshot = completedTask.emoji,
                        recurrenceSnapshot = completedTask.recurrence,
                        completedAt = 1,
                        revokedAt = null,
                    ),
                ),
            ),
            repository,
        )

        composeRule.onNodeWithText("Stats").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(completedTask.title))
        composeRule.onAllNodesWithContentDescription("Undo completion").assertCountEquals(0)
    }

    @Test
    fun shopInventoryAndStatsMatchV052States() {
        val state = MainUiState(
            tasks = listOf(
                task("daily", "每日任务", TaskRecurrence.DAILY, completed = true),
                task("once-done", "已完成自定义任务", TaskRecurrence.ONCE, completed = true),
            ),
            rewardOptions = listOf(
                RewardPurchaseOption(
                    Reward("reward", "电影之夜", "看一部好片", 150, null, true, 0, "🎬", 2),
                    RejectionReason.INSUFFICIENT_BALANCE,
                ),
            ),
            inventoryOptions = listOf(
                InventoryOption(InventoryItem("inventory-1", "reward", "电影之夜", "🎬", 150, 1), 105),
                InventoryOption(InventoryItem("inventory-2", "reward", "电影之夜", "🎬", 150, 2), 105),
                InventoryOption(InventoryItem("inventory-3", "reward", "电影之夜", "🎬", 150, 3), 105),
            ),
            refundPercent = 70,
            recentCompletions = listOf(
                TaskCompletion(
                    id = "completion-daily",
                    taskId = "daily",
                    occurrenceKey = "2026-08-26",
                    profileId = "family",
                    rewardSnapshot = 30,
                    titleSnapshot = "每日任务",
                    emojiSnapshot = "✅",
                    recurrenceSnapshot = TaskRecurrence.DAILY,
                    completedAt = 1_777_000_000_001,
                    revokedAt = null,
                ),
                TaskCompletion(
                    id = "completion",
                    taskId = "once-done",
                    occurrenceKey = "once",
                    profileId = "family",
                    rewardSnapshot = 30,
                    titleSnapshot = "已完成自定义任务",
                    emojiSnapshot = "✅",
                    recurrenceSnapshot = TaskRecurrence.ONCE,
                    completedAt = 1_777_000_000_000,
                    revokedAt = null,
                ),
            ),
            completedToday = 1,
            completedTaskCount = 8,
            totalEarned = 245,
            mostCompletedTasks = listOf(
                TaskLeaderboardEntry("daily", "每日任务", "✅", 8, 240, 100),
            ),
            highestEarningTasks = listOf(
                TaskLeaderboardEntry("once-done", "高价值任务", "✅", 1, 300, 99),
            ),
            gameProgress = GameProgress(PlayerProgress(2, 245, 45, 200), emptyList()),
            balance = 300,
            wishGoalRewardId = "reward",
        )
        setScreen(state, RecordingUiRepository())

        composeRule.onNodeWithText("Store").performClick()
        composeRule.onNodeWithText("电影之夜").assertIsDisplayed()
        composeRule.onNodeWithText("Upgrade to Premium to create your own rewards!").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Remove 电影之夜 wish goal").assertCountEquals(0)
        unlockPremiumFromStore()
        composeRule.onNodeWithContentDescription("Remove 电影之夜 wish goal").assertIsDisplayed()
        composeRule.onAllNodesWithText("x2").assertCountEquals(0)
        composeRule.onNodeWithText("Add New Reward").assertIsDisplayed()
        composeRule.onAllNodesWithText("Your rewards bag is full. Sell an item first.").assertCountEquals(0)
        composeRule.onNodeWithText("Not enough").assertIsNotEnabled()

        composeRule.onNodeWithText("Rewards").performClick()
        composeRule.onAllNodesWithText("电影之夜").assertCountEquals(3)
        composeRule.onAllNodesWithText("🎉 Use").assertCountEquals(3)
        composeRule.onAllNodesWithText("cost 150🪙").assertCountEquals(3)
        composeRule.onAllNodesWithText("售 105🪙").assertCountEquals(3)
        composeRule.onNodeWithText("3 items · sell back at 70%").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Use 电影之夜")[0].performClick()
        composeRule.onNodeWithText("Enjoy 电影之夜?").assertIsDisplayed()
        composeRule.onNodeWithText("🎉 Confirm Use").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithContentDescription("Sell 电影之夜 for 105 coins")[0].performClick()
        composeRule.onNodeWithText("Sell 电影之夜?").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm Sell").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Stats").performClick()
        composeRule.onAllNodesWithText("Stats").assertCountEquals(2)
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        composeRule.onAllNodesWithText("今日还剩 0 项任务").assertCountEquals(0)
        composeRule.onNodeWithText("Done Today").assertIsDisplayed()
        composeRule.onNodeWithText("All Time").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Stats menu").assertCountEquals(0)
        composeRule.onAllNodesWithText("Export pending events", substring = true).assertCountEquals(0)

        val statsList = composeRule.onNode(hasScrollAction())
        statsList.performScrollToNode(hasText("已完成自定义任务"))
        composeRule.onNodeWithText("已完成自定义任务").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Undo completion").assertCountEquals(0)
        statsList.performScrollToNode(hasText("🏅 Quest Harvest Board"))
        composeRule.onNodeWithText("🏅 Quest Harvest Board").assertIsDisplayed()
        composeRule.onNodeWithText("8 ×").assertIsDisplayed()
        statsList.performScrollToNode(hasText("💰 Wish Savings Board"))
        composeRule.onNodeWithText("💰 Wish Savings Board").assertIsDisplayed()
        composeRule.onNodeWithText("300 🪙").assertIsDisplayed()

        statsList.performScrollToNode(hasText("📤 Import Data"))
        composeRule.onNodeWithText("📤 Import Data").assertIsDisplayed()
        composeRule.onNodeWithText("📥 Export Backup").assertIsDisplayed()

        composeRule.onNodeWithText("Reset All Data")
            .performScrollTo()
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Reset Everything?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("⚠️ No, Keep My Data").assertIsDisplayed()
        composeRule.onNodeWithText("Reset Anyway").assertIsDisplayed()
        composeRule.onAllNodesWithText("Export backup", substring = true).assertCountEquals(0)
    }

    @Test
    fun emptyCycleShowsOnlyFigmaAddRow() {
        setScreen(MainUiState(), RecordingUiRepository())

        composeRule.onNodeWithText("Set Your First Daily Quest").assertIsDisplayed()
        composeRule.onAllNodesWithText("No quests for this cycle yet").assertCountEquals(0)
    }

    @Test
    fun affordableWishRewardUsesRedeemLabel() {
        val reward = Reward("wish", "Short Trip", "A weekend away", 80, null, true, 0, "✈️")
        setScreen(
            MainUiState(
                rewardOptions = listOf(RewardPurchaseOption(reward, null, effectiveCost = 72, wishDeposit = 8)),
                balance = 72,
                wishGoalRewardId = reward.id,
            ),
            RecordingUiRepository(),
        )

        unlockPremium()
        composeRule.onNodeWithText("Store").performClick()
        composeRule.onNodeWithText("🎉 Redeem").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun editorDeleteImmediatelyUsesSoftDeleteCommand() {
        val repository = RecordingUiRepository()
        val reward = Reward("delete-reward", "Delete Reward", "", 50, null, true, 0, "🎁")
        setScreen(
            MainUiState(
                tasks = listOf(task("delete-me", "Delete Me", TaskRecurrence.DAILY)),
                rewardOptions = listOf(RewardPurchaseOption(reward, null)),
            ),
            repository,
        )

        unlockPremium()
        composeRule.onNodeWithText("Delete Me").performClick()
        composeRule.onNodeWithText("🗑️ Delete").performClick()

        composeRule.waitUntil { repository.deletedTaskIds == listOf("delete-me") }
        composeRule.onAllNodesWithText("Delete quest").assertCountEquals(0)

        composeRule.onNodeWithText("Store").performClick()
        composeRule.onNodeWithContentDescription("Edit Delete Reward").performClick()
        composeRule.onNodeWithText("🗑️ Delete").performClick()
        composeRule.waitUntil { repository.deletedRewardIds == listOf("delete-reward") }
        composeRule.onAllNodesWithText("Delete reward").assertCountEquals(0)
    }

    @Test
    fun failedTaskCommandDoesNotPlayCoinAnimation() {
        val failedTask = task("failed", "失败任务", TaskRecurrence.DAILY)
        val viewModel = MainViewModel(FamilyQuestService(FailingCompleteRepository, EventExporter { "" }))

        composeRule.setContent {
            FamilyQuestTheme(darkTheme = true) {
                FamilyQuestScreen(MainUiState(tasks = listOf(failedTask)), viewModel)
            }
        }

        composeRule.runOnIdle { viewModel.toggleTask(failedTask) }

        composeRule.onNodeWithText("This quest no longer exists.", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("+30 🪙").assertCountEquals(0)
    }

    @Test
    fun idempotentNoChangeDoesNotPlayCoinAnimation() {
        val completedTask = task("duplicate", "已完成任务", TaskRecurrence.DAILY)
        val viewModel = MainViewModel(FamilyQuestService(NoChangeCompleteRepository, EventExporter { "" }))

        composeRule.setContent {
            FamilyQuestTheme(darkTheme = true) {
                FamilyQuestScreen(MainUiState(tasks = listOf(completedTask)), viewModel)
            }
        }

        composeRule.runOnIdle { viewModel.toggleTask(completedTask) }

        composeRule.onAllNodesWithText("+30 🪙").assertCountEquals(0)
    }

    private fun unlockPremium() {
        composeRule.onNodeWithText("Tap to edit & customize quests", substring = true).performClick()
        composeRule.onNodeWithText("See Plans").performClick()
        composeRule.onNodeWithText("Start Free Trial").performClick()
    }

    private fun unlockPremiumFromStore() {
        composeRule.onNodeWithText("Upgrade to Premium to create your own rewards!").performClick()
        composeRule.onNodeWithText("Start Free Trial").performClick()
    }

    private fun setScreen(state: MainUiState, repository: FamilyQuestRepository): MainViewModel {
        val viewModel = MainViewModel(FamilyQuestService(repository, EventExporter { "" }))
        composeRule.setContent {
            FamilyQuestTheme(darkTheme = true) {
                FamilyQuestScreen(state, viewModel)
            }
        }
        return viewModel
    }

    private fun assertCompletedTaskCanBeUndone(completedTask: HabitTask) {
        val repository = RecordingUiRepository()
        setScreen(MainUiState(tasks = listOf(completedTask)), repository)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(completedTask.title))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithContentDescription("Undo quest completion").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(
                    listOf(requireNotNull(completedTask.currentCompletionId)),
                    repository.revokedCompletionIds,
                )
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("-30 🪙").assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }
}

private fun task(
    id: String,
    title: String,
    recurrence: TaskRecurrence,
    completed: Boolean = false,
    deadlineMinutes: Int? = null,
    weekDays: Set<Int> = emptySet(),
    monthDay: Int? = null,
    monthlyTargetCount: Int = 1,
    currentPeriodCompletionCount: Int = 0,
) = HabitTask(
    id = id,
    title = title,
    notes = "",
    rewardPoints = 30,
    assigneeId = "family",
    isCompleted = completed,
    createdAt = 0,
    updatedAt = 0,
    completedAt = if (completed) 1 else null,
    recurrence = recurrence,
    deadlineMinutes = deadlineMinutes,
    weekDays = weekDays,
    monthDay = monthDay,
    monthlyTargetCount = monthlyTargetCount,
    currentPeriodCompletionCount = currentPeriodCompletionCount,
    currentCompletionId = if (completed) "completion-$id" else null,
    currentCompletionReward = if (completed) 30 else null,
)

private data class TaskUpdateCall(
    val taskId: String,
    val title: String,
    val rewardPoints: Int,
    val recurrence: TaskRecurrence,
    val emoji: String,
    val deadlineMinutes: Int?,
    val weekDays: Set<Int>,
    val monthDay: Int?,
    val monthlyTargetCount: Int = 1,
)

private class RecordingUiRepository : FamilyQuestRepository {
    override val profiles: Flow<List<Profile>> = flowOf(emptyList())
    override val rewards: Flow<List<Reward>> = flowOf(emptyList())
    override val selectedProfileId = MutableStateFlow<String?>(null)
    override val pendingEventCount: Flow<Int> = flowOf(0)
    var lastTaskUpdate: TaskUpdateCall? = null
    val revokedCompletionIds = mutableListOf<String>()
    val deletedTaskIds = mutableListOf<String>()
    val deletedRewardIds = mutableListOf<String>()

    override fun observeTasks(profileId: String): Flow<List<HabitTask>> = flowOf(emptyList())
    override fun observeBalance(profileId: String): Flow<Int> = flowOf(0)
    override fun observeLedger(profileId: String): Flow<List<LedgerEntry>> = flowOf(emptyList())
    override fun observeProgressStats(profileId: String): Flow<ProgressStats> = flowOf(ProgressStats())
    override fun observeCompletions(profileId: String): Flow<List<TaskCompletion>> = flowOf(emptyList())
    override fun observeInventory(profileId: String): Flow<List<InventoryItem>> = flowOf(emptyList())
    override suspend fun profileDataSnapshot(profileId: String): ProfileDataSnapshot? = null
    override suspend fun ensureSeedData(metadata: CommandMetadata) = OperationResult.Success
    override suspend fun selectProfile(profileId: String, metadata: CommandMetadata) = OperationResult.Success
    override suspend fun addProfile(name: String, metadata: CommandMetadata) = OperationResult.Success
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
    ) = OperationResult.Success

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
        lastTaskUpdate = TaskUpdateCall(
            taskId = taskId,
            title = title,
            rewardPoints = rewardPoints,
            recurrence = recurrence,
            emoji = emoji,
            deadlineMinutes = deadlineMinutes,
            weekDays = weekDays,
            monthDay = monthDay,
            monthlyTargetCount = monthlyTargetCount,
        )
        return OperationResult.Success
    }

    override suspend fun deleteTask(taskId: String, metadata: CommandMetadata): OperationResult {
        deletedTaskIds += taskId
        return OperationResult.Success
    }
    override suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
    ) = OperationResult.Success

    override suspend fun revokeCompletion(completionId: String, metadata: CommandMetadata): OperationResult {
        revokedCompletionIds += completionId
        return OperationResult.Success
    }
    override suspend fun addReward(
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ) = OperationResult.Success

    override suspend fun updateReward(
        rewardId: String,
        name: String,
        description: String,
        cost: Int,
        stock: Int?,
        emoji: String,
        metadata: CommandMetadata,
    ) = OperationResult.Success

    override suspend fun deleteReward(rewardId: String, metadata: CommandMetadata): OperationResult {
        deletedRewardIds += rewardId
        return OperationResult.Success
    }
    override suspend fun redeemReward(rewardId: String, profileId: String, metadata: CommandMetadata) =
        OperationResult.Success

    override suspend fun sellInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ) = OperationResult.Success

    override suspend fun useInventoryItem(
        itemId: String,
        profileId: String,
        metadata: CommandMetadata,
    ) = OperationResult.Success

    override suspend fun resetData(profileId: String, metadata: CommandMetadata) = OperationResult.Success

    override suspend fun pendingEvents(): List<DomainEvent> = emptyList()
}

private object FailingCompleteRepository : FamilyQuestRepository by RecordingUiRepository() {
    override suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
    ) = OperationResult.Rejected(RejectionReason.TASK_NOT_FOUND)
}

private object NoChangeCompleteRepository : FamilyQuestRepository by RecordingUiRepository() {
    override suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        timeZone: TimeZone,
        metadata: CommandMetadata,
    ) = OperationResult.NoChange
}
