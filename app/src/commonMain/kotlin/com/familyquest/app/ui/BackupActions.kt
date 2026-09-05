package com.familyquest.app.ui

interface BackupActions {
    fun export(
        fileName: String,
        content: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    )

    fun import(
        onContent: (String) -> Unit,
        onFailure: () -> Unit,
    )
}

object UnavailableBackupActions : BackupActions {
    override fun export(
        fileName: String,
        content: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) = onFailure()

    override fun import(onContent: (String) -> Unit, onFailure: () -> Unit) = onFailure()
}
