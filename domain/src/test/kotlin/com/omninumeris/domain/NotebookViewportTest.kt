package com.omninumeris.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookViewportTest {
    @Test
    fun zoomKeepsTheSamePagePointUnderTheMovedGestureCenter() {
        val original = NotebookViewport(left = 10f, top = 20f, zoom = 1f)
        val updated = original.transform(anchorX = 80f, anchorY = 120f, panX = 10f, panY = -20f, scale = 2f)
        assertEquals(90f, updated.left + 90f / updated.zoom, 0.001f)
        assertEquals(140f, updated.top + 100f / updated.zoom, 0.001f)
    }

    @Test
    fun zoomAndPanStayInTheRepresentableRange() {
        val updated = NotebookViewport.Initial.transform(0f, 0f, -3_500_000f, 3_500_000f, 100f)
        assertEquals(3f, updated.zoom, 0f)
        assertEquals(NotebookViewport.LIMIT, updated.left, 0f)
        assertEquals(-NotebookViewport.LIMIT, updated.top, 0f)
    }
}
