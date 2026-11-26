package com.omninumeris.domain.canvas

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InkLassoTest {
    private val loop = listOf(InkPoint(1.0, 1.0), InkPoint(9.0, 1.0), InkPoint(9.0, 8.0), InkPoint(1.0, 8.0))

    @Test
    fun zoomAndPanSelectTheSameStrokeFromTheSource() {
        val viewport = InkViewport(4.0, 120.0, -30.0)
        val screenLoop = loop.map { InkPoint(it.x * 4 + 120, it.y * 4 - 30) }
        val selected = stroke(3f to 5f, 5f to 6f)
        val excluded = stroke(3f to 12f)
        val sourceLoop = screenLoop.map { requireNotNull(viewport.source(it.x, it.y)) }
        assertEquals(loop, sourceLoop)
        assertEquals(InkLassoHits.Selected(listOf(selected)), requireNotNull(InkLasso.create(sourceLoop)).select(listOf(excluded, selected)))
    }

    @Test
    fun crossingSegmentsAndBoundaryDotsHitButConcaveGapDoesNot() {
        val lasso = requireNotNull(InkLasso.create(listOf(
            InkPoint(0.0, 0.0), InkPoint(8.0, 0.0), InkPoint(8.0, 2.0),
            InkPoint(2.0, 2.0), InkPoint(2.0, 8.0), InkPoint(0.0, 8.0),
        )))
        val crossing = stroke(-2f to 1f, 10f to 1f)
        val boundary = stroke(0f to 5f)
        val gap = stroke(5f to 5f)
        assertEquals(InkLassoHits.Selected(listOf(crossing, boundary)), lasso.select(listOf(crossing, boundary, gap)))
        assertEquals(InkLassoHits.Selected(emptyList()), lasso.select(listOf(gap)))
    }

    @Test
    fun invalidAndOversizedSelectionsFailExplicitly() {
        assertNull(InkLasso.create(listOf(InkPoint(0.0, 0.0), InkPoint(5.0, 5.0), InkPoint(10.0, 10.0))))
        assertNull(InkViewport(1.0, 0.0, 0.0).source(Double.NaN, 0.0))
        val lasso = requireNotNull(InkLasso.create(loop))
        assertEquals(InkLassoHits.TooComplex, lasso.select(List(65) { stroke(3f to 3f) }))
    }

    private fun stroke(vararg points: Pair<Float, Float>) =
        InkStroke(UUID.randomUUID(), points.map { (x, y) -> InkSample(x, y, 0.5f) })
}
