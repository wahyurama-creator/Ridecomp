package com.ridecomp.core.engine.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Parameters of password-protected backups (decision D-25, SEC-BK-02). */
public object BackupCryptoConfig {
    /** File starts with these bytes when encrypted ("RCBK"); a plain backup starts with the ZIP magic. */
    public val MAGIC: ByteArray = byteArrayOf(0x52, 0x43, 0x42, 0x4B)
    public const val FORMAT_VERSION: Int = 1
    public const val KDF_ALGORITHM: String = "PBKDF2WithHmacSHA256"

    /** OWASP 2023 recommendation for PBKDF2-HMAC-SHA256. */
    public const val KDF_ITERATIONS: Int = 600_000

    /** Refuse files that ask for absurd work (denial of service by a crafted header). */
    public const val KDF_MAX_ITERATIONS: Int = 10_000_000
    public const val KEY_BITS: Int = 256
    public const val SALT_BYTES: Int = 16
    public const val CIPHER: String = "AES/GCM/NoPadding"
    public const val KEY_ALGORITHM: String = "AES"
    public const val TAG_BITS: Int = 128
    public const val TAG_BYTES: Int = TAG_BITS / Byte.SIZE_BITS
    public const val NONCE_BYTES: Int = 12

    /** Nonce = 7-byte random prefix ‖ 4-byte segment counter ‖ 1-byte "last segment" flag. */
    public const val NONCE_PREFIX_BYTES: Int = 7

    /** Plaintext per segment: bounded memory while encrypting and decrypting. */
    public const val SEGMENT_BYTES: Int = 64 * 1024
}

/** Wrong password, damaged or truncated file: all look the same to the user. */
public class BackupDecryptionException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Password-based authenticated encryption for backup files, built only from JCA primitives (AES-GCM,
 * PBKDF2; SEC-BK-02). The stream is split into segments, each sealed with AES-GCM under a nonce made of a
 * random prefix, the segment index and a last-segment flag (the "STREAM" construction also used by Tink
 * streaming AEAD). Every segment authenticates the header; reordering, truncation or appending is detected.
 *
 * Layout: `MAGIC ‖ version(1) ‖ iterations(4) ‖ salt(16) ‖ noncePrefix(7) ‖ segments…`, each segment being
 * `length(4) ‖ ciphertext‖tag`.
 */
public object BackupCrypto {
    private val random = SecureRandom()

    /** True when [firstBytes] (at least the magic length) start an encrypted backup. */
    public fun isEncrypted(firstBytes: ByteArray): Boolean = firstBytes.size >= BackupCryptoConfig.MAGIC.size &&
        firstBytes.copyOf(BackupCryptoConfig.MAGIC.size).contentEquals(BackupCryptoConfig.MAGIC)

    /** Wraps [output]: everything written is encrypted; closing writes the last segment. */
    public fun encrypt(output: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(BackupCryptoConfig.SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(BackupCryptoConfig.NONCE_PREFIX_BYTES).also(random::nextBytes)
        val header = header(BackupCryptoConfig.KDF_ITERATIONS, salt, prefix)
        output.write(header)
        val key = deriveKey(password, salt, BackupCryptoConfig.KDF_ITERATIONS)
        return EncryptingStream(output, SegmentSealer(key, prefix, header))
    }

    /** Wraps [input] (positioned at the start of the file); throws [BackupDecryptionException] on any problem. */
    public fun decrypt(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(BackupCryptoConfig.MAGIC.size).also { data.readFullyOrFail(it) }
        if (!magic.contentEquals(BackupCryptoConfig.MAGIC)) corrupt("Not an encrypted backup")
        val version = data.read()
        if (version != BackupCryptoConfig.FORMAT_VERSION) corrupt("Unknown format $version")
        val iterations = data.readInt()
        if (iterations !in 1..BackupCryptoConfig.KDF_MAX_ITERATIONS) corrupt("Bad header")
        val salt = ByteArray(BackupCryptoConfig.SALT_BYTES).also { data.readFullyOrFail(it) }
        val prefix = ByteArray(BackupCryptoConfig.NONCE_PREFIX_BYTES).also { data.readFullyOrFail(it) }
        val key = deriveKey(password, salt, iterations)
        return DecryptingStream(data, SegmentSealer(key, prefix, header(iterations, salt, prefix)))
    }

    private fun header(iterations: Int, salt: ByteArray, prefix: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(BackupCryptoConfig.MAGIC)
        write(BackupCryptoConfig.FORMAT_VERSION)
        write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(iterations).array())
        write(salt)
        write(prefix)
    }.toByteArray()

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, BackupCryptoConfig.KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(BackupCryptoConfig.KDF_ALGORITHM).generateSecret(spec).encoded
            return SecretKeySpec(bytes, BackupCryptoConfig.KEY_ALGORITHM)
        } finally {
            spec.clearPassword()
        }
    }

    private fun DataInputStream.readFullyOrFail(target: ByteArray) {
        try {
            readFully(target)
        } catch (e: EOFException) {
            throw BackupDecryptionException("Truncated header", e)
        }
    }
}

/** AES-GCM for one segment; the header is the associated data of every segment. */
private class SegmentSealer(
    private val key: SecretKeySpec,
    private val prefix: ByteArray,
    private val header: ByteArray,
) {
    fun seal(index: Int, isLast: Boolean, plain: ByteArray, length: Int): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, index, isLast).doFinal(plain, 0, length)

    fun open(index: Int, isLast: Boolean, sealed: ByteArray): ByteArray = try {
        cipher(Cipher.DECRYPT_MODE, index, isLast).doFinal(sealed)
    } catch (e: GeneralSecurityException) {
        throw BackupDecryptionException("Wrong password or damaged backup", e)
    }

    private fun cipher(mode: Int, index: Int, isLast: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(BackupCryptoConfig.NONCE_BYTES)
            .put(prefix)
            .putInt(index)
            .put(if (isLast) LAST_FLAG else NOT_LAST_FLAG)
            .array()
        return Cipher.getInstance(BackupCryptoConfig.CIPHER).apply {
            init(mode, key, GCMParameterSpec(BackupCryptoConfig.TAG_BITS, nonce))
            updateAAD(header)
        }
    }

    private companion object {
        const val LAST_FLAG: Byte = 1
        const val NOT_LAST_FLAG: Byte = 0
    }
}

private class EncryptingStream(private val output: OutputStream, private val sealer: SegmentSealer) : OutputStream() {
    private val buffer = ByteArray(BackupCryptoConfig.SEGMENT_BYTES)
    private var filled = 0
    private var index = 0
    private var isClosed = false

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full buffer is written only when more data follows, so the last segment is always known.
            if (filled == buffer.size) flushSegment(isLast = false)
            val count = minOf(remaining, buffer.size - filled)
            System.arraycopy(b, offset, buffer, filled, count)
            filled += count
            offset += count
            remaining -= count
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        flushSegment(isLast = true)
        output.close()
    }

    private fun flushSegment(isLast: Boolean) {
        val sealed = sealer.seal(index++, isLast, buffer, filled)
        output.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(sealed.size).array())
        output.write(sealed)
        filled = 0
    }
}

private class DecryptingStream(private val input: DataInputStream, private val sealer: SegmentSealer) : InputStream() {
    private var plain = ByteArray(0)
    private var position = 0
    private var index = 0
    private var isFinished = false
    private var pending: ByteArray? = null

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == END) END else one[0].toInt() and BYTE_MASK
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        while (position == plain.size) {
            if (isFinished) return END
            nextSegment()
        }
        val count = minOf(len, plain.size - position)
        System.arraycopy(plain, position, b, off, count)
        position += count
        return count
    }

    override fun close() {
        input.close()
    }

    /** Reads one segment ahead, so the last one is opened with the "last" flag (truncation is detected). */
    private fun nextSegment() {
        val current = pending ?: readSealed() ?: throw BackupDecryptionException("Empty backup")
        val next = readSealed()
        pending = next
        isFinished = next == null
        plain = sealer.open(index++, isLast = isFinished, sealed = current)
        position = 0
    }

    private fun readSealed(): ByteArray? {
        val length = try {
            input.readInt()
        } catch (_: EOFException) {
            return null
        }
        if (length !in BackupCryptoConfig.TAG_BYTES..MAX_SEALED_BYTES) throw BackupDecryptionException("Bad segment")
        return ByteArray(length).also {
            try {
                input.readFully(it)
            } catch (e: EOFException) {
                throw BackupDecryptionException("Truncated backup", e)
            }
        }
    }

    private companion object {
        const val END = -1
        const val BYTE_MASK = 0xFF
        const val MAX_SEALED_BYTES = BackupCryptoConfig.SEGMENT_BYTES + BackupCryptoConfig.TAG_BYTES
    }
}

/** Rejects the file: wrong password, damage and truncation all end here. */
private fun corrupt(message: String): Nothing = throw BackupDecryptionException(message)
