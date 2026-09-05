package com.familyquest.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        ProfileEntity::class,
        TaskEntity::class,
        RewardEntity::class,
        CompletionEntity::class,
        LedgerEntryEntity::class,
        RedemptionEntity::class,
        EventEntity::class,
        ProcessedCommandEntity::class,
        WishGoalEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@ConstructedBy(FamilyQuestDatabaseConstructor::class)
abstract class FamilyQuestDatabase : RoomDatabase() {
    abstract fun dao(): FamilyQuestDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL("ALTER TABLE events ADD COLUMN traceId TEXT")
                database.execSQL("ALTER TABLE events ADD COLUMN idempotencyKey TEXT")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_events_idempotencyKey " +
                        "ON events(idempotencyKey)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS processed_commands (" +
                        "idempotencyKey TEXT NOT NULL, " +
                        "operation TEXT NOT NULL, " +
                        "traceId TEXT NOT NULL, " +
                        "result TEXT NOT NULL, " +
                        "rejectionReason TEXT, " +
                        "processedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(idempotencyKey))",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL(
                    "ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'DAILY'",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'ONCE'")
                database.execSQL("ALTER TABLE tasks ADD COLUMN emoji TEXT NOT NULL DEFAULT '✅'")
                database.execSQL("ALTER TABLE rewards ADD COLUMN emoji TEXT NOT NULL DEFAULT '🎁'")
                database.execSQL("ALTER TABLE completions ADD COLUMN titleSnapshot TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE completions ADD COLUMN emojiSnapshot TEXT NOT NULL DEFAULT '✅'")
                database.execSQL("ALTER TABLE completions ADD COLUMN recurrenceSnapshot TEXT NOT NULL DEFAULT 'ONCE'")
                database.execSQL(
                    "UPDATE completions SET titleSnapshot = COALESCE(" +
                        "(SELECT title FROM tasks WHERE tasks.id = completions.taskId), '')",
                )
                val canonicalProfile = "(SELECT id FROM profiles WHERE archived = 0 ORDER BY createdAt, id LIMIT 1)"
                database.execSQL(
                    "UPDATE tasks SET assigneeId = $canonicalProfile " +
                        "WHERE EXISTS (SELECT 1 FROM profiles WHERE archived = 0)",
                )
                database.execSQL(
                    "UPDATE completions SET profileId = $canonicalProfile " +
                        "WHERE EXISTS (SELECT 1 FROM profiles WHERE archived = 0)",
                )
                database.execSQL(
                    "UPDATE ledger_entries SET profileId = $canonicalProfile " +
                        "WHERE EXISTS (SELECT 1 FROM profiles WHERE archived = 0)",
                )
                database.execSQL(
                    "UPDATE redemptions SET profileId = $canonicalProfile " +
                        "WHERE EXISTS (SELECT 1 FROM profiles WHERE archived = 0)",
                )
                database.execSQL(
                    "UPDATE profiles SET archived = 1 WHERE archived = 0 AND id <> $canonicalProfile",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL(
                    "ALTER TABLE redemptions ADD COLUMN emojiSnapshot TEXT NOT NULL DEFAULT '🎁'",
                )
                database.execSQL(
                    "ALTER TABLE redemptions ADD COLUMN inventoryState TEXT NOT NULL DEFAULT 'LEGACY_CONSUMED'",
                )
                database.execSQL("ALTER TABLE redemptions ADD COLUMN soldAt INTEGER")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_redemptions_profileId_inventoryState " +
                        "ON redemptions(profileId, inventoryState)",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN deadlineMinutes INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN weekDaysMask INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tasks ADD COLUMN monthDay INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL(
                    "ALTER TABLE tasks ADD COLUMN monthlyTargetCount INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SQLiteConnection) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS wish_goals (" +
                        "profileId TEXT NOT NULL, " +
                        "rewardId TEXT NOT NULL, " +
                        "deposit INTEGER NOT NULL, " +
                        "lastReminderDate TEXT, " +
                        "PRIMARY KEY(profileId))",
                )
            }
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object FamilyQuestDatabaseConstructor : RoomDatabaseConstructor<FamilyQuestDatabase> {
    override fun initialize(): FamilyQuestDatabase
}
