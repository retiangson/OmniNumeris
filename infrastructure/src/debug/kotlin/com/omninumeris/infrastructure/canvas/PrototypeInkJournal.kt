package com.omninumeris.infrastructure.canvas

import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTip
import com.omninumeris.domain.canvas.MAX_INK_SAMPLES
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.CRC32

data class InkRecovery(val strokes: List<InkStroke>, val unreadableRecords: Int)

/** Debug canvas data only. Call on an IO thread. Each committed stroke owns one versioned file. */
class PrototypeInkJournal(private val directory: File) {
    fun commit(stroke: InkStroke) = synchronized(journalLock) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create canvas journal")
        val target = File(directory, "${stroke.id}.stroke")
        if (File(directory, "${stroke.id}.erased").exists()) throw IOException("Stroke ID was erased")
        if (target.exists()) {
            val existing = readStroke(target)
            if (existing.samples != stroke.samples || existing.tip != stroke.tip) throw IOException("Conflicting stroke ID")
            return@synchronized
        }

        val pending = File.createTempFile("${stroke.id}-", ".pending", directory)
        try {
            val payload = encode(stroke)
            val checksum = CRC32().also { it.update(payload) }.value
            FileOutputStream(pending).use { stream ->
                val output = DataOutputStream(stream)
                output.write(payload)
                output.writeInt(checksum.toInt())
                output.flush()
                stream.fd.sync()
            }
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: Exception) {
            try {
                Files.deleteIfExists(pending.toPath())
            } catch (cleanupFailure: IOException) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
        Unit
    }

    fun setErased(id: UUID, erased: Boolean) = synchronized(journalLock) {
        val visible = File(directory, "$id.stroke").toPath()
        val hidden = File(directory, "$id.erased").toPath()
        val source = if (erased) visible else hidden
        val destination = if (erased) hidden else visible
        if (Files.exists(destination)) {
            if (Files.exists(source)) throw IOException("Conflicting stroke copies")
        } else {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        }
        Unit
    }

    fun recover(): InkRecovery = synchronized(journalLock) {
        if (!directory.exists()) return@synchronized InkRecovery(emptyList(), 0)
        val entries = directory.listFiles { file -> file.extension == "stroke" }
            ?: throw IOException("Cannot read canvas journal")
        val strokes = ArrayList<InkStroke>(entries.size)
        var unreadable = 0
        for (file in entries.sortedBy { it.name }) {
            try {
                strokes.add(readStroke(file))
            } catch (_: IOException) {
                unreadable++
            } catch (_: IllegalArgumentException) {
                unreadable++
            }
        }
        InkRecovery(strokes, unreadable)
    }

    private fun encode(stroke: InkStroke): ByteArray {
        val bytes = ByteArrayOutputStream(HEADER_SIZE + stroke.samples.size * SAMPLE_SIZE)
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC_V2)
            output.writeInt(stroke.samples.size)
            output.writeInt(when (stroke.tip) {
                InkTip.Fine -> 1
                InkTip.Medium -> 2
                InkTip.Bold -> 3
            })
            for (sample in stroke.samples) {
                output.writeFloat(sample.x)
                output.writeFloat(sample.y)
                output.writeFloat(sample.pressure)
            }
        }
        return bytes.toByteArray()
    }

    private fun readStroke(file: File): InkStroke {
        val id = UUID.fromString(file.nameWithoutExtension)
        val byteCount = file.length()
        require(byteCount in (HEADER_SIZE + SAMPLE_SIZE + CHECKSUM_SIZE).toLong()..MAX_RECORD_SIZE.toLong())
        val bytes = ByteArray(byteCount.toInt())
        DataInputStream(file.inputStream()).use { input ->
            input.readFully(bytes)
            require(input.read() == -1)
        }
        val payloadSize = bytes.size - CHECKSUM_SIZE
        val checksum = CRC32().also { it.update(bytes, 0, payloadSize) }.value.toInt()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = input.readInt()
            require(magic == MAGIC_V1 || magic == MAGIC_V2)
            val size = input.readInt()
            val headerSize = if (magic == MAGIC_V1) HEADER_SIZE else HEADER_SIZE + Int.SIZE_BYTES
            require(size in 1..MAX_INK_SAMPLES && payloadSize == headerSize + size * SAMPLE_SIZE)
            val tip = if (magic == MAGIC_V1) InkTip.Bold else when (input.readInt()) {
                1 -> InkTip.Fine
                2 -> InkTip.Medium
                3 -> InkTip.Bold
                else -> throw IllegalArgumentException("Unknown stroke tip")
            }
            val samples = List(size) { InkSample(input.readFloat(), input.readFloat(), input.readFloat()) }
            require(input.readInt() == checksum)
            return InkStroke(id, samples, tip)
        }
    }

    companion object {
        private val journalLock = Any()
        private const val MAGIC_V1 = 0x4f574901
        private const val MAGIC_V2 = 0x4f574902
        private const val SAMPLE_SIZE = 3 * Float.SIZE_BYTES
        private const val HEADER_SIZE = 2 * Int.SIZE_BYTES
        private const val CHECKSUM_SIZE = Int.SIZE_BYTES
        private const val MAX_RECORD_SIZE = HEADER_SIZE + Int.SIZE_BYTES + SAMPLE_SIZE * MAX_INK_SAMPLES + CHECKSUM_SIZE
    }
}
