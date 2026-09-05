package com.familyquest.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyQuestDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun legacyRedemptionsDoNotBecomeOwnedInventory() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO profiles VALUES ('profile-1','家庭',0,100,0)")
            execSQL("INSERT INTO rewards VALUES ('reward-1','旧奖励','说明',50,NULL,1,100,100,'🎁')")
            execSQL(
                "INSERT INTO redemptions VALUES " +
                    "('redemption-1','reward-1','旧奖励','profile-1',50,'ACCEPTED',101)",
            )
            execSQL(
                "INSERT INTO events VALUES " +
                    "('event-1',1,'device-1','trace-1','key-1','profile-1','REDEMPTION'," +
                    "'redemption-1','REWARD_REDEEMED',102,1,'{\"stable\":true}',0)",
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            FamilyQuestDatabase.MIGRATION_4_5,
        )

        database.query(
            "SELECT emojiSnapshot, inventoryState, soldAt FROM redemptions WHERE id = 'redemption-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("🎁", cursor.getString(0))
            assertEquals("LEGACY_CONSUMED", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
        }
        assertEquals(
            0,
            database.query(
                "SELECT COUNT(*) FROM redemptions WHERE profileId = 'profile-1' AND inventoryState = 'OWNED'",
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) },
        )
        assertEquals(
            "{\"stable\":true}",
            database.query("SELECT payload FROM events WHERE eventId = 'event-1'").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            },
        )
        database.close()
    }

    private companion object {
        const val TEST_DB = "migration-4-5-test"
    }
}
