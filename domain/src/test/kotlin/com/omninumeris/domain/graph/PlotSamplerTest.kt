package com.omninumeris.domain.graph

import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Calculator
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotSamplerTest {
    @Test
    fun samplesLineAndParabolaInTheSameCoordinateInterval() {
        val viewport = PlotViewport(-2.0, 2.0, -4.0, 4.0)
        val line = sample("x", viewport, 8).segments.single()
        val parabola = sample("x^2", viewport, 8).segments.single()
        assertEquals(line.map { it.x }, parabola.map { it.x })
        line.zip(parabola).forEach { (linear, squared) ->
            assertEquals(linear.x, linear.y, 1e-12)
            assertEquals(squared.x * squared.x, squared.y, 1e-12)
        }
    }

    @Test
    fun separatesOnGridAndOffGridPolesIncludingRemovableHoles() {
        for ((source, pole) in listOf("1/x" to 0.0, "1/(x-0.017)" to 0.017, "(x-0.017)/(x-0.017)" to 0.017,
            "1/(x-0.017)^2" to 0.017)) {
            val segments = sample(source).segments
            assertTrue(source, segments.any { segment -> segment.all { it.x < pole } })
            assertTrue(source, segments.any { segment -> segment.all { it.x > pole } })
            assertFalse(source, segments.any { it.first().x < pole && it.last().x > pole })
        }
    }

    @Test
    fun clipsSteepContinuousLinesAndOmitsInvalidRealDomains() {
        val steep = sample("100000*x").segments.single()
        assertEquals(-10.0, steep.first().y, 1e-12)
        assertEquals(10.0, steep.last().y, 1e-12)
        assertEquals(-0.0001, steep.first().x, 1e-12)
        assertEquals(0.0001, steep.last().x, 1e-12)
        assertTrue(sample("sqrt(x)").segments.flatten().all { it.x >= 0.0 })
        assertTrue(sample("1/0").segments.isEmpty())
        assertTrue(sample("(-2)^0.5").segments.isEmpty())
    }

    @Test
    fun checksCancellationDuringSampling() {
        var checks = 0
        val marker = CancellationException("fixture")
        try {
            PlotSampler.sample(expression("x")) {
                if (++checks == 19) throw marker
            }
            error("Sampling ignored cancellation")
        } catch (failure: CancellationException) {
            assertEquals(marker, failure)
        }
        assertEquals(19, checks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidViewport() {
        PlotViewport(0.0, 0.0, Double.NaN, 4.0)
    }

    private fun sample(source: String, viewport: PlotViewport = PlotViewport.Default, intervals: Int = 400) =
        PlotSampler.sample(expression(source), viewport, intervals)

    private fun expression(source: String) =
        (Calculator.calculate(Calculation.Graph(source)) as CalculationResult.GraphExpression).expression
}
