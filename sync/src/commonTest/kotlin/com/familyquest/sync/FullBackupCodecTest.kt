package com.familyquest.sync

import com.familyquest.application.BackupDecodeException
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FullBackupCodecTest {
    private val codec = SnapshotCodec()

    @Test
    fun `round trips every persisted record and ignores optional future fields`() {
        val source = completeArchive()

        val encoded = codec.encode(source).replaceFirst("{", "{\"futureField\":true,")
        val restored = codec.decode(encoded)

        assertEquals(source, restored)
        assertFalse(encoded.contains("\"coins\""))
    }

    @Test
    fun `decodes v2 backups without wish goal deposit fields`() {
        val encodedV2 = codec.encode(
            completeArchive().copy(
                formatVersion = 2,
                wishGoalDeposit = 15,
                wishGoalLastReminderDate = "2026-09-03",
            ),
        )
            .replace("\"wishGoalDeposit\":15,", "")
            .replace("\"wishGoalLastReminderDate\":\"2026-09-03\",", "")

        val restored = codec.decode(encodedV2)

        assertEquals(2, restored.formatVersion)
        assertEquals("reward-1", restored.wishGoalRewardId)
        assertEquals(0, restored.wishGoalDeposit)
        assertEquals(null, restored.wishGoalLastReminderDate)
    }

    @Test
    fun `rejects legacy display snapshots instead of performing a lossy restore`() {
        val exception = assertFailsWith<BackupDecodeException> {
            codec.decode("""{"tasks":[],"coins":0,"totalEarned":0,"completedToday":0,"inventory":[]}""")
        }

        assertEquals(true, exception.unsupportedVersion)
    }

    @Test
    fun `rejects unsupported full backup versions`() {
        val content = codec.encode(completeArchive()).replace(
            "\"formatVersion\":${BackupArchive.CURRENT_FORMAT_VERSION}",
            "\"formatVersion\":99",
        )

        val exception = assertFailsWith<BackupDecodeException> { codec.decode(content) }

        assertEquals(true, exception.unsupportedVersion)
    }

    @Test
    fun `refuses to encode a backup that its decoder would reject for size`() {
        val oversizedPayload = "{\"data\":\"${"x".repeat(8 * 1024 * 1024)}\"}"
        val archive = completeArchive().copy(
            events = listOf(completeArchive().events.single().copy(payload = oversizedPayload)),
        )

        assertFailsWith<IllegalArgumentException> {
            codec.encode(archive)
        }
    }

    private fun completeArchive() = BackupArchive(
        formatVersion = BackupArchive.CURRENT_FORMAT_VERSION,
        exportedAt = 1_800_000_000_000,
        selectedProfileId = "profile-1",
        wishGoalRewardId = "reward-1",
        profiles = listOf(BackupProfileRecord("profile-1", "家庭", 0, 100, false)),
        tasks = listOf(
            BackupTaskRecord(
                id = "task-1",
                title = "阅读",
                notes = "notes",
                category = "DAILY",
                rewardPoints = 15,
                assigneeId = "profile-1",
                isCompleted = false,
                createdAt = 101,
                updatedAt = 102,
                completedAt = null,
                deletedAt = null,
                recurrence = "MONTHLY",
                emoji = "📚",
                deadlineMinutes = null,
                weekDaysMask = 0,
                monthDay = 15,
                monthlyTargetCount = 8,
            ),
        ),
        rewards = listOf(
            BackupRewardRecord("reward-1", "电影", "好片", 150, null, true, 103, 104, "🎬"),
        ),
        completions = listOf(
            BackupCompletionRecord(
                "completion-1", "task-1", "monthly:2026-09#2", "profile-1", 15, 105, null,
                "阅读", "📚", "MONTHLY",
            ),
        ),
        ledgerEntries = listOf(
            BackupLedgerRecord("ledger-1", "profile-1", 15, "TASK_COMPLETED", "completion-1", "完成 阅读", 105),
        ),
        redemptions = listOf(
            BackupRedemptionRecord(
                "redemption-1", "reward-1", "电影", "profile-1", 150, "ACCEPTED", 106,
                "🎬", "OWNED", null,
            ),
        ),
        events = listOf(
            BackupEventRecord(
                "event-1", 1, "device-1", "trace-1", "command-1", "profile-1", "TASK", "task-1",
                "TASK_COMPLETED", 105, 1, "{}", false,
            ),
        ),
        processedCommands = listOf(
            BackupProcessedCommandRecord("command-1", "COMPLETE_TASK", "trace-1", "SUCCESS", null, 105),
        ),
    )
}
