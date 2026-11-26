package com.omninumeris.domain.canvas

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class InkPoint(val x: Double, val y: Double)
data class InkSelectionBounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun overlaps(a: InkPoint, b: InkPoint): Boolean =
        max(a.x, b.x) >= left && min(a.x, b.x) <= right &&
            max(a.y, b.y) >= top && min(a.y, b.y) <= bottom
}

data class InkViewport(val scale: Double, val offsetX: Double, val offsetY: Double) {
    init {
        require(scale.isFinite() && scale > 0 && offsetX.isFinite() && offsetY.isFinite())
    }

    fun source(x: Double, y: Double): InkPoint? {
        val point = InkPoint((x - offsetX) / scale, (y - offsetY) / scale)
        val limit = MAX_INK_COORDINATE.toDouble()
        return point.takeIf { it.x in -limit..limit && it.y in -limit..limit }
    }
}

sealed interface InkLassoHits {
    data class Selected(val strokes: List<InkStroke>) : InkLassoHits
    data object TooComplex : InkLassoHits
}

class InkLasso private constructor(val points: List<InkPoint>, val bounds: InkSelectionBounds) {
    fun select(strokes: Iterable<InkStroke>, checkCancelled: () -> Unit = {}): InkLassoHits {
        val selected = ArrayList<InkStroke>()
        var comparisons = 0
        for (stroke in strokes) {
            checkCancelled()
            var previous = stroke.samples.first().point()
            var hit = false
            for (sample in stroke.samples) {
                if (++comparisons > MAX_TESTED_SEGMENTS) return InkLassoHits.TooComplex
                if (comparisons % 128 == 0) checkCancelled()
                val current = sample.point()
                if (bounds.overlaps(previous, current) && touches(previous, current)) {
                    hit = true
                    break
                }
                previous = current
            }
            if (hit) {
                if (selected.size == MAX_SELECTED_STROKES) return InkLassoHits.TooComplex
                selected.add(stroke)
            }
        }
        return InkLassoHits.Selected(selected.toList())
    }

    private fun touches(start: InkPoint, end: InkPoint): Boolean {
        var inside = false
        var a = points.last()
        for (b in points) {
            if (intersects(start, end, a, b)) return true
            if ((a.y > end.y) != (b.y > end.y) &&
                end.x < a.x + (end.y - a.y) * (b.x - a.x) / (b.y - a.y)
            ) inside = !inside
            a = b
        }
        return inside
    }

    companion object {
        const val MAX_POINTS = 192
        private const val MAX_TESTED_SEGMENTS = 80_000
        private const val MAX_SELECTED_STROKES = 64

        fun create(points: List<InkPoint>): InkLasso? {
            val limit = MAX_INK_COORDINATE.toDouble()
            if (points.size !in 3..MAX_POINTS || points.any { it.x !in -limit..limit || it.y !in -limit..limit }) return null
            val bounds = InkSelectionBounds(
                points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
            )
            // Use the first vertex as the origin to avoid subtracting large absolute products.
            val origin = points.first()
            var twiceArea = 0.0
            for (index in 1 until points.lastIndex) twiceArea += cross(origin, points[index], points[index + 1])
            if (abs(twiceArea) < 0.5) return null
            return InkLasso(points.toList(), bounds)
        }
    }
}

private fun InkSample.point() = InkPoint(x.toDouble(), y.toDouble())

private fun cross(a: InkPoint, b: InkPoint, c: InkPoint): Double =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

private fun intersects(a: InkPoint, b: InkPoint, c: InkPoint, d: InkPoint): Boolean {
    if (max(a.x, b.x) < min(c.x, d.x) || max(c.x, d.x) < min(a.x, b.x) ||
        max(a.y, b.y) < min(c.y, d.y) || max(c.y, d.y) < min(a.y, b.y)
    ) return false
    val ac = cross(a, b, c)
    val ad = cross(a, b, d)
    val ca = cross(c, d, a)
    val cb = cross(c, d, b)
    return (ac <= 0 && ad >= 0 || ac >= 0 && ad <= 0) &&
        (ca <= 0 && cb >= 0 || ca >= 0 && cb <= 0)
}
