package com.omninumeris.infrastructure.canvas

import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTip
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrototypeInkJournalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `committed ink is recovered by a new journal and retries are idempotent`() {
        val location = temporaryFolder.newFolder()
        val ink = InkStroke(UUID.randomUUID(), listOf(InkSample(42f, -18f, 0.25f)))
        PrototypeInkJournal(location).commit(ink)
        PrototypeInkJournal(location).commit(ink)

        val restored = PrototypeInkJournal(location).recover()
        assertEquals(0, restored.unreadableRecords)
        assertEquals(ink.id, restored.strokes.single().id)
        assertEquals(ink.samples, restored.strokes.single().samples)
    }

    @Test
    fun `legacy strokes keep their width and erased strokes survive restart`() {
        val location = temporaryFolder.newFolder()
        val legacyId = UUID.randomUUID()
        val payload = ByteBuffer.allocate(20)
            .putInt(0x4f574901).putInt(1).putFloat(5f).putFloat(9f).putFloat(0.5f).array()
        val crc = CRC32().apply { update(payload) }.value.toInt()
        File(location, "$legacyId.stroke").writeBytes(payload + ByteBuffer.allocate(4).putInt(crc).array())

        val journal = PrototypeInkJournal(location)
        val fine = InkStroke(UUID.randomUUID(), listOf(InkSample(12f, 14f, 0.5f)), InkTip.Fine)
        journal.commit(fine)
        assertEquals(InkTip.Bold, journal.recover().strokes.single { it.id == legacyId }.tip)
        assertEquals(InkTip.Fine, journal.recover().strokes.single { it.id == fine.id }.tip)

        journal.setErased(legacyId, true)
        journal.setErased(legacyId, true)
        val reopened = PrototypeInkJournal(location)
        assertEquals(fine.id, reopened.recover().strokes.single().id)
        reopened.setErased(legacyId, false)
        reopened.setErased(legacyId, false)
        assertEquals(InkTip.Bold, journal.recover().strokes.single { it.id == legacyId }.tip)
        assertEquals(2, journal.recover().strokes.size)
    }

    @Test
    fun `an interrupted write and damaged record preserve other commits`() {
        val location = temporaryFolder.newFolder()
        val journal = PrototypeInkJournal(location)
        val damaged = InkStroke(UUID.randomUUID(), listOf(InkSample(3f, 5f, 0.4f)))
        val good = InkStroke(UUID.randomUUID(), listOf(InkSample(7f, 9f, 0.8f)))
        journal.commit(damaged)
        journal.commit(good)
        File(location, "interrupted.pending").writeText("partial")
        val damagedFile = File(location, "${damaged.id}.stroke")
        damagedFile.writeBytes(damagedFile.readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() })

        val restored = PrototypeInkJournal(location).recover()
        assertEquals(1, restored.unreadableRecords)
        assertEquals(good.id, restored.strokes.single().id)
    }

    @Test(expected = IOException::class)
    fun `conflicting stroke IDs keep the original commit`() {
        val journal = PrototypeInkJournal(temporaryFolder.newFolder())
        val id = UUID.randomUUID()
        journal.commit(InkStroke(id, listOf(InkSample(1f, 2f, 0.5f))))
        journal.commit(InkStroke(id, listOf(InkSample(3f, 4f, 0.5f))))
    }
}
