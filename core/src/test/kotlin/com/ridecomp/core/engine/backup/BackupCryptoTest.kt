package com.ridecomp.core.engine.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCryptoTest {
    private val password = "kata-sandi-panjang".toCharArray()

    private fun encrypt(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(out, password).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(sealed: ByteArray, with: CharArray = password): ByteArray =
        BackupCrypto.decrypt(ByteArrayInputStream(sealed), with).use { it.readBytes() }

    @Test
    fun `round trip over several segments, empty data too`() {
        val plain = Random(SEED).nextBytes(BackupCryptoConfig.SEGMENT_BYTES * 3 + 123)
        val sealed = encrypt(plain)

        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertContentEquals(plain, decrypt(sealed))
        assertContentEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
        // Exactly one full segment: the last-segment flag still lands on it.
        val exact = ByteArray(BackupCryptoConfig.SEGMENT_BYTES) { 7 }
        assertContentEquals(exact, decrypt(encrypt(exact)))
    }

    @Test
    fun `wrong password, tampering and truncation are all rejected`() {
        val plain = Random(SEED).nextBytes(BackupCryptoConfig.SEGMENT_BYTES * 2 + 10)
        val sealed = encrypt(plain)

        assertFailsWith<BackupDecryptionException> { decrypt(sealed, "salah".toCharArray()) }

        val tampered = sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertFailsWith<BackupDecryptionException> { decrypt(tampered) }

        val segment = Int.SIZE_BYTES + BackupCryptoConfig.SEGMENT_BYTES + BackupCryptoConfig.TAG_BYTES
        // Dropping the whole last segment: the previous one was sealed as "not last".
        val withoutLast = sealed.copyOf(sealed.size - (sealed.size - HEADER_BYTES) % segment)
        assertFailsWith<BackupDecryptionException> { decrypt(withoutLast) }
        assertFailsWith<BackupDecryptionException> { decrypt(sealed.copyOf(sealed.size - 1)) }
    }

    @Test
    fun `plain zip files are not taken for encrypted ones`() {
        assertFalse(BackupCrypto.isEncrypted(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFailsWith<BackupDecryptionException> { decrypt(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0)) }
    }

    private companion object {
        const val SEED = 42
        const val HEADER_BYTES = 4 + 1 + 4 + BackupCryptoConfig.SALT_BYTES + BackupCryptoConfig.NONCE_PREFIX_BYTES
    }
}
