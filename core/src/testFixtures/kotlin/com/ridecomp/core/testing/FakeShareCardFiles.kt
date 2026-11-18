package com.ridecomp.core.testing

import com.ridecomp.core.engine.share.ShareTarget
import com.ridecomp.core.usecase.ShareCardFiles

/** In-memory [ShareCardFiles]: remembers saved and shared cards. */
public class FakeShareCardFiles(private val installed: Set<ShareTarget> = emptySet()) : ShareCardFiles {
    public val saved: MutableMap<String, ByteArray> = mutableMapOf()
    public val shared: MutableList<String> = mutableListOf()
    public val targets: MutableList<ShareTarget> = mutableListOf()

    override suspend fun save(fileName: String, png: ByteArray): String {
        saved[fileName] = png
        return "/cache/share/$fileName"
    }

    override suspend fun share(path: String, fileName: String, target: ShareTarget) {
        shared += fileName
        targets += target
    }

    override fun installedTargets(): Set<ShareTarget> = installed

    override suspend fun saveTo(destinationUri: String, png: ByteArray): Boolean {
        saved[destinationUri] = png
        return true
    }

    override suspend fun clear() {
        saved.clear()
    }
}
