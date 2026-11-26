package com.omninumeris.app.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorPanelPlacementTest {
    @Test
    fun moveClampsTheFullPanelAtEveryEdge() {
        val initial = CalculatorPanelPlacement()
        assertEquals(CalculatorPanelFrame(560f, 0f, 440f, 680f), initial.frame(1000f, 900f))
        val bottomLeft = initial.move(1000f, 900f, -1000f, 1000f)
        assertEquals(CalculatorPanelFrame(0f, 220f, 440f, 680f), bottomLeft.frame(1000f, 900f))
        assertEquals(initial, bottomLeft.move(1000f, 900f, 1000f, -1000f))
    }

    @Test
    fun resizingKeepsTheTopLeftAndHonorsBothSizeBounds() {
        val moved = CalculatorPanelPlacement().move(1000f, 900f, -400f, 120f)
        val large = moved.resize(1000f, 900f, 1000f, 1000f).frame(1000f, 900f)
        assertEquals(160f, large.left, 0.001f)
        assertEquals(120f, large.top, 0.001f)
        assertEquals(1000f, large.left + large.width, 0.001f)
        assertEquals(900f, large.top + large.height, 0.001f)

        val small = moved.resize(1000f, 900f, -1000f, -1000f).frame(1000f, 900f)
        assertEquals(large.left, small.left, 0.001f)
        assertEquals(large.top, small.top, 0.001f)
        assertTrue(small.width < large.width && small.height < large.height)
    }

    @Test
    fun compactBoundsOrKeyboardDoNotOverwriteThePreferredSizeAndPosition() {
        val initial = CalculatorPanelPlacement().move(1000f, 900f, -200f, 110f)
        assertEquals(CalculatorPanelFrame(0f, 0f, 220f, 180f), initial.frame(220f, 180f))
        val clamped = initial.move(220f, 180f, 25f, 25f).resize(220f, 180f, 100f, 100f)
        assertEquals(initial, clamped)
        assertEquals(initial.frame(1000f, 900f), clamped.frame(1000f, 900f))
    }
}
