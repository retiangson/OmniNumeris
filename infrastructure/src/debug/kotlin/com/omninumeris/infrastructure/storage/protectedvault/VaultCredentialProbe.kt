package com.omninumeris.infrastructure.storage.protectedvault

import com.omninumeris.infrastructure.storage.AndroidVaultKeyWrapper
import java.security.KeyStore
import java.util.UUID

/** Synthetic test harness for the device-credential wrapping policy. */
class VaultCredentialProbe : AutoCloseable {
    private val alias = "omninumeris-credential-probe-${UUID.randomUUID()}"
    private val owner = UUID.randomUUID()
    private val wrapper = AndroidVaultKeyWrapper(alias, VALIDITY_SECONDS)

    fun wrap(raw: ByteArray): ByteArray = wrapper.wrap(owner, raw, allowCreate = true)
    fun unwrap(wrapped: ByteArray): ByteArray = wrapper.unwrap(owner, wrapped)

    override fun close() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    companion object {
        const val VALIDITY_SECONDS = 8
    }
}
