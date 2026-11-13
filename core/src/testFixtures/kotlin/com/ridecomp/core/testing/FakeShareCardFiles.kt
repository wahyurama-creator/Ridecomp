package com.ridecomp.core.testing

import com.ridecomp.core.usecase.ShareCardFiles

/** In-memory [ShareCardFiles]: remembers saved and shared cards. */
public class FakeShareCardFiles : ShareCardFiles {
    public val saved: MutableMap<String, ByteArray> = mutableMapOf()
    public val shared: MutableList<String> = mutableListOf()

    override suspend fun save(fileName: String, png: ByteArray): String {
        saved[fileName] = png
        return "/cache/share/$fileName"
    }

    override suspend fun share(path: String, fileName: String) {
        shared += fileName
    }

    override suspend fun clear() {
        saved.clear()
    }
}
