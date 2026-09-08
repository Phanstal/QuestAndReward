@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.familyquest.data

import androidx.room.Room
import com.familyquest.data.db.FamilyQuestDatabase
import com.familyquest.data.db.buildFamilyQuestDatabase
import com.familyquest.domain.event.EventPayload
import com.familyquest.domain.event.EventPayloadCodec
import com.familyquest.domain.model.CommandMetadata
import com.familyquest.domain.model.OperationResult
import com.familyquest.domain.model.RejectionReason
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

class IosBackupTransactionTest {
    @Test
    fun invalidImportAndTransactionConflictPreserveEntireDatabase() = runTest {
        val testId = "quest-backup-test-${NSUUID().UUIDString}"
        val path = NSTemporaryDirectory() + "$testId.db"
        val preferencesSuite = "$testId.preferences"
        val deviceSuite = "$testId.device"
        val database = buildFamilyQuestDatabase(Room.databaseBuilder<FamilyQuestDatabase>(name = path))
        val repository = RoomFamilyQuestRepository(
            eventPayloadCodec = object : EventPayloadCodec {
                override fun encodePayload(payload: EventPayload): String = "{}"
                override fun decodePayload(content: String): EventPayload {
                    require(content == "{}")
                    return emptyMap()
                }
            },
            database = database,
            preferences = IosPreferencesStore(preferencesSuite),
            deviceIdentity = DeviceIdentity(IosPreferencesStore(deviceSuite)),
        )
        var command = 0
        fun metadata(): CommandMetadata {
            command += 1
            return CommandMetadata("$testId-$command", "$testId-trace-$command")
        }
        try {
            assertEquals(OperationResult.Success, repository.ensureSeedData(metadata()))
            val archive = repository.backupArchive(exportedAt = 1234)
            assertEquals(OperationResult.Success, repository.addReward("Keep this reward", "", 10, null, "🎁", metadata()))
            val liveArchive = repository.backupArchive(exportedAt = 1234)

            assertEquals(
                OperationResult.Rejected(RejectionReason.BACKUP_INVALID),
                repository.restoreBackup(archive.copy(selectedProfileId = "missing-profile"), metadata()),
            )
            assertEquals(liveArchive, repository.backupArchive(exportedAt = 1234))

            // Conflict occurs when the restore event is inserted after replacement writes.
            val restoreMetadata = metadata()
            val conflictingArchive = archive.copy(events = archive.events.mapIndexed { index, event ->
                if (index == 0) event.copy(idempotencyKey = restoreMetadata.idempotencyKey) else event
            })
            assertEquals(
                OperationResult.Rejected(RejectionReason.BACKUP_RESTORE_FAILED),
                repository.restoreBackup(conflictingArchive, restoreMetadata),
            )
            assertEquals(liveArchive, repository.backupArchive(exportedAt = 1234))
            assertEquals(liveArchive.selectedProfileId, repository.selectedProfileId.value)
        } finally {
            database.close()
            NSUserDefaults(suiteName = preferencesSuite).removePersistentDomainForName(preferencesSuite)
            NSUserDefaults(suiteName = deviceSuite).removePersistentDomainForName(deviceSuite)
            listOf(path, "$path-wal", "$path-shm").forEach { testFile ->
                NSFileManager.defaultManager.removeItemAtPath(testFile, error = null)
            }
        }
    }
}
