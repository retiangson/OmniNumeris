package com.omninumeris.infrastructure.storage.protectedvault

import com.omninumeris.infrastructure.storage.AuthenticatedEncryption
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object VaultCrypto {
    const val NONCE_BYTES = AuthenticatedEncryption.NONCE_BYTES
    const val TAG_BYTES = AuthenticatedEncryption.TAG_BYTES
    const val KEY_BYTES = AuthenticatedEncryption.KEY_BYTES
    const val SALT_BYTES = 16
    private const val BACKUP_ITERATIONS = 600_000

    fun randomBytes(count: Int): ByteArray = AuthenticatedEncryption.randomBytes(count)

    fun seal(key: SecretKey, authenticatedData: ByteArray, plaintext: ByteArray): ByteArray =
        AuthenticatedEncryption.seal(key, authenticatedData, plaintext)

    fun open(key: SecretKey, authenticatedData: ByteArray, encrypted: ByteArray): ByteArray =
        AuthenticatedEncryption.open(key, authenticatedData, encrypted)

    fun deriveBackupKey(password: CharArray, salt: ByteArray): ByteArray {
        require(password.size in 12..128) { "Use a backup passphrase of 12 to 128 characters" }
        val spec = PBEKeySpec(password, salt, BACKUP_ITERATIONS, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun aes(key: ByteArray): SecretKey = AuthenticatedEncryption.aes(key)
}
