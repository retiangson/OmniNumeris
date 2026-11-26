package com.omninumeris.app.calculator

import com.omninumeris.application.CalculatorHistoryEntry
import com.omninumeris.application.CalculatorValueKind
import com.omninumeris.application.calculatorCanonicalValue
import com.omninumeris.domain.calculator.CalculationResult
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.UUID

internal enum class CalculatorPrecision(val digits: Int) { Four(4), Eight(8), Twelve(12), Fifteen(15) }

internal data class CalculatorDisplaySettings(
    val decimal: Boolean = false,
    val precision: CalculatorPrecision = CalculatorPrecision.Fifteen,
)

internal data class CalculatorValueText(val text: String, val approximate: Boolean = false)

internal fun CalculationResult.display(settings: CalculatorDisplaySettings): CalculatorValueText? =
    when (this) {
        is CalculationResult.Exact -> {
            if (!settings.decimal) CalculatorValueText(value.toString())
            else {
                val denominator = BigDecimal(value.denominator)
                val numerator = BigDecimal(value.numerator)
                val decimal = numerator.divide(denominator, settings.precision.context())
                CalculatorValueText(decimal.stripTrailingZeros().toString(), decimal.multiply(denominator).compareTo(numerator) != 0)
            }
        }
        is CalculationResult.Approximate -> CalculatorValueText(
            BigDecimal.valueOf(value).round(settings.precision.context())
                .stripTrailingZeros().toString(),
            approximate = true,
        )
        is CalculationResult.Decimal -> {
            val rounded = value.round(settings.precision.context())
            CalculatorValueText(rounded.stripTrailingZeros().toPlainString(), rounded.compareTo(value) != 0)
        }
        is CalculationResult.IntegerText -> CalculatorValueText(value)
        is CalculationResult.Matrix -> CalculatorValueText(rows.joinToString("\n") { it.joinToString(", ", "[", "]") })
        is CalculationResult.GraphExpression, is CalculationResult.Rejected -> null
    }

internal fun CalculationResult.canonicalValue(): CalculatorValueText? =
    calculatorCanonicalValue()?.let { CalculatorValueText(it.text, it.kind == CalculatorValueKind.Approximate) }

internal fun CalculationResult.historyEntry(mode: CalculatorMode, draft: CalculatorDraft): CalculatorHistoryEntry? {
    val value = calculatorCanonicalValue() ?: return null
    val options = when (mode) {
        CalculatorMode.Scientific -> listOf(draft.scientific.name, draft.angle.name)
        CalculatorMode.Programmer -> listOf(draft.sourceRadix.name, draft.targetRadix.name)
        CalculatorMode.Statistics -> listOf(draft.statistic.name)
        CalculatorMode.Units -> listOf(draft.sourceUnit.name, draft.targetUnit.name)
        else -> emptyList()
    }
    val input = (listOf(draft.main, draft.second, draft.third).filterNot(String::isBlank) + options)
        .joinToString("\n")
    return CalculatorHistoryEntry(UUID.randomUUID(), mode.name, input, value.text, value.kind)
}

private fun CalculatorPrecision.context() = MathContext(digits, RoundingMode.HALF_EVEN)
