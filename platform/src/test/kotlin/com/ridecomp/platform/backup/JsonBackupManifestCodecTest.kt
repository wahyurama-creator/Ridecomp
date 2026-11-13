package com.ridecomp.platform.backup

import com.ridecomp.core.engine.backup.BackupManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonBackupManifestCodecTest {
    private val codec = JsonBackupManifestCodec()

    @Test
    fun `manifest survives a round trip`() {
        val manifest = BackupManifest("0.1.0", 4, 1_700_000_000_000L, 12, true, mapOf("db/ridecomp.db" to "ab12"))

        assertEquals(manifest, codec.decode(codec.encode(manifest)))
    }

    @Test
    fun `damaged or foreign json is not a backup`() {
        assertNull(codec.decode("{".toByteArray()))
        assertNull(codec.decode("""{"app_version":"1"}""".toByteArray()))
        assertNull(codec.decode(byteArrayOf(0x50, 0x4B)))
    }
}
