package com.familyquest.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyQuestDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationDefaultsMonthlyTargetWithoutChangingLegacyScheduleOrHistory() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("INSERT INTO profiles VALUES ('profile-1','家庭',0,100,0)")
            execSQL(
                "INSERT INTO tasks " +
                    "(id,title,notes,category,rewardPoints,assigneeId,isCompleted,createdAt,updatedAt," +
                    "completedAt,deletedAt,recurrence,emoji,deadlineMinutes,weekDaysMask,monthDay) VALUES " +
                    "('monthly','月度任务','','DAILY',20,'profile-1',0,100,100,NULL,NULL," +
                    "'MONTHLY','✅',NULL,0,31)",
            )
            execSQL(
                "INSERT INTO completions VALUES " +
                    "('completion-1','monthly','monthly:2026-08','profile-1',20,200,NULL," +
                    "'月度任务','✅','MONTHLY')",
            )
            execSQL(
                "INSERT INTO ledger_entries VALUES " +
                    "('ledger-1','profile-1',20,'TASK_COMPLETED','completion-1','月度任务',200)",
            )
            execSQL(
                "INSERT INTO events VALUES " +
                    "('event-1',1,'device-1','trace-1','key-1','profile-1','TASK','monthly'," +
                    "'TASK_COMPLETED',201,1,'{\"stable\":true}',0)",
            )
            execSQL(
                "INSERT INTO processed_commands VALUES " +
                    "('key-1','COMPLETE_TASK','trace-1','SUCCESS',NULL,201)",
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            FamilyQuestDatabase.MIGRATION_6_7,
        )

        database.query(
            "SELECT monthDay, monthlyTargetCount FROM tasks WHERE id = 'monthly'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(31, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
        database.query(
            "SELECT occurrenceKey, rewardSnapshot FROM completions WHERE id = 'completion-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("monthly:2026-08", cursor.getString(0))
            assertEquals(20, cursor.getInt(1))
        }
        database.query("SELECT delta, referenceId FROM ledger_entries WHERE id = 'ledger-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(20, cursor.getInt(0))
            assertEquals("completion-1", cursor.getString(1))
        }
        database.query("SELECT payload FROM events WHERE eventId = 'event-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"stable\":true}", cursor.getString(0))
        }
        database.query(
            "SELECT operation, result FROM processed_commands WHERE idempotencyKey = 'key-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("COMPLETE_TASK", cursor.getString(0))
            assertEquals("SUCCESS", cursor.getString(1))
        }
        database.close()
    }

    private companion object {
        const val TEST_DB = "migration-6-7-test"
    }
}
