package com.omninumeris.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Calculator
import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.ExactMathResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ExactMathBenchmarkTest {
    @Test
    fun measureArithmeticAndLinearSolving() {
        measure("arithmetic", "2+3×4", "14")
        measure("linear", "2*x+3=x-5", "-8")
    }

    @Test
    fun measureStandardCalculator() {
        val request = Calculation.Standard("2+3×4")
        measureOperation("calculator-standard", "14", { Calculator.calculate(request) }) {
            (it as CalculationResult.Exact).value.toString()
        }
    }

    private fun measure(label: String, input: String, expected: String) =
        measureOperation(label, expected, { ExactMath.analyze(input) }) { result ->
            when (result) {
                is ExactMathResult.Value -> result.value.toString()
                is ExactMathResult.VerifiedLinearSolution -> result.value.toString()
                else -> error("Unexpected benchmark result: $result")
            }
        }

    private fun <T> measureOperation(label: String, expected: String, calculate: () -> T, display: (T) -> String) {
        fun invokeAndMeasure(): Long {
            val start = SystemClock.elapsedRealtimeNanos()
            val result = calculate()
            val duration = SystemClock.elapsedRealtimeNanos() - start
            assertEquals(expected, display(result))
            return duration
        }

        val first = invokeAndMeasure()
        repeat(50) { invokeAndMeasure() }
        val warm = LongArray(200) { invokeAndMeasure() }.sorted()
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString(
                "benchmark",
                "operation=$label device=${Build.MODEL} api=${Build.VERSION.SDK_INT} " +
                    "samples=${warm.size} firstNs=$first warmP50Ns=${warm[99]} warmP95Ns=${warm[189]}",
            )
        })
    }
}
