package com.familyquest.data

import android.content.Context

class AndroidPreferencesStore(
    context: Context,
    name: String,
) : PreferencesStore {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getLong(key: String, defaultValue: Long): Long =
        preferences.getLong(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int =
        preferences.getInt(key, defaultValue)

    override fun putString(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Unable to persist preference"
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Unable to remove preference"
        }
    }

    override fun putLongAndInt(longKey: String, longValue: Long, intKey: String, intValue: Int) {
        check(
            preferences.edit()
                .putLong(longKey, longValue)
                .putInt(intKey, intValue)
                .commit(),
        ) { "Unable to persist event clock" }
    }
}

