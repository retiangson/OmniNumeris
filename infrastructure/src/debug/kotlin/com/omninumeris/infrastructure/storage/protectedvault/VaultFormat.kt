package com.omninumeris.infrastructure.storage.protectedvault

import java.nio.ByteBuffer
import java.util.UUID

data class ProtectedNotebookSnapshot(val document: ByteArray, val thumbnail: ByteArray)

internal object VaultFormat {
    const val MAX_DOCUMENT = 1_048_576
    private const val MAX_THUMBNAIL = 131_072
    private const val SNAPSHOT_VERSION = 1
    private const val MANIFEST_BYTES = 24
    private const val BACKUP_MAGIC = 0x4f4e5631
    private const val SNAPSHOT_HEADER_BYTES = 12
    private const val BACKUP_HEADER_BYTES = 4 + VaultCrypto.SALT_BYTES
    private const val ENCRYPTION_OVERHEAD = VaultCrypto.NONCE_BYTES + VaultCrypto.TAG_BYTES
    const val MAX_ENCRYPTED_SNAPSHOT = SNAPSHOT_HEADER_BYTES + MAX_DOCUMENT + MAX_THUMBNAIL + ENCRYPTION_OVERHEAD

    fun encode(snapshot: ProtectedNotebookSnapshot): ByteArray {
        require(snapshot.document.size <= MAX_DOCUMENT && snapshot.thumbnail.size <= MAX_THUMBNAIL)
        return ByteBuffer.allocate(SNAPSHOT_HEADER_BYTES + snapshot.document.size + snapshot.thumbnail.size)
            .putInt(SNAPSHOT_VERSION)
            .putInt(snapshot.document.size)
            .putInt(snapshot.thumbnail.size)
            .put(snapshot.document)
            .put(snapshot.thumbnail)
            .array()
    }

    fun decode(input: ByteBuffer): ProtectedNotebookSnapshot {
        require(input.remaining() >= SNAPSHOT_HEADER_BYTES && input.int == SNAPSHOT_VERSION) {
            "Unknown snapshot format"
        }
        val documentSize = input.int
        val thumbnailSize = input.int
        require(documentSize in 0..MAX_DOCUMENT && thumbnailSize in 0..MAX_THUMBNAIL &&
            input.remaining() == documentSize + thumbnailSize) { "Invalid snapshot sizes" }
        return ProtectedNotebookSnapshot(
            ByteArray(documentSize).also { input.get(it) },
            ByteArray(thumbnailSize).also { input.get(it) },
        )
    }

    fun backup(id: UUID, revision: Long, snapshot: ProtectedNotebookSnapshot, password: CharArray): ByteArray {
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val header = ByteBuffer.allocate(BACKUP_HEADER_BYTES).putInt(BACKUP_MAGIC).put(salt).array()
        val encoded = encode(snapshot)
        try {
            val manifest = ByteBuffer.allocate(MANIFEST_BYTES + encoded.size)
                .putLong(id.mostSignificantBits)
                .putLong(id.leastSignificantBits)
                .putLong(revision)
                .put(encoded).array()
            try {
                val key = VaultCrypto.deriveBackupKey(password, salt)
                try {
                    return header + VaultCrypto.seal(VaultCrypto.aes(key), header, manifest)
                } finally {
                    key.fill(0)
                }
            } finally {
                manifest.fill(0)
            }
        } finally {
            encoded.fill(0)
        }
    }

    fun readBackup(archive: ByteArray, password: CharArray): RestoredNotebook {
        val minimum = BACKUP_HEADER_BYTES + MANIFEST_BYTES + SNAPSHOT_HEADER_BYTES + ENCRYPTION_OVERHEAD
        val maximum = BACKUP_HEADER_BYTES + MANIFEST_BYTES + MAX_ENCRYPTED_SNAPSHOT
        require(archive.size in minimum..maximum) { "Invalid backup size" }
        val header = archive.copyOfRange(0, BACKUP_HEADER_BYTES)
        val headerReader = ByteBuffer.wrap(header)
        require(headerReader.int == BACKUP_MAGIC) { "Unknown backup format" }
        val salt = ByteArray(VaultCrypto.SALT_BYTES).also { headerReader.get(it) }
        val key = VaultCrypto.deriveBackupKey(password, salt)
        try {
            val plaintext = VaultCrypto.open(
                VaultCrypto.aes(key), header, archive.copyOfRange(BACKUP_HEADER_BYTES, archive.size),
            )
            try {
                val manifest = ByteBuffer.wrap(plaintext)
                val id = UUID(manifest.long, manifest.long)
                val revision = manifest.long
                require(revision >= 0 && revision < Long.MAX_VALUE) { "Invalid backup revision" }
                return RestoredNotebook(id, revision, decode(manifest))
            } finally {
                plaintext.fill(0)
            }
        } finally {
            key.fill(0)
        }
    }
}

internal data class RestoredNotebook(val id: UUID, val revision: Long, val snapshot: ProtectedNotebookSnapshot)
