package com.omninumeris.app.calculator

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omninumeris.app.R
import com.omninumeris.domain.calculator.CalculationResult

private enum class PanelVisibility { Closed, Expanded, Collapsed }

@Composable
internal fun CalculatorHost(
    model: CalculatorViewModel,
    onInsertResult: ((CalculationResult) -> Unit)? = null,
    content: @Composable (openCalculator: () -> Unit, calculatorExpanded: Boolean) -> Unit,
) {
    var visibility by rememberSaveable { mutableStateOf(PanelVisibility.Closed) }
    var placement by rememberSaveable(stateSaver = placementSaver) { mutableStateOf(CalculatorPanelPlacement()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun changeVisibility(next: PanelVisibility) {
        focus.clearFocus()
        keyboard?.hide()
        if (next != PanelVisibility.Expanded) model.dismissHistory()
        visibility = next
    }
    BackHandler(enabled = visibility == PanelVisibility.Expanded) {
        changeVisibility(PanelVisibility.Collapsed)
    }

    Box(Modifier.fillMaxSize()) {
        content({ changeVisibility(PanelVisibility.Expanded) }, visibility == PanelVisibility.Expanded)
        if (visibility != PanelVisibility.Closed) {
            BoxWithConstraints(
                modifier = Modifier.matchParentSize().safeDrawingPadding().imePadding().padding(8.dp),
                contentAlignment = AbsoluteAlignment.TopLeft,
            ) {
                val width = maxWidth.value
                val height = maxHeight.value
                if (visibility == PanelVisibility.Collapsed) {
                    Button(
                        onClick = { changeVisibility(PanelVisibility.Expanded) },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(stringResource(R.string.calculator_restore))
                    }
                } else {
                    val frame = placement.frame(width, height)
                    CalculatorPanel(
                        model = model,
                        modifier = Modifier
                            .absoluteOffset {
                                val maxLeft = maxWidth.roundToPx() - frame.width.dp.roundToPx()
                                val maxTop = maxHeight.roundToPx() - frame.height.dp.roundToPx()
                                IntOffset(
                                    frame.left.dp.roundToPx().coerceAtMost(maxLeft),
                                    frame.top.dp.roundToPx().coerceAtMost(maxTop),
                                )
                            }
                            .size(frame.width.dp, frame.height.dp),
                        move = { dx, dy -> placement = placement.move(width, height, dx, dy) },
                        resize = { dx, dy -> placement = placement.resize(width, height, dx, dy) },
                        collapse = { changeVisibility(PanelVisibility.Collapsed) },
                        close = { changeVisibility(PanelVisibility.Closed) },
                        onInsertResult = onInsertResult,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatorPanel(
    model: CalculatorViewModel,
    modifier: Modifier,
    move: (Float, Float) -> Unit,
    resize: (Float, Float) -> Unit,
    collapse: () -> Unit,
    close: () -> Unit,
    onInsertResult: ((CalculationResult) -> Unit)?,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val title = stringResource(R.string.calculator_title)
    val moveLabel = stringResource(R.string.calculator_move_handle)
    val resizeLabel = stringResource(R.string.calculator_resize_handle)
    Surface(
        modifier = modifier.semantics { paneTitle = title },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(48.dp)
                        .panelHandle(moveLabel, movementActions, move)
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DragGrip()
                        Text(
                            title,
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PanelButton("−", stringResource(R.string.calculator_collapse), collapse)
                PanelButton("×", stringResource(R.string.calculator_close), close)
            }
            HorizontalDivider()
            CalculatorContent(
                state = state,
                onMode = model::selectMode,
                onEdit = model::edit,
                onCalculate = model::calculate,
                onRemovePlot = model::removePlot,
                onTransformPlot = model::transformPlot,
                onResetPlot = model::resetPlotViewport,
                onAnalyzePlot = model::analyzeGraph,
                onCancelGraphSearch = model::cancelGraphSearch,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelAdjustmentMenu(move, resize)
                CalculatorTools(state, model, onInsertResult)
                Box(
                    Modifier.size(48.dp).panelHandle(resizeLabel, resizingActions, resize),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↘", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun PanelAdjustmentMenu(move: (Float, Float) -> Unit, resize: (Float, Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.calculator_adjust))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (adjustment in movementActions) {
                DropdownMenuItem(text = { Text(stringResource(adjustment.label)) }, onClick = {
                    expanded = false
                    move(adjustment.dx, adjustment.dy)
                })
            }
            HorizontalDivider()
            for (adjustment in resizingActions) {
                DropdownMenuItem(text = { Text(stringResource(adjustment.label)) }, onClick = {
                    expanded = false
                    resize(adjustment.dx, adjustment.dy)
                })
            }
        }
    }
}

@Composable
private fun DragGrip() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(12.dp, 18.dp)) {
        for (column in 1..2) {
            for (row in 1..3) {
                drawCircle(color, 1.1.dp.toPx(), Offset(size.width * column / 3, size.height * row / 4))
            }
        }
    }
}

@Composable
private fun PanelButton(symbol: String, label: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Text(symbol, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Modifier.panelHandle(
    label: String,
    adjustments: List<PanelAdjustment>,
    adjust: (Float, Float) -> Unit,
): Modifier {
    val currentAdjust by rememberUpdatedState(adjust)
    val actions = adjustments.map { adjustment ->
        CustomAccessibilityAction(stringResource(adjustment.label)) {
            currentAdjust(adjustment.dx, adjustment.dy)
            true
        }
    }
    return semantics(mergeDescendants = true) {
        contentDescription = label
        customActions = actions
    }.pointerInput(Unit) {
        detectDragGestures { _, delta ->
            currentAdjust(delta.x / density, delta.y / density)
        }
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val adjustment = adjustments.firstOrNull { it.key == event.key } ?: return@onKeyEvent false
        currentAdjust(adjustment.dx, adjustment.dy)
        true
    }.focusable()
}

private data class PanelAdjustment(@get:StringRes val label: Int, val key: Key, val dx: Float, val dy: Float)

private const val STEP = 32f
private val movementActions = listOf(
    PanelAdjustment(R.string.calculator_move_left, Key.DirectionLeft, -STEP, 0f),
    PanelAdjustment(R.string.calculator_move_right, Key.DirectionRight, STEP, 0f),
    PanelAdjustment(R.string.calculator_move_up, Key.DirectionUp, 0f, -STEP),
    PanelAdjustment(R.string.calculator_move_down, Key.DirectionDown, 0f, STEP),
)
private val resizingActions = listOf(
    PanelAdjustment(R.string.calculator_narrower, Key.DirectionLeft, -STEP, 0f),
    PanelAdjustment(R.string.calculator_wider, Key.DirectionRight, STEP, 0f),
    PanelAdjustment(R.string.calculator_shorter, Key.DirectionUp, 0f, -STEP),
    PanelAdjustment(R.string.calculator_taller, Key.DirectionDown, 0f, STEP),
)

private val placementSaver = listSaver<CalculatorPanelPlacement, Float>(
    save = { listOf(it.preferredWidth, it.preferredHeight, it.horizontalBias, it.verticalBias) },
    restore = { CalculatorPanelPlacement(it[0], it[1], it[2], it[3]) },
)
