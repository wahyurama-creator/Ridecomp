package com.ridecomp.core.testing

import com.ridecomp.core.engine.export.ByteSink
import com.ridecomp.core.usecase.ExportFileStore
import java.io.ByteArrayOutputStream

/** In-memory [ExportFileStore]; files are kept by name. Set [isFailing] to simulate a full disk. */
public class FakeExportFileStore : ExportFileStore {
    public var isFailing: Boolean = false
    public val files: MutableMap<String, ByteArray> = mutableMapOf()

    override suspend fun write(fileName: String, bytes: ByteArray): String {
        if (isFailing) error("simulated write failure")
        files[fileName] = bytes
        return PATH_PREFIX + fileName
    }

    override suspend fun stream(fileName: String, block: suspend (ByteSink) -> Unit): String {
        if (isFailing) error("simulated write failure")
        val buffer = ByteArrayOutputStream()
        block(
            object : ByteSink {
                override fun write(bytes: ByteArray) {
                    buffer.write(bytes)
                }
            },
        )
        files[fileName] = buffer.toByteArray()
        return PATH_PREFIX + fileName
    }

    override suspend fun delete(fileName: String) {
        files.remove(fileName)
    }

    public companion object {
        public const val PATH_PREFIX: String = "/exports/"
    }
}
