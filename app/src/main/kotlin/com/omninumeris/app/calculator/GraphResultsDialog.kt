package com.omninumeris.app.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.domain.graph.PlotCoordinate
import com.omninumeris.domain.graph.PlotPoint
import com.omninumeris.domain.graph.PlotSolutions

@Composable
internal fun GraphResultsDialog(analysis: GraphAnalysis, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.plot_features_title)) },
        text = {
            when (analysis) {
                GraphAnalysis.Idle -> Text(stringResource(R.string.plot_features_cancelled))
                GraphAnalysis.Searching -> Text(
                    stringResource(R.string.plot_features_searching),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                is GraphAnalysis.Ready -> GraphResults(analysis)
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(R.string.plot_features_close))
            }
        },
    )
}

@Composable
private fun GraphResults(analysis: GraphAnalysis.Ready) {
    Column(
        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(
                R.string.plot_features_range,
                labelValue(analysis.viewport.left),
                labelValue(analysis.viewport.right),
            ),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        for ((source, result) in analysis.intercepts) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FeatureTitle(stringResource(R.string.plot_function, source))
                Solutions(result.roots, intersection = false)
                val intercept = result.yIntercept
                Text(when {
                    intercept is PlotCoordinate.Exact ->
                        stringResource(R.string.plot_y_intercept, exactCoordinates(intercept))
                    intercept is PlotCoordinate.Approximate ->
                        stringResource(R.string.plot_y_intercept, coordinates(intercept.point))
                    0.0 !in analysis.viewport.left..analysis.viewport.right ->
                        stringResource(R.string.plot_y_intercept_outside)
                    else -> stringResource(R.string.plot_y_intercept_missing)
                })
            }
        }
        for (pair in analysis.intersections) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FeatureTitle(stringResource(R.string.plot_intersections_pair, pair.first, pair.second))
                Solutions(pair.solutions, intersection = true)
            }
        }
    }
}

@Composable
private fun FeatureTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Solutions(result: PlotSolutions, intersection: Boolean) {
    when (result) {
        is PlotSolutions.Exact -> Text(
            if (intersection) exactCoordinates(result.point)
            else stringResource(R.string.plot_exact_root, result.point.x.toString()),
        )
        PlotSolutions.Coincident -> Text(stringResource(
            if (intersection) R.string.plot_intersections_coincident else R.string.plot_roots_coincident,
        ))
        PlotSolutions.NoRealSamples -> Text(stringResource(R.string.plot_features_no_real_samples))
        is PlotSolutions.Approximate -> {
            if (result.points.isEmpty() && !result.incomplete) {
                Text(stringResource(if (intersection) R.string.plot_intersections_none else R.string.plot_roots_none))
            }
            for (point in result.points) {
                Text(if (intersection) coordinates(point) else stringResource(R.string.plot_root, labelValue(point.x)))
            }
            if (result.incomplete) Text(stringResource(R.string.plot_features_incomplete))
        }
    }
}

@Composable
private fun coordinates(point: PlotPoint): String =
    stringResource(R.string.plot_approx_coordinates, labelValue(point.x), labelValue(point.y))

@Composable
private fun exactCoordinates(point: PlotCoordinate.Exact): String =
    stringResource(R.string.plot_exact_coordinates, point.x.toString(), point.y.toString())
