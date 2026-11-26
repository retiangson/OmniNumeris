package com.omninumeris.infrastructure.storage

import com.omninumeris.application.CalculatorHistory
import com.omninumeris.application.CalculatorHistoryEntry
import com.omninumeris.application.CalculatorHistoryResult
import com.omninumeris.application.CalculatorValueKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

/** Holds standalone calculator data in a dedicated app-private Keystore and file scope. */
class EncryptedCalculatorHistory(directory: File, dispatcher: CoroutineDispatcher) : CalculatorHistory {
    private val file = EncryptedHistoryFile(
        directory = directory,
        name = "calculator-history.v1",
        keyAlias = "omninumeris-calculator-history-v1",
        historyScope = UUID.fromString("e49bf51b-bf17-469d-a36d-10845dacdc5b"),
        historyAad = "omninumeris:standalone-calculator-history:v1".toByteArray(Charsets.US_ASCII),
        dispatcher = dispatcher,
        maxEntries = CalculatorHistoryFormat.MAX_ENTRIES,
        encode = CalculatorHistoryFormat::encode,
        decode = CalculatorHistoryFormat::decode,
    )

    override suspend fun read(): CalculatorHistoryResult =
        file.read()?.let { CalculatorHistoryResult.Available(it) } ?: CalculatorHistoryResult.Unavailable

    override suspend fun append(entry: CalculatorHistoryEntry): CalculatorHistoryResult {
        if (!CalculatorHistoryFormat.accepts(entry)) return CalculatorHistoryResult.EntryTooLarge
        return file.prepend(entry)?.let { CalculatorHistoryResult.Available(it) } ?: CalculatorHistoryResult.Unavailable
    }
}

internal object CalculatorHistoryFormat {
    const val MAX_ENTRIES = 32
    private const val VERSION = 1
    private const val MAX_TEXT_LENGTH = 8192

    fun accepts(entry: CalculatorHistoryEntry): Boolean =
        entry.mode.length in 1..32 && entry.input.length <= MAX_TEXT_LENGTH && entry.result.length <= MAX_TEXT_LENGTH

    fun encode(entries: List<CalculatorHistoryEntry>): ByteArray {
        require(entries.size <= MAX_ENTRIES && entries.all(::accepts))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeInt(entries.size)
            for (entry in entries) {
                output.writeLong(entry.id.mostSignificantBits)
                output.writeLong(entry.id.leastSignificantBits)
                output.writeUTF(entry.mode)
                output.writeUTF(entry.input)
                output.writeUTF(entry.result)
                output.writeUTF(entry.kind.name)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<CalculatorHistoryEntry> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == VERSION)
        val count = input.readInt()
        require(count in 0..MAX_ENTRIES)
        val entries = List(count) {
            CalculatorHistoryEntry(
                UUID(input.readLong(), input.readLong()),
                input.readUTF(),
                input.readUTF(),
                input.readUTF(),
                CalculatorValueKind.valueOf(input.readUTF()),
            ).also { require(accepts(it)) }
        }
        require(input.read() == -1)
        entries
    }
}

