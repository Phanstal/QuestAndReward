package com.familyquest.data

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceIdentity(
    private val preferences: PreferencesStore,
) {
    private val clockMutex = Mutex()
    @OptIn(ExperimentalUuidApi::class)
    val id: String = preferences.getString(KEY_DEVICE_ID) ?: Uuid.random().toString().also {
        preferences.putString(KEY_DEVICE_ID, it)
    }

    private var lastTimestamp: Long = preferences.getLong(KEY_LAST_TIMESTAMP, 0L)
    private var counter: Int = preferences.getInt(KEY_COUNTER, 0)

    suspend fun nextClock(now: Long): Pair<Long, Int> = clockMutex.withLock {
        if (now > lastTimestamp) {
            lastTimestamp = now
            counter = 0
        } else {
            counter += 1
        }
        preferences.putLongAndInt(KEY_LAST_TIMESTAMP, lastTimestamp, KEY_COUNTER, counter)
        lastTimestamp to counter
    }

    private companion object {
        const val KEY_DEVICE_ID = "device-id"
        const val KEY_LAST_TIMESTAMP = "last-event-timestamp"
        const val KEY_COUNTER = "event-counter"
    }
}

interface PreferencesStore {
    fun getString(key: String): String?
    fun getLong(key: String, defaultValue: Long): Long
    fun getInt(key: String, defaultValue: Int): Int
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun putLongAndInt(longKey: String, longValue: Long, intKey: String, intValue: Int)
}
