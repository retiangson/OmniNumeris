package com.omninumeris.infrastructure.storage

import android.util.AtomicFile
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class EncryptedHistoryFile<T>(
    directory: File,
    name: String,
    keyAlias: String,
    private val historyScope: UUID,
    private val historyAad: ByteArray,
    private val dispatcher: CoroutineDispatcher,
    private val maxEntries: Int,
    private val encode: (List<T>) -> ByteArray,
    private val decode: (ByteArray) -> List<T>,
) {
    private val file = AtomicFile(File(directory, name))
    private val wrapper = AndroidVaultKeyWrapper(keyAlias)
    private val lock = Mutex()

    suspend fun read(): List<T>? = access { readEntries() }

    suspend fun prepend(entry: T): List<T>? = access {
        val entries = (listOf(entry) + readEntries()).take(maxEntries)
        persistEntries(entries)
        entries
    }

    private suspend fun access(operation: () -> List<T>): List<T>? = withContext(dispatcher) {
        lock.withLock {
            try {
                operation()
            } catch (_: IOException) {
                null
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: ProviderException) {
                null
            }
        }
    }

    private fun readEntries(): List<T> {
        val record = try {
            readEncryptedFile()
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            return emptyList()
        }
        val wrapped = record.copyOfRange(0, AuthenticatedEncryption.WRAPPED_KEY_BYTES)
        val rawKey = wrapper.unwrap(historyScope, wrapped)
        try {
            val ciphertext = record.copyOfRange(wrapped.size, record.size)
            val plaintext = AuthenticatedEncryption.open(AuthenticatedEncryption.aes(rawKey), historyAad, ciphertext)
            return try {
                decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        } finally {
            rawKey.fill(0)
        }
    }

    private fun persistEntries(entries: List<T>) {
        val plaintext = encode(entries)
        val rawKey = AuthenticatedEncryption.randomBytes(AuthenticatedEncryption.KEY_BYTES)
        val record = try {
            val wrapped = wrapper.wrap(historyScope, rawKey, allowCreate = true)
            wrapped + AuthenticatedEncryption.seal(AuthenticatedEncryption.aes(rawKey), historyAad, plaintext)
        } finally {
            rawKey.fill(0)
            plaintext.fill(0)
        }
        val pending = file.startWrite()
        try {
            pending.write(record)
            pending.fd.sync()
        } catch (failure: IOException) {
            file.failWrite(pending)
            throw failure
        }
        file.finishWrite(pending)
        if (!readEncryptedFile().contentEquals(record)) throw IOException("History commit could not be verified")
    }

    private fun readEncryptedFile(): ByteArray = file.openRead().use { input ->
        val size = input.channel.size()
        val minimum = AuthenticatedEncryption.WRAPPED_KEY_BYTES + AuthenticatedEncryption.NONCE_BYTES +
            AuthenticatedEncryption.TAG_BYTES
        require(size in minimum.toLong()..MAX_CIPHERTEXT_BYTES)
        ByteArray(size.toInt()).also { record ->
            val reader = DataInputStream(input)
            reader.readFully(record)
            require(reader.read() == -1)
        }
    }

    private companion object {
        const val MAX_CIPHERTEXT_BYTES = 2L * 1024 * 1024
    }
}

