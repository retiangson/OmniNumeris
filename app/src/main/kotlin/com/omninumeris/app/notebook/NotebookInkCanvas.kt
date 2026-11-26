package com.omninumeris.app.notebook

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.NotebookViewport
import com.omninumeris.domain.canvas.InkLasso
import com.omninumeris.domain.canvas.InkPoint
import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTip
import com.omninumeris.domain.canvas.MAX_INK_COORDINATE
import com.omninumeris.domain.canvas.MAX_INK_SAMPLES
import com.omninumeris.domain.canvas.MAX_INK_SAMPLE_DISTANCE
import com.omninumeris.domain.canvas.inkWidth
import java.util.UUID
import kotlin.math.abs

internal enum class NotebookCanvasMode { Navigate, Draw, Select }

internal fun Modifier.notebookInkInput(
    mode: NotebookCanvasMode,
    viewport: NotebookViewport,
    page: NotebookPage,
    density: Float,
    showTrace: (List<Offset>) -> Unit,
    append: (UUID, InkStroke) -> Unit,
    select: (InkLasso) -> Unit,
): Modifier = pointerInput(mode, viewport, page.id, page.readOnly, density) {
    if (mode == NotebookCanvasMode.Draw && page.readOnly) return@pointerInput
    val scale = viewport.zoom * density
    awaitEachGesture {
        val first = awaitFirstDown()
        val pen = first.type == PointerType.Stylus
        val samples = ArrayList<InkSample>()
        val path = ArrayList<Offset>()
        val drawing = mode == NotebookCanvasMode.Draw
        var valid = true
        fun capture(position: Offset, pressure: Float) {
            val x = viewport.left + position.x / scale
            val y = viewport.top + position.y / scale
            if (drawing) {
                if (x !in -MAX_INK_COORDINATE..MAX_INK_COORDINATE || y !in -MAX_INK_COORDINATE..MAX_INK_COORDINATE ||
                    page.fixed && (x !in 0f..NotebookPage.WIDTH || y !in 0f..NotebookPage.HEIGHT)) return
                val previous = samples.lastOrNull()
                if (previous != null && (abs(previous.x - x) > MAX_INK_SAMPLE_DISTANCE ||
                        abs(previous.y - y) > MAX_INK_SAMPLE_DISTANCE)) {
                    valid = false
                    return
                }
                if (samples.size < MAX_INK_SAMPLES && (previous == null || previous.x != x || previous.y != y)) {
                    samples.add(InkSample.fromPointer(x, y, pressure, pen))
                }
            }
            if (path.size < InkLasso.MAX_POINTS && (path.lastOrNull()?.let { (it - position).getDistance() >= 3f } != false)) {
                path.add(position)
                showTrace(path.toList())
            }
        }
        first.consume()
        try {
            capture(first.position, first.pressure)
            while (valid) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                if (change.isConsumed || (!pen && event.changes.any { it.id != first.id && it.pressed })) break
                if (change.pressed) capture(change.position, change.pressure)
                change.consume()
                if (!change.pressed) {
                    if (drawing && samples.isNotEmpty()) append(page.id, InkStroke(UUID.randomUUID(), samples, InkTip.Medium))
                    if (!drawing) {
                        InkLasso.create(path.map {
                            InkPoint((viewport.left + it.x / scale).toDouble(), (viewport.top + it.y / scale).toDouble())
                        })?.let(select)
                    }
                    break
                }
            }
        } finally {
            showTrace(emptyList())
        }
    }
}

internal fun DrawScope.drawNotebookStroke(stroke: InkStroke, origin: Offset, scale: Float, color: Color) {
    fun InkSample.position() = origin + Offset(x * scale, y * scale)
    val first = stroke.samples.first()
    drawCircle(color, inkWidth(first.pressure, stroke.tip) * scale / 2, first.position())
    for (index in 1 until stroke.samples.size) {
        val a = stroke.samples[index - 1]
        val b = stroke.samples[index]
        drawLine(color, a.position(), b.position(), inkWidth((a.pressure + b.pressure) / 2, stroke.tip) * scale, StrokeCap.Round)
    }
}

@Composable
internal fun NotebookInkPreview(strokes: List<InkStroke>, description: String) {
    val color = MaterialTheme.colorScheme.onSurface
    val sampled = remember(strokes) {
        strokes.map { stroke ->
            val samples = stroke.samples
            val count = minOf(samples.size, PREVIEW_SAMPLES_PER_STROKE)
            List(count) { index ->
                samples[if (count == 1) 0 else index * (samples.size - 1) / (count - 1)]
            }
        }
    }
    val (left, top, right, bottom) = remember(sampled) {
        listOf(
            sampled.minOf { it.minOf(InkSample::x) },
            sampled.minOf { it.minOf(InkSample::y) },
            sampled.maxOf { it.maxOf(InkSample::x) },
            sampled.maxOf { it.maxOf(InkSample::y) },
        )
    }
    Canvas(Modifier.fillMaxWidth().height(100.dp).padding(8.dp).semantics { contentDescription = description }) {
        val scale = minOf(size.width / (right - left + 12f), size.height / (bottom - top + 12f))
        val origin = center - Offset((left + right) * scale / 2, (top + bottom) * scale / 2)
        fun InkSample.offset() = origin + Offset(x * scale, y * scale)
        val width = 1.25.dp.toPx()
        sampled.forEach { stroke ->
            if (stroke.size == 1) drawCircle(color, width / 2, stroke.single().offset())
            else for (index in 1 until stroke.size) {
                drawLine(color, stroke[index - 1].offset(), stroke[index].offset(), width, StrokeCap.Round)
            }
        }
    }
}

private const val PREVIEW_SAMPLES_PER_STROKE = 96
