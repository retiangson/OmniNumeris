package com.omninumeris.app.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import com.omninumeris.app.R
import com.omninumeris.domain.canvas.INK_TILE_SIZE
import com.omninumeris.domain.canvas.InkLasso
import com.omninumeris.domain.canvas.InkLassoHits
import com.omninumeris.domain.canvas.InkPoint
import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTile
import com.omninumeris.domain.canvas.InkTip
import com.omninumeris.domain.canvas.InkViewport
import com.omninumeris.domain.canvas.MAX_INK_COORDINATE
import com.omninumeris.domain.canvas.MAX_INK_SAMPLE_DISTANCE
import com.omninumeris.domain.canvas.MAX_INK_SAMPLES
import com.omninumeris.domain.canvas.coveredTiles
import com.omninumeris.domain.canvas.inkWidth
import com.omninumeris.infrastructure.canvas.PrototypeInkJournal
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

internal data class CanvasProbeState(
    val loading: Boolean = true,
    val recoveryFailed: Boolean = false,
    val unreadable: Int = 0,
    val committed: Int = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val editing: Boolean = false,
    val editFailed: Boolean = false,
    val canUndo: Boolean = false,
)

internal sealed interface CanvasLassoState {
    data object Idle : CanvasLassoState
    data object Working : CanvasLassoState
    data object Empty : CanvasLassoState
    data object TooComplex : CanvasLassoState
    data class Preview(val image: Bitmap?, val strokeIds: List<UUID>, val excluded: Set<UUID>) : CanvasLassoState
}

@SuppressLint("ViewConstructor") // Created by Compose AndroidView with its state callback.
internal class InkCanvasView(
    context: Context,
    journalDirectory: File,
    private val reportLasso: (CanvasLassoState) -> Unit = {},
    private val reportState: (CanvasProbeState) -> Unit,
) : View(context) {
    private val journal = PrototypeInkJournal(journalDirectory)
    private val rasterWorker = Executors.newSingleThreadExecutor()
    private val tileCache = LruCache<InkTile, Bitmap>(MAX_CACHED_TILES)
    private val inFlightTiles = HashSet<InkTile>()
    private val visibleTiles = ArrayList<InkTile>()
    private val pendingStrokes = linkedMapOf<UUID, PendingStroke>()
    private val activeSamples = ArrayList<InkSample>()
    private val undoHistory = ArrayDeque<InkEdit>()
    private val backgroundPaint = Paint().apply { color = 0xfffffdf8.toInt() }
    private val strokePaint = newInkPaint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val measurements = InkMeasurements()
    private val lassoWorker = Executors.newSingleThreadExecutor()
    private val lassoPoints = ArrayList<InkPoint>()
    private val lassoOutline = Path()
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff3155bb.toInt()
        style = Paint.Style.STROKE
    }
    private var lassoEnabled = false
    private var lassoOverflow = false
    private var lassoJob: Future<*>? = null
    private var lassoGeneration = 0L
    private var lassoSelection: Pair<InkLasso, List<InkStroke>>? = null
    private var excludedStrokes = emptySet<UUID>()

    private var tileIndex = mutableMapOf<InkTile, List<InkStroke>>()
    private var state = CanvasProbeState()
    private var closed = false
    private var pauseRasterUntil = 0L
    private var activePointer = MotionEvent.INVALID_POINTER_ID
    private var stylusActive = false
    private var stylusOnly = true
    private var selectedTip = InkTip.Fine
    private var strokeTip = InkTip.Fine
    private var eraserSelected = false
    private var erasing = false
    private var eraseTarget: InkStroke? = null
    private var pinch: Pinch? = null
    private var scale = resources.displayMetrics.density
    private var offsetX = 0f
    private var offsetY = 0f

    init {
        contentDescription = context.getString(R.string.canvas_accessibility_label)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        recover()
    }

    private fun recover() {
        journalWorker.execute {
            val restore = runCatching {
                val recovered = journal.recover()
                val index = mutableMapOf<InkTile, MutableList<InkStroke>>()
                for (stroke in recovered.strokes) {
                    for (tile in stroke.coveredTiles()) index.getOrPut(tile, ::mutableListOf).add(stroke)
                }
                recovered to index.toMutableMap<InkTile, List<InkStroke>>()
            }
            post {
                if (closed) return@post
                restore.onSuccess { (recovered, index) ->
                    tileIndex = index
                    state = CanvasProbeState(
                        loading = false, unreadable = recovered.unreadableRecords, committed = recovered.strokes.size,
                    )
                }.onFailure {
                    state = CanvasProbeState(loading = false, recoveryFailed = true)
                }
                reportState(state)
                invalidate()
            }
        }
    }

    private fun save(stroke: InkStroke) {
        pendingStrokes[stroke.id] = PendingStroke(stroke, failed = false)
        publishSaveStatus()
        journalWorker.execute {
            val result = runCatching {
                val tiles = stroke.coveredTiles()
                journal.commit(stroke)
                tiles
            }
            post {
                if (closed) return@post
                result.onSuccess { tiles ->
                    pendingStrokes.remove(stroke.id)
                    for (tile in tiles) {
                        tileIndex[tile] = tileIndex[tile].orEmpty() + stroke
                        tileCache.remove(tile)
                    }
                    state = state.copy(committed = state.committed + 1)
                    rememberEdit(InkEdit(stroke, erased = false))
                }.onFailure {
                    pendingStrokes[stroke.id] = PendingStroke(stroke, failed = true)
                }
                publishSaveStatus()
                postInvalidateOnAnimation()
            }
        }
    }

    fun retrySaves() {
        pendingStrokes.values.filter { it.failed }.forEach { save(it.stroke) }
    }

    fun setTools(tip: InkTip, eraser: Boolean, penOnly: Boolean) {
        if (tip == selectedTip && eraser == eraserSelected && penOnly == stylusOnly) return
        discardActiveStroke()
        pinch = null
        selectedTip = tip
        eraserSelected = eraser
        stylusOnly = penOnly
        invalidate()
    }

    fun undo() {
        if (!state.canUndo || activePointer != MotionEvent.INVALID_POINTER_ID) return
        val latest = undoHistory.lastOrNull() ?: return
        editVisibility(latest.stroke, erased = !latest.erased, undoing = true)
    }

    private fun rememberEdit(edit: InkEdit) {
        if (undoHistory.size == MAX_UNDO_EDITS) undoHistory.removeFirst()
        undoHistory.addLast(edit)
    }

    private fun editVisibility(stroke: InkStroke, erased: Boolean, undoing: Boolean = false) {
        state = state.copy(editing = true, editFailed = false)
        publishSaveStatus()
        journalWorker.execute {
            val result = runCatching {
                val tiles = stroke.coveredTiles()
                journal.setErased(stroke.id, erased)
                tiles
            }
            post {
                if (closed) return@post
                result.onSuccess { tiles ->
                    for (tile in tiles) {
                        val strokes = tileIndex[tile].orEmpty()
                        val updated = if (erased) strokes.filterNot { it.id == stroke.id } else strokes + stroke
                        if (updated.isEmpty()) tileIndex.remove(tile) else tileIndex[tile] = updated
                        tileCache.remove(tile)
                    }
                    if (undoing) undoHistory.removeLast() else rememberEdit(InkEdit(stroke, erased))
                    state = state.copy(committed = state.committed + if (erased) -1 else 1)
                }
                state = state.copy(editing = false, editFailed = result.isFailure)
                publishSaveStatus()
                invalidate()
            }
        }
    }

    private fun publishSaveStatus() {
        state = state.copy(
            saveFailed = pendingStrokes.values.any { it.failed },
            saving = pendingStrokes.values.any { !it.failed },
            canUndo = !state.editing && pendingStrokes.isEmpty() && undoHistory.isNotEmpty(),
        )
        reportState(state)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        scale = scale.coerceIn(minScale(), MAX_SCALE)
        updateVisibleTiles()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val transform = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        for (tile in visibleTiles) drawTile(canvas, tile)
        for (pending in pendingStrokes.values) {
            paintStroke(canvas, pending.stroke.samples, pending.stroke.tip, strokePaint)
        }
        paintStroke(canvas, activeSamples, strokeTip, strokePaint)
        lassoPaint.strokeWidth = 2 * resources.displayMetrics.density / scale
        canvas.drawPath(lassoOutline, lassoPaint)
        canvas.restoreToCount(transform)
        measurements.onDraw()
    }

    private fun drawTile(canvas: Canvas, tile: InkTile) {
        val strokes = tileIndex[tile] ?: return
        val left = (tile.column * INK_TILE_SIZE).toFloat()
        val top = (tile.row * INK_TILE_SIZE).toFloat()
        val bitmap = tileCache[tile]
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, left, top, bitmapPaint)
            return
        }

        val clip = canvas.save()
        canvas.clipRect(left, top, left + INK_TILE_SIZE, top + INK_TILE_SIZE)
        for (stroke in strokes) paintStroke(canvas, stroke.samples, stroke.tip, strokePaint)
        canvas.restoreToCount(clip)
        if (canRasterize() && inFlightTiles.size < MAX_PENDING_TILES && inFlightTiles.add(tile)) {
            rasterWorker.execute {
                val rendered = runCatching {
                    Bitmap.createBitmap(INK_TILE_SIZE, INK_TILE_SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.density = Bitmap.DENSITY_NONE
                        val tileCanvas = Canvas(bitmap)
                        tileCanvas.translate(-left, -top)
                        val paint = newInkPaint()
                        for (stroke in strokes) paintStroke(tileCanvas, stroke.samples, stroke.tip, paint)
                    }
                }.getOrNull()
                post {
                    if (closed) return@post
                    inFlightTiles.remove(tile)
                    if (rendered != null && canRasterize() && tileIndex[tile] === strokes) {
                        tileCache.put(tile, rendered)
                        postInvalidateOnAnimation()
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state.loading || state.recoveryFailed || state.editing || closed) return true
        if (lassoEnabled) return onLassoTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pinch = null
                discardActiveStroke()
                beginPointer(event, 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!stylusActive && isPen(event, event.actionIndex)) {
                    discardActiveStroke()
                    pinch = null
                    beginPointer(event, event.actionIndex)
                } else if (!stylusActive && !stylusOnly && !eraserSelected) {
                    discardActiveStroke()
                    pinch = pinchOf(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val previous = pinch
                if (previous != null && event.pointerCount >= 2) {
                    val next = pinchOf(event)
                    transformViewport(previous, next)
                    pinch = next
                } else {
                    val index = event.findPointerIndex(activePointer)
                    if (index >= 0) dispatchSample(event, index)
                }
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) {
                    val canceled = Build.VERSION.SDK_INT >= 33 &&
                        event.flags and MotionEvent.FLAG_CANCELED != 0
                    if (!canceled) {
                        dispatchSample(event, event.actionIndex)
                        if (erasing) {
                            eraseTarget?.let { editVisibility(it, erased = true) }
                        } else {
                            if (activeSamples.isNotEmpty()) save(InkStroke(UUID.randomUUID(), activeSamples, strokeTip))
                            measurements.finish()
                        }
                        performClick()
                    }
                    discardActiveStroke()
                }
                if (event.pointerCount <= 2) pinch = null
                if (event.actionMasked == MotionEvent.ACTION_UP) parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                discardActiveStroke()
                pinch = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        postInvalidateOnAnimation()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setLassoEnabled(enabled: Boolean) {
        if (lassoEnabled == enabled) return
        lassoEnabled = enabled
        cancelLasso()
    }

    fun cancelLasso() {
        lassoGeneration++
        lassoJob?.cancel(true)
        lassoJob = null
        lassoSelection = null
        excludedStrokes = emptySet()
        lassoPoints.clear()
        lassoOutline.reset()
        lassoOverflow = false
        discardActiveStroke()
        pinch = null
        parent?.requestDisallowInterceptTouchEvent(false)
        if (!closed) reportLasso(CanvasLassoState.Idle)
        invalidate()
    }

    private fun onLassoTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelLasso()
                if (pendingStrokes.isEmpty() && (!stylusOnly || isPen(event, 0))) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    activePointer = event.getPointerId(0)
                    stylusActive = isPen(event, 0)
                    appendLassoPoint(event.getX(0), event.getY(0))
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!stylusActive && !stylusOnly && event.pointerCount == 2) {
                cancelLasso()
                parent?.requestDisallowInterceptTouchEvent(true)
                pinch = pinchOf(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val previous = pinch
                if (previous != null && event.pointerCount >= 2) {
                    val next = pinchOf(event)
                    transformViewport(previous, next)
                    pinch = next
                } else {
                    val index = event.findPointerIndex(activePointer)
                    if (index >= 0) appendLassoSamples(event, index)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (event.getPointerId(index) == activePointer) {
                    if (Build.VERSION.SDK_INT >= 33 && event.flags and MotionEvent.FLAG_CANCELED != 0) {
                        cancelLasso()
                    } else {
                        appendLassoSamples(event, index)
                        activePointer = MotionEvent.INVALID_POINTER_ID
                        stylusActive = false
                        finishLasso()
                    }
                }
                if (event.pointerCount <= 2) pinch = null
                if (event.actionMasked == MotionEvent.ACTION_UP) parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> cancelLasso()
        }
        postInvalidateOnAnimation()
        return true
    }

    private fun appendLassoSamples(event: MotionEvent, index: Int) {
        if (event.historySize >= MAX_LASSO_SAMPLES_PER_EVENT) {
            lassoOverflow = true
            return
        }
        for (sample in 0 until event.historySize) {
            if (lassoOverflow) return
            appendLassoPoint(event.getHistoricalX(index, sample), event.getHistoricalY(index, sample))
        }
        appendLassoPoint(event.getX(index), event.getY(index))
    }

    private fun appendLassoPoint(x: Float, y: Float) {
        if (lassoOverflow) return
        val viewport = InkViewport(scale.toDouble(), offsetX.toDouble(), offsetY.toDouble())
        val point = viewport.source(x.coerceIn(0f, width.toFloat()).toDouble(), y.coerceIn(0f, height.toFloat()).toDouble()) ?: return
        val previous = lassoPoints.lastOrNull()
        if (previous != null && hypot(previous.x - point.x, previous.y - point.y) * scale <
            3 * resources.displayMetrics.density) return
        if (lassoPoints.size == InkLasso.MAX_POINTS) {
            lassoOverflow = true
            return
        }
        lassoPoints.add(point)
        if (previous == null) lassoOutline.moveTo(point.x.toFloat(), point.y.toFloat())
        else lassoOutline.lineTo(point.x.toFloat(), point.y.toFloat())
    }

    private fun finishLasso() {
        if (lassoOverflow) {
            reportLasso(CanvasLassoState.TooComplex)
            return
        }
        val polygon = InkLasso.create(lassoPoints)
        if (polygon == null) {
            reportLasso(CanvasLassoState.Empty)
            return
        }
        lassoOutline.close()
        val bounds = polygon.bounds
        val candidates = linkedMapOf<UUID, InkStroke>()
        for (row in tileAt(bounds.top.toFloat())..tileAt(bounds.bottom.toFloat())) {
            for (column in tileAt(bounds.left.toFloat())..tileAt(bounds.right.toFloat())) {
                for (stroke in tileIndex[InkTile(column, row)].orEmpty()) {
                    candidates[stroke.id] = stroke
                    if (candidates.size > MAX_LASSO_CANDIDATES) {
                        reportLasso(CanvasLassoState.TooComplex)
                        return
                    }
                }
            }
        }
        val generation = ++lassoGeneration
        val snapshot = candidates.values.toList()
        reportLasso(CanvasLassoState.Working)
        lassoJob = lassoWorker.submit {
            when (val hits = polygon.select(snapshot, ::checkLassoCancelled)) {
                InkLassoHits.TooComplex -> postLasso(generation) { reportLasso(CanvasLassoState.TooComplex) }
                is InkLassoHits.Selected -> if (hits.strokes.isEmpty()) {
                    postLasso(generation) { reportLasso(CanvasLassoState.Empty) }
                } else if (hits.strokes.sumOf { it.samples.size } > MAX_PREVIEW_SAMPLES) {
                    postLasso(generation) { reportLasso(CanvasLassoState.TooComplex) }
                } else {
                    renderLasso(generation, polygon, hits.strokes, emptySet())
                }
            }
        }
    }

    fun toggleLassoStroke(id: UUID) {
        val (polygon, strokes) = lassoSelection ?: return
        if (strokes.none { it.id == id }) return
        excludedStrokes = if (id in excludedStrokes) excludedStrokes - id else excludedStrokes + id
        val excluded = excludedStrokes
        val generation = ++lassoGeneration
        lassoJob?.cancel(true)
        lassoJob = lassoWorker.submit { renderLasso(generation, polygon, strokes, excluded) }
    }

    private fun renderLasso(generation: Long, polygon: InkLasso, strokes: List<InkStroke>, excluded: Set<UUID>) {
        val included = strokes.filterNot { it.id in excluded }
        checkLassoCancelled()
        val bitmap = if (included.isEmpty()) null else lassoBitmap(polygon, included)
        postLasso(generation) {
            lassoSelection = polygon to strokes
            reportLasso(CanvasLassoState.Preview(bitmap, strokes.map(InkStroke::id), excluded))
        }
    }

    private fun postLasso(generation: Long, update: () -> Unit) {
        post { if (!closed && generation == lassoGeneration) update() }
    }

    private fun isPen(event: MotionEvent, pointer: Int): Boolean = when (event.getToolType(pointer)) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> true
        else -> false
    }

    private fun beginPointer(event: MotionEvent, pointer: Int) {
        val pen = isPen(event, pointer)
        if ((stylusOnly && !pen) || pendingStrokes.size >= MAX_PENDING_STROKES) return
        val erase = eraserSelected || event.getToolType(pointer) == MotionEvent.TOOL_TYPE_ERASER
        if (erase && pendingStrokes.isNotEmpty()) return
        activePointer = event.getPointerId(pointer)
        stylusActive = pen
        erasing = erase
        strokeTip = selectedTip
        if (!erase) measurements.start(pen)
        dispatchSample(event, pointer)
    }

    private fun dispatchSample(event: MotionEvent, pointer: Int) {
        if (!erasing) {
            record(event, pointer)
        } else if (eraseTarget == null) {
            eraseTarget = findEraseTarget(event.getX(pointer), event.getY(pointer))
        }
    }

    private fun findEraseTarget(x: Float, y: Float): InkStroke? {
        val worldX = (x - offsetX) / scale
        val worldY = (y - offsetY) / scale
        val radius = 10f * resources.displayMetrics.density / scale
        for (row in tileAt(worldY - radius)..tileAt(worldY + radius)) {
            for (column in tileAt(worldX - radius)..tileAt(worldX + radius)) {
                tileIndex[InkTile(column, row)]?.lastOrNull { it.isNear(worldX, worldY, radius) }?.let { return it }
            }
        }
        return null
    }

    private fun record(event: MotionEvent, pointer: Int) {
        var addedSample = false
        for (history in 0 until event.historySize) {
            if (addSample(event.getHistoricalX(pointer, history), event.getHistoricalY(pointer, history),
                    event.getHistoricalPressure(pointer, history))) {
                measurements.input(event.getHistoricalEventTime(history))
                addedSample = true
            }
        }
        val force = if (event.actionMasked == MotionEvent.ACTION_UP && stylusActive) {
            activeSamples.lastOrNull()?.pressure ?: event.getPressure(pointer)
        } else {
            event.getPressure(pointer)
        }
        if (addSample(event.getX(pointer), event.getY(pointer), force) && !addedSample) {
            measurements.input(event.eventTime)
        }
    }

    private fun addSample(x: Float, y: Float, pressure: Float): Boolean {
        val worldX = (x - offsetX) / scale
        val worldY = (y - offsetY) / scale
        if (worldX !in -MAX_INK_COORDINATE..MAX_INK_COORDINATE ||
            worldY !in -MAX_INK_COORDINATE..MAX_INK_COORDINATE) return false
        val sample = InkSample.fromPointer(worldX, worldY, pressure, stylusActive)
        val previous = activeSamples.lastOrNull()
        if (previous != null && (sample == previous || abs(previous.x - worldX) > MAX_INK_SAMPLE_DISTANCE ||
                abs(previous.y - worldY) > MAX_INK_SAMPLE_DISTANCE)) return false
        if (activeSamples.size == MAX_INK_SAMPLES) return false
        activeSamples.add(sample)
        return true
    }

    private fun discardActiveStroke() {
        activeSamples.clear()
        activePointer = MotionEvent.INVALID_POINTER_ID
        stylusActive = false
        erasing = false
        eraseTarget = null
        measurements.cancel()
    }

    private fun pinchOf(event: MotionEvent): Pinch {
        val x1 = event.getX(0)
        val y1 = event.getY(0)
        val x2 = event.getX(1)
        val y2 = event.getY(1)
        return Pinch((x1 + x2) / 2, (y1 + y2) / 2, hypot(x1 - x2, y1 - y2).coerceAtLeast(1f))
    }

    private fun transformViewport(previous: Pinch, next: Pinch) {
        val updatedScale = (scale * next.span / previous.span).coerceIn(minScale(), MAX_SCALE)
        val maximumOffset = (MAX_INK_COORDINATE - MAX_VISIBLE_WORLD_SIDE) * updatedScale
        offsetX = (next.x - (previous.x - offsetX) * updatedScale / scale).coerceIn(-maximumOffset, maximumOffset)
        offsetY = (next.y - (previous.y - offsetY) * updatedScale / scale).coerceIn(-maximumOffset, maximumOffset)
        scale = updatedScale
        updateVisibleTiles()
    }

    private fun minScale(): Float = (maxOf(width, height) / MAX_VISIBLE_WORLD_SIDE).coerceIn(0.5f, MAX_SCALE)

    private fun tileAt(world: Float): Int = floor(world / INK_TILE_SIZE).toInt()

    private fun updateVisibleTiles() {
        visibleTiles.clear()
        val left = tileAt(-offsetX / scale)
        val top = tileAt(-offsetY / scale)
        val right = tileAt((width - offsetX) / scale)
        val bottom = tileAt((height - offsetY) / scale)
        for (row in top..bottom) {
            for (column in left..right) visibleTiles.add(InkTile(column, row))
        }
    }

    private fun canRasterize(): Boolean = !closed && SystemClock.uptimeMillis() >= pauseRasterUntil

    fun trimRasterCache() {
        tileCache.evictAll()
        pauseRasterUntil = SystemClock.uptimeMillis() + 1_000
    }

    fun close() {
        closed = true
        cancelLasso()
        lassoWorker.shutdownNow()
        tileCache.evictAll()
        rasterWorker.shutdownNow()
    }

    private data class PendingStroke(val stroke: InkStroke, val failed: Boolean)
    private data class InkEdit(val stroke: InkStroke, val erased: Boolean)
    private data class Pinch(val x: Float, val y: Float, val span: Float)

    companion object {
        private val journalWorker = Executors.newSingleThreadExecutor()
        private const val MAX_CACHED_TILES = 96
        private const val MAX_PENDING_TILES = 16
        private const val MAX_PENDING_STROKES = 8
        private const val MAX_UNDO_EDITS = 50
        private const val MAX_LASSO_CANDIDATES = 256
        private const val MAX_LASSO_SAMPLES_PER_EVENT = 256
        private const val MAX_PREVIEW_SAMPLES = 80_000
        private const val MAX_VISIBLE_WORLD_SIDE = 2_048f
        private const val MAX_SCALE = 6f
    }
}

private fun checkLassoCancelled() {
    if (Thread.currentThread().isInterrupted) throw CancellationException()
}

private fun lassoBitmap(polygon: InkLasso, strokes: List<InkStroke>): Bitmap {
    val bounds = polygon.bounds
    val width = bounds.right - bounds.left
    val height = bounds.bottom - bounds.top
    val scale = minOf(2.0, 1024 / maxOf(width, height))
    val bitmap = Bitmap.createBitmap(
        ceil(width * scale).toInt().coerceIn(1, 1024),
        ceil(height * scale).toInt().coerceIn(1, 1024),
        Bitmap.Config.ARGB_8888,
    )
    bitmap.eraseColor(android.graphics.Color.WHITE)
    val clip = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        val first = polygon.points.first()
        moveTo(first.x.toFloat(), first.y.toFloat())
        for (point in polygon.points.drop(1)) lineTo(point.x.toFloat(), point.y.toFloat())
        close()
    }
    val canvas = Canvas(bitmap)
    canvas.scale(scale.toFloat(), scale.toFloat())
    canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
    canvas.clipPath(clip)
    val paint = newInkPaint()
    for (stroke in strokes) {
        checkLassoCancelled()
        paintStroke(canvas, stroke.samples, stroke.tip, paint)
    }
    return bitmap
}

private fun newInkPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xff272044.toInt()
    strokeCap = Paint.Cap.ROUND
}

private fun paintStroke(canvas: Canvas, samples: List<InkSample>, tip: InkTip, paint: Paint) {
    val first = samples.firstOrNull() ?: return
    canvas.drawCircle(first.x, first.y, inkWidth(first.pressure, tip) / 2f, paint)
    for (index in 1 until samples.size) {
        val from = samples[index - 1]
        val to = samples[index]
        paint.strokeWidth = inkWidth((from.pressure + to.pressure) / 2f, tip)
        canvas.drawLine(from.x, from.y, to.x, to.y, paint)
    }
}

private class InkMeasurements {
    private val drawTimes = ArrayDeque<Long>()
    private val drawDelays = ArrayDeque<Long>()
    private var pendingInput: Long? = null
    private var tracking = false
    private var stylus = false

    fun start(stylus: Boolean) {
        drawTimes.clear()
        drawDelays.clear()
        pendingInput = null
        tracking = true
        this.stylus = stylus
    }

    fun input(eventUptimeMs: Long) {
        if (tracking) pendingInput = minOf(pendingInput ?: eventUptimeMs, eventUptimeMs)
    }

    fun onDraw() {
        val eventTime = pendingInput ?: return
        pendingInput = null
        val drawTime = SystemClock.uptimeMillis()
        if (drawTimes.size == MAX_SAMPLES) drawTimes.removeFirst()
        if (drawDelays.size == MAX_SAMPLES) drawDelays.removeFirst()
        drawTimes.addLast(drawTime)
        drawDelays.addLast((drawTime - eventTime).coerceAtLeast(0))
    }

    fun finish() {
        if (drawTimes.size > 1) {
            val elapsed = drawTimes.last() - drawTimes.first()
            val drawRate = if (elapsed > 0) (drawTimes.size - 1) * 1_000.0 / elapsed else 0.0
            val delays = drawDelays.sorted()
            val percentile95 = delays[((delays.size * 95 + 99) / 100) - 1]
            Log.i("OmniInkProbe", "stylus=$stylus draws=${drawTimes.size} drawRateHz=$drawRate eventToDrawP95Ms=$percentile95")
        }
        cancel()
    }

    fun cancel() {
        pendingInput = null
        tracking = false
    }

    companion object {
        private const val MAX_SAMPLES = 240
    }
}
