package com.omninumeris.app.calculator

import com.omninumeris.domain.calculator.AngleUnit
import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Calculator
import com.omninumeris.domain.calculator.MeasurementUnit
import com.omninumeris.domain.calculator.Radix
import com.omninumeris.domain.calculator.ScientificOperation
import com.omninumeris.domain.calculator.Statistic
import com.omninumeris.domain.math.ExactRational
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathErrorCode
import java.math.BigDecimal
import java.math.MathContext

internal enum class CalculatorMode { Standard, Scientific, Programmer, Financial, Statistics, Matrices, Graphing, Units }

internal data class CalculatorDraft(
    val main: String = "",
    val second: String = "",
    val third: String = "",
    val angle: AngleUnit = AngleUnit.Degrees,
    val scientific: ScientificOperation = ScientificOperation.Sin,
    val sourceRadix: Radix = Radix.Decimal,
    val targetRadix: Radix = Radix.Hexadecimal,
    val statistic: Statistic = Statistic.Mean,
    val sourceUnit: MeasurementUnit = MeasurementUnit.Kilometer,
    val targetUnit: MeasurementUnit = MeasurementUnit.Meter,
)

internal fun evaluateDraft(mode: CalculatorMode, draft: CalculatorDraft): CalculationResult = try {
    if (listOf(draft.main, draft.second, draft.third).any { it.length > 8_192 }) {
        inputError("Input exceeds the calculator form limit", MathErrorCode.ComplexityLimit)
    }
    val request = when (mode) {
        CalculatorMode.Standard -> Calculation.Standard(draft.main)
        CalculatorMode.Scientific -> Calculation.Scientific(
            draft.scientific,
            rational(draft.main.replace(piToken, Math.PI.toString())).toDouble(),
            draft.angle,
        )
        CalculatorMode.Programmer -> Calculation.Programmer(draft.main, draft.sourceRadix, draft.targetRadix)
        CalculatorMode.Financial -> Calculation.CompoundGrowth(
            draft.main.trim().toBigDecimalOrNull() ?: inputError("Enter a decimal principal"),
            draft.second.trim().toBigDecimalOrNull() ?: inputError("Enter a decimal rate per period"),
            draft.third.trim().toIntOrNull() ?: inputError("Enter the period count as an integer"),
        )
        CalculatorMode.Statistics -> Calculation.Statistics(
            draft.statistic, numbers(draft.main, 128),
        )
        CalculatorMode.Matrices -> Calculation.MultiplyMatrices(matrix(draft.main), matrix(draft.second))
        CalculatorMode.Graphing -> Calculation.Graph(draft.main)
        CalculatorMode.Units -> Calculation.ConvertUnit(rational(draft.main), draft.sourceUnit, draft.targetUnit)
    }
    Calculator.calculate(request)
} catch (failure: InvalidCalculatorInput) {
    CalculationResult.Rejected(failure.error)
}

private val piToken = Regex("(?<![A-Za-z0-9])(?:pi|π)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

private fun rational(text: String): ExactRational = when (val result = Calculator.calculate(Calculation.Standard(text))) {
    is CalculationResult.Exact -> result.value
    is CalculationResult.Rejected -> throw InvalidCalculatorInput(result.error)
    else -> error("Standard calculation returned an unexpected result")
}

private fun ExactRational.toDouble(): Double =
    BigDecimal(numerator).divide(BigDecimal(denominator), MathContext.DECIMAL128).toDouble()

private fun numbers(raw: String, maximum: Int): List<ExactRational> {
    val entries = raw.split(',', limit = maximum + 1)
    if (entries.size > maximum) inputError("Enter at most $maximum values per list or row", MathErrorCode.ComplexityLimit)
    if (entries.any(String::isBlank)) inputError("Enter a value in each comma-separated position")
    return entries.map(::rational)
}

private fun matrix(raw: String): List<List<ExactRational>> {
    val rows = raw.trim().lines()
    if (rows.size > 16) inputError("Enter at most 16 matrix rows", MathErrorCode.ComplexityLimit)
    return rows.map { numbers(it, 16) }
}

private fun inputError(detail: String, code: MathErrorCode = MathErrorCode.Syntax): Nothing =
    throw InvalidCalculatorInput(MathError(code, detail))

private class InvalidCalculatorInput(val error: MathError) : RuntimeException(error.detail)
