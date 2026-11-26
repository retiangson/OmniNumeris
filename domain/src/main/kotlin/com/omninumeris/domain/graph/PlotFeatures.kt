package com.omninumeris.domain.graph

import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.ExactMathResult
import com.omninumeris.domain.math.ExactRational
import com.omninumeris.domain.math.MathExpression
import com.omninumeris.domain.math.MathInput
import com.omninumeris.domain.math.MathOperator
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.max

sealed interface PlotSolutions {
    data class Exact(val point: PlotCoordinate.Exact) : PlotSolutions
    data class Approximate(val points: List<PlotPoint>, val incomplete: Boolean) : PlotSolutions
    data object Coincident : PlotSolutions
    data object NoRealSamples : PlotSolutions
}

sealed interface PlotCoordinate {
    data class Exact(val x: ExactRational, val y: ExactRational) : PlotCoordinate
    data class Approximate(val point: PlotPoint) : PlotCoordinate

    fun toPlotPoint(): PlotPoint = when (this) {
        is Exact -> PlotPoint(x.toDouble(), y.toDouble())
        is Approximate -> point
    }
}

data class PlotIntercepts(val roots: PlotSolutions, val yIntercept: PlotCoordinate?)

object PlotFeatures {
    fun intercepts(
        expression: MathExpression,
        viewport: PlotViewport,
        checkCancelled: () -> Unit = {},
    ): PlotIntercepts {
        checkCancelled()
        val roots = exactSolutions(expression, MathExpression.Number("0"), viewport)
            ?: search(expression, viewport, checkCancelled) { x -> PlotPoint(x, 0.0) }
        val yIntercept = if (0.0 in viewport.left..viewport.right) {
            ExactMath.exactValueAt(expression, ExactRational.ZERO)?.let {
                PlotCoordinate.Exact(ExactRational.ZERO, it)
            } ?: value(expression, 0.0)?.let { PlotCoordinate.Approximate(PlotPoint(0.0, it)) }
        } else null
        return PlotIntercepts(roots, yIntercept)
    }

    fun intersections(
        first: MathExpression,
        second: MathExpression,
        viewport: PlotViewport,
        checkCancelled: () -> Unit = {},
    ): PlotSolutions {
        val difference = MathExpression.Binary(MathOperator.Subtract, first, second)
        if (first == second) {
            return if ((0..SCAN_INTERVALS).any { index ->
                    checkCancelled()
                    value(first, sampleX(viewport, index)) != null
                }) PlotSolutions.Coincident else PlotSolutions.NoRealSamples
        }
        checkCancelled()
        return exactSolutions(first, second, viewport) ?: search(difference, viewport, checkCancelled) { x ->
            value(first, x)?.let { PlotPoint(x, it) }
        }
    }

    private fun exactSolutions(
        first: MathExpression,
        second: MathExpression,
        viewport: PlotViewport,
    ): PlotSolutions? = when (val result = ExactMath.analyze(MathInput.Equation(first, second))) {
        is ExactMathResult.VerifiedLinearSolution -> {
            if (result.value.inRange(viewport)) {
                ExactMath.exactValueAt(first, result.value)?.let {
                    PlotSolutions.Exact(PlotCoordinate.Exact(result.value, it))
                }
            } else PlotSolutions.Approximate(emptyList(), false)
        }
        ExactMathResult.Identity -> PlotSolutions.Coincident
        ExactMathResult.Contradiction -> PlotSolutions.Approximate(emptyList(), false)
        is ExactMathResult.Rejected, is ExactMathResult.Value -> null
    }

    private fun search(
        expression: MathExpression,
        viewport: PlotViewport,
        checkCancelled: () -> Unit,
        point: (Double) -> PlotPoint?,
    ): PlotSolutions {
        checkCancelled()
        val fullRange = evaluate(expression, Interval(viewport.left, viewport.right))
        if (fullRange?.let { it.low == 0.0 && it.high == 0.0 } == true && isStructurallyZero(expression)) {
            return PlotSolutions.Coincident
        }
        if (fullRange != null && !fullRange.includesZero) return PlotSolutions.Approximate(emptyList(), false)

        val values = Array<Double?>(SCAN_INTERVALS + 1) { index ->
            checkCancelled()
            value(expression, sampleX(viewport, index))
        }
        if (values.all { it == null }) return PlotSolutions.NoRealSamples

        val candidates = mutableListOf<Double>()
        var incomplete = false
        fun refineMinimum(left: Double, right: Double, scale: Double) {
            if (evaluate(expression, Interval(left, right))?.includesZero != true) return
            when (val refined = minimum(expression, left, right, scale, checkCancelled)) {
                is Minimum.Found -> candidates.add(refined.x)
                Minimum.ExcludesZero -> Unit
                Minimum.Unresolved -> incomplete = true
            }
        }

        for (index in values.indices) {
            checkCancelled()
            val current = values[index] ?: continue
            val x = sampleX(viewport, index)
            val previous = values.getOrNull(index - 1)
            val next = values.getOrNull(index + 1)
            val left = sampleX(viewport, (index - 1).coerceAtLeast(0))
            val right = sampleX(viewport, (index + 1).coerceAtMost(SCAN_INTERVALS))

            if (current == 0.0) {
                // A zero plateau can arise from cancellation of large nearly equal numbers.
                val certified = previous != null && previous != 0.0 && evaluate(expression, Interval(left, x)) != null ||
                    next != null && next != 0.0 && evaluate(expression, Interval(x, right)) != null
                if (certified && previous != 0.0 && next != 0.0) candidates.add(x) else incomplete = true
                continue
            }

            if ((index > 0 && previous == null) || (index < SCAN_INTERVALS && next == null)) incomplete = true

            if (next != null && next != 0.0 && oppositeSigns(current, next)) {
                val enclosure = evaluate(expression, Interval(x, right))
                if (enclosure == null || !enclosure.includesZero) {
                    incomplete = true
                } else {
                    val bracket = ZeroBracket(x, current, right, next)
                    crossing(expression, bracket, checkCancelled)?.let(candidates::add) ?: run { incomplete = true }
                }
            }

            if (previous != null && next != null && previous != 0.0 && next != 0.0 &&
                !oppositeSigns(previous, current) && !oppositeSigns(current, next) &&
                abs(current) <= abs(previous) && abs(current) <= abs(next) &&
                (abs(current) < abs(previous) || abs(current) < abs(next))
            ) {
                refineMinimum(left, right, max(abs(previous), abs(next)))
            }
            if (index == 0 && next != null && !oppositeSigns(current, next) && abs(current) < abs(next)) {
                refineMinimum(x, right, abs(next))
            }
            if (index == SCAN_INTERVALS && previous != null && !oppositeSigns(previous, current) &&
                abs(current) < abs(previous)
            ) refineMinimum(left, x, abs(previous))
        }

        val points = mutableListOf<PlotPoint>()
        for (x in candidates.sorted()) {
            checkCancelled()
            if (points.lastOrNull()?.let { abs(it.x - x) <= 4 * xTolerance(x) } == true) continue
            if (points.size == MAX_POINTS) {
                incomplete = true
                break
            }
            val found = point(x)
            if (found != null) points.add(found) else incomplete = true
        }
        return PlotSolutions.Approximate(points, incomplete)
    }

    private fun crossing(
        expression: MathExpression,
        bracket: ZeroBracket,
        checkCancelled: () -> Unit,
    ): Double? {
        var low = bracket.left
        var high = bracket.right
        var lowValue = bracket.leftValue
        var highValue = bracket.rightValue
        val scale = max(abs(lowValue), abs(highValue))
        repeat(MAX_REFINEMENTS) {
            checkCancelled()
            val mid = midpoint(low, high)
            if (high - low <= xTolerance(mid)) {
                val best = if (abs(lowValue) <= abs(highValue)) low else high
                return best.takeIf { minOf(abs(lowValue), abs(highValue)) / scale <= RESIDUAL_RATIO }
            }
            val middleValue = value(expression, mid) ?: return null
            if (middleValue == 0.0) return mid
            if (oppositeSigns(lowValue, middleValue)) {
                high = mid
                highValue = middleValue
            } else {
                low = mid
                lowValue = middleValue
            }
        }
        return null
    }

    private fun minimum(
        expression: MathExpression,
        start: Double,
        end: Double,
        scale: Double,
        checkCancelled: () -> Unit,
    ): Minimum {
        var low = start
        var high = end
        repeat(MAX_REFINEMENTS) {
            checkCancelled()
            val mid = midpoint(low, high)
            if (high - low <= max(1e-14, 8 * Math.ulp(mid))) {
                val residual = value(expression, mid) ?: return Minimum.Unresolved
                val enclosure = evaluate(expression, Interval(low, high))
                return when {
                    enclosure == null -> Minimum.Unresolved
                    !enclosure.includesZero -> Minimum.ExcludesZero
                    abs(residual) / scale <= RESIDUAL_RATIO -> Minimum.Found(mid)
                    else -> Minimum.Unresolved
                }
            }
            val a = low + (high - low) / 3
            val b = high - (high - low) / 3
            val first = value(expression, a) ?: return Minimum.Unresolved
            val second = value(expression, b) ?: return Minimum.Unresolved
            when {
                abs(first) < abs(second) -> high = b
                abs(first) > abs(second) -> low = a
                else -> {
                    low = a
                    high = b
                }
            }
        }
        return Minimum.Unresolved
    }

    private fun sampleX(viewport: PlotViewport, index: Int): Double =
        if (index == SCAN_INTERVALS) viewport.right else viewport.worldX(index.toDouble() / SCAN_INTERVALS)

    private fun value(expression: MathExpression, x: Double): Double? =
        evaluate(expression, Interval(x, x))?.low

    // A collapsed binary64 enclosure can reflect rounding rather than identical functions.
    private fun isStructurallyZero(expression: MathExpression): Boolean = when (expression) {
        is MathExpression.Number -> expression.text.toBigDecimalOrNull()?.signum() == 0
        is MathExpression.Negate -> isStructurallyZero(expression.expression)
        is MathExpression.SquareRoot -> isStructurallyZero(expression.expression)
        is MathExpression.Binary -> when (expression.operator) {
            MathOperator.Subtract -> expression.left == expression.right
            MathOperator.Multiply -> isStructurallyZero(expression.left) || isStructurallyZero(expression.right)
            MathOperator.Divide -> isStructurallyZero(expression.left)
            else -> false
        }
        MathExpression.Variable -> false
    }

    private fun midpoint(low: Double, high: Double): Double = low + (high - low) / 2
    private fun oppositeSigns(a: Double, b: Double): Boolean = (a < 0.0) != (b < 0.0)
    private fun xTolerance(x: Double): Double = max(1e-10, 8 * Math.ulp(x))

    private data class ZeroBracket(val left: Double, val leftValue: Double, val right: Double, val rightValue: Double)
    private sealed interface Minimum {
        data class Found(val x: Double) : Minimum
        data object ExcludesZero : Minimum
        data object Unresolved : Minimum
    }
    private const val SCAN_INTERVALS = 512
    private const val MAX_REFINEMENTS = 96
    private const val MAX_POINTS = 32
    private const val RESIDUAL_RATIO = 1e-7
}

private fun ExactRational.toDouble(): Double =
    BigDecimal(numerator).divide(BigDecimal(denominator), MathContext.DECIMAL128).toDouble()

private fun ExactRational.inRange(viewport: PlotViewport): Boolean {
    fun compare(bound: Double): Int {
        val limit = ExactRational.literal(BigDecimal(bound).toPlainString())
        return (numerator * limit.denominator).compareTo(limit.numerator * denominator)
    }
    return compare(viewport.left) >= 0 && compare(viewport.right) <= 0
}
