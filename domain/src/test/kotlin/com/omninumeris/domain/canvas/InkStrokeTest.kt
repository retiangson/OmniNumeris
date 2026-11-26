package com.omninumeris.domain.canvas

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkStrokeTest {
    @Test
    fun `stylus pressure affects width and finger pressure stays stable`() {
        val light = InkSample.fromPointer(0f, 0f, 0.1f, stylus = true)
        val heavy = InkSample.fromPointer(0f, 0f, 1.2f, stylus = true)
        val finger = InkSample.fromPointer(0f, 0f, Float.NaN, stylus = false)

        assertEquals(1f, heavy.pressure, 0f)
        assertEquals(0.5f, finger.pressure, 0f)
        assertTrue(inkWidth(heavy.pressure) > inkWidth(light.pressure))
    }

    @Test
    fun `eraser hits a line between samples and accounts for pen width`() {
        val samples = listOf(InkSample(0f, 0f, 1f), InkSample(50f, 0f, 1f))
        val fine = InkStroke(UUID.randomUUID(), samples, InkTip.Fine)
        val bold = InkStroke(UUID.randomUUID(), samples, InkTip.Bold)

        assertTrue(fine.isNear(25f, 2f, 1f))
        assertFalse(fine.isNear(25f, 4f, 1f))
        assertTrue(bold.isNear(25f, 4f, 1f))
        assertFalse(bold.isNear(60f, 0f, 1f))
    }

    @Test
    fun `stroke snapshots its input and crosses a negative tile boundary`() {
        val samples = mutableListOf(InkSample(-10f, 20f, 0.5f), InkSample(10f, 20f, 0.5f))
        val stroke = InkStroke(UUID.randomUUID(), samples)
        samples.clear()

        assertEquals(2, stroke.samples.size)
        assertEquals(setOf(InkTile(-1, 0), InkTile(0, 0)), stroke.coveredTiles())
    }

    @Test
    fun `long diagonal schedules only tiles near its path`() {
        val stroke = InkStroke(UUID.randomUUID(), listOf(InkSample(20f, 20f, 0f), InkSample(5020f, 5020f, 1f)))
        val tiles = stroke.coveredTiles()

        assertTrue(InkTile(19, 19) in tiles)
        assertFalse(InkTile(19, 0) in tiles)
        assertTrue(tiles.size <= 60)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `samples reject unusable coordinates`() {
        InkSample(Float.POSITIVE_INFINITY, 0f, 0.5f)
    }
}
