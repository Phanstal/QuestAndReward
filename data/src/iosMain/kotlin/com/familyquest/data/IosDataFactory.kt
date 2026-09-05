@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.familyquest.data

import androidx.room.Room
import com.familyquest.data.db.DATABASE_NAME
import com.familyquest.data.db.FamilyQuestDatabase
import com.familyquest.data.db.buildFamilyQuestDatabase
import com.familyquest.domain.event.EventPayloadCodec
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun createIosRepository(
    eventPayloadCodec: EventPayloadCodec,
): RoomFamilyQuestRepository {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Documents directory is unavailable")
    val databasePath = documents.URLByAppendingPathComponent(DATABASE_NAME)?.path
        ?: error("Database path is unavailable")
    val database = buildFamilyQuestDatabase(
        Room.databaseBuilder<FamilyQuestDatabase>(name = databasePath),
    )
    return RoomFamilyQuestRepository(
        eventPayloadCodec = eventPayloadCodec,
        database = database,
        preferences = IosPreferencesStore("family-quest-preferences"),
        deviceIdentity = DeviceIdentity(IosPreferencesStore("family-quest-device")),
    )
}
