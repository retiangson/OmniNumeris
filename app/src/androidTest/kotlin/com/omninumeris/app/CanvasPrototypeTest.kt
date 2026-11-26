package com.omninumeris.app

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.app.canvas.InkCanvasView
import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTip
import com.omninumeris.domain.canvas.inkWidth
import com.omninumeris.infrastructure.canvas.PrototypeInkJournal
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPrototypeTest {
    @Test
    fun injectedStylusStrokeRetainsDifferentPressuresInJournal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testId = UUID.randomUUID()
        val location = File(context.noBackupFilesDir, "canvas-test-$testId")
        val journal = PrototypeInkJournal(location)
        val before = journal.recover().strokes.map { it.id }.toSet()
        val activity = instrumentation.startActivitySync(
            Intent(context, CanvasPrototypeActivity::class.java)
                .putExtra(CanvasPrototypeActivity.EXTRA_TEST_CANVAS, testId.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            var geometry: IntArray? = null
            repeat(50) {
                if (geometry != null) return@repeat
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val view = findCanvas(activity.window.decorView)
                    if (view != null && view.width > 0 && view.height > 0) {
                        val location = IntArray(2).also(view::getLocationOnScreen)
                        geometry = intArrayOf(location[0], location[1], view.width, view.height)
                    }
                }
                if (geometry == null) SystemClock.sleep(50)
            }
            val (left, top, width, height) = requireNotNull(geometry)
            SystemClock.sleep(300)
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
            val down = SystemClock.uptimeMillis()
            for (step in 0..80) {
                val pressure = 0.15f + 0.75f * (step / 80f)
                val coordinates = MotionEvent.PointerCoords().apply {
                    x = left + width * (0.15f + 0.7f * step / 80f)
                    y = top + height * (0.30f + 0.25f * step / 80f)
                    this.pressure = pressure
                    size = 0.1f
                }
                val action = when (step) {
                    0 -> MotionEvent.ACTION_DOWN
                    80 -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                val event = MotionEvent.obtain(
                    down, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coordinates),
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
                )
                try {
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, action != MotionEvent.ACTION_MOVE))
                } finally {
                    event.recycle()
                }
                if (step < 80) SystemClock.sleep(8)
            }

            var saved: InkStroke? = null
            repeat(50) {
                if (saved != null) return@repeat
                saved = journal.recover().strokes.singleOrNull { it.id !in before }
                if (saved == null) SystemClock.sleep(50)
            }
            assertNotNull("Injected stylus stroke was never committed", saved)
            val samples = requireNotNull(saved).samples
            val minPressure = samples.minOf { it.pressure }
            val maxPressure = samples.maxOf { it.pressure }
            assertTrue(samples.size >= 10)
            assertTrue(minPressure < 0.3f && maxPressure > 0.75f)
            assertTrue(inkWidth(maxPressure) > inkWidth(minPressure))
            assertEquals(InkTip.Fine, requireNotNull(saved).tip)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
            location.deleteRecursively()
        }
    }

    @Test
    fun stylusOnlyIgnoresTouchAndEraserAndUndoKeepJournalConsistent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testId = UUID.randomUUID()
        val location = File(context.noBackupFilesDir, "canvas-test-$testId")
        val journal = PrototypeInkJournal(location)
        val activity = instrumentation.startActivitySync(
            Intent(context, CanvasPrototypeActivity::class.java)
                .putExtra(CanvasPrototypeActivity.EXTRA_TEST_CANVAS, testId.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            instrumentation.waitForIdleSync()
            lateinit var canvas: InkCanvasView
            instrumentation.runOnMainSync { canvas = requireNotNull(findCanvas(activity.window.decorView)) }
            val stylus = MotionEvent.TOOL_TYPE_STYLUS
            val finger = MotionEvent.TOOL_TYPE_FINGER

            fun tap(tool: Int, endAction: Int = MotionEvent.ACTION_UP) {
                instrumentation.runOnMainSync {
                    val started = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, endAction)) {
                        val properties = MotionEvent.PointerProperties().apply {
                            id = 0
                            toolType = tool
                        }
                        val coordinates = MotionEvent.PointerCoords().apply {
                            x = 60f
                            y = 60f
                            pressure = 0.7f
                        }
                        val event = MotionEvent.obtain(
                            started, SystemClock.uptimeMillis(), action, 1, arrayOf(properties),
                            arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
                        )
                        try {
                            canvas.dispatchTouchEvent(event)
                        } finally {
                            event.recycle()
                        }
                    }
                }
            }

            tap(finger)
            tap(stylus, endAction = MotionEvent.ACTION_CANCEL)
            tap(stylus)
            val first = awaitStrokeCount(journal, 1).single()
            assertEquals(InkTip.Fine, first.tip)
            awaitCanvasNode("Undo", clickable = true)

            instrumentation.runOnMainSync { canvas.setTools(InkTip.Fine, eraser = true, penOnly = true) }
            tap(stylus, endAction = MotionEvent.ACTION_CANCEL)
            tap(stylus)
            awaitStrokeCount(journal, 0)
            clickCanvasControl("Undo")
            assertEquals(first.id, awaitStrokeCount(journal, 1).single().id)
            clickCanvasControl("Undo")
            awaitStrokeCount(journal, 0)
            awaitCanvasNode(context.getString(R.string.canvas_committed, 0))

            instrumentation.runOnMainSync { canvas.setTools(InkTip.Medium, eraser = false, penOnly = false) }
            tap(finger)
            assertEquals(InkTip.Medium, awaitStrokeCount(journal, 1).single().tip)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
            location.deleteRecursively()
        }
    }

    @Test
    fun zoomedLassoUsesBatchedMotionWithoutChangingTheInkJournal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testId = UUID.randomUUID()
        val location = File(context.noBackupFilesDir, "canvas-test-$testId")
        val journal = PrototypeInkJournal(location)
        for (points in listOf(
            listOf(32f to 32f, 44f to 44f),
            listOf(44f to 32f, 32f to 44f),
            listOf(110f to 110f),
        )) {
            journal.commit(InkStroke(UUID.randomUUID(), points.map { (x, y) -> InkSample(x, y, 0.5f) }))
        }
        fun fileContents() = location.walkTopDown().filter(File::isFile)
            .associate { it.relativeTo(location).path to it.readBytes().toList() }
        val original = fileContents()
        val activity = instrumentation.startActivitySync(
            Intent(context, CanvasPrototypeActivity::class.java)
                .putExtra(CanvasPrototypeActivity.EXTRA_TEST_CANVAS, testId.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            fun loop(start: Float, end: Float) = instrumentation.runOnMainSync {
                val canvas = requireNotNull(findCanvas(activity.window.decorView))
                val density = canvas.resources.displayMetrics.density
                val time = SystemClock.uptimeMillis()
                val pointer = MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_STYLUS
                }
                var eventTime = time
                fun dispatch(action: Int, vararg points: Pair<Float, Float>) {
                    val event = MotionEvent.obtain(
                        time, eventTime++, action, 1, arrayOf(pointer), arrayOf(coords(points.first(), density)),
                        0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
                    )
                    try {
                        points.drop(1).forEach { point ->
                            event.addBatch(eventTime++, arrayOf(coords(point, density)), 0)
                        }
                        assertEquals(points.size - 1, event.historySize)
                        canvas.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
                dispatch(MotionEvent.ACTION_DOWN, start to start)
                dispatch(MotionEvent.ACTION_MOVE, end to start, end to end, start to end)
                dispatch(MotionEvent.ACTION_UP, start to start)
            }

            fun pinchOut() = instrumentation.runOnMainSync {
                val canvas = requireNotNull(findCanvas(activity.window.decorView))
                val density = canvas.resources.displayMetrics.density
                val time = SystemClock.uptimeMillis()
                val fingers = Array(2) { index -> MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                } }
                val secondFinger = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
                fun dispatch(action: Int, vararg points: Pair<Float, Float>) {
                    val event = MotionEvent.obtain(
                        time, SystemClock.uptimeMillis(), action, points.size, fingers.copyOfRange(0, points.size),
                        points.map { coords(it, density) }.toTypedArray(), 0, 0, 1f, 1f, 0, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0,
                    )
                    try {
                        canvas.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
                val fixed = 12f to 12f
                dispatch(MotionEvent.ACTION_DOWN, fixed)
                dispatch(MotionEvent.ACTION_POINTER_DOWN or secondFinger, fixed, 52f to 12f)
                dispatch(MotionEvent.ACTION_MOVE, fixed, 92f to 12f)
                dispatch(MotionEvent.ACTION_POINTER_UP or secondFinger, fixed, 92f to 12f)
                dispatch(MotionEvent.ACTION_UP, fixed)
            }

            awaitStrokeCount(journal, 3)
            clickCanvasControl("Stylus only")
            clickCanvasControl("Lasso math")
            pinchOut()
            loop(24f, 112f)
            awaitCanvasNode("Selection preview")
            clickCanvasControl("Stroke 1")
            clickCanvasControl("Stroke 2")
            awaitCanvasNode("All strokes are excluded.")
            clickCanvasControl("Close")
            loop(120f, 135f)
            awaitCanvasNode("No ink in this selection.")
            clickCanvasControl("Close")
            assertEquals(original, fileContents())
        } finally {
            instrumentation.runOnMainSync(activity::finish)
            location.deleteRecursively()
        }
    }

    private fun coords(point: Pair<Float, Float>, density: Float) = MotionEvent.PointerCoords().apply {
        x = point.first * density
        y = point.second * density
        pressure = 0.5f
    }

    private fun clickCanvasControl(label: String) {
        val node = awaitCanvasNode(label, clickable = true)
        assertTrue("Could not click $label", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun awaitCanvasNode(label: String, clickable: Boolean = false): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            var node = root?.let { findCanvasNode(it, label) }
            if (clickable) {
                while (node != null && !node.isClickable) node = node.parent
            }
            if (node != null && (!clickable || node.isEnabled)) return node
            SystemClock.sleep(40)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Expected to see $label")
    }

    private fun findCanvasNode(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == label) return node
        for (index in 0 until node.childCount) {
            val found = node.getChild(index)?.let { findCanvasNode(it, label) }
            if (found != null) return found
        }
        return null
    }

    private fun awaitStrokeCount(journal: PrototypeInkJournal, count: Int): List<InkStroke> {
        val deadline = SystemClock.uptimeMillis() + 3_000
        do {
            val strokes = journal.recover().strokes
            if (strokes.size == count) return strokes
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Expected $count committed strokes")
    }

    private fun findCanvas(view: View): InkCanvasView? {
        if (view is InkCanvasView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findCanvas(view.getChildAt(index))
                if (found != null) return found
            }
        }
        return null
    }
}
