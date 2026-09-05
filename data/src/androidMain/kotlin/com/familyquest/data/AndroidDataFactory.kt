package com.familyquest.data

import android.content.Context
import androidx.room.Room
import com.familyquest.data.db.DATABASE_NAME
import com.familyquest.data.db.FamilyQuestDatabase
import com.familyquest.data.db.buildFamilyQuestDatabase
import com.familyquest.domain.event.EventPayloadCodec

fun createAndroidRepository(
    context: Context,
    eventPayloadCodec: EventPayloadCodec,
): RoomFamilyQuestRepository {
    val applicationContext = context.applicationContext
    val database = buildFamilyQuestDatabase(
        Room.databaseBuilder<FamilyQuestDatabase>(
            context = applicationContext,
            name = applicationContext.getDatabasePath(DATABASE_NAME).absolutePath,
        ),
    )
    return RoomFamilyQuestRepository(
        eventPayloadCodec = eventPayloadCodec,
        database = database,
        preferences = AndroidPreferencesStore(applicationContext, "family-quest-preferences"),
        deviceIdentity = DeviceIdentity(
            AndroidPreferencesStore(applicationContext, "family-quest-device"),
        ),
    )
}
