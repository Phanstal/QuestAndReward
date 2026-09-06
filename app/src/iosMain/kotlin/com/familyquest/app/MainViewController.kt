@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.familyquest.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyquest.app.ui.BackupActions
import com.familyquest.app.ui.FamilyQuestScreen
import com.familyquest.app.ui.FirstRunExperience
import com.familyquest.app.ui.theme.FamilyQuestTheme
import com.familyquest.data.createIosRepository
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.UIKit.UIViewController

private const val APP_UI_PREFERENCES = "app_ui"
private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"

fun MainViewController(): UIViewController {
    val container = AppContainer { eventPayloadCodec ->
        createIosRepository(eventPayloadCodec)
    }
    val preferences = NSUserDefaults(suiteName = APP_UI_PREFERENCES)
    val backupActions = IosDocumentsBackupActions()

    return ComposeUIViewController {
        val mainViewModel = viewModel { MainViewModel(container.service) }
        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
        var firstRunCompleted by remember {
            mutableStateOf(preferences.boolForKey(KEY_FIRST_RUN_COMPLETED))
        }

        FamilyQuestTheme(darkTheme = true) {
            if (firstRunCompleted) {
                FamilyQuestScreen(
                    state = state,
                    viewModel = mainViewModel,
                    backupActions = backupActions,
                )
            } else {
                FirstRunExperience(
                    onComplete = {
                        preferences.setBool(true, forKey = KEY_FIRST_RUN_COMPLETED)
                        firstRunCompleted = true
                    },
                )
            }
        }
    }
}

private class IosDocumentsBackupActions : BackupActions {
    private val documentsPath: String
        get() = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path ?: error("Documents directory is unavailable")

    override fun export(
        fileName: String,
        content: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val path = "$documentsPath/$fileName"
        val bytes = content.encodeToByteArray()
        val success = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }.writeToFile(path, atomically = true)
        if (success) onSuccess() else onFailure()
    }

    override fun import(onContent: (String) -> Unit, onFailure: () -> Unit) {
        val fileName = NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(documentsPath, error = null)
            ?.filterIsInstance<String>()
            ?.filter { it.startsWith("quest-backup-") && it.endsWith(".json") }
            ?.maxOrNull()
        if (fileName == null) {
            onFailure()
            return
        }
        val content = NSString.create(
            contentsOfFile = "$documentsPath/$fileName",
            encoding = NSUTF8StringEncoding,
            error = null,
        ) as String?
        if (content == null) onFailure() else onContent(content)
    }
}
