package com.familyquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyquest.app.ui.FamilyQuestScreen
import com.familyquest.app.ui.FirstRunExperience
import com.familyquest.app.ui.PremiumUiState
import com.familyquest.app.ui.rememberAndroidBackupActions
import com.familyquest.app.ui.theme.FamilyQuestTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as QuestAndRewardApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backupActions = rememberAndroidBackupActions()
            var firstRunCompleted by rememberSaveable {
                mutableStateOf(
                    getSharedPreferences(APP_UI_PREFERENCES, MODE_PRIVATE)
                        .getBoolean(KEY_FIRST_RUN_COMPLETED, false),
                )
            }
            var demoPremium by rememberSaveable { mutableStateOf(false) }
            FamilyQuestTheme(darkTheme = true) {
                if (firstRunCompleted) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    FamilyQuestScreen(
                        state = state,
                        viewModel = viewModel,
                        backupActions = backupActions,
                        premiumState = if (demoPremium) PremiumUiState.premium() else PremiumUiState.free(),
                        onPurchasePremium = { demoPremium = true },
                        onRestorePremium = { demoPremium = true },
                    )
                } else {
                    FirstRunExperience(
                        onComplete = {
                            getSharedPreferences(APP_UI_PREFERENCES, MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_FIRST_RUN_COMPLETED, true)
                                .apply()
                            firstRunCompleted = true
                        },
                    )
                }
            }
        }
    }

    private companion object {
        const val APP_UI_PREFERENCES = "app_ui"
        const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
    }
}
