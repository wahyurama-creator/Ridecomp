package com.ridecomp.platform.strava

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that never leaves the Android Keystore (SEC-STO-02). The key cannot be copied to
 * another phone, so an encrypted file restored elsewhere simply cannot be read. Blocking: call on IO.
 */
internal class KeystoreCipher(private val alias: String) {
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    /** Throws when the data was not made by this key (e.g. after a reinstall): the caller drops it. */
    fun decrypt(data: ByteArray): ByteArray {
        val ivSize = data.first().toInt()
        require(ivSize in 1 until data.size) { "Malformed data" }
        val iv = data.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(data, 1 + ivSize, data.size - 1 - ivSize)
    }

    fun deleteKey() {
        keyStore().deleteEntry(alias)
    }

    private fun key(): SecretKey = (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            generateKey()
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
