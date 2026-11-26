package com.omninumeris.app

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.InputDevice
import android.view.FrameMetrics
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.application.NotebookRead
import com.omninumeris.application.NotebookInkRegion
import com.omninumeris.domain.NotebookKind
import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.infrastructure.storage.RoomNotebookStore
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NotebookWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun physicalTabletMeasuresLargeNotebookCanvas() {
        val context = instrumentation.targetContext
        assumeTrue("Use the isolated notebook device fixture on physical hardware",
            context.packageName == "com.omninumeris.app.notebookfixture" && Build.HARDWARE !in setOf("ranchu", "goldfish"))
        val title = "Canvas benchmark ${UUID.randomUUID().toString().take(8)}"
        val strokeCount = 1_024
        runBlocking {
            val store = RoomNotebookStore.open(context)
            var page = (store.create(title, NotebookKind.Infinite) as NotebookRead.Available).value
            repeat(strokeCount) { index ->
                val x = 24f + index % 32 * 180f
                val y = 24f + index / 32 * 180f
                val stroke = InkStroke(UUID.randomUUID(), List(16) { point ->
                    InkSample(x + point * 2, y + (point % 6) * 3, 0.6f)
                })
                page = (store.appendInk(page, stroke) as NotebookRead.Available).value
            }
            val lookup = List(48) { index ->
                val left = index % 8 * 600f
                val top = index / 8 * 720f
                val start = SystemClock.elapsedRealtimeNanos()
                val reopened = (store.openById(page.notebook.id, page.id) as NotebookRead.Available).value
                val visible = (store.ink(reopened, NotebookInkRegion(left, top, left + 1_000f, top + 760f))
                    as NotebookRead.Available).value
                assertTrue(visible.strokes.isNotEmpty())
                SystemClock.elapsedRealtimeNanos() - start
            }.sorted()
            val result = String.format(Locale.ROOT,
                "strokes=%d lookups=%d p50_ms=%.2f p95_ms=%.2f",
                strokeCount, lookup.size, percentileMillis(lookup, 50), percentileMillis(lookup, 95))
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nNotebook storage benchmark: $result\n") })
        }
        if (InstrumentationRegistry.getArguments().getString("notebookStorageOnly") == "true") return
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val worker = HandlerThread("NotebookFrameMetrics").apply { start() }
        val frames = mutableListOf<Long>()
        var droppedReports = 0
        var listening = false
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metric, dropped ->
            synchronized(frames) {
                val duration = metric.getMetric(FrameMetrics.TOTAL_DURATION)
                if (duration > 0) frames.add(duration)
                droppedReports += dropped
            }
        }
        try {
            click("Notebooks")
            openNotebook(title)
            text("Infinite canvas")
            val touchPath = List(13) { step -> (0.75f - step * 0.035f) to 0.4f }
            path(touchPath, intervalMillis = 18)
            var refreshRate = 0f
            instrumentation.runOnMainSync {
                refreshRate = activity.window.decorView.display.refreshRate
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
                listening = true
            }
            val before = Debug.getPss()
            repeat(30) { index -> path(if (index % 2 == 0) touchPath else touchPath.asReversed(), intervalMillis = 18) }
            val after = Debug.getPss()
            instrumentation.runOnMainSync {
                activity.window.removeOnFrameMetricsAvailableListener(listener)
                listening = false
            }
            val (sorted, dropped) = synchronized(frames) { frames.sorted() to droppedReports }
            assertTrue("Expected frames from the production canvas, got ${sorted.size}", sorted.size >= 100)
            assertTrue("The canvas should remain available",
                instrumentation.uiAutomation.rootInActiveWindow?.let(::descendants).orEmpty().none {
                    it.text?.toString() == "Ink is unavailable for this area. Try a smaller area."
                })
            val metrics = String.format(Locale.ROOT,
                "strokes=%d frames=%d dropped_reports=%d refresh_hz=%.1f p50_ms=%.2f p95_ms=%.2f p99_ms=%.2f pss_before_kib=%d pss_after_kib=%d",
                strokeCount, sorted.size, dropped, refreshRate, percentileMillis(sorted, 50), percentileMillis(sorted, 95),
                percentileMillis(sorted, 99), before, after)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nNotebook benchmark: $metrics\n") })
        } finally {
            instrumentation.runOnMainSync {
                if (listening) activity.window.removeOnFrameMetricsAvailableListener(listener)
                activity.finish()
            }
            worker.quitSafely()
        }
    }

    private fun percentileMillis(sorted: List<Long>, percentile: Int): Double =
        sorted[(sorted.size * percentile + 99) / 100 - 1] / 1_000_000.0

    @Test
    fun drawnInkSupportsLassoTranscriptionInsertionAndUndo() {
        assumeTrue("This workflow creates a persistent fixture notebook", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val name = "Ink workflow ${UUID.randomUUID().toString().take(8)}"
        val lasso = listOf(0.13f to 0.15f, 0.49f to 0.15f, 0.49f to 0.47f, 0.13f to 0.47f, 0.13f to 0.15f)
        try {
            click("Notebooks")
            enter(name)
            click("Create fixed notebook")
            selectCanvasMode("Draw")
            path(listOf(0.2f to 0.27f, 0.3f to 0.33f, 0.4f to 0.27f))
            selectCanvasMode("Lasso")
            path(listOf(0.64f to 0.23f, 0.88f to 0.23f, 0.88f to 0.47f, 0.64f to 0.47f, 0.64f to 0.23f))
            text("No ink in the selection.")
            path(lasso)
            click("Review selected ink")
            text("Type the expression from the selected ink.")
            enter("8+4")
            click("Check expression")
            click("Confirm expression")
            click("Solve")
            click("Save solution")
            text("Solution saved.")
            click("Insert into note")
            click("Insert")
            text("Verified result added to the page.")
            click("Close review")
            click("Back")
            text("8+4\n12")
            click("8+4\n12")
            text("Original ink source")
            await("original ink preview") { nodes -> nodes.any { it.contentDescription?.toString() == "Ink used for this result" } }
            click("Open source page 1")
            text("Page 1 of 1")
            click("Undo insertion")
            await("math result removed") { nodes ->
                nodes.any { it.text?.toString() == "Fixed page" } && nodes.none { it.text?.toString() == "8+4\n12" }
            }
            click("Add page")
            text("Page 2 of 2")
            click("Previous page")
            text("Page 1 of 2")
            click("Manage pages")
            click("Move page later")
            text("Page 2 of 2")
            click("Manage pages")
            click("Undo page move")
            text("Page 1 of 2")
            click("Manage pages")
            click("Duplicate this page")
            text("Page 2 of 3")
            click("Manage pages")
            click("Insert page after this one")
            text("Page 3 of 4")
            click("Back")
            openNotebook(name)
            text("Page 1 of 4")
            click("Manage pages")
            click("Delete this page")
            text("Delete page 1?")
            click("Cancel")
            text("Page 1 of 4")
            click("Manage pages")
            click("Delete this page")
            click("Delete page")
            text("Page 1 of 3")
            click("Back")
            openNotebook(name)
            text("Page 1 of 3")
            click("Manage pages")
            click("Restore last deleted page")
            text("Page 1 of 4")
            click("Manage pages")
            click("Page 2")
            text("Page 2 of 4")
            click("Previous page")
            text("Page 1 of 4")
            selectCanvasMode("Lasso")
            path(listOf(0.05f to 0.05f, 0.72f to 0.05f, 0.72f to 0.75f, 0.05f to 0.75f, 0.05f to 0.05f))
            click("Review selected ink")
            text("Type the expression from the selected ink.")
            click("Solution history")
            click("8+4")
            text("Verified")
            text("Solution saved.")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun suggestionAwaitsApprovalAndAutomaticPermissionStaysOnOnePage() {
        assumeTrue("This workflow creates a persistent fixture notebook", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val name = "Math modes ${UUID.randomUUID().toString().take(8)}"
        try {
            click("Notebooks")
            enter(name)
            click("Create fixed notebook")
            click("Math insertion: Manual")
            click("Suggestion")
            text("Math insertion: Suggestion")
            click("Math editor")
            solve("3+4")
            text("Add verified result 7 to this page?")
            click("Close review")
            click("Back")
            await("suggestion left the page unchanged") { nodes ->
                nodes.any { it.text?.toString() == "Fixed page" } && nodes.none { it.text?.toString() == "3+4\n7" }
            }
            click("Math insertion: Suggestion")
            click("Automatic")
            text("Math insertion: Automatic")
            click("Back")
            openNotebook(name)
            text("Math insertion: Automatic")
            click("Math editor")
            solve("5+6")
            text("Verified result added to the page.")
            click("Solve")
            text("Verified result added to the page.")
            click("Close review")
            click("Back")
            text("5+6\n11")
            click("Undo insertion")
            await("undo removed the sole automatic insertion") { nodes ->
                nodes.any { it.text?.toString() == "Fixed page" } && nodes.none { it.text?.toString() == "5+6\n11" }
            }
            click("Add page")
            text("Math insertion: Manual")
            click("Previous page")
            text("Page 1 of 2")
            text("Math insertion: Automatic")
            click("Make read-only")
            text("Math insertion: Suggestion")
            click("Enable editing")
            text("Math insertion: Suggestion")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun verifiedMathRequiresApprovalAndCanBeUndoneAfterReopen() {
        assumeTrue("This workflow creates a persistent fixture notebook", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val name = "Verified math ${UUID.randomUUID().toString().take(8)}"
        val placed = "2*x+3=7\nx = 2"
        try {
            click("Notebooks")
            enter(name)
            click("Create fixed notebook")
            click("Math editor")
            solve("2*x+3=7")
            click("Insert into note")
            text("Add verified result x = 2 to this page?")
            click("Cancel")
            click("Close review")
            click("Back")
            await("empty notebook after cancelling preview") { nodes ->
                nodes.any { it.text?.toString() == "Fixed page" } &&
                    nodes.none { it.text?.toString() == placed }
            }
            click("Math editor")
            solve("2*x+3=7")
            click("Insert into note")
            click("Insert")
            text("Verified result added to the page.")
            click("Close review")
            click("Back")
            text(placed)
            click("Back")
            openNotebook(name)
            text(placed)
            click("Undo insertion")
            await("verified result removed") { nodes ->
                nodes.any { it.text?.toString() == "Fixed page" } && nodes.none { it.text?.toString() == placed }
            }
            click("Make read-only")
            click("Math editor")
            solve("2*x+3=7")
            click("Insert into note")
            text("This note is read-only.")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun solve(expression: String) {
        enter(expression)
        click("Review expression")
        click("Check expression")
        click("Confirm expression")
        click("Solve")
    }

    @Test
    fun insertReopenUndoAndRejectReadOnlyOnAnOrdinaryNotebook() {
        assumeTrue("This workflow creates persistent fixture notebooks", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            val fixture = UUID.randomUUID().toString().take(8)
            val fixedName = "Fixed $fixture"
            click("Notebooks")
            enter(fixedName)
            click("Create fixed notebook")
            await("fixed page") { it.any { node -> node.text?.toString() == "Fixed page" } }
            click("Calculator")
            enter("1/3+1/6")
            click("Calculate")
            text("1/2")
            click("Tools")
            click("Insert into note")
            click("Insert")
            click("Close calculator")
            text("1/2")
            click("Back")
            openNotebook(fixedName)
            text("1/2")
            click("Add page")
            text("Page 2 of 2")
            click("Previous page")
            text("Page 1 of 2")
            text("1/2")
            click("Undo insertion")
            await("inserted result removed") { nodes ->
                nodes.any { it.text?.toString() == "Fixed page" } && nodes.none { it.text?.toString() == "1/2" }
            }
            click("Make read-only")
            text("Enable editing")
            click("Calculator")
            click("Tools")
            click("Insert into note")
            text("This note is read-only.")
            click("OK")
            click("Close calculator")
            click("Back")
            enter("Infinite $fixture")
            click("Create infinite notebook")
            text("Infinite canvas")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun text(value: String) = await(value) { nodes -> nodes.any { it.text?.toString() == value } }

    private fun selectCanvasMode(label: String) {
        click(label)
        await("active canvas mode $label") { nodes ->
            nodes.any { it.text?.toString() == label } && nodes.none { button(it, label) != null }
        }
    }

    private fun path(points: List<Pair<Float, Float>>, intervalMillis: Long = 0) {
        val matches: (AccessibilityNodeInfo) -> Boolean = { it.contentDescription?.toString() == "Notebook canvas" }
        val node = await("notebook canvas") { nodes -> nodes.any(matches) }.first(matches)
        val bounds = Rect().also(node::getBoundsInScreen)
        assertTrue("Notebook canvas has gesture space", bounds.width() > 100 && bounds.height() > 100)
        val downTime = SystemClock.uptimeMillis()
        points.forEachIndexed { index, (x, y) ->
            val action = if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                bounds.left + x * bounds.width(), bounds.top + y * bounds.height(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            if (intervalMillis > 0) SystemClock.sleep(intervalMillis)
        }
        val (x, y) = points.last()
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
            bounds.left + x * bounds.width(), bounds.top + y * bounds.height(), 0)
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true))
        } finally {
            up.recycle()
        }
    }

    private fun openNotebook(title: String) {
        text("Notebooks")
        repeat(30) {
            val nodes = instrumentation.uiAutomation.rootInActiveWindow?.let(::descendants).orEmpty()
            val button = nodes.firstNotNullOfOrNull { button(it, title) }
            if (button != null && button.isVisibleToUser) {
                assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return
            }
            val list = nodes.firstOrNull { node ->
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
            } ?: error("Could not find notebook $title in the list")
            assertTrue(list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            SystemClock.sleep(200)
        }
        error("Could not scroll to notebook $title")
    }

    private fun enter(value: String) {
        val field = await("editable field") { nodes -> nodes.any { it.isEditable && it.isVisibleToUser } }
            .first { it.isEditable && it.isVisibleToUser }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        text(value)
    }

    private fun click(label: String) {
        await("clickable $label") { nodes ->
            val target = nodes.firstNotNullOfOrNull { button(it, label) }
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }

    private fun button(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.isEditable || node.text?.toString() != label && node.contentDescription?.toString() != label) return null
        return generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
    }

    private fun await(description: String, ready: (List<AccessibilityNodeInfo>) -> Boolean): List<AccessibilityNodeInfo> {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            val nodes = instrumentation.uiAutomation.rootInActiveWindow?.let(::descendants).orEmpty()
            if (ready(nodes)) return nodes
            SystemClock.sleep(40)
        } while (SystemClock.uptimeMillis() < deadline)
        val visible = instrumentation.uiAutomation.rootInActiveWindow?.let(::descendants).orEmpty()
            .filter { it.isVisibleToUser }.mapNotNull { it.text?.toString() }
        error("Notebook UI did not show $description. Visible: $visible")
    }

    private fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(node)
        for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(descendants(it)) }
    }
}
