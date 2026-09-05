package com.familyquest.application

data class BackupSnapshot(
    val tasks: List<BackupTask>,
    val coins: Int,
    val totalEarned: Int,
    val completedToday: Int,
    val inventory: List<BackupInventoryItem>,
)

data class BackupTask(
    val id: String,
    val title: String,
    val type: String,
    val coins: Int,
    val completed: Boolean,
    val emoji: String,
    val deadlineMinutes: Int? = null,
    val weekDays: List<Int> = emptyList(),
    val monthDay: Int? = null,
    val completions: Int = 0,
)

data class BackupInventoryItem(
    val id: String,
    val title: String,
    val emoji: String,
    val cost: Int,
)

fun interface SnapshotExporter {
    fun export(snapshot: BackupSnapshot): String
}
