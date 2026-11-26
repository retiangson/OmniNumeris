package com.omninumeris.infrastructure.storage.protectedvault

import android.content.Context
import android.os.SystemClock
import android.security.keystore.UserNotAuthenticatedException
import android.system.Os
import android.system.OsConstants
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omninumeris.infrastructure.storage.AndroidVaultKeyWrapper
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VaultCommitStage { BeforeBlobPublish, BeforeMetadataCommit }

/** An opaque, process-local grant for one continuous unlock session. */
class ProtectedNotebookLease internal constructor(internal val notebookId: UUID, internal val deadlineMillis: Long? = null) {
    internal val placementId: UUID = UUID.randomUUID()
}

internal class VaultLeaseExpired : SecurityException("Notebook unlock session ended")

internal data class VaultDocumentEdit<T>(val result: T, val replacement: ByteArray? = null)

class ProtectedVault private constructor(
    private val root: File,
    private val database: VaultDatabase,
    private val wrapper: AndroidVaultKeyWrapper,
    private val authorize: suspend (UUID) -> Boolean,
    private val onCommitStage: (VaultCommitStage) -> Unit,
    private val credentialValiditySeconds: Int?,
) {
    private val unlocked = mutableMapOf<UUID, ProtectedNotebookLease>()
    private var closed = false
    private var lockEpoch = 0L
    private val dao = database.vaultDao()
    private val blobs = File(root, "blobs")

    suspend fun create(snapshot: ProtectedNotebookSnapshot): UUID = operation {
        val id = UUID.randomUUID()
        publishNew(id, 0, snapshot)
        id
    }

    suspend fun unlock(id: UUID): Boolean = unlockWithLease(id) != null

    suspend fun unlockWithLease(id: UUID): ProtectedNotebookLease? {
        val epoch = operation {
            requireNotNull(dao.record(id.toString())) { "Notebook does not exist" }
            lockEpoch
        }
        if (!authorize(id)) return null
        return operation {
            if (epoch != lockEpoch) return@operation null
            val validity = credentialValiditySeconds
            if (validity != null) {
                val deadline = SystemClock.elapsedRealtime() + validity * 1_000L
                val record = requireNotNull(dao.record(id.toString()))
                try {
                    wrapper.unwrap(id, record.wrappedKey).fill(0)
                } catch (_: UserNotAuthenticatedException) {
                    unlocked.remove(id)
                    return@operation null
                }
                ProtectedNotebookLease(id, deadline).also { unlocked[id] = it }
            } else unlocked.getOrPut(id) { ProtectedNotebookLease(id) }
        }
    }

    suspend fun lock(id: UUID) = operation {
        lockEpoch++
        unlocked.remove(id)
        Unit
    }

    suspend fun lockAll() = operation {
        lockEpoch++
        unlocked.clear()
    }

    suspend fun revision(id: UUID): Long = operation { authorizedRecord(id).revision }

    suspend fun search(id: UUID, query: String): Boolean = operation {
        val record = authorizedRecord(id)
        require(query.isNotBlank() && query.length <= 128)
        withSnapshot(record) { it.document.toString(Charsets.UTF_8).contains(query, ignoreCase = true) }
    }

    suspend fun thumbnail(id: UUID): ByteArray = operation {
        withSnapshot(authorizedRecord(id)) { it.thumbnail.copyOf() }
    }

    suspend fun inferenceInput(id: UUID): ByteArray = operation {
        withSnapshot(authorizedRecord(id)) { it.document.copyOf() }
    }

    suspend fun save(id: UUID, expectedRevision: Long, snapshot: ProtectedNotebookSnapshot): Long = operation {
        commit(authorizedRecord(id), expectedRevision, snapshot)
    }

    suspend fun save(
        lease: ProtectedNotebookLease,
        expectedRevision: Long,
        snapshot: ProtectedNotebookSnapshot,
    ): Long = operation {
        withLease(lease) { record -> commit(record, expectedRevision, snapshot) }
    }

    internal suspend fun <T> editDocument(
        lease: ProtectedNotebookLease,
        edit: (ByteArray) -> VaultDocumentEdit<T>,
    ): T = operation {
        withLease(lease) { record ->
            withSnapshot(record) { snapshot ->
                val decision = edit(snapshot.document)
                decision.replacement?.let { replacement ->
                    try {
                        commit(record, record.revision, ProtectedNotebookSnapshot(replacement, snapshot.thumbnail))
                    } finally {
                        replacement.fill(0)
                    }
                }
                decision.result
            }
        }
    }

    private suspend fun commit(old: VaultRecord, expectedRevision: Long, snapshot: ProtectedNotebookSnapshot): Long {
        val id = canonicalId(old.id)
        check(old.revision == expectedRevision && expectedRevision < Long.MAX_VALUE - 1) {
            "Notebook revision changed"
        }
        val next = old.revision + 1
        val raw = wrapper.unwrap(id, old.wrappedKey)
        val blob = try {
            stageBlob(id, next, raw, snapshot)
        } finally {
            raw.fill(0)
        }
        onCommitStage(VaultCommitStage.BeforeMetadataCommit)
        database.withTransaction {
            check(dao.advanceRevision(id.toString(), expectedRevision, next) == 1)
            check(dao.movePointer(id.toString(), blob.toString()) == 1)
        }
        val previousPath = blobPath(canonicalId(old.blobId))
        try {
            Files.deleteIfExists(previousPath.toPath())
        } catch (_: IOException) {
            // Recovery collects obsolete blobs on the next open.
        }
        return next
    }

    suspend fun backup(id: UUID, password: CharArray): ByteArray = operation {
        val record = authorizedRecord(id)
        withSnapshot(record) { VaultFormat.backup(id, record.revision, it, password) }
    }

    suspend fun restore(archive: ByteArray, password: CharArray): UUID = operation {
        val restored = VaultFormat.readBackup(archive, password)
        try {
            check(!dao.exists(restored.id.toString())) { "Restore would replace an existing notebook" }
            publishNew(restored.id, restored.revision, restored.snapshot)
            restored.id
        } finally {
            clear(restored.snapshot)
        }
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        coordinator.withLock {
            if (!closed) {
                closed = true
                unlocked.clear()
                try {
                    database.close()
                } finally {
                    openRoots.remove(root)
                }
            }
        }
    }

    private suspend fun authorizedRecord(id: UUID): VaultRecord {
        return authorizedRecord(unlocked[id] ?: throw VaultLeaseExpired())
    }

    private suspend fun authorizedRecord(lease: ProtectedNotebookLease): VaultRecord {
        if (unlocked[lease.notebookId] !== lease) throw VaultLeaseExpired()
        if (lease.deadlineMillis?.let { SystemClock.elapsedRealtime() >= it } == true) {
            unlocked.remove(lease.notebookId)
            lockEpoch++
            throw VaultLeaseExpired()
        }
        return requireNotNull(dao.record(lease.notebookId.toString())) { "Notebook does not exist" }
    }

    private suspend inline fun <T> withLease(lease: ProtectedNotebookLease, action: (VaultRecord) -> T): T {
        return try {
            action(authorizedRecord(lease))
        } catch (_: UserNotAuthenticatedException) {
            unlocked.remove(lease.notebookId)
            lockEpoch++
            throw VaultLeaseExpired()
        }
    }

    private suspend fun publishNew(id: UUID, revision: Long, snapshot: ProtectedNotebookSnapshot) {
        val raw = VaultCrypto.randomBytes(VaultCrypto.KEY_BYTES)
        val wrapped: ByteArray
        val blob: UUID
        try {
            wrapped = wrapper.wrap(id, raw, allowCreate = dao.notebookCount() == 0)
            blob = stageBlob(id, revision, raw, snapshot)
        } finally {
            raw.fill(0)
        }
        onCommitStage(VaultCommitStage.BeforeMetadataCommit)
        database.withTransaction {
            dao.insertNotebook(NotebookRow(id.toString(), wrapped, revision))
            dao.insertPointer(BlobPointer(id.toString(), blob.toString()))
        }
    }

    private fun stageBlob(id: UUID, revision: Long, key: ByteArray, snapshot: ProtectedNotebookSnapshot): UUID {
        val blob = UUID.randomUUID()
        val encoded = VaultFormat.encode(snapshot)
        val sealed = try {
            VaultCrypto.seal(VaultCrypto.aes(key), associatedData(id, revision, blob), encoded)
        } finally {
            encoded.fill(0)
        }
        val pending = File.createTempFile("$blob-", ".pending", blobs)
        FileOutputStream(pending).use {
            it.write(sealed)
            it.fd.sync()
        }
        onCommitStage(VaultCommitStage.BeforeBlobPublish)
        Files.move(pending.toPath(), blobPath(blob).toPath(), StandardCopyOption.ATOMIC_MOVE)
        syncDirectory(blobs)
        return blob
    }

    private inline fun <T> withSnapshot(record: VaultRecord, consume: (ProtectedNotebookSnapshot) -> T): T {
        val id = canonicalId(record.id)
        val blob = canonicalId(record.blobId)
        val file = blobPath(blob)
        val size = file.length()
        require(size in 1L..VaultFormat.MAX_ENCRYPTED_SNAPSHOT.toLong()) { "Invalid vault blob size" }
        val encrypted = ByteArray(size.toInt())
        DataInputStream(file.inputStream()).use {
            it.readFully(encrypted)
            require(it.read() == -1) { "Unexpected vault blob data" }
        }
        val raw = wrapper.unwrap(id, record.wrappedKey)
        val bytes = try {
            VaultCrypto.open(VaultCrypto.aes(raw), associatedData(id, record.revision, blob), encrypted)
        } finally {
            raw.fill(0)
        }
        try {
            val snapshot = VaultFormat.decode(ByteBuffer.wrap(bytes))
            try {
                return consume(snapshot)
            } finally {
                clear(snapshot)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun blobPath(id: UUID) = File(blobs, "$id.blob")

    private suspend fun recover() {
        val ids = dao.blobIds()
        check(ids.size == dao.notebookCount()) { "Vault metadata has a missing pointer" }
        val referenced = ids.map { blobPath(canonicalId(it)).name }.toSet()
        check(referenced.all { File(blobs, it).isFile }) { "Vault has a missing committed blob" }
        for (file in blobs.listFiles() ?: throw IOException("Cannot list vault blobs")) {
            if (file.name !in referenced && file.isFile &&
                (file.extension == "pending" || file.extension == "blob")) {
                Files.delete(file.toPath())
            }
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        coordinator.withLock {
            check(!closed) { "Vault is closed" }
            block()
        }
    }

    companion object {
        private val coordinator = Mutex()
        private val openRoots = mutableSetOf<File>()

        suspend fun open(
            context: Context,
            vaultId: UUID,
            authorize: suspend (UUID) -> Boolean,
            onCommitStage: (VaultCommitStage) -> Unit = {},
        ): ProtectedVault = openVault(context, vaultId, authorize, onCommitStage, null)

        suspend fun openWithDeviceCredential(
            context: Context,
            vaultId: UUID,
            validitySeconds: Int,
            authorize: suspend (UUID) -> Boolean,
        ): ProtectedVault {
            require(validitySeconds > 0)
            return openVault(context, vaultId, authorize, {}, validitySeconds)
        }

        private suspend fun openVault(
            context: Context,
            vaultId: UUID,
            authorize: suspend (UUID) -> Boolean,
            onCommitStage: (VaultCommitStage) -> Unit,
            validitySeconds: Int?,
        ): ProtectedVault {
            var opened: ProtectedVault? = null
            try {
                return withContext(Dispatchers.IO) {
                    coordinator.withLock {
                        val credentialBound = validitySeconds != null
                        val directory = if (credentialBound) "credential-protected-vault" else "protected-vault"
                        val alias = if (credentialBound) "omninumeris-credential-vault-v1-$vaultId" else "omninumeris-prototype-v1-$vaultId"
                        val root = File(context.noBackupFilesDir, "$directory/$vaultId").canonicalFile
                        check(openRoots.add(root)) { "Vault is already open" }
                        var database: VaultDatabase? = null
                        try {
                            val blobs = File(root, "blobs")
                            check(blobs.isDirectory || blobs.mkdirs()) { "Cannot create vault storage" }
                            database = Room.databaseBuilder(context, VaultDatabase::class.java, File(root, "vault.db").path)
                                .addMigrations(VAULT_MIGRATION_1_2)
                                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                                .addCallback(object : RoomDatabase.Callback() {
                                    override fun onOpen(db: SupportSQLiteDatabase) {
                                        db.execSQL("PRAGMA synchronous = FULL")
                                    }
                                })
                                .build()
                            val vault = ProtectedVault(
                                root, database, AndroidVaultKeyWrapper(alias, validitySeconds),
                                authorize, onCommitStage, validitySeconds,
                            )
                            vault.recover()
                            vault.also { opened = it }
                        } catch (failure: Throwable) {
                            try {
                                database?.close()
                            } finally {
                                openRoots.remove(root)
                            }
                            throw failure
                        }
                    }
                }
            } catch (failure: Throwable) {
                try {
                    opened?.close()
                } catch (cleanup: Throwable) {
                    failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }

        private fun canonicalId(text: String): UUID = UUID.fromString(text).also { require(it.toString() == text) }

        private fun associatedData(id: UUID, revision: Long, blob: UUID) =
            "omninumeris:blob:v1:$id:$revision:$blob".toByteArray(Charsets.US_ASCII)

        private fun clear(snapshot: ProtectedNotebookSnapshot) {
            snapshot.document.fill(0)
            snapshot.thumbnail.fill(0)
        }

        private fun syncDirectory(directory: File) {
            val fd = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            try {
                check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) { "Vault storage is not a directory" }
                Os.fsync(fd)
            } finally {
                Os.close(fd)
            }
        }
    }
}
