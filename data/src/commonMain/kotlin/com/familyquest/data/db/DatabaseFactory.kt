package com.familyquest.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

// The legacy filename is part of Android upgrade compatibility.
const val DATABASE_NAME = "family-quest.db"

fun buildFamilyQuestDatabase(
    builder: RoomDatabase.Builder<FamilyQuestDatabase>,
): FamilyQuestDatabase {
    return builder
        .addMigrations(
            FamilyQuestDatabase.MIGRATION_1_2,
            FamilyQuestDatabase.MIGRATION_2_3,
            FamilyQuestDatabase.MIGRATION_3_4,
            FamilyQuestDatabase.MIGRATION_4_5,
            FamilyQuestDatabase.MIGRATION_5_6,
            FamilyQuestDatabase.MIGRATION_6_7,
            FamilyQuestDatabase.MIGRATION_7_8,
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
