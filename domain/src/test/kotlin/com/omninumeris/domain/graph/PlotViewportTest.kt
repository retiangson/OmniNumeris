package com.omninumeris.domain.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotViewportTest {
    @Test
    fun worldAndScreenRoundTripWithDownwardScreenY() {
        val viewport = PlotViewport(-2.0, 12.0, -6.0, 2.0)
        assertEquals(5.0, viewport.worldX(viewport.screenX(5.0)), 1e-12)
        assertEquals(-3.0, viewport.worldY(viewport.screenY(-3.0)), 1e-12)
        assertEquals(0.0, viewport.screenY(viewport.top), 1e-12)
        assertEquals(1.0, viewport.screenY(viewport.bottom), 1e-12)
    }

    @Test
    fun zoomKeepsWorldPointUnderMovingFocalPoint() {
        val start = PlotViewport.Default
        val zoomed = start.transform(0.2, 0.7, 0.15, -0.25, 2.0)
        assertEquals(start.worldX(0.2), zoomed.worldX(0.35), 1e-12)
        assertEquals(start.worldY(0.7), zoomed.worldY(0.45), 1e-12)
        assertEquals(start.width / 2, zoomed.width, 1e-12)
        assertEquals(start.height / 2, zoomed.height, 1e-12)
    }

    @Test
    fun panningMovesBothAxesInTheDirectionOfTheGesture() {
        val viewport = PlotViewport.Default.transform(0.5, 0.5, 0.2, -0.1, 1.0)
        assertEquals(0.7, viewport.screenX(0.0), 1e-12)
        assertEquals(0.4, viewport.screenY(0.0), 1e-12)
        assertEquals(20.0, viewport.width, 1e-12)
        assertEquals(20.0, viewport.height, 1e-12)
    }

    @Test
    fun extremeZoomAndPanStayFiniteWithinWorldBounds() {
        val start = PlotViewport.Default
        val zoomed = start.transform(0.5, 0.5, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE)
        assertEquals(-1e6, zoomed.left, 0.0)
        assertEquals(-1e6, zoomed.bottom, 0.0)
        assertTrue(zoomed.width in 9e-5..1.1e-4)
        assertTrue(zoomed.height in 9e-5..1.1e-4)
        assertEquals(PlotViewport(-1e6, 1e6, -1e6, 1e6),
            zoomed.transform(0.5, 0.5, 0.0, 0.0, Double.MIN_VALUE))
        assertEquals(start, start.transform(0.5, 0.5, 0.0, 0.0, Double.NaN))
    }
}
