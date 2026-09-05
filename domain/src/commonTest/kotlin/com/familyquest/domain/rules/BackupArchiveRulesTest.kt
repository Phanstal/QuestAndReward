package com.familyquest.domain.rules

import com.familyquest.domain.model.BackupArchive
import com.familyquest.domain.model.BackupCompletionRecord
import com.familyquest.domain.model.BackupEventRecord
import com.familyquest.domain.model.BackupLedgerRecord
import com.familyquest.domain.model.BackupProcessedCommandRecord
import com.familyquest.domain.model.BackupProfileRecord
import com.familyquest.domain.model.BackupRedemptionRecord
import com.familyquest.domain.model.BackupRewardRecord
import com.familyquest.domain.model.BackupTaskRecord
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupArchiveRulesTest {
    @Test
    fun `accepts persisted ledger redemption and processed command values`() {
        val source = validArchive()

        assertTrue(BackupArchiveRules.isValid(source))
        listOf("ACCEPTED", "REJECTED_INSUFFICIENT_BALANCE", "RESET").forEach { status ->
            assertTrue(
                BackupArchiveRules.isValid(
                    source.copy(redemptions = source.redemptions.map { it.copy(status = status) }),
                ),
            )
        }
        listOf("LEGACY_CONSUMED", "OWNED", "SOLD", "USED", "RESET").forEach { state ->
            assertTrue(
                BackupArchiveRules.isValid(
                    source.copy(redemptions = source.redemptions.map { it.copy(inventoryState = state) }),
                ),
            )
        }
        assertTrue(
            BackupArchiveRules.isValid(
                source.copy(
                    processedCommands = source.processedCommands.map {
                        it.copy(result = "NO_CHANGE", rejectionReason = null)
                    },
                ),
            ),
        )
        assertTrue(
            BackupArchiveRules.isValid(
                source.copy(
                    processedCommands = source.processedCommands.map {
                        it.copy(result = "REJECTED", rejectionReason = "BACKUP_INVALID")
                    },
                ),
            ),
        )
    }

    @Test
    fun `rejects ledger references that do not resolve to their aggregate`() {
        val source = validArchive()

        listOf("TASK_COMPLETED", "TASK_REOPENED").forEach { reason ->
            assertFalse(
                BackupArchiveRules.isValid(
                    source.copy(
                        ledgerEntries = listOf(
                            source.ledgerEntries[0].copy(
                                reason = reason,
                                referenceId = "missing-completion",
                            ),
                        ),
                    ),
                ),
            )
        }
        listOf("REWARD_REDEEMED", "INVENTORY_ITEM_SOLD").forEach { reason ->
            assertFalse(
                BackupArchiveRules.isValid(
                    source.copy(
                        ledgerEntries = listOf(
                            source.ledgerEntries[0].copy(
                                reason = reason,
                                referenceId = "missing-redemption",
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `rejects unknown redemption and processed command states`() {
        val source = validArchive()

        assertFalse(
            BackupArchiveRules.isValid(
                source.copy(redemptions = source.redemptions.map { it.copy(status = "UNKNOWN") }),
            ),
        )
        assertFalse(
            BackupArchiveRules.isValid(
                source.copy(redemptions = source.redemptions.map { it.copy(inventoryState = "UNKNOWN") }),
            ),
        )
        assertFalse(
            BackupArchiveRules.isValid(
                source.copy(
                    processedCommands = source.processedCommands.map {
                        it.copy(result = "SUCCESS", rejectionReason = "INVALID_INPUT")
                    },
                ),
            ),
        )
        assertFalse(
            BackupArchiveRules.isValid(
                source.copy(
                    processedCommands = source.processedCommands.map {
                        it.copy(result = "REJECTED", rejectionReason = "UNKNOWN")
                    },
                ),
            ),
        )
    }

    private fun validArchive() = BackupArchive(
        formatVersion = BackupArchive.CURRENT_FORMAT_VERSION,
        exportedAt = 100,
        selectedProfileId = "profile-1",
        wishGoalRewardId = "reward-1",
        profiles = listOf(BackupProfileRecord("profile-1", "家庭", 0, 1, false)),
        tasks = listOf(
            BackupTaskRecord(
                id = "task-1",
                title = "阅读",
                notes = "",
                category = "DAILY",
                rewardPoints = 20,
                assigneeId = "profile-1",
                isCompleted = false,
                createdAt = 2,
                updatedAt = 2,
                completedAt = null,
                deletedAt = null,
                recurrence = "DAILY",
                emoji = "📚",
                deadlineMinutes = null,
                weekDaysMask = 0,
                monthDay = null,
                monthlyTargetCount = 1,
            ),
        ),
        rewards = listOf(
            BackupRewardRecord("reward-1", "电影", "", 50, null, true, 3, 3, "🎬"),
        ),
        completions = listOf(
            BackupCompletionRecord(
                "completion-1",
                "task-1",
                "daily:2026-09-02",
                "profile-1",
                20,
                4,
                null,
                "阅读",
                "📚",
                "DAILY",
            ),
        ),
        ledgerEntries = listOf(
            BackupLedgerRecord(
                "ledger-1",
                "profile-1",
                20,
                "TASK_COMPLETED",
                "completion-1",
                "阅读",
                4,
            ),
        ),
        redemptions = listOf(
            BackupRedemptionRecord(
                "redemption-1",
                "reward-1",
                "电影",
                "profile-1",
                50,
                "ACCEPTED",
                5,
                "🎬",
                "OWNED",
                null,
            ),
        ),
        events = listOf(
            BackupEventRecord(
                "event-1",
                1,
                "device-1",
                "trace-1",
                "command-1",
                "profile-1",
                "TASK",
                "task-1",
                "TASK_COMPLETED",
                4,
                1,
                "{}",
                false,
            ),
        ),
        processedCommands = listOf(
            BackupProcessedCommandRecord("command-1", "COMPLETE_TASK", "trace-1", "SUCCESS", null, 4),
        ),
    )
}
