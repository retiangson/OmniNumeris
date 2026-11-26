package com.omninumeris.domain.graph

import com.omninumeris.domain.math.MathExpression
import com.omninumeris.domain.math.MathOperator
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class PlotViewport(val left: Double, val right: Double, val bottom: Double, val top: Double) {
    val width: Double get() = right - left
    val height: Double get() = top - bottom

    init {
        require(left >= -WORLD_LIMIT && right <= WORLD_LIMIT && width >= 1e-6)
        require(bottom >= -WORLD_LIMIT && top <= WORLD_LIMIT && height >= 1e-6)
    }

    // Normalized screen coordinates increase rightward and downward.
    fun screenX(x: Double): Double = (x - left) / width
    fun screenY(y: Double): Double = (top - y) / height
    fun worldX(x: Double): Double = left + x * width
    fun worldY(y: Double): Double = top - y * height

    fun transform(focusX: Double, focusY: Double, panX: Double, panY: Double, zoom: Double): PlotViewport {
        if (!focusX.isFinite() || !focusY.isFinite() || !panX.isFinite() || !panY.isFinite() ||
            !zoom.isFinite() || zoom <= 0.0
        ) return this

        val x = focusX.coerceIn(0.0, 1.0)
        val y = focusY.coerceIn(0.0, 1.0)
        val newWidth = (width / zoom).coerceIn(MIN_NAVIGATION_SPAN, 2 * WORLD_LIMIT)
        val newHeight = (height / zoom).coerceIn(MIN_NAVIGATION_SPAN, 2 * WORLD_LIMIT)
        val newLeft = (worldX(x) - x * newWidth - panX * newWidth)
            .coerceIn(-WORLD_LIMIT, WORLD_LIMIT - newWidth)
        val newTop = (worldY(y) + y * newHeight + panY * newHeight)
            .coerceIn(-WORLD_LIMIT + newHeight, WORLD_LIMIT)
        return PlotViewport(
            newLeft, (newLeft + newWidth).coerceAtMost(WORLD_LIMIT),
            (newTop - newHeight).coerceAtLeast(-WORLD_LIMIT), newTop,
        )
    }

    companion object {
        private const val WORLD_LIMIT = 1e6
        private const val MIN_NAVIGATION_SPAN = 1e-4
        val Default = PlotViewport(-10.0, 10.0, -10.0, 10.0)
    }
}

data class PlotPoint(val x: Double, val y: Double)
data class SampledPlot(val segments: List<List<PlotPoint>>)

object PlotSampler {
    fun sample(
        expression: MathExpression,
        viewport: PlotViewport = PlotViewport.Default,
        intervals: Int = 400,
        checkCancelled: () -> Unit = {},
    ): SampledPlot {
        require(intervals in 8..1_024)
        val segments = mutableListOf<List<PlotPoint>>()
        var active = mutableListOf<PlotPoint>()
        fun endSegment() {
            if (active.size >= 2) segments.add(active)
            active = mutableListOf()
        }

        var previousX = viewport.left
        var previousY = evaluate(expression, Interval(previousX, previousX))?.low
        for (index in 1..intervals) {
            checkCancelled()
            val x = if (index == intervals) viewport.right
                else viewport.left + (viewport.right - viewport.left) * index / intervals
            val y = evaluate(expression, Interval(x, x))?.low
            val edge = if (previousY != null && y != null &&
                evaluate(expression, Interval(previousX, x)) != null) {
                clip(PlotPoint(previousX, previousY), PlotPoint(x, y), viewport)
            } else null

            if (edge == null) {
                endSegment()
            } else {
                val (start, end) = edge
                if (active.lastOrNull() != start) {
                    endSegment()
                    active.add(start)
                }
                if (active.last() != end) active.add(end)
            }
            previousX = x
            previousY = y
        }
        endSegment()
        return SampledPlot(segments)
    }

    private fun clip(first: PlotPoint, last: PlotPoint, viewport: PlotViewport): Pair<PlotPoint, PlotPoint>? {
        if (max(first.y, last.y) < viewport.bottom || min(first.y, last.y) > viewport.top) return null
        fun endpoint(point: PlotPoint): PlotPoint {
            val ordinate = point.y.coerceIn(viewport.bottom, viewport.top)
            if (ordinate == point.y) return point
            // Scaling before subtraction keeps interpolation finite for large opposite-signed ordinates.
            val scale = max(abs(first.y), abs(last.y))
            val fraction = ((ordinate / scale - first.y / scale) / (last.y / scale - first.y / scale))
                .coerceIn(0.0, 1.0)
            return PlotPoint(first.x + (last.x - first.x) * fraction, ordinate)
        }
        return endpoint(first) to endpoint(last)
    }
}

internal data class Interval(val low: Double, val high: Double) {
    val includesZero: Boolean get() = low <= 0.0 && high >= 0.0
}

internal fun evaluate(expression: MathExpression, x: Interval): Interval? = when (expression) {
    is MathExpression.Number -> expression.text.toDoubleOrNull()?.let { finite(it, it) }
    MathExpression.Variable -> x
    is MathExpression.Negate -> evaluate(expression.expression, x)?.let { Interval(-it.high, -it.low) }
    is MathExpression.SquareRoot -> evaluate(expression.expression, x)?.let {
        if (it.low < 0.0) null else Interval(StrictMath.sqrt(it.low), StrictMath.sqrt(it.high))
    }
    is MathExpression.Binary -> {
        val left = evaluate(expression.left, x)
        val right = evaluate(expression.right, x)
        if (left == null || right == null) null else combine(left, right, expression.operator)
    }
}

private fun combine(left: Interval, right: Interval, operator: MathOperator): Interval? = when (operator) {
    MathOperator.Add -> finite(left.low + right.low, left.high + right.high)
    MathOperator.Subtract -> finite(left.low - right.high, left.high - right.low)
    MathOperator.Multiply -> corners(left, right) { a, b -> a * b }
    MathOperator.Divide -> if (right.includesZero) null else corners(left, right) { a, b -> a / b }
    MathOperator.Power -> power(left, right)
}

private fun power(base: Interval, exponent: Interval): Interval? {
    val value = exponent.low
    val fixedInteger = value == exponent.high && value == floor(value)
    if (fixedInteger) {
        if (value <= 0.0 && base.includesZero) return null
        val first = StrictMath.pow(base.low, value)
        val last = StrictMath.pow(base.high, value)
        val minimum = if (base.includesZero && value > 0.0 && value % 2.0 == 0.0) 0.0 else min(first, last)
        return finite(minimum, max(first, last))
    }
    if (base.low < 0.0 || (base.includesZero && exponent.low <= 0.0)) return null
    return corners(base, exponent, StrictMath::pow)
}

private inline fun corners(first: Interval, second: Interval, operation: (Double, Double) -> Double): Interval? {
    val a = operation(first.low, second.low)
    val b = operation(first.low, second.high)
    val c = operation(first.high, second.low)
    val d = operation(first.high, second.high)
    return finite(minOf(a, b, c, d), maxOf(a, b, c, d))
}

private fun finite(low: Double, high: Double): Interval? =
    if (low.isFinite() && high.isFinite()) Interval(low, high) else null
