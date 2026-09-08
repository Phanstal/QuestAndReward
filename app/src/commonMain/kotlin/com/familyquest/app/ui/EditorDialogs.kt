package com.familyquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.familyquest.app.ui.theme.CardBackground
import com.familyquest.app.ui.theme.CoinGold
import com.familyquest.app.ui.theme.HealthRed
import com.familyquest.app.ui.theme.MagicPurple
import com.familyquest.app.ui.theme.QuestTeal
import com.familyquest.domain.model.HabitTask
import com.familyquest.domain.model.Reward
import com.familyquest.domain.model.TaskRecurrence
import com.familyquest.domain.rules.TaskScheduleRules

private val taskEmojis = listOf(
    "✅", "💡", "🎯", "🏃", "🧠", "💼", "🎵", "🍎", "🌱", "⭐",
    "💪", "📚", "💧", "🌅", "🧹", "💰", "📞", "📊", "🏥", "🎨",
    "🧘", "🍳", "🛌", "🚴", "✍️", "🌿", "🎮", "🏋️", "📝", "🔥",
    "🎤", "🌍", "🤝", "💌", "🧪", "🏆", "🚀", "🎁", "🌸", "🦋",
)
private val rewardEmojis = listOf("🎁", "⭐", "🎮", "🎵", "🍕", "🌴", "🏋️", "🎨", "📷", "🎯", "🎪", "🎉")
private val weekDayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val monthlyTargetCountRange = 1..8
private const val DEFAULT_DEADLINE_MINUTES = 8 * 60

@Composable
fun TaskEditorDialog(
    task: HabitTask?,
    initialRecurrence: TaskRecurrence = TaskRecurrence.ONCE,
    onDismiss: () -> Unit,
    onDelete: ((HabitTask) -> Unit)? = null,
    onSave: (
        title: String,
        reward: Int,
        recurrence: TaskRecurrence,
        emoji: String,
        deadlineMinutes: Int?,
        weekDays: Set<Int>,
        monthlyTargetCount: Int,
    ) -> Unit,
) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var reward by remember(task?.id) { mutableStateOf(task?.rewardPoints?.toString() ?: "30") }
    var recurrence by remember(task?.id, initialRecurrence) {
        mutableStateOf(task?.recurrence ?: initialRecurrence)
    }
    var emoji by remember(task?.id) { mutableStateOf(task?.emoji ?: "✅") }
    var deadlineMinutes by remember(task?.id) {
        mutableStateOf(task?.deadlineMinutes.normalizeToFiveMinuteStep())
    }
    var weekDays by remember(task?.id) {
        mutableStateOf(task?.weekDays?.ifEmpty { TaskScheduleRules.DEFAULT_WEEK_DAYS } ?: TaskScheduleRules.DEFAULT_WEEK_DAYS)
    }
    var monthlyTargetCount by remember(task?.id) {
        mutableStateOf(task?.monthlyTargetCount ?: TaskScheduleRules.DEFAULT_MONTHLY_TARGET_COUNT)
    }
    val parsedReward = reward.toIntOrNull()
    val supportsDeadline = recurrence == TaskRecurrence.DAILY || recurrence == TaskRecurrence.WEEKLY
    val valid = title.isNotBlank() && parsedReward != null && parsedReward >= 1 &&
        (recurrence != TaskRecurrence.MONTHLY || monthlyTargetCount in monthlyTargetCountRange)

    BottomDrawer(onDismiss = onDismiss) {
        DrawerHandle()
        EditorHeader(
            title = if (task == null) "New Quest" else "Edit Quest",
            showDelete = task != null && onDelete != null,
            onDelete = { task?.let { onDelete?.invoke(it) } },
        )
        TaskTypeSelector(selected = recurrence, onSelected = { recurrence = it })
        if (recurrence == TaskRecurrence.WEEKLY) {
            WeekDaySelector(selected = weekDays, onSelected = { weekDays = it })
        }
        if (recurrence == TaskRecurrence.MONTHLY) {
            MonthlyTargetCountPicker(
                value = monthlyTargetCount,
                onValueChange = { monthlyTargetCount = it },
            )
        }
        if (supportsDeadline) {
            DeadlinePicker(
                value = deadlineMinutes,
                onValueChange = { deadlineMinutes = it },
            )
        }
        EmojiPicker(taskEmojis, emoji, onSelect = { emoji = it })
        DrawerTextField(
            value = title,
            onValueChange = { title = it.take(50) },
            label = "Quest name...",
            modifier = Modifier.testTag("task-title"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        DrawerTextField(
            value = reward,
            onValueChange = { reward = it.filter(Char::isDigit).take(6) },
            label = "Coin reward...",
            modifier = Modifier.testTag("task-reward"),
            prefix = { Text("🪙") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        DrawerActions(
            confirmLabel = if (task == null) "Add" else "Save",
            enabled = valid,
            onDismiss = onDismiss,
            onConfirm = {
                onSave(
                    title.trim(),
                    parsedReward ?: 1,
                    recurrence,
                    emoji,
                    if (supportsDeadline) deadlineMinutes else null,
                    if (recurrence == TaskRecurrence.WEEKLY) {
                        weekDays.ifEmpty { TaskScheduleRules.DEFAULT_WEEK_DAYS }
                    } else {
                        emptySet()
                    },
                    if (recurrence == TaskRecurrence.MONTHLY) {
                        monthlyTargetCount
                    } else {
                        TaskScheduleRules.DEFAULT_MONTHLY_TARGET_COUNT
                    },
                )
            },
        )
    }
}

@Composable
fun RewardEditorDialog(
    reward: Reward?,
    onDismiss: () -> Unit,
    onDelete: ((Reward) -> Unit)? = null,
    onSave: (name: String, description: String, cost: Int, emoji: String) -> Unit,
) {
    var name by remember(reward?.id) { mutableStateOf(reward?.name.orEmpty()) }
    var description by remember(reward?.id) { mutableStateOf(reward?.description.orEmpty()) }
    var cost by remember(reward?.id) { mutableStateOf(reward?.cost?.toString() ?: "200") }
    var emoji by remember(reward?.id) { mutableStateOf(reward?.emoji ?: "🎁") }
    val parsedCost = cost.toIntOrNull()
    val valid = name.isNotBlank() && parsedCost != null && parsedCost >= 1

    BottomDrawer(onDismiss = onDismiss) {
        DrawerHandle()
        EditorHeader(
            title = if (reward == null) "New Reward" else "Edit Reward",
            showDelete = reward != null && onDelete != null,
            onDelete = { reward?.let { onDelete?.invoke(it) } },
        )
        EmojiPicker(rewardEmojis, emoji, onSelect = { emoji = it })
        DrawerTextField(
            value = name,
            onValueChange = { name = it.take(50) },
            label = "Reward name...",
            modifier = Modifier.testTag("reward-name"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        DrawerTextField(
            value = description,
            onValueChange = { description = it.take(120) },
            label = "Description (optional)...",
            singleLine = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        DrawerTextField(
            value = cost,
            onValueChange = { cost = it.filter(Char::isDigit).take(6) },
            label = "Coin cost...",
            modifier = Modifier.testTag("reward-cost"),
            prefix = { Text("🪙") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        DrawerActions(
            confirmLabel = if (reward == null) "Create" else "Save",
            enabled = valid,
            onDismiss = onDismiss,
            onConfirm = { onSave(name.trim(), description.trim(), parsedCost ?: 1, emoji) },
        )
    }
}

@Composable
private fun EditorHeader(title: String, showDelete: Boolean, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (showDelete) {
            TextButton(onClick = onDelete) {
                Text("🗑️ Delete", color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TaskTypeSelector(selected: TaskRecurrence, onSelected: (TaskRecurrence) -> Unit) {
    val options = listOf(
        Triple(TaskRecurrence.DAILY, "Daily", HealthRed),
        Triple(TaskRecurrence.WEEKLY, "Weekly", QuestTeal),
        Triple(TaskRecurrence.MONTHLY, "Monthly", MagicPurple),
        Triple(TaskRecurrence.ONCE, "Custom", Color(0xFFA78BFA)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quest Type", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        if (selected == TaskRecurrence.YEARLY) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MagicPurple.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .border(1.dp, MagicPurple.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Yearly (legacy quest)", color = MagicPurple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (recurrence, label, color) ->
                val isSelected = selected == recurrence
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) color.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(10.dp),
                        )
                        .border(
                            1.dp,
                            if (isSelected) color.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp),
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelected(recurrence) },
                        )
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (isSelected) color else Color.White.copy(alpha = 0.38f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyTargetCountPicker(value: Int, onValueChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-monthly-target-count"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Times per Month", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        monthlyTargetCountRange.chunked(4).forEach { counts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                counts.forEach { count ->
                    val selected = count == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) MagicPurple.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(10.dp),
                            )
                            .border(
                                1.dp,
                                if (selected) MagicPurple.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(10.dp),
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onValueChange(count) },
                            )
                            .testTag("task-monthly-target-$count")
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${count}×",
                            color = if (selected) MagicPurple else Color.White.copy(alpha = 0.42f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadlinePicker(value: Int?, onValueChange: (Int?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-deadline"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Deadline (optional)",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                TextButton(
                    onClick = { onValueChange(null) },
                    modifier = Modifier.testTag("task-deadline-clear"),
                ) {
                    Text("Clear", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
                }
            }
        }
        if (value == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onValueChange(DEFAULT_DEADLINE_MINUTES) }
                    .testTag("task-deadline-set")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⏰ Tap to set a deadline",
                    color = MagicPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            TimeDrumPicker(value = value, onValueChange = onValueChange)
        }
    }
}

@Composable
private fun TimeDrumPicker(value: Int, onValueChange: (Int) -> Unit) {
    val hour = value / 60
    val minute = value % 60
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeDrum(
            value = hour,
            cycleSize = 24,
            step = 1,
            testTagPrefix = "task-deadline-hour",
            onValueChange = { onValueChange(it * 60 + minute) },
        )
        Text(
            text = ":",
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TimeDrum(
            value = minute,
            cycleSize = 60,
            step = 5,
            testTagPrefix = "task-deadline-minute",
            onValueChange = { onValueChange(hour * 60 + it) },
        )
    }
}

@Composable
private fun TimeDrum(
    value: Int,
    cycleSize: Int,
    step: Int,
    testTagPrefix: String,
    onValueChange: (Int) -> Unit,
) {
    val previous = ((value - step) % cycleSize + cycleSize) % cycleSize
    val next = (value + step) % cycleSize
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = { onValueChange(previous) },
            modifier = Modifier
                .size(40.dp)
                .testTag("$testTagPrefix-up"),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Decrease time",
                tint = Color.White.copy(alpha = 0.42f),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 48.dp)
                .background(MagicPurple.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                .border(1.dp, MagicPurple.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
                .testTag("$testTagPrefix-value"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(
            onClick = { onValueChange(next) },
            modifier = Modifier
                .size(40.dp)
                .testTag("$testTagPrefix-down"),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Increase time",
                tint = Color.White.copy(alpha = 0.42f),
            )
        }
    }
}

@Composable
private fun WeekDaySelector(selected: Set<Int>, onSelected: (Set<Int>) -> Unit) {
    val presets = listOf(
        "Weekdays" to TaskScheduleRules.DEFAULT_WEEK_DAYS,
        "Weekends" to setOf(0, 6),
        "Every Day" to (0..6).toSet(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Repeat On", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { (label, days) ->
                ScheduleChoice(
                    label = label,
                    selected = selected == days,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(days) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            weekDayLabels.forEachIndexed { day, label ->
                ScheduleChoice(
                    label = label,
                    selected = day in selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(if (day in selected) selected - day else selected + day) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .background(
                if (selected) QuestTeal.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (selected) QuestTeal.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp),
            )
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) QuestTeal else Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BottomDrawer(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxDrawerHeight = maxHeight * 0.9f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDrawerHeight)
                    .clickable(onClick = {})
                    .background(CardBackground, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .imePadding()
                    .testTag("editor-viewport")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DrawerHandle() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun EmojiPicker(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose Icon", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        options.chunked(8).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                if (selected == item) MagicPurple.copy(alpha = 0.30f)
                                else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(10.dp),
                            )
                            .then(
                                if (selected == item) Modifier.border(2.dp, MagicPurple, RoundedCornerShape(10.dp))
                                else Modifier,
                            )
                            .clickable { onSelect(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item, fontSize = 18.sp)
                    }
                }
                repeat(8 - row.size) { Box(modifier = Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun DrawerActions(
    confirmLabel: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Cancel", color = Color.White.copy(alpha = 0.55f))
        }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MagicPurple),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(confirmLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DrawerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = keyboardOptions,
        prefix = prefix,
        suffix = suffix,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MagicPurple,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            cursorColor = MagicPurple,
            focusedLabelColor = MagicPurple,
            unfocusedLabelColor = Color.White.copy(alpha = 0.40f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPrefixColor = CoinGold,
            unfocusedPrefixColor = CoinGold,
            focusedSuffixColor = Color.White.copy(alpha = 0.40f),
            unfocusedSuffixColor = Color.White.copy(alpha = 0.40f),
        ),
    )
}

fun TaskRecurrence.displayName(): String = when (this) {
    TaskRecurrence.ONCE -> "Custom"
    TaskRecurrence.DAILY -> "Daily"
    TaskRecurrence.WEEKLY -> "Weekly"
    TaskRecurrence.MONTHLY -> "Monthly"
    TaskRecurrence.YEARLY -> "Yearly"
}

private fun Int?.normalizeToFiveMinuteStep(): Int? = this
    ?.coerceIn(0, 24 * 60 - 1)
    ?.let { it - it % 5 }
