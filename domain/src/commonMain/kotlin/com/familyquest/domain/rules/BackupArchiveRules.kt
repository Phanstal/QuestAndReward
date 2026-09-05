package com.familyquest.domain.rules

import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.LedgerReason
import com.familyquest.domain.model.RejectionReason
import com.familyquest.domain.model.TaskRecurrence

object BackupArchiveRules {
    fun isValid(archive: BackupArchive): Boolean {
        if (archive.formatVersion !in 2..BackupArchive.CURRENT_FORMAT_VERSION) return false
        if (archive.profiles.isEmpty() || archive.profiles.none { !it.archived }) return false
        if (!archive.profiles.hasUniqueIds { it.id }) return false
        if (!archive.tasks.hasUniqueIds { it.id }) return false
        if (!archive.rewards.hasUniqueIds { it.id }) return false
        if (!archive.completions.hasUniqueIds { it.id }) return false
        if (!archive.ledgerEntries.hasUniqueIds { it.id }) return false
        if (!archive.redemptions.hasUniqueIds { it.id }) return false
        if (!archive.events.hasUniqueIds { it.eventId }) return false
        if (!archive.processedCommands.hasUniqueIds { it.idempotencyKey }) return false

        val profiles = archive.profiles.associateBy { it.id }
        val tasks = archive.tasks.associateBy { it.id }
        val rewards = archive.rewards.associateBy { it.id }
        val completions = archive.completions.associateBy { it.id }
        val redemptions = archive.redemptions.associateBy { it.id }
        val activeProfileIds = archive.profiles.filterNot { it.archived }.mapTo(mutableSetOf()) { it.id }
        if (archive.selectedProfileId != null && archive.selectedProfileId !in activeProfileIds) return false
        if (archive.wishGoalRewardId != null && rewards[archive.wishGoalRewardId]?.active != true) return false
        if (archive.wishGoalDeposit < 0 || (archive.wishGoalRewardId == null && archive.wishGoalDeposit != 0)) return false
        if (archive.wishGoalRewardId == null && archive.wishGoalLastReminderDate != null) return false

        val recurrenceNames = enumValues<TaskRecurrence>().mapTo(mutableSetOf()) { it.name }
        if (archive.profiles.any { it.id.isBlank() || it.name.isBlank() || it.accentIndex !in 0..100 }) return false
        if (archive.tasks.any { task ->
                task.id.isBlank() || task.title.isBlank() || task.rewardPoints < 1 || task.assigneeId !in profiles ||
                    task.recurrence !in recurrenceNames || task.emoji.isBlank() ||
                    task.deadlineMinutes?.let { it !in 0 until MINUTES_PER_DAY } == true ||
                    task.weekDaysMask and VALID_WEEK_DAYS_MASK.inv() != 0 ||
                    task.monthlyTargetCount !in
                    TaskScheduleRules.MIN_MONTHLY_TARGET_COUNT..TaskScheduleRules.MAX_MONTHLY_TARGET_COUNT
            }
        ) return false
        if (archive.rewards.any {
                it.id.isBlank() || it.name.isBlank() || it.cost < 1 || it.stock?.let { stock -> stock < 0 } == true ||
                    it.emoji.isBlank()
            }
        ) return false

        val occurrenceKeys = mutableSetOf<Pair<String, String>>()
        if (archive.completions.any { completion ->
                completion.id.isBlank() || completion.taskId !in tasks || completion.profileId !in profiles ||
                    completion.occurrenceKey.isBlank() || completion.rewardSnapshot < 1 ||
                    completion.recurrenceSnapshot !in recurrenceNames ||
                    !occurrenceKeys.add(completion.taskId to completion.occurrenceKey)
            }
        ) return false
        val ledgerReasons = enumValues<LedgerReason>().associateBy { it.name }
        if (archive.ledgerEntries.any { entry ->
                val reason = ledgerReasons[entry.reason]
                entry.id.isBlank() || entry.profileId !in profiles || reason == null || entry.referenceId.isBlank() ||
                    when (reason) {
                        LedgerReason.INITIAL_BALANCE,
                        LedgerReason.WISH_GOAL_DEPOSITED,
                        LedgerReason.WISH_GOAL_CLEARED,
                        LedgerReason.WISH_GOAL_REDEEM_BONUS,
                        -> false
                        LedgerReason.TASK_COMPLETED,
                        LedgerReason.TASK_REOPENED,
                        -> entry.referenceId !in completions

                        LedgerReason.REWARD_REDEEMED,
                        LedgerReason.INVENTORY_ITEM_SOLD,
                        -> entry.referenceId !in redemptions

                        LedgerReason.DATA_RESET,
                        LedgerReason.EXPERIENCE_RESET,
                        -> false
                    }
            }
        ) return false
        if (archive.redemptions.any {
                it.id.isBlank() || it.rewardId !in rewards || it.profileId !in profiles || it.costSnapshot < 1 ||
                    it.status !in VALID_REDEMPTION_STATUSES || it.inventoryState !in VALID_INVENTORY_STATES
            }
        ) return false
        val eventIdempotencyKeys = archive.events.mapNotNull { it.idempotencyKey }
        if (eventIdempotencyKeys.size != eventIdempotencyKeys.toSet().size) return false
        if (archive.events.any {
                it.eventId.isBlank() || it.schemaVersion < 1 || it.deviceId.isBlank() || it.aggregateType.isBlank() ||
                    it.aggregateId.isBlank() || it.eventType.isBlank() || it.logicalCounter < 0 || it.payload.isBlank()
            }
        ) return false
        val rejectionReasons = enumValues<RejectionReason>().mapTo(mutableSetOf()) { it.name }
        return archive.processedCommands.none { command ->
            command.idempotencyKey.isBlank() || command.operation.isBlank() || command.traceId.isBlank() ||
                when (command.result) {
                    RESULT_SUCCESS, RESULT_NO_CHANGE -> command.rejectionReason != null
                    RESULT_REJECTED -> command.rejectionReason !in rejectionReasons
                    else -> true
                }
        }
    }

    private inline fun <T> List<T>.hasUniqueIds(id: (T) -> String): Boolean {
        val ids = map(id)
        return ids.none(String::isBlank) && ids.size == ids.toSet().size
    }

    private const val MINUTES_PER_DAY = 24 * 60
    private const val VALID_WEEK_DAYS_MASK = 0b111_1111
    private const val RESULT_SUCCESS = "SUCCESS"
    private const val RESULT_NO_CHANGE = "NO_CHANGE"
    private const val RESULT_REJECTED = "REJECTED"
    private val VALID_REDEMPTION_STATUSES = setOf("ACCEPTED", "REJECTED_INSUFFICIENT_BALANCE", "RESET")
    private val VALID_INVENTORY_STATES = setOf("LEGACY_CONSUMED", "OWNED", "SOLD", "USED", "RESET")
}
