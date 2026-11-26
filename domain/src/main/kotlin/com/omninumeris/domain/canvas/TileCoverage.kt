package com.omninumeris.domain.canvas

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

const val INK_TILE_SIZE = 256

data class InkTile(val column: Int, val row: Int)

fun InkStroke.coveredTiles(): Set<InkTile> = buildSet {
    val first = samples.first()
    addSegment(first, first)
    for (index in 1 until samples.size) {
        addSegment(samples[index - 1], samples[index])
    }
}

private fun MutableSet<InkTile>.addSegment(from: InkSample, to: InkSample) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val steps = maxOf(1, ceil(maxOf(abs(dx), abs(dy)) / (INK_TILE_SIZE / 2f)).toInt())
    val radius = inkWidth((from.pressure + to.pressure) / 2f) / 2f + 1f
    for (index in 0 until steps) {
        val start = index.toFloat() / steps
        val end = (index + 1).toFloat() / steps
        val x1 = from.x + dx * start
        val x2 = from.x + dx * end
        val y1 = from.y + dy * start
        val y2 = from.y + dy * end
        val left = floor((minOf(x1, x2) - radius) / INK_TILE_SIZE).toInt()
        val right = floor((maxOf(x1, x2) + radius) / INK_TILE_SIZE).toInt()
        val top = floor((minOf(y1, y2) - radius) / INK_TILE_SIZE).toInt()
        val bottom = floor((maxOf(y1, y2) + radius) / INK_TILE_SIZE).toInt()
        for (row in top..bottom) {
            for (column in left..right) {
                if (intersectsTile(x1, y1, x2, y2, radius, column, row)) add(InkTile(column, row))
            }
        }
    }
}

private fun intersectsTile(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    radius: Float,
    column: Int,
    row: Int,
): Boolean {
    val xRange = clipAxis(x1, x2, column, radius) ?: return false
    val yRange = clipAxis(y1, y2, row, radius) ?: return false
    return maxOf(0f, xRange.start, yRange.start) <= minOf(1f, xRange.endInclusive, yRange.endInclusive)
}

private fun clipAxis(from: Float, to: Float, tile: Int, radius: Float): ClosedFloatingPointRange<Float>? {
    val minimum = tile * INK_TILE_SIZE - radius
    val maximum = (tile + 1) * INK_TILE_SIZE + radius
    val delta = to - from
    if (delta == 0f) return if (from in minimum..maximum) 0f..1f else null
    val entry = (minimum - from) / delta
    val exit = (maximum - from) / delta
    return minOf(entry, exit)..maxOf(entry, exit)
}
