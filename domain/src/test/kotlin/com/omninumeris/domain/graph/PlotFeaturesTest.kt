package com.omninumeris.domain.graph

import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Calculator
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotFeaturesTest {
    private val default = PlotViewport.Default

    @Test
    fun rootsAndYInterceptRespectSelectedHorizontalRange() {
        val source = expression("x^2-4")
        val all = PlotFeatures.intercepts(source, default)
        assertPoints(all.roots, -2.0, 2.0)
        assertEquals(PlotPoint(0.0, -4.0), all.yIntercept?.toPlotPoint())
        assertTrue(all.yIntercept is PlotCoordinate.Exact)

        val positive = PlotFeatures.intercepts(source, PlotViewport(2.0, 3.0, 4.0, 5.0))
        assertPoints(positive.roots, 2.0)
        assertNull(positive.yIntercept)
    }

    @Test
    fun findsBothIntersectionsWithoutRoundingCoordinatesToIntegers() {
        val intersections = PlotFeatures.intersections(expression("x"), expression("x^2"), default)
        val points = assertPoints(intersections, 0.0, 1.0)
        assertEquals(0.0, points[0].y, 1e-9)
        assertEquals(1.0, points[1].y, 1e-9)
    }

    @Test
    fun verifiedLinearIntersectionsStayExactAndAreFilteredByRange() {
        val first = expression("3*x")
        val second = expression("1")
        val result = PlotFeatures.intersections(first, second, default) as PlotSolutions.Exact
        assertEquals("1/3", result.point.x.toString())
        assertEquals("1", result.point.y.toString())
        val left = PlotViewport(-1.0, 0.0, -1.0, 1.0)
        assertPoints(PlotFeatures.intersections(first, second, left))
    }

    @Test
    fun findsOffGridTangenciesAndRejectsNearMisses() {
        val tangent = expression("(x-0.1234567)^2")
        assertPoints(PlotFeatures.intercepts(tangent, default).roots, 0.1234567)
        assertPoints(PlotFeatures.intersections(tangent, expression("0"), default), 0.1234567)
        val edge = PlotFeatures.intercepts(expression("(x-0.0001)^2"), PlotViewport(0.0, 1.0, -1.0, 1.0))
        assertPoints(edge.roots, 0.0001)
        val positive = PlotFeatures.intercepts(expression("(x-0.1234567)^2+0.000000000001"), default)
        assertPoints(positive.roots)
        val nearMiss = PlotFeatures.intersections(expression("x^2+1+0.000000000001"), expression("2*x"), default)
        assertPoints(nearMiss)
    }

    @Test
    fun polesAndHolesNeverBecomeApproximateRootsOrIntersections() {
        val pole = PlotFeatures.intercepts(expression("1/(x-0.017)"), default).roots as PlotSolutions.Approximate
        assertTrue(pole.points.isEmpty())
        assertTrue(pole.incomplete)
        val split = PlotFeatures.intersections(expression("1/(x-0.017)"), expression("1"), default)
            as PlotSolutions.Approximate
        assertEquals(1, split.points.size)
        assertEquals(1.017, split.points.single().x, 1e-9)
        assertTrue(split.incomplete)
        val hole = PlotFeatures.intercepts(expression("(x-0.017)/(x-0.017)"), default)
        assertPoints(hole.roots)
    }

    @Test
    fun distinguishesNoRootsCoincidenceAndUnavailableSamples() {
        assertPoints(PlotFeatures.intercepts(expression("x^2+1"), default).roots)
        assertEquals(PlotSolutions.Coincident, PlotFeatures.intercepts(expression("0"), default).roots)
        val pole = expression("1/x")
        assertEquals(PlotSolutions.Coincident, PlotFeatures.intersections(pole, pole, default))
        assertEquals(PlotSolutions.NoRealSamples, PlotFeatures.intercepts(expression("sqrt(-1)"), default).roots)
        assertNull(PlotFeatures.intercepts(pole, default).yIntercept)
        assertPoints(PlotFeatures.intercepts(expression("sqrt(x)"), default).roots, 0.0)
    }

    @Test
    fun largeNearlyEqualFunctionsDoNotReportEveryRoundedZeroAsAnIntersection() {
        val result = PlotFeatures.intersections(
            expression("100000000000000000000+x^2"), expression("100000000000000000000"), default,
        ) as PlotSolutions.Approximate
        assertTrue(result.points.isEmpty())
        assertTrue(result.incomplete)
    }

    @Test
    fun cancellationIsCheckedDuringSamplingAndRefinement() {
        val math = expression("x^2-4")
        for (limit in listOf(4, 590)) {
            var steps = 0
            assertThrows(CancellationException::class.java) {
                PlotFeatures.intercepts(math, default) {
                    if (++steps == limit) throw CancellationException()
                }
            }
        }
    }

    private fun expression(source: String) =
        (Calculator.calculate(Calculation.Graph(source)) as CalculationResult.GraphExpression).expression

    private fun assertPoints(result: PlotSolutions, vararg expected: Double): List<PlotPoint> {
        val approximate = result as PlotSolutions.Approximate
        assertFalse(approximate.incomplete)
        assertEquals(expected.size, approximate.points.size)
        expected.zip(approximate.points).forEach { (x, point) -> assertEquals(x, point.x, 1e-9) }
        return approximate.points
    }
}
