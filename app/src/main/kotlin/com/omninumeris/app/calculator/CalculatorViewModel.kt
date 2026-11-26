package com.omninumeris.app.calculator

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omninumeris.application.CalculatorHistory
import com.omninumeris.application.CalculatorHistoryResult
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Calculator
import com.omninumeris.domain.graph.PlotFeatures
import com.omninumeris.domain.graph.PlotSampler
import com.omninumeris.domain.graph.PlotViewport
import com.omninumeris.domain.graph.SampledPlot
import com.omninumeris.domain.math.ExactRational
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.MathExpression
import com.omninumeris.domain.math.MathParser
import com.omninumeris.infrastructure.storage.EncryptedCalculatorHistory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class CalculatorState(
    val mode: CalculatorMode = CalculatorMode.Standard,
    val draft: CalculatorDraft = CalculatorDraft(),
    val calculating: Boolean = false,
    val result: CalculationResult? = null,
    val plots: List<CalculatorPlot> = emptyList(),
    val viewport: PlotViewport = PlotViewport.Default,
    val sampledViewport: PlotViewport = PlotViewport.Default,
    val analysis: GraphAnalysis = GraphAnalysis.Idle,
    val display: CalculatorDisplaySettings = CalculatorDisplaySettings(),
    val memory: ExactRational? = null,
    val memoryError: MathError? = null,
    val historyOpen: Boolean = false,
    val history: CalculatorHistoryResult? = null,
    val historyWriteProblem: CalculatorHistoryResult? = null,
)

internal data class CalculatorPlot(
    val slot: Int,
    val source: String,
    val expression: MathExpression,
    val sampled: SampledPlot,
)

@HiltViewModel
internal class CalculatorViewModel @Inject constructor(
    @param:CalculatorDispatcher private val dispatcher: CoroutineDispatcher,
    private val historyStore: CalculatorHistory,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CalculatorState())
    val state = mutableState.asStateFlow()
    private val drafts = mutableMapOf<CalculatorMode, CalculatorDraft>()
    private var calculation: Job? = null
    private var resampling: Job? = null
    private var graphSearch: Job? = null
    private val historyGate = Mutex()

    @MainThread
    fun selectMode(mode: CalculatorMode) {
        if (mode == mutableState.value.mode) return
        calculation?.cancel()
        cancelGraphSearch()
        mutableState.value = mutableState.value.copy(
            mode = mode,
            draft = drafts[mode] ?: CalculatorDraft(),
            calculating = false,
            result = null,
        )
    }

    @MainThread
    fun edit(change: (CalculatorDraft) -> CalculatorDraft) {
        val current = mutableState.value
        val draft = change(current.draft)
        if (draft == current.draft) return
        calculation?.cancel()
        drafts[current.mode] = draft
        mutableState.value = current.copy(draft = draft, calculating = false, result = null)
    }

    @MainThread
    fun calculate() {
        val current = mutableState.value
        if (current.calculating ||
            (current.mode == CalculatorMode.Graphing && current.viewport != current.sampledViewport)
        ) return
        mutableState.value = current.copy(calculating = true, result = null)
        calculation = viewModelScope.launch {
            val (updated, entry) = withContext(dispatcher) {
                val evaluated = evaluate(current)
                evaluated to evaluated.result?.historyEntry(current.mode, current.draft)
            }
            val latest = mutableState.value
            val plots = if (current.mode == CalculatorMode.Graphing) updated.plots else latest.plots
            if (plots != latest.plots) graphSearch?.cancel()
            mutableState.value = latest.copy(
                result = updated.result,
                calculating = false,
                plots = plots,
                analysis = if (plots != latest.plots) GraphAnalysis.Idle else latest.analysis,
            )
            if (entry != null) {
                viewModelScope.launch {
                    historyGate.withLock {
                        val saved = historyStore.append(entry)
                        val now = mutableState.value
                        mutableState.value = now.copy(
                            historyWriteProblem = saved.takeUnless { it is CalculatorHistoryResult.Available },
                            history = if (now.historyOpen && saved is CalculatorHistoryResult.Available) saved else now.history,
                        )
                    }
                }
            }
        }
    }

    @MainThread
    fun setDecimalDisplay(decimal: Boolean) {
        val current = mutableState.value
        mutableState.value = current.copy(display = current.display.copy(decimal = decimal))
    }

    @MainThread
    fun setPrecision(precision: CalculatorPrecision) {
        val current = mutableState.value
        mutableState.value = current.copy(display = current.display.copy(precision = precision))
    }

    @MainThread
    fun storeMemory() {
        val current = mutableState.value
        val exact = current.result as? CalculationResult.Exact ?: return
        mutableState.value = current.copy(memory = exact.value, memoryError = null)
    }

    @MainThread
    fun adjustMemory(subtract: Boolean) {
        val current = mutableState.value
        val exact = current.result as? CalculationResult.Exact ?: return
        when (val adjusted = Calculator.adjustMemory(current.memory, exact.value, subtract)) {
            is CalculationResult.Exact -> mutableState.value = current.copy(memory = adjusted.value, memoryError = null)
            is CalculationResult.Rejected -> mutableState.value = current.copy(memoryError = adjusted.error)
            else -> error("Memory adjustment produced a non-exact result")
        }
    }

    @MainThread
    fun recallMemory() {
        val memory = mutableState.value.memory ?: return
        selectMode(CalculatorMode.Standard)
        calculation?.cancel()
        val current = mutableState.value
        val canonical = memory.toString()
        val fitsEditor = canonical.split('/').all { it.removePrefix("-").length <= MathParser.MAX_LITERAL_LENGTH }
        val draft = if (fitsEditor) current.draft.copy(main = canonical) else current.draft
        drafts[CalculatorMode.Standard] = draft
        mutableState.value = current.copy(
            draft = draft,
            result = CalculationResult.Exact(memory),
            calculating = false,
            memoryError = null,
        )
    }

    @MainThread
    fun clearMemory() {
        mutableState.value = mutableState.value.copy(memory = null, memoryError = null)
    }

    @MainThread
    fun showHistory() {
        mutableState.value = mutableState.value.copy(historyOpen = true, history = null)
        viewModelScope.launch {
            historyGate.withLock {
                if (!mutableState.value.historyOpen) return@withLock
                val entries = historyStore.read()
                val current = mutableState.value
                if (current.historyOpen) mutableState.value = current.copy(history = entries)
            }
        }
    }

    @MainThread
    fun dismissHistory() {
        mutableState.value = mutableState.value.copy(historyOpen = false, history = null)
    }

    @MainThread
    fun removePlot(slot: Int) {
        val current = mutableState.value
        calculation?.cancel()
        graphSearch?.cancel()
        updatePlots(current.copy(
            plots = current.plots.filterNot { it.slot == slot },
            calculating = false,
            result = null,
            analysis = GraphAnalysis.Idle,
        ))
    }

    @MainThread
    fun transformPlot(focusX: Double, focusY: Double, panX: Double, panY: Double, zoom: Double) {
        setViewport(mutableState.value.viewport.transform(focusX, focusY, panX, panY, zoom))
    }

    @MainThread
    fun resetPlotViewport() = setViewport(PlotViewport.Default)

    @MainThread
    fun analyzeGraph() {
        val current = mutableState.value
        if (current.mode != CalculatorMode.Graphing || current.plots.isEmpty() ||
            current.calculating || current.analysis != GraphAnalysis.Idle
        ) return
        mutableState.value = current.copy(analysis = GraphAnalysis.Searching)
        graphSearch = viewModelScope.launch {
            val result = withContext(dispatcher) {
                val context = currentCoroutineContext()
                val checkCancelled = { context.ensureActive() }
                val intersections = buildList {
                    for ((index, first) in current.plots.withIndex()) {
                        for (second in current.plots.drop(index + 1)) {
                            val solutions = PlotFeatures.intersections(first.expression, second.expression,
                                current.viewport, checkCancelled)
                            add(GraphIntersection(first.source, second.source, solutions))
                        }
                    }
                }
                val intercepts = current.plots.associate { plot ->
                    plot.source to PlotFeatures.intercepts(plot.expression, current.viewport, checkCancelled)
                }
                GraphAnalysis.Ready(current.viewport, intercepts, intersections)
            }
            mutableState.value = mutableState.value.copy(analysis = result)
        }
    }

    @MainThread
    fun cancelGraphSearch() {
        graphSearch?.cancel()
        val current = mutableState.value
        if (current.analysis == GraphAnalysis.Searching) mutableState.value = current.copy(analysis = GraphAnalysis.Idle)
    }

    private fun setViewport(viewport: PlotViewport) {
        val current = mutableState.value
        if (current.mode != CalculatorMode.Graphing || current.viewport == viewport) return
        calculation?.cancel()
        graphSearch?.cancel()
        updatePlots(current.copy(viewport = viewport, calculating = false, analysis = GraphAnalysis.Idle))
    }

    private fun updatePlots(next: CalculatorState) {
        resampling?.cancel()
        if (next.plots.isEmpty() || next.sampledViewport == next.viewport) {
            mutableState.value = next.copy(sampledViewport = next.viewport)
            return
        }
        mutableState.value = next
        resampling = viewModelScope.launch {
            delay(RESAMPLE_SETTLE_MS)
            val updated = withContext(dispatcher) {
                next.plots.map { it.copy(sampled = sample(it.expression, next.viewport)) }
            }
            mutableState.value = mutableState.value.copy(plots = updated, sampledViewport = next.viewport)
        }
    }

    private suspend fun evaluate(current: CalculatorState): CalculatorState {
        val result = evaluateDraft(current.mode, current.draft)
        if (result !is CalculationResult.GraphExpression) return current.copy(result = result)
        val source = current.draft.main.trim()
        if (current.plots.any { it.source == source }) return current.copy(result = result)
        val slot = (0 until MAX_PLOTS).firstOrNull { slot -> current.plots.none { it.slot == slot } }
            ?: return current.copy(result = graphError(MathErrorCode.ComplexityLimit, "Remove a plot to add another"))
        val plot = sample(result.expression, current.viewport)
        if (plot.segments.isEmpty()) {
            return current.copy(result = graphError(MathErrorCode.RealDomain, "No drawable curve in this viewport"))
        }
        return current.copy(result = result, plots = current.plots + CalculatorPlot(slot, source, result.expression, plot))
    }

    private suspend fun sample(expression: MathExpression, viewport: PlotViewport): SampledPlot {
        val context = currentCoroutineContext()
        return PlotSampler.sample(expression, viewport, checkCancelled = { context.ensureActive() })
    }

    private fun graphError(code: MathErrorCode, message: String) =
        CalculationResult.Rejected(MathError(code, message))

    private companion object {
        const val MAX_PLOTS = 4
        const val RESAMPLE_SETTLE_MS = 24L
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CalculatorDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object CalculatorModule {
    @Provides
    @CalculatorDispatcher
    fun calculatorDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun calculatorHistory(@ApplicationContext context: Context): CalculatorHistory =
        EncryptedCalculatorHistory(context.noBackupFilesDir, Dispatchers.IO)
}
