package com.omninumeris.domain.calculator

import com.omninumeris.domain.math.ExactRational
import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.MathInput
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import java.math.BigDecimal
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorTest {
    @Test
    fun standardIsExactAndRejectsEquationsAndDivisionByZero() {
        assertEquals(exact("14"), calculate(Calculation.Standard("2+3×4")))
        assertEquals("1/3", (calculate(Calculation.Standard("1/3")) as CalculationResult.Exact).value.toString())
        rejected(MathErrorCode.DivisionByZero, Calculation.Standard("1/0"))
        rejected(MathErrorCode.Unsupported, Calculation.Standard("x=2"))
    }

    @Test
    fun scientificHonorsAngleUnitsAndItsRealDomain() {
        val degrees = Calculation.Scientific(ScientificOperation.Sin, 30.0, AngleUnit.Degrees)
        assertEquals(0.5, (calculate(degrees) as CalculationResult.Approximate).value, 1e-12)
        val radians = degrees.copy(operand = Math.PI / 2, angle = AngleUnit.Radians)
        assertEquals(1.0, (calculate(radians) as CalculationResult.Approximate).value, 1e-12)
        rejected(MathErrorCode.RealDomain, degrees.copy(operation = ScientificOperation.Tan, operand = 90.0))
        rejected(MathErrorCode.RealDomain, degrees.copy(operation = ScientificOperation.NaturalLog, operand = 0.0))
        rejected(MathErrorCode.RealDomain, degrees.copy(operation = ScientificOperation.SquareRoot, operand = -1.0))
        rejected(MathErrorCode.RealDomain, degrees.copy(operand = Double.NaN))
    }

    @Test
    fun programmerRoundTripsSignedIntegersAndRejectsOverflow() {
        assertEquals("FF", convert("255", Radix.Decimal, Radix.Hexadecimal))
        val random = Random(802)
        repeat(150) {
            val value = random.nextLong().toString()
            for (base in Radix.entries) assertEquals(value, convert(convert(value, Radix.Decimal, base), base, Radix.Decimal))
        }
        assertEquals(Long.MIN_VALUE.toString(), convert("-8000000000000000", Radix.Hexadecimal, Radix.Decimal))
        rejected(MathErrorCode.ComplexityLimit, Calculation.Programmer("8000000000000000", Radix.Hexadecimal, Radix.Decimal))
        rejected(MathErrorCode.Syntax, Calculation.Programmer("12", Radix.Binary, Radix.Decimal))
    }

    @Test
    fun financeCompoundsAtTheEndOfEachPeriod() {
        val base = Calculation.CompoundGrowth(BigDecimal("1000"), BigDecimal("0.05"), 2)
        val value = (calculate(base) as CalculationResult.Decimal).value
        assertEquals(0, BigDecimal("1102.50").compareTo(value))
        assertEquals(CalculationResult.Decimal(BigDecimal("1000")), calculate(base.copy(periods = 0)))
        rejected(MathErrorCode.RealDomain, base.copy(ratePerPeriod = BigDecimal("-1")))
        rejected(MathErrorCode.ComplexityLimit, base.copy(periods = 1_201))
    }

    @Test
    fun statisticsAndMatricesKeepExactValues() {
        val values = listOf(number("6"), number("2"), number("4"))
        assertEquals(exact("4"), calculate(Calculation.Statistics(Statistic.Mean, values)))
        assertEquals(exact("4"), calculate(Calculation.Statistics(Statistic.Median, values)))
        assertEquals(exact("3"), calculate(Calculation.Statistics(Statistic.Median, values.drop(1))))
        rejected(MathErrorCode.RealDomain, Calculation.Statistics(Statistic.Mean, emptyList()))
        val left = listOf(listOf(number("1"), number("2")), listOf(number("3"), number("4")))
        val right = listOf(listOf(number("5")), listOf(number("6")))
        assertEquals(
            CalculationResult.Matrix(listOf(listOf(number("17")), listOf(number("39")))),
            calculate(Calculation.MultiplyMatrices(left, right)),
        )
        rejected(MathErrorCode.RealDomain, Calculation.MultiplyMatrices(left, right.take(1)))
        rejected(MathErrorCode.RealDomain, Calculation.MultiplyMatrices(listOf(left.first(), right.first()), right))
    }

    @Test
    fun graphReturnsSharedAstAndUnitsUseExactFactors() {
        val parsed = MathParser.parse("x^2-1") as MathParseResult.Parsed
        assertEquals(CalculationResult.GraphExpression((parsed.input as MathInput.Expression).root),
            calculate(Calculation.Graph("x^2-1")))
        rejected(MathErrorCode.Unsupported, Calculation.Graph("x=1"))
        rejected(MathErrorCode.Syntax, Calculation.Graph("x+"))
        val km = Calculation.ConvertUnit(number("1"), MeasurementUnit.Kilometer, MeasurementUnit.Meter)
        assertEquals(exact("1000"), calculate(km))
        val freezing = Calculation.ConvertUnit(number("32"), MeasurementUnit.Fahrenheit, MeasurementUnit.Celsius)
        assertEquals(exact("0"), calculate(freezing))
        rejected(MathErrorCode.Unsupported, km.copy(target = MeasurementUnit.Kilogram))
        rejected(MathErrorCode.RealDomain, freezing.copy(value = number("-460")))
    }

    private fun calculate(request: Calculation) = Calculator.calculate(request)
    private fun exact(text: String) = CalculationResult.Exact(number(text))
    private fun number(text: String) = ExactRational.literal(text)
    private fun convert(value: String, source: Radix, target: Radix): String =
        (calculate(Calculation.Programmer(value, source, target)) as CalculationResult.IntegerText).value

    private fun rejected(code: MathErrorCode, request: Calculation) {
        assertEquals(code, (calculate(request) as CalculationResult.Rejected).error.code)
    }
}
