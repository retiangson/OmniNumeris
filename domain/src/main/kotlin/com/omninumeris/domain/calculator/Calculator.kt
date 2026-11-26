package com.omninumeris.domain.calculator

import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.ExactRational
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.MathExpression
import com.omninumeris.domain.math.MathInput
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.math.MathRejection
import com.omninumeris.domain.math.reject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.Locale

sealed interface Calculation {
    data class Standard(val expression: String) : Calculation
    data class Scientific(val operation: ScientificOperation, val operand: Double, val angle: AngleUnit) : Calculation
    data class Programmer(val digits: String, val source: Radix, val target: Radix) : Calculation
    data class CompoundGrowth(val principal: BigDecimal, val ratePerPeriod: BigDecimal, val periods: Int) : Calculation
    data class Statistics(val statistic: Statistic, val values: List<ExactRational>) : Calculation
    data class MultiplyMatrices(
        val left: List<List<ExactRational>>,
        val right: List<List<ExactRational>>,
    ) : Calculation
    data class Graph(val expression: String) : Calculation
    data class ConvertUnit(val value: ExactRational, val source: MeasurementUnit, val target: MeasurementUnit) : Calculation
}

enum class AngleUnit { Degrees, Radians }
enum class ScientificOperation { Sin, Cos, Tan, SquareRoot, NaturalLog }
enum class Radix(val base: Int) { Binary(2), Octal(8), Decimal(10), Hexadecimal(16) }
enum class Statistic { Mean, Median }

enum class MeasurementUnit(internal val dimension: Dimension, factor: String, offset: String = "0") {
    Millimeter(Dimension.Length, "0.001"),
    Centimeter(Dimension.Length, "0.01"),
    Meter(Dimension.Length, "1"),
    Kilometer(Dimension.Length, "1000"),
    Inch(Dimension.Length, "0.0254"),
    Foot(Dimension.Length, "0.3048"),
    Gram(Dimension.Mass, "0.001"),
    Kilogram(Dimension.Mass, "1"),
    Pound(Dimension.Mass, "0.45359237"),
    Second(Dimension.Time, "1"),
    Minute(Dimension.Time, "60"),
    Hour(Dimension.Time, "3600"),
    Kelvin(Dimension.Temperature, "1"),
    Celsius(Dimension.Temperature, "1", "273.15"),
    Fahrenheit(Dimension.Temperature, "1", "459.67");

    private val multiplier = ExactRational.literal(factor)
    private val shift = ExactRational.literal(offset)

    internal fun toBase(value: ExactRational): ExactRational =
        if (this == Fahrenheit) (value + shift) * FIVE / NINE else value * multiplier + shift

    internal fun fromBase(value: ExactRational): ExactRational =
        if (this == Fahrenheit) value * NINE / FIVE - shift else (value - shift) / multiplier
}

internal enum class Dimension { Length, Mass, Time, Temperature }

sealed interface CalculationResult {
    data class Exact(val value: ExactRational) : CalculationResult
    data class Approximate(val value: Double) : CalculationResult
    data class Decimal(val value: BigDecimal) : CalculationResult
    data class IntegerText(val value: String, val radix: Radix) : CalculationResult
    data class Matrix(val rows: List<List<ExactRational>>) : CalculationResult
    data class GraphExpression(val expression: MathExpression) : CalculationResult
    data class Rejected(val error: MathError) : CalculationResult
}

object Calculator {
    fun adjustMemory(memory: ExactRational?, operand: ExactRational, subtract: Boolean): CalculationResult = try {
        val previous = memory ?: ExactRational.ZERO
        CalculationResult.Exact(if (subtract) previous - operand else previous + operand)
    } catch (rejection: MathRejection) {
        CalculationResult.Rejected(rejection.error)
    }

    fun calculate(request: Calculation): CalculationResult = try {
        when (request) {
            is Calculation.Standard -> standard(request.expression)
            is Calculation.Scientific -> scientific(request)
            is Calculation.Programmer -> programmer(request)
            is Calculation.CompoundGrowth -> compoundGrowth(request)
            is Calculation.Statistics -> statistics(request)
            is Calculation.MultiplyMatrices -> multiplyMatrices(request)
            is Calculation.Graph -> graph(request.expression)
            is Calculation.ConvertUnit -> convert(request)
        }
    } catch (rejection: MathRejection) {
        CalculationResult.Rejected(rejection.error)
    }

    private fun standard(source: String): CalculationResult = when (val parsed = MathParser.parse(source)) {
        is MathParseResult.Rejected -> CalculationResult.Rejected(parsed.error)
        is MathParseResult.Parsed -> when (val input = parsed.input) {
            is MathInput.Expression -> CalculationResult.Exact(ExactMath.evaluateNumeric(input.root))
            is MathInput.Equation -> reject(MathErrorCode.Unsupported, "Enter a numeric expression in standard mode")
        }
    }

    private fun scientific(request: Calculation.Scientific): CalculationResult {
        val operand = request.operand
        if (!operand.isFinite()) reject(MathErrorCode.RealDomain, "Enter a finite number")
        val radians = if (request.angle == AngleUnit.Degrees) StrictMath.toRadians(operand) else operand
        val value = when (request.operation) {
            ScientificOperation.Sin -> StrictMath.sin(radians)
            ScientificOperation.Cos -> StrictMath.cos(radians)
            ScientificOperation.Tan -> {
                if (StrictMath.abs(StrictMath.cos(radians)) <= 1e-15) {
                    reject(MathErrorCode.RealDomain, "Tangent is undefined at or too close to this angle")
                }
                StrictMath.tan(radians)
            }
            ScientificOperation.SquareRoot -> StrictMath.sqrt(operand)
            ScientificOperation.NaturalLog -> StrictMath.log(operand)
        }
        if (!value.isFinite()) reject(MathErrorCode.RealDomain, "Result is outside the finite real domain")
        return CalculationResult.Approximate(value)
    }

    private fun programmer(request: Calculation.Programmer): CalculationResult {
        val digits = request.digits.trim()
        if (digits.length > 65) reject(MathErrorCode.ComplexityLimit, "Integer exceeds the 64-bit input budget")
        val magnitude = digits.removePrefix("-").removePrefix("+")
        if (magnitude.isEmpty() || magnitude.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
            reject(MathErrorCode.Syntax, "Enter digits in the selected base")
        }
        val value = digits.toBigIntegerOrNull(request.source.base)
            ?: reject(MathErrorCode.Syntax, "Enter digits in the selected base")
        if (value < LONG_MIN || value > LONG_MAX) {
            reject(MathErrorCode.ComplexityLimit, "Integer exceeds the signed 64-bit range")
        }
        return CalculationResult.IntegerText(value.toString(request.target.base).uppercase(Locale.ROOT), request.target)
    }

    private fun compoundGrowth(request: Calculation.CompoundGrowth): CalculationResult.Decimal {
        val (principal, rate, periods) = request
        if (listOf(principal, rate).any { it.precision() > 34 || it.scale() !in -12..24 } || periods !in 0..1_200) {
            reject(MathErrorCode.ComplexityLimit, "Amount, rate, or period count exceeds its supported range")
        }
        if (principal.signum() < 0 || rate <= BigDecimal.ONE.negate() || rate > BigDecimal.ONE) {
            reject(MathErrorCode.RealDomain, "Use a nonnegative principal and a per-period rate above −1 and at most 1")
        }
        val context = MathContext.DECIMAL128
        val growth = BigDecimal.ONE.add(rate, context).pow(periods, context)
        return CalculationResult.Decimal(principal.multiply(growth, context))
    }

    private fun statistics(request: Calculation.Statistics): CalculationResult.Exact {
        val values = request.values
        if (values.isEmpty()) reject(MathErrorCode.RealDomain, "Enter at least one value")
        if (values.size > 128) reject(MathErrorCode.ComplexityLimit, "Enter at most 128 values")
        val value = when (request.statistic) {
            Statistic.Mean -> values.fold(ExactRational.ZERO, ExactRational::plus) /
                ExactRational.literal(values.size.toString())
            Statistic.Median -> {
                val sorted = values.sortedWith { first, second ->
                    (first.numerator * second.denominator).compareTo(second.numerator * first.denominator)
                }
                val center = sorted.size / 2
                if (sorted.size % 2 == 1) sorted[center] else (sorted[center - 1] + sorted[center]) / TWO
            }
        }
        return CalculationResult.Exact(value)
    }

    private fun multiplyMatrices(request: Calculation.MultiplyMatrices): CalculationResult.Matrix {
        val leftColumns = columns(request.left)
        val rightColumns = columns(request.right)
        if (leftColumns != request.right.size) reject(MathErrorCode.RealDomain, "Matrix dimensions cannot be multiplied")
        return CalculationResult.Matrix(request.left.map { row ->
            List(rightColumns) { column ->
                row.indices.fold(ExactRational.ZERO) { sum, index -> sum + row[index] * request.right[index][column] }
            }
        })
    }

    private fun columns(matrix: List<List<ExactRational>>): Int {
        val columns = matrix.firstOrNull()?.size ?: 0
        if (matrix.size > 16 || columns > 16) reject(MathErrorCode.ComplexityLimit, "Use at most 16 rows and 16 columns")
        if (columns == 0 || matrix.any { it.size != columns }) {
            reject(MathErrorCode.RealDomain, "Enter a nonempty rectangular matrix")
        }
        return columns
    }

    private fun graph(source: String): CalculationResult = when (val parsed = MathParser.parse(source)) {
        is MathParseResult.Rejected -> CalculationResult.Rejected(parsed.error)
        is MathParseResult.Parsed -> when (val input = parsed.input) {
            is MathInput.Expression -> CalculationResult.GraphExpression(input.root)
            is MathInput.Equation -> reject(MathErrorCode.Unsupported, "Enter a function of x without an equality")
        }
    }

    private fun convert(request: Calculation.ConvertUnit): CalculationResult.Exact {
        val (value, source, target) = request
        if (source.dimension != target.dimension) reject(MathErrorCode.Unsupported, "Choose units of the same dimension")
        val base = source.toBase(value)
        if (source.dimension == Dimension.Temperature && base.numerator.signum() < 0) {
            reject(MathErrorCode.RealDomain, "Temperature cannot be below absolute zero")
        }
        return CalculationResult.Exact(target.fromBase(base))
    }

    private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
}

private val TWO = ExactRational.literal("2")
private val FIVE = ExactRational.literal("5")
private val NINE = ExactRational.literal("9")
