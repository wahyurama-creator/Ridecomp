package com.ridecomp.core.engine.backup

import com.ridecomp.core.testing.FakeBackupManifestCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupArchiveTest {
    private val codec = FakeBackupManifestCodec()
    private val work: File = Files.createTempDirectory("backup-test").toFile()
    private val manifest = BackupManifest("1.0", 4, 1_000L, 3, isEncrypted = false, checksums = emptyMap())

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    private fun source(name: String, content: String) =
        BackupEntrySource(name, File(work, "src-${name.replace('/', '_')}").apply { writeText(content) })

    private fun archive(entries: List<BackupEntrySource>): ByteArray =
        ByteArrayOutputStream().also { BackupArchiveWriter(codec).write(it, manifest, entries) }.toByteArray()

    private fun extract(bytes: ByteArray): BackupManifest =
        BackupArchiveReader(codec).extract(ByteArrayInputStream(bytes), File(work, "out").apply { mkdirs() })

    @Test
    fun `round trip with manifest first and verified checksums`() {
        val bytes = archive(listOf(source(BackupArchiveConfig.DATABASE, "db"), source("fit/ride_1.fit", "fit")))

        assertEquals(3, BackupArchiveReader(codec).readManifest(ByteArrayInputStream(bytes)).activityCount)
        val restored = extract(bytes)
        assertEquals(setOf(BackupArchiveConfig.DATABASE, "fit/ride_1.fit"), restored.checksums.keys)
        assertEquals("db", File(work, "out/db/ridecomp.db").readText())
    }

    /** Hand-made archive: manifest claims [claimed] checksums, then raw entries. */
    private fun crafted(claimed: Map<String, String>, entries: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchiveConfig.MANIFEST))
            zip.write(codec.encode(manifest.copy(checksums = claimed)))
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `zip slip and unexpected names are rejected before anything is written outside`() {
        listOf("../evil.so", "/data/evil", "db/../../evil", "fit/sub/x.fit", "shared_prefs/x.xml").forEach { name ->
            val error =
                assertFailsWith<BackupArchiveException> { extract(crafted(mapOf(name to "x"), listOf(name to "x"))) }
            assertEquals(BackupError.UNSAFE_ENTRY, error.error)
        }
        assertFalse(File(work.parentFile, "evil.so").exists())
    }

    @Test
    fun `checksum mismatch, missing entries and a manifest that is not first are rejected`() {
        val good = archive(listOf(source(BackupArchiveConfig.DATABASE, "db")))
        val tampered =
            crafted(mapOf(BackupArchiveConfig.DATABASE to "00"), listOf(BackupArchiveConfig.DATABASE to "db"))
        assertEquals(BackupError.CHECKSUM_MISMATCH, assertFailsWith<BackupArchiveException> { extract(tampered) }.error)

        val missing = crafted(mapOf(BackupArchiveConfig.DATABASE to "00"), emptyList())
        assertEquals(BackupError.MISSING_ENTRY, assertFailsWith<BackupArchiveException> { extract(missing) }.error)

        val noManifest = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry(BackupArchiveConfig.DATABASE)) }
        }.toByteArray()
        assertEquals(BackupError.NOT_A_BACKUP, assertFailsWith<BackupArchiveException> { extract(noManifest) }.error)
        assertTrue(extract(good).checksums.isNotEmpty())
    }

    @Test
    fun `encrypted archive round trip`() {
        val plain = archive(listOf(source(BackupArchiveConfig.DATABASE, "db")))
        val sealed = ByteArrayOutputStream().also { out ->
            BackupCrypto.encrypt(out, PASSWORD.toCharArray()).use { it.write(plain) }
        }.toByteArray()

        val restored = BackupArchiveReader(codec).extract(
            BackupCrypto.decrypt(ByteArrayInputStream(sealed), PASSWORD.toCharArray()),
            File(work, "enc").apply { mkdirs() },
        )
        assertEquals(setOf(BackupArchiveConfig.DATABASE), restored.checksums.keys)
    }

    private companion object {
        const val PASSWORD = "rahasia-panjang"
    }
}
