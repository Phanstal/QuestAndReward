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
class Migration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyQuestDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationAddsWishGoalsWithoutChangingExistingRows() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL("INSERT INTO profiles VALUES ('profile-1','Family',0,100,0)")
            execSQL(
                "INSERT INTO ledger_entries VALUES " +
                    "('ledger-1','profile-1',120,'INITIAL_BALANCE','profile-1','Welcome balance',100)",
            )
            execSQL("INSERT INTO rewards VALUES ('reward-1','Coffee','',500,NULL,1,101,101,'☕')")
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            FamilyQuestDatabase.MIGRATION_7_8,
        )

        database.query("SELECT COUNT(*) FROM wish_goals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.query("SELECT delta FROM ledger_entries WHERE id = 'ledger-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(120, cursor.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DB = "migration-7-8-test"
    }
}
