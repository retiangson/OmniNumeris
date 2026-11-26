package com.omninumeris.app.calculator

import com.omninumeris.domain.graph.PlotIntercepts
import com.omninumeris.domain.graph.PlotPoint
import com.omninumeris.domain.graph.PlotSolutions
import com.omninumeris.domain.graph.PlotViewport

internal sealed interface GraphAnalysis {
    data object Idle : GraphAnalysis
    data object Searching : GraphAnalysis
    data class Ready(
        val viewport: PlotViewport,
        val intercepts: Map<String, PlotIntercepts>,
        val intersections: List<GraphIntersection>,
    ) : GraphAnalysis {
        fun visiblePoints(): Sequence<PlotPoint> = sequence {
            for (result in intercepts.values) {
                yieldAll(result.roots.estimatedPoints())
                result.yIntercept?.let { yield(it.toPlotPoint()) }
            }
            for (pair in intersections) yieldAll(pair.solutions.estimatedPoints())
        }.filter { it.y in viewport.bottom..viewport.top }
    }
}

internal data class GraphIntersection(
    val first: String,
    val second: String,
    val solutions: PlotSolutions,
)

private fun PlotSolutions.estimatedPoints(): List<PlotPoint> = when (this) {
    is PlotSolutions.Exact -> listOf(point.toPlotPoint())
    is PlotSolutions.Approximate -> points
    PlotSolutions.Coincident, PlotSolutions.NoRealSamples -> emptyList()
}
