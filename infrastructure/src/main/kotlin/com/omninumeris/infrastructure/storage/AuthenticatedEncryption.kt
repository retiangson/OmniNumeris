package com.omninumeris.infrastructure.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.os.Build
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object AuthenticatedEncryption {
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    const val KEY_BYTES = 32
    const val WRAPPED_KEY_BYTES = KEY_BYTES + NONCE_BYTES + TAG_BYTES
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    fun seal(key: SecretKey, authenticatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == NONCE_BYTES)
        cipher.updateAAD(authenticatedData)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun open(key: SecretKey, authenticatedData: ByteArray, encrypted: ByteArray): ByteArray {
        require(encrypted.size >= NONCE_BYTES + TAG_BYTES) { "Ciphertext is incomplete" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, encrypted, 0, NONCE_BYTES))
        cipher.updateAAD(authenticatedData)
        return try {
            cipher.doFinal(encrypted, NONCE_BYTES, encrypted.size - NONCE_BYTES)
        } catch (failure: AEADBadTagException) {
            throw SecurityException("Record authentication failed", failure)
        }
    }

    fun aes(key: ByteArray): SecretKey = SecretKeySpec(key, "AES")
}

internal class AndroidVaultKeyWrapper(
    private val alias: String,
    private val credentialValiditySeconds: Int? = null,
) {
    init {
        require(credentialValiditySeconds == null || credentialValiditySeconds > 0)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun wrap(id: UUID, rawKey: ByteArray, allowCreate: Boolean): ByteArray {
        val key = keyOrNull() ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            if (!allowCreate) throw SecurityException("Wrapping key is unavailable")
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(AuthenticatedEncryption.KEY_BYTES * 8)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .apply { credentialValiditySeconds?.let { requireDeviceCredential(it) } }
                .build())
            generateKey()
        }
        return AuthenticatedEncryption.seal(key, associatedData(id), rawKey)
    }

    fun unwrap(id: UUID, wrapped: ByteArray): ByteArray {
        require(wrapped.size == AuthenticatedEncryption.WRAPPED_KEY_BYTES)
        val key = keyOrNull() ?: throw SecurityException("Wrapping key is unavailable")
        return AuthenticatedEncryption.open(key, associatedData(id), wrapped)
    }

    private fun keyOrNull(): SecretKey? {
        val key = keyStore().getKey(alias, null) as? SecretKey ?: return null
        if (credentialValiditySeconds != null) {
            val factory = SecretKeyFactory.getInstance("AES", "AndroidKeyStore")
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (!info.isUserAuthenticationRequired ||
                info.userAuthenticationValidityDurationSeconds != credentialValiditySeconds ||
                Build.VERSION.SDK_INT >= 30 && info.userAuthenticationType != KeyProperties.AUTH_DEVICE_CREDENTIAL) {
                throw SecurityException("Wrapping key credential policy differs from the requested policy")
            }
        }
        return key
    }

    @Suppress("DEPRECATION")
    private fun KeyGenParameterSpec.Builder.requireDeviceCredential(seconds: Int) {
        setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            setUserAuthenticationParameters(seconds, KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
            setUserAuthenticationValidityDurationSeconds(seconds)
        }
    }

    private fun associatedData(id: UUID): ByteArray = "omninumeris:wrap:v1:$id".toByteArray(Charsets.US_ASCII)
}
