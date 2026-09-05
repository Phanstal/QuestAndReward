package com.familyquest.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyquest.application.ApplicationResult
import com.familyquest.application.ApplicationHealth
import com.familyquest.application.ErrorCodeEnum
import com.familyquest.application.FamilyQuestService
import com.familyquest.application.InventoryOption
import com.familyquest.application.RewardPurchaseOption
import com.familyquest.application.TaskLeaderboardEntry
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.GameProgress
import com.familyquest.domain.model.PlayerProgress
import com.familyquest.domain.model.Profile
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.model.WishGoal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: Profile? = null,
    val tasks: List<HabitTask> = emptyList(),
    val rewardOptions: List<RewardPurchaseOption> = emptyList(),
    val inventoryOptions: List<InventoryOption> = emptyList(),
    val refundPercent: Int = 70,
    val recentCompletions: List<TaskCompletion> = emptyList(),
    val completedToday: Int = 0,
    val completedTaskCount: Int = 0,
    val totalEarned: Int = 0,
    val mostCompletedTasks: List<TaskLeaderboardEntry> = emptyList(),
    val highestEarningTasks: List<TaskLeaderboardEntry> = emptyList(),
    val gameProgress: GameProgress = GameProgress(
        player = PlayerProgress(1, 0, 0, 100),
        achievements = emptyList(),
    ),
    val balance: Int = 0,
    val pendingEventCount: Int = 0,
    val wishGoalRewardId: String? = null,
    val wishGoal: WishGoal? = null,
)

data class TaskCoinChange(
    val taskId: String,
    val amount: Int,
)

private data class HeaderState(
    val profiles: List<Profile>,
    val selectedProfile: Profile?,
    val pendingEventCount: Int,
    val wishGoalRewardId: String?,
    val wishGoal: WishGoal?,
)

private data class ProfileState(
    val tasks: List<HabitTask>,
    val progress: com.familyquest.application.ProgressOverview,
    val completions: List<TaskCompletion>,
    val completedToday: Int,
    val mostCompletedTasks: List<TaskLeaderboardEntry>,
    val highestEarningTasks: List<TaskLeaderboardEntry>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val service: FamilyQuestService,
) : ViewModel() {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val _coinChanges = MutableSharedFlow<TaskCoinChange>(extraBufferCapacity = 4)
    val coinChanges = _coinChanges.asSharedFlow()

    init {
        viewModelScope.launch {
            service.health.collect { health ->
                if (health is ApplicationHealth.Degraded) {
                    messages.emit(
                        "${health.errorCode.userMessage} (${health.errorCode.code}, ref ${health.traceId.take(8)})",
                    )
                }
            }
        }
    }

    private val header = combine(
        service.profiles,
        service.selectedProfileId,
        service.pendingEventCount,
        service.wishGoal,
    ) { profiles, selectedId, pendingCount, wishGoal ->
        HeaderState(
            profiles = profiles,
            selectedProfile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull(),
            pendingEventCount = pendingCount,
            wishGoalRewardId = wishGoal?.rewardId,
            wishGoal = wishGoal,
        )
    }

    val uiState: StateFlow<MainUiState> = header.flatMapLatest { headerState ->
        val profile = headerState.selectedProfile
        if (profile == null) {
            flowOf(
                MainUiState(
                    profiles = headerState.profiles,
                    wishGoalRewardId = headerState.wishGoalRewardId,
                    wishGoal = headerState.wishGoal,
                ),
            )
        } else {
            val profileState = combine(
                service.observeTasks(profile.id),
                service.observeProgress(profile.id),
                service.observeRecentCompletions(profile.id),
                service.observeCompletedToday(profile.id),
                service.observeTaskLeaderboards(profile.id),
            ) { tasks, progress, completions, completedToday, leaderboards ->
                ProfileState(
                    tasks = tasks,
                    progress = progress,
                    completions = completions,
                    completedToday = completedToday,
                    mostCompletedTasks = leaderboards.mostCompleted,
                    highestEarningTasks = leaderboards.highestEarning,
                )
            }
            combine(profileState, service.observeCommerce(profile.id)) { data, commerce ->
                MainUiState(
                    profiles = headerState.profiles,
                    selectedProfile = profile,
                    tasks = data.tasks,
                    rewardOptions = commerce.rewardOptions,
                    inventoryOptions = commerce.inventoryOptions,
                    refundPercent = commerce.refundPercent,
                    recentCompletions = data.completions,
                    completedToday = data.completedToday,
                    gameProgress = data.progress.gameProgress,
                    completedTaskCount = data.progress.stats.completedTaskCount,
                    totalEarned = data.progress.stats.experiencePoints,
                    mostCompletedTasks = data.mostCompletedTasks,
                    highestEarningTasks = data.highestEarningTasks,
                    balance = commerce.balance,
                    pendingEventCount = headerState.pendingEventCount,
                    wishGoalRewardId = headerState.wishGoalRewardId,
                    wishGoal = headerState.wishGoal,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun selectProfile(profileId: String) = launchAction { service.selectProfile(profileId) }

    fun addProfile(name: String) = launchAction { service.addProfile(name) }

    fun saveTask(
        task: HabitTask?,
        title: String,
        reward: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        deadlineMinutes: Int?,
        weekDays: Set<Int>,
        monthDay: Int?,
        monthlyTargetCount: Int = 1,
    ) {
        if (title.isBlank() || emoji.isBlank() || reward < 1) {
            messages.tryEmit("Enter a quest name and a valid coin value.")
            return
        }
        launchAction {
            if (task == null) {
                val profileId = uiState.value.selectedProfile?.id
                    ?: return@launchAction missingProfileResult()
                service.addTask(
                    title = title,
                    notes = "",
                    rewardPoints = reward,
                    recurrence = recurrence,
                    emoji = emoji,
                    assigneeId = profileId,
                    deadlineMinutes = deadlineMinutes,
                    weekDays = weekDays,
                    monthDay = monthDay,
                    monthlyTargetCount = monthlyTargetCount,
                )
            } else {
                service.updateTask(
                    taskId = task.id,
                    title = title,
                    notes = task.notes,
                    rewardPoints = reward,
                    recurrence = recurrence,
                    emoji = emoji,
                    deadlineMinutes = deadlineMinutes,
                    weekDays = weekDays,
                    monthDay = monthDay,
                    monthlyTargetCount = monthlyTargetCount,
                )
            }
        }
    }

    fun deleteTask(taskId: String) = launchAction { service.deleteTask(taskId) }

    fun completeTask(task: HabitTask) = launchTaskMutation(task.id, task.rewardPoints) {
        service.completeTask(task.id)
    }

    fun revokeCompletion(completion: TaskCompletion) = revokeCompletion(
        completionId = completion.id,
        taskId = completion.taskId,
        rewardPoints = completion.rewardSnapshot,
    )

    private fun revokeCompletion(
        completionId: String,
        taskId: String,
        rewardPoints: Int,
    ) = launchTaskMutation(taskId, -rewardPoints) {
        service.revokeCompletion(completionId)
    }

    fun toggleTask(task: HabitTask) {
        if (!task.isCompleted) {
            completeTask(task)
            return
        }
        val completionId = task.currentCompletionId
        if (completionId == null) {
            messages.tryEmit("Completion record not found. Refresh and try again.")
            return
        }
        revokeCompletion(
            completionId = completionId,
            taskId = task.id,
            rewardPoints = task.currentCompletionReward ?: task.rewardPoints,
        )
    }

    fun saveReward(reward: Reward?, name: String, description: String, cost: Int, emoji: String) {
        if (name.isBlank() || emoji.isBlank() || cost < 1) {
            messages.tryEmit("Enter a reward name and a valid coin value.")
            return
        }
        launchAction {
            if (reward == null) {
                service.addReward(name, description, cost, null, emoji)
            } else {
                service.updateReward(reward.id, name, description, cost, reward.stock, emoji)
            }
        }
    }

    fun deleteReward(rewardId: String) = launchAction { service.deleteReward(rewardId) }

    fun setWishGoal(rewardId: String?) = launchAction {
        service.setWishGoal(rewardId)
    }

    fun markWishGoalReminderShown(date: String) {
        val profileId = uiState.value.selectedProfile?.id ?: return
        launchAction { service.markWishGoalReminderShown(profileId, date) }
    }

    fun redeemReward(rewardId: String) = launchAction(successMessage = "Reward claimed!") {
        val profileId = uiState.value.selectedProfile?.id
            ?: return@launchAction missingProfileResult()
        service.redeemReward(rewardId, profileId)
    }

    fun sell(option: InventoryOption) = launchAction(
        successMessage = "Sold for ${option.saleRefund} coins.",
    ) {
        val profileId = uiState.value.selectedProfile?.id
            ?: return@launchAction missingProfileResult()
        service.sellInventoryItem(option.item.id, profileId)
    }

    fun use(option: InventoryOption) = launchAction(
        successMessage = "Used ${option.item.title}.",
    ) {
        val profileId = uiState.value.selectedProfile?.id
            ?: return@launchAction missingProfileResult()
        service.useInventoryItem(option.item.id, profileId)
    }

    fun reset() = launchAction(successMessage = "Data reset.") {
        val profileId = uiState.value.selectedProfile?.id
            ?: return@launchAction missingProfileResult()
        service.resetData(profileId)
    }

    suspend fun exportPendingEvents(): String? {
        return when (val result = service.exportPendingEvents()) {
            is ApplicationResult.Success -> result.value
            is ApplicationResult.Failure -> {
                messages.emit(result.displayMessage())
                null
            }
        }
    }

    suspend fun exportBackup(): String? {
        val profileId = uiState.value.selectedProfile?.id ?: run {
            messages.emit(missingProfileResult().displayMessage())
            return null
        }
        return when (val result = service.exportBackup(profileId)) {
            is ApplicationResult.Success -> result.value
            is ApplicationResult.Failure -> {
                messages.emit(result.displayMessage())
                null
            }
        }
    }

    fun importBackup(content: String) {
        if (content.isBlank()) {
            messages.tryEmit("The import file is empty.")
            return
        }
        launchAction(successMessage = "Backup imported.") {
            service.importBackup(content)
        }
    }

    fun exportCompleted() {
        messages.tryEmit("Pending events exported.")
    }

    fun backupExportCompleted() {
        messages.tryEmit("Backup exported.")
    }

    fun exportFailed() {
        messages.tryEmit("Export failed.")
    }

    fun importFailed() {
        messages.tryEmit("Import failed.")
    }

    private suspend fun handleResult(result: ApplicationResult<Unit>, successMessage: String? = null) {
        when (result) {
            is ApplicationResult.Success -> successMessage?.let { messages.emit(it) }
            is ApplicationResult.Failure -> messages.emit(result.displayMessage())
        }
    }

    private fun launchAction(
        successMessage: String? = null,
        block: suspend () -> ApplicationResult<Unit>,
    ) {
        viewModelScope.launch {
            val result = block()
            handleResult(result, successMessage)
        }
    }

    private fun launchTaskMutation(
        taskId: String,
        coinChange: Int,
        block: suspend () -> ApplicationResult<Boolean>,
    ) {
        viewModelScope.launch {
            when (val result = block()) {
                is ApplicationResult.Success -> if (result.value) {
                    _coinChanges.emit(TaskCoinChange(taskId = taskId, amount = coinChange))
                }
                is ApplicationResult.Failure -> messages.emit(result.displayMessage())
            }
        }
    }

    private fun missingProfileResult(): ApplicationResult.Failure {
        val metadata = FamilyQuestService.newCommandMetadata()
        return ApplicationResult.Failure(
            errorCode = ErrorCodeEnum.PROFILE_NOT_FOUND,
            userMessage = ErrorCodeEnum.PROFILE_NOT_FOUND.userMessage,
            traceId = metadata.traceId,
        )
    }

    private fun ApplicationResult.Failure.displayMessage(): String {
        return "$userMessage (${errorCode.code}, ref ${traceId.take(8)})"
    }

}
