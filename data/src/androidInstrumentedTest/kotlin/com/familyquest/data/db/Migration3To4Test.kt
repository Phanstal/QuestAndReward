package com.familyquest.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyQuestDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationPreservesHistoryAndEventsWhileMergingProfiles() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertV3Data(this)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            FamilyQuestDatabase.MIGRATION_3_4,
        )

        database.query(
            "SELECT assigneeId, recurrence, emoji FROM tasks WHERE id = 'task-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("profile-1", cursor.getString(0))
            assertEquals("ONCE", cursor.getString(1))
            assertEquals("✅", cursor.getString(2))
        }
        database.query(
            "SELECT profileId, titleSnapshot, emojiSnapshot, recurrenceSnapshot " +
                "FROM completions WHERE id = 'completion-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("profile-1", cursor.getString(0))
            assertEquals("旧任务", cursor.getString(1))
            assertEquals("✅", cursor.getString(2))
            assertEquals("ONCE", cursor.getString(3))
        }
        assertEquals("profile-1", database.singleString("SELECT profileId FROM ledger_entries WHERE id = 'ledger-1'"))
        assertEquals("profile-1", database.singleString("SELECT profileId FROM redemptions WHERE id = 'redemption-1'"))
        assertEquals(1, database.singleInt("SELECT archived FROM profiles WHERE id = 'profile-2'"))
        assertEquals("profile-2", database.singleString("SELECT actorId FROM events WHERE eventId = 'event-1'"))
        assertEquals("{\"stable\":true}", database.singleString("SELECT payload FROM events WHERE eventId = 'event-1'"))
        database.close()
    }

    private fun insertV3Data(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO profiles VALUES ('profile-1','家庭一',0,100,0)")
        database.execSQL("INSERT INTO profiles VALUES ('profile-2','家庭二',1,200,0)")
        database.execSQL(
            "INSERT INTO tasks VALUES " +
                "('task-1','旧任务','旧备注','HOME',20,'profile-2',1,300,301,301,NULL)",
        )
        database.execSQL(
            "INSERT INTO rewards VALUES ('reward-1','旧奖励','说明',10,NULL,1,300,300)",
        )
        database.execSQL(
            "INSERT INTO completions VALUES " +
                "('completion-1','task-1','once','profile-2',20,301,NULL)",
        )
        database.execSQL(
            "INSERT INTO ledger_entries VALUES " +
                "('ledger-1','profile-2',20,'TASK_COMPLETED','completion-1','旧任务',301)",
        )
        database.execSQL(
            "INSERT INTO redemptions VALUES " +
                "('redemption-1','reward-1','旧奖励','profile-2',10,'ACCEPTED',302)",
        )
        database.execSQL(
            "INSERT INTO events VALUES " +
                "('event-1',1,'device-1','trace-1','key-1','profile-2','TASK','task-1'," +
                "'TASK_COMPLETED',303,1,'{\"stable\":true}',0)",
        )
    }

    private fun SupportSQLiteDatabase.singleString(query: String): String =
        query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.singleInt(query: String): Int =
        query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-3-4-test"
    }
}
