package com.familyquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.familyquest.app.MainUiState
import com.familyquest.app.MainViewModel
import com.familyquest.application.InventoryOption
import com.familyquest.application.RewardPurchaseOption
import com.familyquest.application.TaskLeaderboardEntry
import com.familyquest.app.ui.theme.BottomBarBackground
import com.familyquest.app.ui.theme.CardBackground
import com.familyquest.app.ui.theme.CoinGold
import com.familyquest.app.ui.theme.HealthRed
import com.familyquest.app.ui.theme.MagicPurple
import com.familyquest.app.ui.theme.QuestTeal
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.TaskCompletion
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.rules.TaskScheduleRules
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private enum class MainSection(val label: String, val emoji: String) {
    TASKS("Quests", "📋"),
    SHOP("Store", "🛍️"),
    INVENTORY("Rewards", "🎒"),
    STATS("Stats", "⚡"),
}

private val PremiumOrange = Color(0xFFFF6B35)

private data class CoinPop(val id: Long, val taskId: String, val amount: Int)

private data class WishGoalPresentation(
    val reward: Reward,
    val balance: Int,
    val deposit: Int,
    val remaining: Int,
    val progress: Float,
    val estimatedDays: Int,
) {
    val isReady: Boolean get() = remaining == 0
}

@Composable
fun FamilyQuestScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    backupActions: BackupActions = UnavailableBackupActions,
    premiumState: PremiumUiState = PremiumUiState.free(),
    onPurchasePremium: () -> Unit = {},
    onRestorePremium: () -> Unit = {},
) {
    var section by remember { mutableStateOf(MainSection.TASKS) }
    var selectedRecurrence by remember { mutableStateOf(TaskRecurrence.DAILY) }
    var newTaskRecurrence by remember { mutableStateOf(TaskRecurrence.DAILY) }
    var taskEditor by remember { mutableStateOf<HabitTask?>(null) }
    var showNewTask by remember { mutableStateOf(false) }
    var rewardEditor by remember { mutableStateOf<Reward?>(null) }
    var showNewReward by remember { mutableStateOf(false) }
    var coinPops by remember { mutableStateOf<List<CoinPop>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var nextCoinPopId by remember { mutableStateOf(0L) }
    var showReset by remember { mutableStateOf(false) }
    var itemToSell by remember { mutableStateOf<InventoryOption?>(null) }
    var itemToUse by remember { mutableStateOf<InventoryOption?>(null) }
    var showUpgradePrompt by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var showWishGoalReminder by remember { mutableStateOf(false) }
    val today = remember { currentLocalDate() }
    val isPremium = premiumState.isPremium

    LaunchedEffect(viewModel) { viewModel.messages.collect(snackbarHostState::showSnackbar) }
    LaunchedEffect(premiumState.errorMessage, showPaywall) {
        snackbarHostState.currentSnackbarData?.dismiss()
        if (!showPaywall) {
            premiumState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
        }
    }
    LaunchedEffect(premiumState.isPremium) {
        if (premiumState.isPremium) showPaywall = false
    }
    LaunchedEffect(viewModel) {
        viewModel.coinChanges.collect { change ->
            nextCoinPopId += 1
            val pop = CoinPop(id = nextCoinPopId, taskId = change.taskId, amount = change.amount)
            coinPops = coinPops + pop
            launch {
                delay(COIN_POP_DURATION_MILLIS.toLong())
                coinPops = coinPops.filterNot { it.id == pop.id }
            }
        }
    }
    LaunchedEffect(state.wishGoal?.rewardId, state.wishGoal?.lastReminderDate, today) {
        val goal = state.wishGoal
        showWishGoalReminder = goal != null && goal.lastReminderDate != today
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppNavigation(section) { section = it } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (section) {
                MainSection.TASKS -> TasksContent(
                    state = state,
                    selectedRecurrence = selectedRecurrence,
                    coinPops = coinPops,
                    padding = padding,
                    onRecurrenceChange = { selectedRecurrence = it },
                    onGoToStore = { section = MainSection.SHOP },
                    onToggle = viewModel::toggleTask,
                    onEdit = { taskEditor = it },
                    onAddRecurring = { recurrence ->
                        newTaskRecurrence = recurrence
                        showNewTask = true
                    },
                    isPremium = isPremium,
                    onShowUpgradePrompt = { showUpgradePrompt = true },
                )
                MainSection.SHOP -> RewardsContent(
                    state = state,
                    padding = padding,
                    onRedeem = viewModel::redeemReward,
                    onEdit = { rewardEditor = it },
                    onAdd = { showNewReward = true },
                    onSetWishGoal = viewModel::setWishGoal,
                    isPremium = isPremium,
                    onShowPaywall = { showPaywall = true },
                )
                MainSection.INVENTORY -> InventoryContent(
                    state = state,
                    padding = padding,
                    onUse = { itemToUse = it },
                    onSell = { itemToSell = it },
                )
                MainSection.STATS -> StatsContent(
                    state = state,
                    padding = padding,
                    onReset = { showReset = true },
                    onImport = {
                        backupActions.import(
                            onContent = viewModel::importBackup,
                            onFailure = viewModel::importFailed,
                        )
                    },
                    onExport = {
                        scope.launch {
                            val content = viewModel.exportBackup()
                            if (!content.isNullOrEmpty()) {
                                backupActions.export(
                                    fileName = "quest-backup-${currentLocalDate()}.json",
                                    content = content,
                                    onSuccess = viewModel::backupExportCompleted,
                                    onFailure = viewModel::exportFailed,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (isPremium && (showNewTask || taskEditor != null)) {
        TaskEditorDialog(
            task = taskEditor,
            initialRecurrence = newTaskRecurrence,
            onDismiss = {
                showNewTask = false
                taskEditor = null
            },
            onDelete = { task ->
                viewModel.deleteTask(task.id)
                showNewTask = false
                taskEditor = null
            },
            onSave = { title, reward, recurrence, emoji, deadlineMinutes, weekDays, monthlyTargetCount ->
                viewModel.saveTask(
                    task = taskEditor,
                    title = title,
                    reward = reward,
                    recurrence = recurrence,
                    emoji = emoji,
                    deadlineMinutes = deadlineMinutes,
                    weekDays = weekDays,
                    monthDay = taskEditor?.monthDay,
                    monthlyTargetCount = monthlyTargetCount,
                )
                showNewTask = false
                taskEditor = null
            },
        )
    }
    if (isPremium && (showNewReward || rewardEditor != null)) {
        RewardEditorDialog(
            reward = rewardEditor,
            onDismiss = {
                showNewReward = false
                rewardEditor = null
            },
            onDelete = { reward ->
                viewModel.deleteReward(reward.id)
                showNewReward = false
                rewardEditor = null
            },
            onSave = { name, description, cost, emoji ->
                viewModel.saveReward(rewardEditor, name, description, cost, emoji)
                showNewReward = false
                rewardEditor = null
            },
        )
    }
    itemToSell?.let { item ->
        SellConfirmation(
            item = item,
            onDismiss = { itemToSell = null },
            onConfirm = {
                viewModel.sell(item)
                itemToSell = null
            },
        )
    }
        itemToUse?.let { item ->
        UseConfirmation(
            option = item,
            onDismiss = { itemToUse = null },
            onConfirm = {
                viewModel.use(item)
                itemToUse = null
            },
        )
    }
    if (showWishGoalReminder) {
        state.wishGoalPresentation()?.let { goal ->
            WishGoalReminderDialog(
                goal = goal,
                onDismiss = {
                    viewModel.markWishGoalReminderShown(today)
                    showWishGoalReminder = false
                },
                onCompleteQuests = {
                    viewModel.markWishGoalReminderShown(today)
                    showWishGoalReminder = false
                    section = MainSection.TASKS
                },
            )
        }
    }
    if (showReset) {
        ResetConfirmation(
            onDismiss = { showReset = false },
            onConfirm = {
                viewModel.reset()
                showReset = false
            },
        )
    }
    if (showUpgradePrompt) {
        UpgradePrompt(
            onDismiss = { showUpgradePrompt = false },
            onSeePlans = {
                showUpgradePrompt = false
                showPaywall = true
            },
        )
    }
    if (showPaywall) {
        PremiumSheet(
            state = premiumState,
            onDismiss = { showPaywall = false },
            onUpgrade = onPurchasePremium,
            onRestore = onRestorePremium,
        )
    }
}

@Composable
private fun TasksContent(
    state: MainUiState,
    selectedRecurrence: TaskRecurrence,
    coinPops: List<CoinPop>,
    padding: PaddingValues,
    onRecurrenceChange: (TaskRecurrence) -> Unit,
    onGoToStore: () -> Unit,
    onToggle: (HabitTask) -> Unit,
    onEdit: (HabitTask) -> Unit,
    onAddRecurring: (TaskRecurrence) -> Unit,
    isPremium: Boolean,
    onShowUpgradePrompt: () -> Unit,
) {
    val recurring = state.tasks.filter { it.recurrence == selectedRecurrence }
    val completedUnits = recurring.sumOf { task ->
        if (task.recurrence == TaskRecurrence.MONTHLY) {
            task.currentPeriodCompletionCount.coerceIn(0, task.monthlyTargetCount)
        } else if (task.isCompleted) {
            1
        } else {
            0
        }
    }
    val targetUnits = recurring.sumOf { task ->
        if (task.recurrence == TaskRecurrence.MONTHLY) task.monthlyTargetCount else 1
    }
    val progress = if (targetUnits == 0) 0f else completedUnits.toFloat() / targetUnits

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 14.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 92.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Today's Quests",
                subtitle = "Complete quests to earn coins",
                balance = state.balance,
            )
        }
        item {
            WishGoalCard(
                state = state,
                isPremium = isPremium,
                onShowUpgradePrompt = onGoToStore,
            )
        }
        item {
            SectionHeading("RECURRING QUESTS", "$completedUnits/$targetUnits")
            Spacer(Modifier.height(9.dp))
            TaskProgress(progress, selectedRecurrence)
        }
        item { RecurrenceTabs(selectedRecurrence, onRecurrenceChange) }
        if (recurring.isNotEmpty()) {
            items(recurring, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    coinPops = coinPops,
                    onToggle = onToggle,
                    onEdit = if (isPremium) onEdit else { _ -> onShowUpgradePrompt() },
                )
            }
        }
        item {
            val locked = !isPremium
            AddTaskRow(
                recurrence = selectedRecurrence,
                isFirst = recurring.size < 2,
                locked = locked,
                onClick = if (locked) onShowUpgradePrompt else {
                    { onAddRecurring(selectedRecurrence) }
                },
            )
        }
        if (!isPremium) {
            item { PremiumEditHint(onShowUpgradePrompt) }
        }
    }
}

@Composable
private fun WishGoalCard(
    state: MainUiState,
    isPremium: Boolean,
    onShowUpgradePrompt: () -> Unit,
) {
    val goal = state.wishGoalPresentation()
    if (goal == null) {
        val accent = if (isPremium) CoinGold else PremiumOrange
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isPremium) Color(0xFFFFF3E0) else accent.copy(alpha = 0.07f),
                    RoundedCornerShape(16.dp),
                )
                .dashedBorder(accent.copy(alpha = 0.40f), 12.dp)
                .clickable(role = Role.Button, onClick = onShowUpgradePrompt)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⭐", fontSize = 22.sp)
            Text(
                text = if (isPremium) {
                    "No wish goal set\nGo to Store and tap ⭐ to pin your goal here"
                } else {
                    "No wish goal set\nGo to Store and tap ⭐ to pin Coffee here"
                },
                color = if (isPremium) Color(0xFF92400E) else accent.copy(alpha = 0.78f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        return
    }
    val animatedProgress by animateFloatAsState(
        targetValue = goal.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = PROGRESS_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "wish-goal-progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3E0), RoundedCornerShape(16.dp))
            .border(
                1.5.dp,
                if (goal.isReady) Color(0xFF22C55E) else CoinGold,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(goal.reward.emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    goal.reward.name,
                    color = Color(0xFF1C1C1E),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatInteger(state.balance)} / ${formatInteger(goal.reward.cost)} coins",
                    color = Color(0xFF78350F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.08f))
            .testTag("wish-goal-progress")
            .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .background(
                        Brush.horizontalGradient(
                            if (goal.isReady) {
                                listOf(Color(0xFF4ADE80), Color(0xFF22C55E))
                            } else {
                                listOf(Color(0xFFF59E0B), CoinGold)
                            },
                        ),
                    ),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (goal.isReady) {
                    "🎉 Ready to redeem!"
                } else {
                    "${formatInteger(goal.remaining)} coins to go!"
                },
                color = if (goal.isReady) Color(0xFF16A34A) else Color(0xFFB45309),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!goal.isReady && goal.estimatedDays > 0) {
                Text(
                    "~${goal.estimatedDays} days away",
                    color = Color(0xFF92400E),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: HabitTask,
    coinPops: List<CoinPop>,
    onToggle: (HabitTask) -> Unit,
    onEdit: (HabitTask) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TaskCard(task, onToggle, onEdit)
        coinPops.filter { it.taskId == task.id }.forEach { pop ->
            CoinPopBadge(
                pop = pop,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp),
            )
        }
    }
}

@Composable
private fun RecurrenceTabs(selected: TaskRecurrence, onSelected: (TaskRecurrence) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            TaskRecurrence.DAILY to "📅 Daily",
            TaskRecurrence.WEEKLY to "📆 Weekly",
            TaskRecurrence.MONTHLY to "🗓️ Monthly",
        ).forEach { (recurrence, label) ->
            val color = recurrence.color()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected == recurrence) color.copy(alpha = 0.14f)
                        else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        1.dp,
                        if (selected == recurrence) color.copy(alpha = 0.55f)
                        else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp),
                    )
                    .selectable(
                        selected = selected == recurrence,
                        role = Role.Tab,
                        onClick = { onSelected(recurrence) },
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected == recurrence) color else Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TaskProgress(progress: Float, recurrence: TaskRecurrence) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = PROGRESS_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "quest-progress",
    )
    val colors = when (recurrence) {
        TaskRecurrence.DAILY -> listOf(HealthRed, CoinGold)
        TaskRecurrence.WEEKLY -> listOf(QuestTeal, MagicPurple)
        TaskRecurrence.MONTHLY -> listOf(MagicPurple, Color(0xFFA78BFA))
        else -> listOf(recurrence.color(), recurrence.color())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .testTag("quest-progress-${recurrence.name.lowercase()}")
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(4.dp)
                .background(Brush.horizontalGradient(colors)),
        )
    }
}

@Composable
private fun AddTaskRow(
    recurrence: TaskRecurrence,
    isFirst: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val type = when (recurrence) {
        TaskRecurrence.DAILY -> "Daily"
        TaskRecurrence.WEEKLY -> "Weekly"
        TaskRecurrence.MONTHLY -> "Monthly"
        else -> "Custom"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(Color.White.copy(alpha = 0.14f), 12.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("＋", color = Color.White.copy(alpha = 0.32f), fontSize = 20.sp)
        Text(
            if (isFirst) "Set Your First $type Quest" else "Add $type Quest",
            color = Color.White.copy(alpha = 0.32f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (locked) Text("🔒", fontSize = 11.sp)
    }
}

@Composable
private fun PremiumEditHint(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PremiumOrange.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .border(1.dp, PremiumOrange.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "✏️  Tap to edit & customize quests — upgrade to unlock",
            color = PremiumOrange.copy(alpha = 0.82f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TaskCard(
    task: HabitTask,
    onToggle: (HabitTask) -> Unit,
    onEdit: (HabitTask) -> Unit,
) {
    val completed = task.isCompleted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed) 0.55f else 1f)
            .background(
                if (completed) Color(0xFF22C55E).copy(alpha = 0.06f) else CardBackground,
                RoundedCornerShape(16.dp),
            )
            .border(
                1.dp,
                if (completed) Color(0xFF22C55E).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .testTag("complete-task-${task.title}")
                .toggleable(
                    value = completed,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(task) },
                )
                .semantics {
                    contentDescription = if (completed) "Undo quest completion" else "Complete quest"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .border(
                        2.dp,
                        if (completed) Color(0xFF22C55E) else task.recurrence.color().copy(alpha = 0.55f),
                        CircleShape,
                    )
                    .background(if (completed) Color(0xFF22C55E) else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (completed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = { onEdit(task) }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(task.emoji, fontSize = 21.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = if (completed) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.deadlineLabel() != null || task.recurrenceScheduleLabel() != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        task.deadlineLabel()?.let { deadline ->
                            Text(deadline, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                        }
                        task.recurrenceScheduleLabel()?.let { schedule ->
                            Text(
                                text = schedule,
                                color = task.recurrence.color().copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (completed) {
            Text("Undo", color = Color.White.copy(alpha = 0.25f), fontSize = 10.sp)
        }
        CoinPill(task.rewardPoints)
    }
}

@Composable
private fun RewardsContent(
    state: MainUiState,
    padding: PaddingValues,
    onRedeem: (String) -> Unit,
    onEdit: (Reward) -> Unit,
    onAdd: () -> Unit,
    onSetWishGoal: (String?) -> Unit,
    isPremium: Boolean,
    onShowPaywall: () -> Unit,
) {
    val visibleRewards = if (isPremium) state.rewardOptions else state.rewardOptions.filter {
        it.reward.id == "seed-reward-coffee"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 14.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Reward Store",
                subtitle = "Spend your hard-earned coins on real rewards",
                balance = state.balance,
            )
        }
        itemsIndexed(visibleRewards, key = { _, option -> option.reward.id }) { index, option ->
            RewardCard(
                option = option,
                catalogIndex = index,
                onRedeem = onRedeem,
                onEdit = onEdit,
                isPremium = isPremium,
                isWishGoal = state.wishGoalRewardId == option.reward.id,
                onToggleWishGoal = {
                    if (isPremium || option.reward.id == "seed-reward-coffee") {
                        onSetWishGoal(if (state.wishGoalRewardId == option.reward.id) null else option.reward.id)
                    } else {
                        onShowPaywall()
                    }
                },
            )
        }
        item {
            if (isPremium) {
                AddRewardRow(onAdd)
            } else {
                PremiumStoreBanner(onShowPaywall)
            }
        }
    }
}

@Composable
private fun RewardCard(
    option: RewardPurchaseOption,
    catalogIndex: Int,
    onRedeem: (String) -> Unit,
    onEdit: (Reward) -> Unit,
    isPremium: Boolean,
    isWishGoal: Boolean,
    onToggleWishGoal: () -> Unit,
) {
    val reward = option.reward
    val canBuy = isPremium && option.canPurchase
    val shape = RoundedCornerShape(16.dp)
    val rarityColor = when (catalogIndex) {
        0 -> Color(0xFFCD7F32)
        1 -> Color(0xFFC0C0C0)
        2 -> CoinGold
        else -> null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (catalogIndex == 2) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = CoinGold,
                        spotColor = CoinGold,
                    )
                } else {
                    Modifier
                },
            )
            .heightIn(min = 82.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            rarityColor ?: if (canBuy) MagicPurple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
        ),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reward.emoji, fontSize = 30.sp, modifier = Modifier.width(38.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reward.name,
                        color = if (isPremium) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPremium) {
                        IconButton(onClick = { onEdit(reward) }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                "Edit ${reward.name}",
                                tint = rarityColor?.copy(alpha = 0.72f) ?: Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
                Text(
                    reward.description,
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "🪙 ${option.effectiveCost}",
                    color = if (isPremium) CoinGold else Color.White.copy(alpha = 0.38f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { onRedeem(reward.id) },
                    modifier = Modifier.testTag("buy-reward-${reward.name}"),
                    enabled = canBuy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MagicPurple,
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        disabledContentColor = Color.White.copy(alpha = 0.20f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        when {
                            !isPremium -> "Locked"
                            option.rejectionReason == RejectionReason.OUT_OF_STOCK -> "Sold out"
                            option.rejectionReason == RejectionReason.INSUFFICIENT_BALANCE -> "Not enough"
                            !canBuy -> "Unavailable"
                            isWishGoal -> "🎉 Redeem"
                            else -> "Buy"
                        },
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
            if (isPremium || reward.id == "seed-reward-coffee") {
                IconButton(onClick = onToggleWishGoal, modifier = Modifier.size(30.dp)) {
                    Text(
                        "⭐",
                        color = if (isWishGoal) CoinGold else Color.White.copy(alpha = 0.32f),
                        fontSize = 15.sp,
                        modifier = Modifier.semantics {
                            contentDescription = if (isWishGoal) {
                                "Remove ${reward.name} wish goal"
                            } else {
                                "Set ${reward.name} as wish goal"
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRewardRow(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PremiumOrange.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, PremiumOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onAdd),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = PremiumOrange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Add New Reward",
            color = PremiumOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 13.dp),
        )
    }
}

@Composable
private fun PremiumStoreBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PremiumOrange.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, PremiumOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Upgrade to Premium to create your own rewards!",
            color = PremiumOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InventoryContent(
    state: MainUiState,
    padding: PaddingValues,
    onUse: (InventoryOption) -> Unit,
    onSell: (InventoryOption) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 14.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            ScreenHeader(
                title = "My Rewards",
                subtitle = "${state.inventoryOptions.size} " +
                    "${if (state.inventoryOptions.size == 1) "item" else "items"} · " +
                    "sell back at ${state.refundPercent}%",
                balance = state.balance,
            )
        }
        if (state.inventoryOptions.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🎒", fontSize = 48.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Your inventory is empty", color = Color.White.copy(alpha = 0.24f), fontSize = 14.sp)
                    Text("Head to the store and buy a reward", color = Color.White.copy(alpha = 0.18f), fontSize = 12.sp)
                }
            }
        } else {
            items(state.inventoryOptions, key = { it.item.id }) { option ->
                InventoryItemCard(option, onUse, onSell)
            }
        }
    }
}

@Composable
private fun InventoryItemCard(
    option: InventoryOption,
    onUse: (InventoryOption) -> Unit,
    onSell: (InventoryOption) -> Unit,
) {
    val item = option.item
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.92f),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, MagicPurple.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(item.emoji, fontSize = 30.sp)
            Text(
                item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "cost ${item.cost}🪙",
                color = Color.White.copy(alpha = 0.32f),
                fontSize = 10.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            InventoryAction(
                label = "🎉 Use",
                color = Color(0xFF4ADE80),
                description = "Use ${item.title}",
                onClick = { onUse(option) },
            )
            InventoryAction(
                label = "售 ${option.saleRefund}🪙",
                color = Color(0xFFF87171),
                description = "Sell ${item.title} for ${option.saleRefund} coins",
                onClick = { onSell(option) },
            )
        }
    }
}

@Composable
private fun InventoryAction(
    label: String,
    color: Color,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.28f), CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsContent(
    state: MainUiState,
    padding: PaddingValues,
    onReset: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 14.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatsHeader() }
        item { StatsGrid(state) }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeading("Completed")
        }
        if (state.recentCompletions.isEmpty()) {
            item { EmptyMessage("No completed quests yet — go earn some coins!") }
        } else {
            items(state.recentCompletions, key = { it.id }) { completion ->
                CompletionRow(completion)
            }
        }
        item {
            LeaderboardCard(
                title = "🏅 Quest Harvest Board",
                subtitle = "Most completions overall",
                entries = state.mostCompletedTasks,
                value = { "${it.completionCount} ×" },
            )
        }
        item {
            LeaderboardCard(
                title = "💰 Wish Savings Board",
                subtitle = "Most coins earned overall",
                entries = state.highestEarningTasks,
                value = { "${it.totalEarned} 🪙" },
            )
        }
        item { BackupActions(onImport = onImport, onExport = onExport) }
        item { ResetDataButton(onReset) }
        item { LegalLinks() }
    }
}

@Composable
private fun BackupActions(onImport: () -> Unit, onExport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestTeal.copy(alpha = 0.12f),
                    contentColor = QuestTeal,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text("📤 Import Data", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoinGold.copy(alpha = 0.12f),
                    contentColor = CoinGold,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text("📥 Export Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatsHeader() {
    Text("Stats", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ResetDataButton(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = HealthRed.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = HealthRed)
            Spacer(Modifier.width(8.dp))
            Text("Reset All Data", color = HealthRed, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatsGrid(state: MainUiState) {
    val stats = listOf(
        StatItem("Balance", state.balance, "🪙", if (state.balance < 0) HealthRed else CoinGold),
        StatItem("Total Earned", state.totalEarned, "💰", QuestTeal),
        StatItem("Done Today", state.completedToday, "✅", Color(0xFF22C55E)),
        StatItem("All Time", state.completedTaskCount, "⭐", Color(0xFFA78BFA)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { rowStats ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStats.forEach { stat ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(stat.emoji, fontSize = 23.sp)
                            Text(
                                stat.value.toString(),
                                color = stat.color,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(stat.label, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class StatItem(val label: String, val value: Int, val emoji: String, val color: Color)

@Composable
private fun LeaderboardCard(
    title: String,
    subtitle: String,
    entries: List<TaskLeaderboardEntry>,
    value: (TaskLeaderboardEntry) -> String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.30f), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    "Complete quests to appear here",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    color = Color.White.copy(alpha = 0.20f),
                    fontSize = 11.sp,
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    LeaderboardRow(index, entry, value(entry))
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(index: Int, entry: TaskLeaderboardEntry, value: String) {
    val rank = when (index) {
        0 -> "🥇"
        1 -> "🥈"
        2 -> "🥉"
        else -> (index + 1).toString()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(rank, color = Color.White.copy(alpha = 0.38f), fontSize = 13.sp, modifier = Modifier.width(24.dp))
        Text(entry.emoji, fontSize = 18.sp)
        Text(
            entry.title,
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(value, color = CoinGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CompletionRow(completion: TaskCompletion) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF22C55E).copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(completion.emojiSnapshot, fontSize = 20.sp)
        Text(
            completion.titleSnapshot,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 13.sp,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("+${completion.rewardSnapshot}🪙", color = CoinGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, balance: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp)
        }
        CoinBalance(balance)
    }
}

@Composable
private fun CoinBalance(balance: Int) {
    val balanceColor = if (balance < 0) HealthRed else CoinGold
    Row(
        modifier = Modifier
            .background(Color(0xFF1E1B3A), CircleShape)
            .border(1.dp, CoinGold.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("🪙", fontSize = 13.sp)
        Text(balance.toString(), color = balanceColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CoinPill(amount: Int) {
    Text(
        text = "🪙 $amount",
        color = CoinGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(CoinGold.copy(alpha = 0.11f), CircleShape)
            .border(1.dp, CoinGold.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (trailing != null) {
            Text(trailing, color = Color.White.copy(alpha = 0.34f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
        color = Color.White.copy(alpha = 0.22f),
        fontSize = 13.sp,
    )
}

@Composable
private fun CoinPopBadge(pop: CoinPop, modifier: Modifier = Modifier) {
    val progress = remember(pop.id) { Animatable(0f) }
    LaunchedEffect(pop.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = COIN_POP_DURATION_MILLIS,
                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
            ),
        )
    }
    Text(
        text = "${if (pop.amount > 0) "+" else ""}${pop.amount} 🪙",
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .graphicsLayer {
                translationY = -52.dp.toPx() * progress.value
                scaleX = 1f + 0.25f * progress.value
                scaleY = 1f + 0.25f * progress.value
                alpha = if (progress.value < 0.6f) 1f else (1f - progress.value) / 0.4f
            }
            .background(if (pop.amount > 0) Color(0xFF22C55E) else HealthRed, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private const val COIN_POP_DURATION_MILLIS = 1_800
private const val PROGRESS_ANIMATION_MILLIS = 500

@Composable
private fun AppNavigation(selected: MainSection, onSelected: (MainSection) -> Unit) {
    NavigationBar(containerColor = BottomBarBackground, tonalElevation = 0.dp) {
        MainSection.values().forEach { section ->
            NavigationBarItem(
                selected = selected == section,
                onClick = { onSelected(section) },
                icon = {
                    Column(
                        modifier = Modifier.semantics { contentDescription = section.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = section.emoji,
                            fontSize = if (selected == section) 20.sp else 18.sp,
                        )
                        Box(
                            Modifier
                                .size(4.dp)
                                .background(
                                    if (selected == section) MagicPurple else Color.Transparent,
                                    CircleShape,
                                ),
                        )
                    }
                },
                label = { Text(section.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MagicPurple,
                    selectedTextColor = MagicPurple,
                    unselectedIconColor = Color.White.copy(alpha = 0.30f),
                    unselectedTextColor = Color.White.copy(alpha = 0.30f),
                    indicatorColor = BottomBarBackground,
                ),
            )
        }
    }
}

@Composable
private fun UpgradePrompt(onDismiss: () -> Unit, onSeePlans: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 34.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF1A1040), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                    .clickable { }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🔒", fontSize = 36.sp)
                Spacer(Modifier.height(12.dp))
                Text("Premium Feature", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Upgrade to unlock custom quests and rewards.",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSeePlans,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("See Plans", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Not Now", color = Color.White.copy(alpha = 0.32f))
                }
            }
        }
    }
}

@Composable
private fun PremiumSheet(
    state: PremiumUiState,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1040), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    )
                    .clickable { }
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.20f), CircleShape),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "🔒 Upgrade to QuestReward\nPremium",
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Customize your quests and claim real rewards.",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                listOf(
                    "Customize all daily and weekly quests",
                    "Set your own wish goal",
                    "Buy rewards from the store",
                ).forEach { feature ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(PremiumOrange.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", color = PremiumOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(feature, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PremiumOrange.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .border(1.dp, PremiumOrange.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.canStartFreeTrial) "🎁 7-day free trial, then ${state.priceLabel}"
                        else state.priceLabel,
                        color = PremiumOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onUpgrade,
                    enabled = !state.isBusy && state.status != PremiumStatus.CHECKING,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        when {
                            state.status == PremiumStatus.CHECKING -> "Checking Subscription…"
                            state.isBusy -> "Processing…"
                            state.canStartFreeTrial -> "Start Free Trial"
                            else -> "Subscribe"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                state.errorMessage?.let { message ->
                    Text(message, color = HealthRed, fontSize = 12.sp)
                }
                TextButton(
                    onClick = onRestore,
                    enabled = !state.isBusy && (state.status != PremiumStatus.CHECKING || state.errorMessage != null),
                ) {
                    Text("Restore Purchases", color = Color.White.copy(alpha = 0.62f))
                }
                TextButton(onClick = onDismiss) {
                    Text("No thanks, continue free", color = Color.White.copy(alpha = 0.30f))
                }
                Text(
                    state.subscriptionNotice,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                )
                LegalLinks()
            }
        }
    }
}

@Composable
private fun LegalLinks() {
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    Column {
        Row {
            TextButton(onClick = {
                linkError = runCatching {
                    uriHandler.openUri("https://github.com/Phanstal/QuestAndReward/blob/77377b1e0006d72fc2577631ad0a278a25a98a17/docs/privacy-policy.md")
                }.isFailure
            }) { Text("Privacy Policy", color = PremiumOrange) }
            TextButton(onClick = {
                linkError = runCatching {
                    uriHandler.openUri("https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")
                }.isFailure
            }) { Text("Terms of Use", color = PremiumOrange) }
        }
        if (linkError) Text("Unable to open this link.", color = HealthRed, fontSize = 12.sp)
    }
}

@Composable
private fun WishGoalReminderDialog(
    goal: WishGoalPresentation,
    onDismiss: () -> Unit,
    onCompleteQuests: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = goal.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = PROGRESS_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "wish-reminder-progress",
    )
    val greeting = when (currentLocalHour()) {
        in 0..11 -> "Good morning ☀️"
        in 12..17 -> "Good afternoon 🌤️"
        else -> "Good evening 🌙"
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1A1040), CardBackground)),
                        RoundedCornerShape(24.dp),
                    )
                    .border(1.dp, CoinGold.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                    .clickable { }
                    .padding(24.dp),
            ) {
                Text(
                    "$greeting — Today's Goal",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(goal.reward.emoji, fontSize = 52.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            goal.reward.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            goal.reward.description,
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatInteger(goal.balance)} / ${formatInteger(goal.reward.cost)} 🪙",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (goal.isReady) "🎉 Ready to redeem!" else "${goal.remaining}🪙 to go",
                        color = if (goal.isReady) Color(0xFF4ADE80) else CoinGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .testTag("wish-reminder-progress")
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(10.dp)
                            .background(
                                Brush.horizontalGradient(
                                    if (goal.isReady) {
                                        listOf(Color(0xFF4ADE80), Color(0xFF22C55E))
                                    } else {
                                        listOf(CoinGold, Color(0xFFA78BFA))
                                    },
                                ),
                            ),
                    )
                }
                if (!goal.isReady && goal.estimatedDays > 0) {
                    Text(
                        "At this pace, about ${goal.estimatedDays} days away",
                        color = Color.White.copy(alpha = 0.28f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White.copy(alpha = 0.45f),
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Got it", fontSize = 12.sp) }
                    Button(
                        onClick = onCompleteQuests,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CoinGold),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp),
                    ) {
                        Text(
                            "Go Complete Quests →",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellConfirmation(
    item: InventoryOption,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val inventoryItem = item.item
    CenteredConfirmation(
        emoji = inventoryItem.emoji,
        title = "Sell ${inventoryItem.title}?",
        message = "You'll receive ${item.saleRefund} 🪙\n(70% of original ${inventoryItem.cost}🪙)",
        confirmLabel = "Confirm Sell",
        confirmColor = HealthRed,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun UseConfirmation(
    option: InventoryOption,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CenteredConfirmation(
        emoji = option.item.emoji,
        title = "Enjoy ${option.item.title}?",
        message = "This will remove it from your inventory.\nYou've earned it — go enjoy it!",
        confirmLabel = "🎉 Confirm Use",
        confirmColor = Color(0xFF22C55E),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun ResetConfirmation(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CenteredConfirmation(
        emoji = "⚠️",
        title = "Reset Everything?",
        message = "All quests and rewards will be cleared. We strongly recommend exporting your data first.",
        dismissLabel = "⚠️ No, Keep My Data",
        confirmLabel = "Reset Anyway",
        confirmColor = HealthRed,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun CenteredConfirmation(
    emoji: String,
    title: String,
    message: String,
    dismissLabel: String = "Cancel",
    confirmLabel: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .widthIn(max = 340.dp)
                    .background(Color(0xFF1A1040), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                    .clickable { }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, fontSize = 42.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                        contentColor = Color.White.copy(alpha = 0.58f),
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(dismissLabel, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = confirmColor),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(confirmLabel, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun TaskRecurrence.color(): Color = when (this) {
    TaskRecurrence.DAILY -> HealthRed
    TaskRecurrence.WEEKLY -> QuestTeal
    TaskRecurrence.MONTHLY -> MagicPurple
    TaskRecurrence.YEARLY -> MagicPurple
    TaskRecurrence.ONCE -> Color(0xFFA78BFA)
}

private fun MainUiState.wishGoalPresentation(): WishGoalPresentation? {
    val reward = rewardOptions.firstOrNull { it.reward.id == wishGoalRewardId }?.reward ?: return null
    val remaining = (reward.cost - balance).coerceAtLeast(0)
    val averageDailyCoins = tasks.sumOf { task ->
        when (task.recurrence) {
            TaskRecurrence.DAILY -> task.rewardPoints.toDouble()
            TaskRecurrence.WEEKLY -> task.rewardPoints / 7.0
            TaskRecurrence.MONTHLY -> task.rewardPoints * task.monthlyTargetCount / 30.0
            TaskRecurrence.YEARLY, TaskRecurrence.ONCE -> 0.0
        }
    }
    val estimatedDays = if (remaining > 0 && averageDailyCoins > 0.0) {
        ceil(remaining / averageDailyCoins).toInt()
    } else {
        0
    }
    return WishGoalPresentation(
        reward = reward,
        balance = balance,
        deposit = wishGoal?.deposit ?: 0,
        remaining = remaining,
        progress = if (reward.cost <= 0) 1f else (balance.toFloat() / reward.cost).coerceIn(0f, 1f),
        estimatedDays = estimatedDays,
    )
}

private fun HabitTask.deadlineLabel(): String? = deadlineMinutes?.let { minutes ->
    val hour = (minutes / 60).toString().padStart(2, '0')
    val minute = (minutes % 60).toString().padStart(2, '0')
    "⏰ ${hour}:${minute}"
}

private fun HabitTask.recurrenceScheduleLabel(): String? = when (recurrence) {
    TaskRecurrence.WEEKLY -> weekDays.takeIf { it.isNotEmpty() }?.displayName()
    TaskRecurrence.MONTHLY -> "This month $currentPeriodCompletionCount/$monthlyTargetCount · $monthlyTargetCount times/month"
    TaskRecurrence.YEARLY -> "Yearly (legacy)"
    else -> null
}

private fun Set<Int>.displayName(): String {
    val normalized = filter { it in 0..6 }.toSet()
    return when (normalized) {
        TaskScheduleRules.DEFAULT_WEEK_DAYS -> "Weekdays"
        setOf(0, 6) -> "Weekends"
        (0..6).toSet() -> "Every day"
        else -> normalized.sorted().joinToString(" ") { listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[it] }
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        ),
    )
}

private fun currentLocalDate(): String {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}

private fun currentLocalHour(): Int {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
}

private fun formatInteger(value: Int): String {
    val number = value.toLong()
    val sign = if (number < 0) "-" else ""
    val digits = if (number < 0) (-number).toString() else number.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return sign + grouped
}
