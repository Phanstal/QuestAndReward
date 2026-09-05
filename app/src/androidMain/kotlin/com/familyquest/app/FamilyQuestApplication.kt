package com.familyquest.app

import android.app.Application
import com.familyquest.data.createAndroidRepository

class QuestAndRewardApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer { eventPayloadCodec ->
            createAndroidRepository(this, eventPayloadCodec)
        }
    }
}
