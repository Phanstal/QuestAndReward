package com.familyquest.application

import com.familyquest.domain.model.BackupArchive

interface BackupCodec {
    fun encode(archive: BackupArchive): String

    @Throws(BackupDecodeException::class)
    fun decode(content: String): BackupArchive
}

class BackupDecodeException(
    val unsupportedVersion: Boolean = false,
    cause: Throwable? = null,
) : IllegalArgumentException(cause)
