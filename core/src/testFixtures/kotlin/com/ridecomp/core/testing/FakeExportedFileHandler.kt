package com.ridecomp.core.testing

import com.ridecomp.core.usecase.ExportedFile
import com.ridecomp.core.usecase.ExportedFileHandler

/** Records shares and copies; [isCopyFailing] simulates a destination that cannot be written. */
public class FakeExportedFileHandler : ExportedFileHandler {
    public val shared: MutableList<ExportedFile> = mutableListOf()
    public val copied: MutableList<Pair<ExportedFile, String>> = mutableListOf()
    public var isCopyFailing: Boolean = false

    override suspend fun share(file: ExportedFile) {
        shared += file
    }

    override suspend fun copyTo(file: ExportedFile, destinationUri: String): Boolean {
        if (isCopyFailing) return false
        copied += file to destinationUri
        return true
    }
}
