package com.familyquest.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExportRequest(
    val content: String,
    val onSuccess: () -> Unit,
    val onFailure: () -> Unit,
)

private data class ImportRequest(
    val onContent: (String) -> Unit,
    val onFailure: () -> Unit,
)

@Composable
fun rememberAndroidBackupActions(): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportRequest by remember { mutableStateOf<ExportRequest?>(null) }
    var importRequest by remember { mutableStateOf<ImportRequest?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val request = exportRequest ?: return@rememberLauncherForActivityResult
        exportRequest = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(request.content)
                    } ?: error("Unable to open export destination")
                }
            }.isSuccess
            if (success) request.onSuccess() else request.onFailure()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val request = importRequest ?: return@rememberLauncherForActivityResult
        importRequest = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText()
                    } ?: error("Unable to open import source")
                }
            }.getOrNull()
            if (content == null) request.onFailure() else request.onContent(content)
        }
    }

    return remember(exportLauncher, importLauncher) {
        object : BackupActions {
            override fun export(
                fileName: String,
                content: String,
                onSuccess: () -> Unit,
                onFailure: () -> Unit,
            ) {
                exportRequest = ExportRequest(content, onSuccess, onFailure)
                exportLauncher.launch(fileName)
            }

            override fun import(onContent: (String) -> Unit, onFailure: () -> Unit) {
                importRequest = ImportRequest(onContent, onFailure)
                importLauncher.launch(arrayOf("application/json", "text/plain"))
            }
        }
    }
}
