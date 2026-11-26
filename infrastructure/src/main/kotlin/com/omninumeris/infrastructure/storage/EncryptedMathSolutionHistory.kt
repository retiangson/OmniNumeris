package com.omninumeris.infrastructure.storage

import com.omninumeris.application.MathSolutionHistory
import com.omninumeris.application.MathSolutionHistoryRead
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.TypedSolutionAnswer
import com.omninumeris.application.TypedSolutionKind
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.domain.math.MathParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

/** Standalone typed solutions use a different file and Keystore scope from calculator history. */
class EncryptedMathSolutionHistory(directory: File, dispatcher: CoroutineDispatcher) : MathSolutionHistory {
    private val file = EncryptedHistoryFile(
        directory = directory,
        name = "typed-solution-history.v1",
        keyAlias = "omninumeris-typed-solution-history-v1",
        historyScope = UUID.fromString("b396d4bb-f84a-4b43-9724-c2f655ec09a1"),
        historyAad = "omninumeris:typed-solution-history:v1".toByteArray(Charsets.US_ASCII),
        dispatcher = dispatcher,
        maxEntries = TypedSolutionFormat.MAX_ENTRIES,
        encode = TypedSolutionFormat::encode,
        decode = TypedSolutionFormat::decode,
    )

    override suspend fun read(): MathSolutionHistoryRead =
        file.read()?.let { MathSolutionHistoryRead.Available(it) } ?: MathSolutionHistoryRead.Unavailable

    override suspend fun save(record: TypedSolutionRecord): MathSolutionSave {
        if (!TypedSolutionFormat.accepts(record)) return MathSolutionSave.TooLarge
        return if (file.prepend(record) == null) MathSolutionSave.Unavailable else MathSolutionSave.Saved
    }
}

internal object TypedSolutionFormat {
    const val MAX_ENTRIES = 32
    private const val VERSION = 1
    private const val MAX_ANSWER_LENGTH = 4096

    fun accepts(record: TypedSolutionRecord): Boolean =
        record.original.length in 1..TypedSolutionRecord.MAX_ORIGINAL_LENGTH &&
            record.confirmed.length in 1..MathParser.MAX_INPUT_LENGTH &&
            record.answer.value.length <= MAX_ANSWER_LENGTH && record.engineVersion > 0

    fun encode(records: List<TypedSolutionRecord>): ByteArray {
        require(records.size <= MAX_ENTRIES && records.all(::accepts))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeInt(records.size)
            for (entry in records) {
                output.writeLong(entry.id.mostSignificantBits)
                output.writeLong(entry.id.leastSignificantBits)
                output.writeUTF(entry.original)
                output.writeUTF(entry.confirmed)
                output.writeUTF(entry.answer.kind.name)
                output.writeUTF(entry.answer.value)
                output.writeInt(entry.engineVersion)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<TypedSolutionRecord> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == VERSION)
        val count = input.readInt()
        require(count in 0..MAX_ENTRIES)
        val entries = List(count) {
            TypedSolutionRecord(
                UUID(input.readLong(), input.readLong()),
                input.readUTF(),
                input.readUTF(),
                TypedSolutionAnswer(TypedSolutionKind.valueOf(input.readUTF()), input.readUTF()),
                input.readInt(),
            ).also { require(accepts(it)) }
        }
        require(input.read() == -1)
        entries
    }
}
