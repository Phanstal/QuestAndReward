@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.familyquest.data

import platform.Foundation.NSUserDefaults

class IosPreferencesStore(
    suiteName: String,
) : PreferencesStore {
    private val preferences = NSUserDefaults(suiteName = suiteName)

    override fun getString(key: String): String? = preferences.stringForKey(key)

    override fun getLong(key: String, defaultValue: Long): Long {
        return if (preferences.objectForKey(key) == null) defaultValue else preferences.integerForKey(key)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return if (preferences.objectForKey(key) == null) defaultValue else preferences.integerForKey(key).toInt()
    }

    override fun putString(key: String, value: String) {
        preferences.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        preferences.removeObjectForKey(key)
    }

    override fun putLongAndInt(longKey: String, longValue: Long, intKey: String, intValue: Int) {
        preferences.setInteger(longValue, forKey = longKey)
        preferences.setInteger(intValue.toLong(), forKey = intKey)
    }
}
