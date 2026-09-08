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
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON

private const val APP_UI_PREFERENCES = "app_ui"
private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"

fun MainViewController(
    premiumBridge: IosPremiumBridge,
    premiumRequestHandler: IosPremiumRequestHandler,
): UIViewController {
    val container = AppContainer { eventPayloadCodec ->
        createIosRepository(eventPayloadCodec)
    }
    val preferences = NSUserDefaults(suiteName = APP_UI_PREFERENCES)
    val backupActions = IosDocumentsBackupActions()

    return ComposeUIViewController {
        val mainViewModel = viewModel { MainViewModel(container.service) }
        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
        val premiumState by premiumBridge.state.collectAsStateWithLifecycle()
        var firstRunCompleted by remember {
            mutableStateOf(preferences.boolForKey(KEY_FIRST_RUN_COMPLETED))
        }

        FamilyQuestTheme(darkTheme = true) {
            if (firstRunCompleted) {
                FamilyQuestScreen(
                    state = state,
                    viewModel = mainViewModel,
                    backupActions = backupActions,
                    premiumState = premiumState,
                    onPurchasePremium = premiumRequestHandler::purchase,
                    onRestorePremium = premiumRequestHandler::restorePurchases,
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

private class IosDocumentsBackupActions : NSObject(), BackupActions, UIDocumentPickerDelegateProtocol {
    private var pendingContent: ((String) -> Unit)? = null
    private var pendingFailure: (() -> Unit)? = null
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
        val success = runCatching {
            val path = "$documentsPath/$fileName"
            val bytes = content.encodeToByteArray()
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            NSFileManager.defaultManager.createFileAtPath(path = path, contents = data, attributes = null)
        }.getOrDefault(false)
        if (success) onSuccess() else onFailure()
    }

    override fun import(onContent: (String) -> Unit, onFailure: () -> Unit) {
        if (pendingContent != null) {
            onFailure()
            return
        }
        val presenter = generateSequence(UIApplication.sharedApplication.keyWindow?.rootViewController) {
            it.presentedViewController
        }.lastOrNull()
        if (presenter == null) {
            onFailure()
            return
        }
        runCatching {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
            picker.allowsMultipleSelection = false
            picker.delegate = this
            picker.directoryURL = NSURL.fileURLWithPath(documentsPath, isDirectory = true)
            pendingContent = onContent
            pendingFailure = onFailure
            presenter.presentViewController(picker, animated = true, completion = null)
        }.onFailure {
            pendingContent = null
            pendingFailure = null
            onFailure()
        }
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val onContent = pendingContent
        val onFailure = pendingFailure
        pendingContent = null
        pendingFailure = null
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onFailure?.invoke()
            return
        }
        val scoped = url.startAccessingSecurityScopedResource()
        val content = try {
            runCatching {
                NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null) as String?
            }.getOrNull()
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
        if (content == null) onFailure?.invoke() else onContent?.invoke(content)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        pendingContent = null
        pendingFailure = null
    }
}
