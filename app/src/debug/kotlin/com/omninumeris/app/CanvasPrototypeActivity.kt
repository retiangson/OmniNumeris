package com.omninumeris.app

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.omninumeris.app.canvas.CanvasLassoState
import com.omninumeris.app.canvas.CanvasProbeState
import com.omninumeris.app.canvas.InkCanvasView
import com.omninumeris.domain.canvas.InkTip
import java.io.File
import java.util.UUID

class CanvasPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val testId = intent.getStringExtra(EXTRA_TEST_CANVAS)?.let(UUID::fromString)
        val journalDirectory = File(noBackupFilesDir, testId?.let { "canvas-test-$it" } ?: "canvas-prototype")
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                CanvasPrototypeScreen(journalDirectory, onBack = ::finish)
            }
        }
    }

    internal companion object {
        const val EXTRA_TEST_CANVAS = "com.omninumeris.app.extra.TEST_CANVAS"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanvasPrototypeScreen(
    journalDirectory: File,
    onBack: () -> Unit,
    onOpenCalculator: (() -> Unit)? = null,
) {
    var canvas by remember { mutableStateOf<InkCanvasView?>(null) }
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(appContext) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(config: Configuration) = Unit
            override fun onLowMemory() { canvas?.trimRasterCache() }
            override fun onTrimMemory(level: Int) { canvas?.trimRasterCache() }
        }
        appContext.registerComponentCallbacks(callbacks)
        onDispose { appContext.unregisterComponentCallbacks(callbacks) }
    }
    var state by remember { mutableStateOf(CanvasProbeState()) }
    var tip by rememberSaveable { mutableStateOf(InkTip.Fine) }
    var eraser by rememberSaveable { mutableStateOf(false) }
    var stylusOnly by rememberSaveable { mutableStateOf(true) }
    var lasso by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf<CanvasLassoState>(CanvasLassoState.Idle) }
    val canvasLabel = stringResource(R.string.canvas_accessibility_label)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.canvas_back)) }
                Text(
                    stringResource(R.string.canvas_prototype),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (onOpenCalculator != null) {
                    TextButton(onClick = onOpenCalculator) { Text(stringResource(R.string.calculator_title)) }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                for (candidate in InkTip.entries) {
                    val label = when (candidate) {
                        InkTip.Fine -> R.string.canvas_tip_fine
                        InkTip.Medium -> R.string.canvas_tip_medium
                        InkTip.Bold -> R.string.canvas_tip_bold
                    }
                    FilterChip(
                        selected = tip == candidate && !eraser && !lasso,
                        onClick = {
                            tip = candidate
                            eraser = false
                            lasso = false
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
                FilterChip(
                    selected = eraser,
                    onClick = {
                        eraser = !eraser
                        lasso = false
                    },
                    label = { Text(stringResource(R.string.canvas_eraser)) },
                )
                FilterChip(
                    selected = lasso,
                    enabled = !state.loading && !state.recoveryFailed && !state.saving && !state.saveFailed && !state.editing,
                    onClick = {
                        lasso = !lasso
                        eraser = false
                    },
                    label = { Text(stringResource(R.string.canvas_lasso)) },
                )
                FilterChip(
                    selected = stylusOnly,
                    onClick = { stylusOnly = !stylusOnly },
                    label = { Text(stringResource(R.string.canvas_stylus_only)) },
                )
                TextButton(onClick = { canvas?.undo() }, enabled = state.canUndo && !lasso) {
                    Text(stringResource(R.string.canvas_undo))
                }
            }
            Text(
                stringResource(when {
                    selection == CanvasLassoState.Working -> R.string.canvas_lasso_working
                    lasso && stylusOnly -> R.string.canvas_lasso_pen_help
                    lasso -> R.string.canvas_lasso_help
                    eraser && stylusOnly -> R.string.canvas_stylus_eraser_help
                    eraser -> R.string.canvas_eraser_help
                    stylusOnly -> R.string.canvas_stylus_help
                    else -> R.string.canvas_input_help
                }),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            AndroidView(
                factory = { context ->
                    InkCanvasView(context, journalDirectory, reportLasso = { selection = it }) {
                        state = it
                    }.also { canvas = it }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .semantics { contentDescription = canvasLabel },
                update = {
                    it.setTools(tip, eraser, stylusOnly)
                    it.setLassoEnabled(lasso)
                },
                onReset = null,
                onRelease = {
                    it.close()
                    if (canvas === it) canvas = null
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = when {
                    state.recoveryFailed -> stringResource(R.string.canvas_recover_failed)
                    state.loading -> stringResource(R.string.canvas_loading)
                    state.saveFailed -> stringResource(R.string.canvas_save_failed)
                    state.editFailed -> stringResource(R.string.canvas_edit_failed)
                    state.editing -> stringResource(R.string.canvas_editing)
                    state.saving -> stringResource(R.string.canvas_saving)
                    state.unreadable > 0 -> stringResource(R.string.canvas_recovery_partial, state.committed, state.unreadable)
                    else -> stringResource(R.string.canvas_committed, state.committed)
                }
                Text(status, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                if (state.saveFailed) {
                    TextButton(onClick = { canvas?.retrySaves() }) { Text(stringResource(R.string.canvas_retry_save)) }
                }
            }
            CanvasLassoDialog(selection, onDismiss = { canvas?.cancelLasso() }) { canvas?.toggleLassoStroke(it) }
        }
    }
}

@Composable
private fun CanvasLassoDialog(state: CanvasLassoState, onDismiss: () -> Unit, onStroke: (UUID) -> Unit) {
    if (state == CanvasLassoState.Idle || state == CanvasLassoState.Working) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.canvas_lasso_preview)) },
        text = {
            when (state) {
                CanvasLassoState.Empty -> Text(stringResource(R.string.canvas_lasso_empty))
                CanvasLassoState.TooComplex -> Text(stringResource(R.string.canvas_lasso_complex))
                is CanvasLassoState.Preview -> Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.image == null) {
                        Text(stringResource(R.string.canvas_lasso_all_excluded))
                    } else {
                        Image(state.image.asImageBitmap(), stringResource(R.string.canvas_lasso_image), Modifier.heightIn(max = 160.dp))
                    }
                    Text(stringResource(R.string.canvas_lasso_recognition_unavailable))
                    state.strokeIds.forEachIndexed { index, id ->
                        FilterChip(
                            selected = id !in state.excluded,
                            onClick = { onStroke(id) },
                            label = { Text(stringResource(R.string.canvas_lasso_stroke, index + 1)) },
                        )
                    }
                }
                CanvasLassoState.Idle, CanvasLassoState.Working -> Unit
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.canvas_lasso_redraw)) } },
    )
}
