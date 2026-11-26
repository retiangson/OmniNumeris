package com.omninumeris.app.calculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
internal fun CalculatorPlots(
    state: CalculatorState,
    remove: (Int) -> Unit,
    transform: (Double, Double, Double, Double, Double) -> Unit,
    reset: () -> Unit,
    analyze: () -> Unit,
    cancelSearch: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val curveColors = listOf(colors.primary, colors.tertiary, colors.error, colors.onSurface)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val description = stringResource(R.string.plot_accessibility)
    val viewport = state.viewport
    var showResults by rememberSaveable { mutableStateOf(false) }
    val points = remember(state.analysis) {
        (state.analysis as? GraphAnalysis.Ready)?.visiblePoints()?.distinct()?.toList().orEmpty()
    }
    val viewportLabel = stringResource(
        R.string.plot_viewport_accessibility,
        labelValue(viewport.left), labelValue(viewport.right), labelValue(viewport.bottom), labelValue(viewport.top),
    )
    val currentTransform = rememberUpdatedState(transform)
    val horizontalTicks = remember(viewport.left, viewport.right) { ticks(viewport.left, viewport.right) }
    val verticalTicks = remember(viewport.bottom, viewport.top) { ticks(viewport.bottom, viewport.top) }
    val panActions = listOf(
        panAction(stringResource(R.string.plot_pan_left), -0.25, 0.0, transform),
        panAction(stringResource(R.string.plot_pan_right), 0.25, 0.0, transform),
        panAction(stringResource(R.string.plot_pan_up), 0.0, -0.25, transform),
        panAction(stringResource(R.string.plot_pan_down), 0.0, 0.25, transform),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (plot in state.plots) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(28.dp).height(12.dp)) {
                    val middle = size.height / 2f
                    drawLine(curveColors[plot.slot], Offset(0f, middle), Offset(size.width, middle),
                        strokeWidth = 2.5.dp.toPx(), pathEffect = dash(plot.slot, density))
                }
                Text(stringResource(R.string.plot_function, plot.source), Modifier.weight(1f))
                val removeLabel = stringResource(R.string.plot_remove_accessibility, plot.source)
                TextButton(onClick = { remove(plot.slot) }, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                    Text(stringResource(R.string.plot_remove), Modifier.clearAndSetSemantics {})
                }
            }
        }
        Row {
            TextButton(onClick = { transform(0.5, 0.5, 0.0, 0.0, 2.0) }) {
                Text(stringResource(R.string.plot_zoom_in))
            }
            TextButton(onClick = { transform(0.5, 0.5, 0.0, 0.0, 0.5) }) {
                Text(stringResource(R.string.plot_zoom_out))
            }
            TextButton(onClick = reset) { Text(stringResource(R.string.plot_reset_view)) }
        }
        TextButton(
            onClick = {
                analyze()
                showResults = true
            },
            enabled = !state.calculating,
        ) {
            Text(stringResource(R.string.plot_find_features))
        }
        Canvas(
            Modifier.fillMaxWidth().height(270.dp).semantics {
                contentDescription = description
                stateDescription = viewportLabel
                customActions = panActions
            }.pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { focus, pan, zoom, _ ->
                    val frame = plotFrame(size.width.toFloat(), size.height.toFloat())
                    if (frame.width > 0f && frame.height > 0f) {
                        currentTransform.value(
                            ((focus.x - frame.left) / frame.width).toDouble(),
                            ((focus.y - frame.top) / frame.height).toDouble(),
                            (pan.x / frame.width).toDouble(),
                            (pan.y / frame.height).toDouble(),
                            zoom.toDouble(),
                        )
                    }
                }
            },
        ) {
            val frame = plotFrame(size.width, size.height)
            val (left, top, right, bottom) = frame
            if (right <= left || bottom <= top) return@Canvas

            fun x(value: Double) = left + frame.width * viewport.screenX(value).toFloat()
            fun y(value: Double) = top + frame.height * viewport.screenY(value).toFloat()
            for ((mark, text) in horizontalTicks) {
                val px = x(mark)
                val rule = if (mark == 0.0) colors.onSurface else colors.outlineVariant
                drawLine(rule, Offset(px, top), Offset(px, bottom), 1.dp.toPx())
                val label = textMeasurer.measure(text, labelStyle)
                val labelX = (px - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
                drawText(label, topLeft = Offset(labelX, bottom + 3.dp.toPx()))
            }
            for ((mark, text) in verticalTicks) {
                val py = y(mark)
                val rule = if (mark == 0.0) colors.onSurface else colors.outlineVariant
                drawLine(rule, Offset(left, py), Offset(right, py), 1.dp.toPx())
                val label = textMeasurer.measure(text, labelStyle)
                val labelX = (left - label.size.width - 3.dp.toPx()).coerceAtLeast(0f)
                drawText(label, topLeft = Offset(labelX, py - label.size.height / 2f))
            }
            drawText(textMeasurer, "x", topLeft = Offset(right + 5.dp.toPx(), y(0.0).coerceIn(top, bottom)), style = labelStyle)
            drawText(textMeasurer, "y", topLeft = Offset(x(0.0).coerceIn(left, right) + 5.dp.toPx(), 0f), style = labelStyle)
            val sampled = state.sampledViewport
            // Reuse nearby samples during a gesture without passing enormous coordinates to Canvas.
            val previewFits = abs(viewport.screenX(sampled.left)) <= 32 && abs(viewport.screenX(sampled.right)) <= 32 &&
                abs(viewport.screenY(sampled.top)) <= 32 && abs(viewport.screenY(sampled.bottom)) <= 32
            if (previewFits) {
                clipRect(left, top, right, bottom) {
                    for (plot in state.plots) {
                        val path = Path()
                        for (segment in plot.sampled.segments) {
                            val first = segment.first()
                            path.moveTo(x(first.x), y(first.y))
                            for (index in 1 until segment.size) {
                                val point = segment[index]
                                path.lineTo(x(point.x), y(point.y))
                            }
                        }
                        drawPath(path, curveColors[plot.slot],
                            style = Stroke(2.5.dp.toPx(), pathEffect = dash(plot.slot, density)))
                    }
                    for (point in points) {
                        val center = Offset(x(point.x), y(point.y))
                        drawCircle(colors.surface, 5.dp.toPx(), center)
                        drawCircle(colors.onSurface, 3.dp.toPx(), center)
                    }
                }
            }
        }
        if (state.viewport != state.sampledViewport) {
            Text(stringResource(R.string.plot_updating), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showResults) {
        GraphResultsDialog(state.analysis) {
            showResults = false
            cancelSearch()
        }
    }
}

private fun Density.plotFrame(width: Float, height: Float) =
    Rect(52.dp.toPx(), 18.dp.toPx(), width - 22.dp.toPx(), height - 24.dp.toPx())

private fun panAction(
    label: String,
    x: Double,
    y: Double,
    transform: (Double, Double, Double, Double, Double) -> Unit,
) = CustomAccessibilityAction(label) {
    transform(0.5, 0.5, x, y, 1.0)
    true
}

private fun ticks(start: Double, end: Double): List<Pair<Double, String>> {
    val desired = (end - start) / 4
    val magnitude = 10.0.pow(floor(log10(desired)))
    val step = listOf(1, 2, 5, 10).first { it * magnitude >= desired } * magnitude
    val first = ceil(start / step).toLong()
    val last = floor(end / step).toLong()
    return (first..last).map { index ->
        val value = index * step
        value to labelValue(value)
    }
}

internal fun labelValue(value: Double): String {
    val decimal = BigDecimal.valueOf(value).round(MathContext(11)).stripTrailingZeros()
    return if (value == 0.0 || abs(value) in 1e-4..1e6) decimal.toPlainString() else decimal.toString()
}

private fun dash(slot: Int, density: Float): PathEffect? = when (slot) {
    0 -> null
    1 -> PathEffect.dashPathEffect(floatArrayOf(7 * density, 4 * density))
    2 -> PathEffect.dashPathEffect(floatArrayOf(2 * density, 4 * density))
    else -> PathEffect.dashPathEffect(floatArrayOf(8 * density, 3 * density, 2 * density, 3 * density))
}
