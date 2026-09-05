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
class Migration5To6Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyQuestDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationAddsSchedulesWithoutChangingHistoryOrEvents() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO profiles VALUES ('profile-1','家庭',0,100,0)")
            insertTask("daily", "DAILY")
            insertTask("weekly", "WEEKLY")
            insertTask("monthly", "MONTHLY")
            insertTask("once", "ONCE")
            insertTask("yearly", "YEARLY")
            execSQL(
                "INSERT INTO completions VALUES " +
                    "('completion-yearly','yearly','yearly:2026','profile-1',500,200,NULL," +
                    "'年度任务','🎯','YEARLY')",
            )
            execSQL(
                "INSERT INTO events VALUES " +
                    "('event-1',1,'device-1','trace-1','key-1','profile-1','TASK','yearly'," +
                    "'TASK_COMPLETED',201,1,'{\"stable\":true}',0)",
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            FamilyQuestDatabase.MIGRATION_5_6,
        )

        database.query(
            "SELECT recurrence, deadlineMinutes, weekDaysMask, monthDay FROM tasks ORDER BY id",
        ).use { cursor ->
            val schedules = buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(0),
                        Triple(
                            if (cursor.isNull(1)) null else cursor.getInt(1),
                            cursor.getInt(2),
                            if (cursor.isNull(3)) null else cursor.getInt(3),
                        ),
                    )
                }
            }
            assertEquals(Triple<Int?, Int, Int?>(null, 0, null), schedules["DAILY"])
            assertEquals(Triple<Int?, Int, Int?>(null, 0, null), schedules["WEEKLY"])
            assertEquals(Triple<Int?, Int, Int?>(null, 0, null), schedules["MONTHLY"])
            assertEquals(Triple<Int?, Int, Int?>(null, 0, null), schedules["ONCE"])
            assertEquals(Triple<Int?, Int, Int?>(null, 0, null), schedules["YEARLY"])
        }
        database.query(
            "SELECT occurrenceKey, recurrenceSnapshot FROM completions WHERE id = 'completion-yearly'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("yearly:2026", cursor.getString(0))
            assertEquals("YEARLY", cursor.getString(1))
        }
        database.query("SELECT payload FROM events WHERE eventId = 'event-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"stable\":true}", cursor.getString(0))
        }
        database.close()
    }

    @Test
    fun versionFourDatabaseMigratesThroughInventoryAndScheduleSchemas() {
        helper.createDatabase(CHAIN_TEST_DB, 4).apply {
            execSQL("INSERT INTO profiles VALUES ('profile-1','家庭',0,100,0)")
            insertTask("weekly", "WEEKLY")
            execSQL("INSERT INTO rewards VALUES ('reward-1','旧奖励','说明',50,NULL,1,100,100,'🎁')")
            execSQL(
                "INSERT INTO redemptions VALUES " +
                    "('redemption-1','reward-1','旧奖励','profile-1',50,'ACCEPTED',101)",
            )
            execSQL(
                "INSERT INTO events VALUES " +
                    "('event-1',1,'device-1','trace-1','key-1','profile-1','TASK','weekly'," +
                    "'TASK_UPDATED',102,1,'{\"stable\":true}',0)",
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            CHAIN_TEST_DB,
            6,
            true,
            FamilyQuestDatabase.MIGRATION_4_5,
            FamilyQuestDatabase.MIGRATION_5_6,
        )

        database.query(
            "SELECT deadlineMinutes, weekDaysMask, monthDay FROM tasks WHERE id = 'weekly'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(0, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
        }
        database.query(
            "SELECT emojiSnapshot, inventoryState, soldAt FROM redemptions WHERE id = 'redemption-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("🎁", cursor.getString(0))
            assertEquals("LEGACY_CONSUMED", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
        database.query("SELECT payload FROM events WHERE eventId = 'event-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"stable\":true}", cursor.getString(0))
        }
        database.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTask(id: String, recurrence: String) {
        execSQL(
            "INSERT INTO tasks " +
                "(id,title,notes,category,rewardPoints,assigneeId,isCompleted,createdAt,updatedAt," +
                "completedAt,deletedAt,recurrence,emoji) VALUES " +
                "('$id','$id','','DAILY',20,'profile-1',0,100,100,NULL,NULL,'$recurrence','✅')",
        )
    }

    private companion object {
        const val TEST_DB = "migration-5-6-test"
        const val CHAIN_TEST_DB = "migration-4-6-test"
    }
}
