package com.omninumeris.app.calculator

import com.omninumeris.application.CalculatorHistory
import com.omninumeris.application.CalculatorHistoryEntry
import com.omninumeris.application.CalculatorHistoryResult
import com.omninumeris.application.CalculatorValueKind
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.graph.PlotViewport
import com.omninumeris.domain.graph.PlotSolutions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
    @Test
    fun precisionMemoryAndHistoryKeepExactValuesIndependentOfDisplay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = TestCalculatorHistory()
            val model = CalculatorViewModel(dispatcher, store)
            fun calculate(source: String) {
                model.edit { it.copy(main = source) }
                model.calculate()
                advanceUntilIdle()
            }
            calculate("1/3+1/6")
            assertEquals("1/2", (model.state.value.result as CalculationResult.Exact).value.toString())
            model.storeMemory()
            calculate("1/3")
            val exact = checkNotNull(model.state.value.result)
            model.setDecimalDisplay(true)
            model.setPrecision(CalculatorPrecision.Four)
            assertEquals(CalculatorValueText("0.3333", true), exact.display(model.state.value.display))
            model.setPrecision(CalculatorPrecision.Eight)
            assertEquals(CalculatorValueText("0.33333333", true), exact.display(model.state.value.display))
            assertEquals(CalculatorValueText("1/3"), exact.canonicalValue())
            assertEquals(exact, model.state.value.result)

            model.adjustMemory(subtract = false)
            assertEquals("5/6", model.state.value.memory.toString())
            model.adjustMemory(subtract = true)
            model.recallMemory()
            assertEquals("1/2", model.state.value.draft.main)
            assertEquals("1/2", (model.state.value.result as CalculationResult.Exact).value.toString())
            model.clearMemory()
            assertNull(model.state.value.memory)

            calculate("1/0")
            val reopened = CalculatorViewModel(dispatcher, store)
            reopened.showHistory()
            advanceUntilIdle()
            val records = (reopened.state.value.history as CalculatorHistoryResult.Available).entries
            assertEquals(listOf("1/3", "1/2"), records.map { it.result })
            assertEquals(listOf(CalculatorValueKind.Exact, CalculatorValueKind.Exact), records.map { it.kind })
            reopened.dismissHistory()
            assertNull(reopened.state.value.history)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun graphSearchCancelsOnViewportChangesAndNeverPublishesStaleRoots() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = CalculatorViewModel(dispatcher, TestCalculatorHistory())
            model.selectMode(CalculatorMode.Graphing)
            model.edit { it.copy(main = "x^2-4") }
            model.calculate()
            runCurrent()
            model.analyzeGraph()
            assertEquals(GraphAnalysis.Searching, model.state.value.analysis)
            model.transformPlot(0.75, 0.5, 0.0, 0.0, 4.0)
            advanceUntilIdle()
            assertEquals(GraphAnalysis.Idle, model.state.value.analysis)

            model.analyzeGraph()
            advanceUntilIdle()
            val result = model.state.value.analysis as GraphAnalysis.Ready
            assertEquals(model.state.value.viewport, result.viewport)
            val roots = result.intercepts.getValue("x^2-4").roots as PlotSolutions.Approximate
            assertEquals(2.0, roots.points.single().x, 1e-9)

            model.removePlot(model.state.value.plots.single().slot)
            assertEquals(GraphAnalysis.Idle, model.state.value.analysis)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rapidGesturesResampleOnlyTheFinalViewportAndPreserveOtherEdits() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = CalculatorViewModel(dispatcher, TestCalculatorHistory())
            model.selectMode(CalculatorMode.Graphing)
            for (source in listOf("x", "1/x")) {
                model.edit { it.copy(main = source) }
                model.calculate()
                runCurrent()
            }
            val initial = model.state.value
            model.transformPlot(0.25, 0.75, 0.0, 0.0, 2.0)
            runCurrent()
            model.transformPlot(0.5, 0.5, 0.1, -0.1, 2.0)
            model.removePlot(initial.plots.last().slot)
            val target = model.state.value.viewport
            assertEquals(PlotViewport.Default, model.state.value.sampledViewport)
            assertEquals(initial.plots.first(), model.state.value.plots.single())

            model.selectMode(CalculatorMode.Standard)
            model.edit { it.copy(main = "5+7") }
            model.calculate()
            advanceUntilIdle()
            val final = model.state.value
            assertEquals(target, final.sampledViewport)
            assertEquals("12", (final.result as CalculationResult.Exact).value.toString())
            assertEquals("5+7", final.draft.main)
            assertEquals("x", final.plots.single().source)
            val points = final.plots.single().sampled.segments.single()
            assertEquals(target.left, points.first().x, 1e-12)
            assertEquals(target.right, points.last().x, 1e-12)

            model.selectMode(CalculatorMode.Graphing)
            model.resetPlotViewport()
            advanceUntilIdle()
            assertEquals(initial.plots.first(), model.state.value.plots.single())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidPlotAndCancellationKeepPreviouslySampledCurves() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = CalculatorViewModel(dispatcher, TestCalculatorHistory())
            model.selectMode(CalculatorMode.Graphing)
            fun plot(source: String) {
                model.edit { it.copy(main = source) }
                model.calculate()
                runCurrent()
            }
            plot("x^2")
            plot("x")
            plot("1/x")
            val curves = model.state.value.plots
            assertEquals(listOf("x^2", "x", "1/x"), curves.map { it.source })
            assertEquals(2, curves.last().sampled.segments.size)
            plot("x+")
            assertEquals(curves, model.state.value.plots)
            assertEquals(true, model.state.value.result is CalculationResult.Rejected)
            plot("1/0")
            assertEquals(curves, model.state.value.plots)
            model.edit { it.copy(main = "x+1") }
            model.calculate()
            model.selectMode(CalculatorMode.Standard)
            runCurrent()
            model.selectMode(CalculatorMode.Graphing)
            assertEquals(curves, model.state.value.plots)
            model.removePlot(curves[0].slot)
            assertEquals(curves.drop(1), model.state.value.plots)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun switchingOrEditingInvalidatesPendingResultsAndRestoresDrafts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = CalculatorViewModel(dispatcher, TestCalculatorHistory())
            model.edit { it.copy(main = "2+3") }
            model.calculate()
            model.selectMode(CalculatorMode.Programmer)
            runCurrent()
            assertEquals(CalculatorMode.Programmer, model.state.value.mode)
            assertNull(model.state.value.result)
            assertFalse(model.state.value.calculating)

            model.edit { it.copy(main = "255") }
            model.selectMode(CalculatorMode.Standard)
            assertEquals("2+3", model.state.value.draft.main)
            model.calculate()
            model.edit { it.copy(main = "4+5") }
            runCurrent()
            assertNull(model.state.value.result)
            model.calculate()
            runCurrent()
            assertEquals("9", (model.state.value.result as CalculationResult.Exact).value.toString())
            model.selectMode(CalculatorMode.Programmer)
            assertEquals("255", model.state.value.draft.main)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class TestCalculatorHistory : CalculatorHistory {
    private var entries = emptyList<CalculatorHistoryEntry>()

    override suspend fun read(): CalculatorHistoryResult = CalculatorHistoryResult.Available(entries)

    override suspend fun append(entry: CalculatorHistoryEntry): CalculatorHistoryResult {
        entries = listOf(entry) + entries
        return read()
    }
}
