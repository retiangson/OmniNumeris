package com.omninumeris.app

import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CalculatorModesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun calculatorToolsDisplayCopyRecallAndSaveExactFractions() {
        assumeTrue("Clipboard verification requires a dedicated emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val activity = launchWorkspace()
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        try {
            click("Calculator")
            evaluate("1/2", "1/3+1/6")
            useTool("Store result in memory")
            useTool("Show decimals")
            awaitText("0.5")
            useTool("Display precision")
            click("4 significant digits")
            evaluate("≈ 0.3333", "1/3")
            useTool("Copy exact result")
            instrumentation.runOnMainSync {
                val clip = checkNotNull(clipboard.primaryClip)
                assertEquals("1/3", clip.getItemAt(0).text.toString())
                assertTrue(clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
            }
            useTool("Add result to memory")
            useTool("Subtract result from memory")
            useTool("Recall memory in Standard")
            await("exact recalled fraction in the editor") {
                it.any { node -> node.isEditable && node.text?.toString() == "1/2" }
            }
            useTool("Calculation history")
            awaitText("1/3+1/6")
            awaitText("1/2")
            click("Close")
        } finally {
            instrumentation.runOnMainSync {
                clipboard.clearPrimaryClip()
                activity.finish()
            }
        }
    }

    @Test
    fun floatingPanelResizesAndMovesOverEditorWithoutChangingEitherDraft() {
        val activity = launchWorkspace()
        try {
            click("Calculator")
            setExpression("272+19")
            click("Collapse calculator")
            awaitText("Restore calculator")
            click("Math editor")
            setExpression("2*x=6")
            click("Calculator")
            await("retained calculator draft") { nodes -> nodes.any { it.isEditable && it.text?.toString() == "272+19" } }

            val grip = handle("Resize calculator")
            val header = handle("Move calculator")
            adjustPanel("Make calculator narrower")
            adjustPanel("Make calculator shorter")
            await("panel shrank vertically and horizontally") {
                boundsFor(it, "Resize calculator")?.let { resized ->
                    resized.right < grip.right && resized.bottom < grip.bottom
                } == true
            }
            adjustPanel("Move calculator right")
            adjustPanel("Move calculator down")
            await("panel moved right and down") {
                boundsFor(it, "Move calculator")?.let { moved ->
                    moved.left > header.left && moved.top > header.top
                } == true
            }

            val moved = handle("Move calculator")
            adjustPanel("Move calculator left")
            await("accessible move left applied") {
                boundsFor(it, "Move calculator")?.let { it.left < moved.left } == true
            }

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitText("Restore calculator")
            awaitText("Math editor")
            click("Restore calculator")
            await("restored calculator draft") { nodes -> nodes.any { it.isEditable && it.text?.toString() == "272+19" } }
            click("Close calculator")
            await("independent editor draft") { nodes -> nodes.any { it.isEditable && it.text?.toString() == "2*x=6" } }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun floatingPanelHandlesTouchOverCanvasAndSharesItsDraftAcrossWorkspaceScreens() {
        val activity = launchWorkspace()
        try {
            click("Calculator")
            setExpression("927+64")
            click("Close calculator")
            click("Canvas prototype")
            val canvasLabel = activity.getString(R.string.canvas_accessibility_label)
            await("canvas surface") { nodes -> nodes.any { it.contentDescription?.toString() == canvasLabel } }
            click("Calculator")
            await("calculator draft from the dashboard") { nodes ->
                nodes.any { it.isEditable && it.text?.toString() == "927+64" }
            }
            val calculate = await("calculate button over the canvas") { nodes -> nodes.any { button(it, "Calculate") != null } }
                .firstNotNullOf { button(it, "Calculate") }
            val bounds = Rect().also(calculate::getBoundsInScreen)
            val down = SystemClock.uptimeMillis()
            touch(down, MotionEvent.ACTION_DOWN, bounds.exactCenterY(), bounds.exactCenterX())
            touch(down, MotionEvent.ACTION_UP, bounds.exactCenterY(), bounds.exactCenterX())
            awaitText("991")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitText("Restore calculator")
            await("canvas still open") { nodes -> nodes.any { it.contentDescription?.toString() == canvasLabel } }
            click("Back")
            click("Math editor")
            await("math editor open beneath the collapsed calculator") { nodes -> nodes.any { it.isEditable } }
            click("Restore calculator")
            awaitText("991")
            await("retained draft over the math editor") { nodes ->
                nodes.any { it.isEditable && it.text?.toString() == "927+64" }
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun floatingPanelRetainsItsDraftAcrossRotationAndCollapse() {
        launchWorkspace()
        try {
            click("Calculator")
            setExpression("271+19")
            val portrait = handle("Move calculator")
            instrumentation.runOnMainSync {
                resumedActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            await("landscape panel with the same draft") { nodes ->
                currentOrientation() == Configuration.ORIENTATION_LANDSCAPE &&
                    nodes.any { it.isEditable && it.text?.toString() == "271+19" }
            }
            assertTrue(handle("Move calculator").left > portrait.left)
            click("Collapse calculator")
            click("Restore calculator")
            click("Calculate")
            awaitText("290")
        } finally {
            instrumentation.runOnMainSync {
                resumedActivity()?.finish()
            }
        }
    }

    @Test
    fun floatingPanelKeepsItsControlsAboveTheKeyboard() {
        val activity = launchWorkspace()
        try {
            click("Calculator")
            handle("Resize calculator")
            val field = await("visible calculator field") { nodes -> nodes.any { it.isEditable && it.isVisibleToUser } }
                .first { it.isEditable && it.isVisibleToUser }
            val bounds = Rect().also(field::getBoundsInScreen)
            val down = SystemClock.uptimeMillis()
            touch(down, MotionEvent.ACTION_DOWN, bounds.exactCenterY(), bounds.exactCenterX())
            touch(down, MotionEvent.ACTION_UP, bounds.exactCenterY(), bounds.exactCenterX())
            await("keyboard and resize handle within the visible window") { nodes ->
                val keyboardTop = keyboardTop(activity)
                keyboardTop != null && boundsFor(nodes, "Resize calculator")?.let {
                    it.height() > 0 && it.bottom <= keyboardTop
                } == true
            }
            setExpression("19+23")
            click("Collapse calculator")
            click("Restore calculator")
            click("Calculate")
            awaitText("42")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun graphAnalysisReportsRootsAndYInterceptAsNumericEstimates() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Calculator")
            switch("Standard", "Graphing")
            evaluate("Function plotted", "x^2-4", button = "Plot function")
            openGraphAnalysis()
            awaitText("Results for x from -10 to 10")
            val roots = await("two approximate roots") { nodes ->
                nodes.count { it.text?.toString()?.startsWith("Root x ≈ ") == true } == 2
            }.mapNotNull { it.text?.toString()?.takeIf { text -> text.startsWith("Root x ≈ ") } }
                .map { it.substringAfter("≈ ").toDouble() }
            assertEquals(-2.0, roots.first(), 1e-9)
            assertEquals(2.0, roots.last(), 1e-9)
            awaitText("Y-intercept (0, -4) (exact)")
            click("Close")
            await("plot after dismissing analysis") { nodes -> nodes.any(::isGraph) }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun graphAnalysisShowsPairIntersectionsAndAnExplicitNoRootsResult() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Calculator")
            switch("Standard", "Graphing")
            for (source in listOf("x^2+1", "x", "x^2")) {
                evaluate("Function plotted", source, button = "Plot function")
                awaitText("y = $source")
            }
            openGraphAnalysis()
            awaitText("No roots found in this range.")
            scrollDownUntil("Intersections of x and x^2")
            val points = await("two approximate intersections") { nodes ->
                nodes.count { it.text?.toString()?.startsWith("≈ (") == true } == 2
            }.mapNotNull { it.text?.toString()?.takeIf { text -> text.startsWith("≈ (") } }
                .map(::graphNumbers)
            assertEquals(listOf(0.0, 0.0), points.first())
            assertEquals(1.0, points.last()[0], 1e-9)
            assertEquals(1.0, points.last()[1], 1e-9)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun graphPansAndPinchesWithTouchAndResetsWithButton() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Calculator")
            switch("Standard", "Graphing")
            evaluate("Function plotted", "x", button = "Plot function")
            val graph = await("graph with default bounds") { nodes ->
                nodes.any { isGraph(it) && viewportDescription(it) != null }
            }.first(::isGraph)
            val defaultBounds = checkNotNull(viewportDescription(graph))
            val bounds = Rect().also(graph::getBoundsInScreen)
            val x = bounds.left + bounds.width() * 0.55f
            val y = bounds.top + bounds.height() * 0.45f
            val span = bounds.width() * 0.15f

            val dragStart = SystemClock.uptimeMillis()
            touch(dragStart, MotionEvent.ACTION_DOWN, y, x)
            repeat(8) { step ->
                touch(dragStart, MotionEvent.ACTION_MOVE, y, x + span * (step + 1) / 8)
            }
            touch(dragStart, MotionEvent.ACTION_UP, y, x + span)
            val movedBounds = graphState("horizontal pan") {
                val values = graphNumbers(it)
                values[0] < -10 && values[1] < 10
            }
            val moved = graphNumbers(movedBounds)
            assertEquals(20.0, moved[1] - moved[0], 1e-6)
            assertEquals(-10.0, moved[2], 1e-6)
            assertEquals(10.0, moved[3], 1e-6)

            val pinchCenter = x - span
            val pinchStart = SystemClock.uptimeMillis()
            touch(pinchStart, MotionEvent.ACTION_DOWN, y, pinchCenter - span / 2)
            touch(pinchStart, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                y, pinchCenter - span / 2, pinchCenter + span / 2)
            repeat(8) { step ->
                val radius = span / 2 + span * (step + 1) / 16
                touch(pinchStart, MotionEvent.ACTION_MOVE, y, pinchCenter - radius, pinchCenter + radius)
            }
            touch(pinchStart, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                y, pinchCenter - span, pinchCenter + span)
            touch(pinchStart, MotionEvent.ACTION_UP, y, pinchCenter - span)
            val pinched = graphNumbers(graphState("narrower pinch viewport") {
                val values = graphNumbers(it)
                values[1] - values[0] < 18 && values[3] - values[2] < 18
            })
            val density = instrumentation.targetContext.resources.displayMetrics.density
            val fraction = (pinchCenter - bounds.left - 52 * density) / (bounds.width() - 74 * density)
            val worldX = moved[0] + fraction * (moved[1] - moved[0])
            // Accessibility reports integer window bounds, while touch and drawing use local float coordinates.
            assertEquals(worldX, pinched[0] + fraction * (pinched[1] - pinched[0]), 0.15)
            click("Reset view")
            graphState("reset viewport") { it == defaultBounds }
            awaitText("y = x")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun graphingShowsMultiplePlotsAndKeepsThemAfterAnInvalidEntry() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Calculator")
            switch("Standard", "Graphing")
            evaluate("Function plotted", "x^2", button = "Plot function")
            awaitText("y = x^2")
            evaluate("Function plotted", "x", button = "Plot function")
            awaitText("y = x")
            evaluate("Function plotted", "1/x", button = "Plot function")
            awaitText("y = 1/x")
            evaluate("Expected a number, x, a square root, or an opening parenthesis", "x+", button = "Plot function")
            await("axes and all three retained legends") { nodes ->
                nodes.any { it.contentDescription?.toString() ==
                    "2D function plot. Horizontal x-axis and vertical y-axis." } &&
                    setOf("y = x^2", "y = x", "y = 1/x").all { label -> nodes.any { it.text?.toString() == label } }
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun workspaceRunsAllEightModesAndKeepsDraftsOnSwitch() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Calculator")
            evaluate("14", "2+3×4")
            evaluate("Cannot divide by zero", "1/0")
            switch("Standard", "Scientific")
            evaluate("≈ 0.5", "30")
            click("Angle unit · Degrees")
            click("Radians")
            awaitText("Angle unit · Radians")
            evaluate("≈ 1", "pi/2")
            switch("Scientific", "Programmer")
            evaluate("FF", "255")
            switch("Programmer", "Financial")
            evaluate("1102.5", "1000", "0.05", "2")
            switch("Financial", "Statistics")
            evaluate("4", "2,4,6")
            switch("Statistics", "Matrices")
            evaluate("[17]\n[39]", "1,2\n3,4", "5\n6")
            evaluate("Matrix dimensions cannot be multiplied", "1,2", "5")
            switch("Matrices", "Graphing")
            evaluate("Function plotted", "x^2-1", button = "Plot function")
            switch("Graphing", "Unit conversions")
            evaluate("1000", "1")
            switch("Unit conversions", "Standard")
            await("restored standard draft") { nodes -> nodes.any { it.isEditable && it.text?.toString() == "1/0" } }
            click("Calculate")
            awaitText("Cannot divide by zero")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun switch(from: String, to: String) {
        click("Mode · $from")
        click(to)
        awaitText("Mode · $to")
    }

    private fun launchWorkspace(): MainActivity = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    ) as MainActivity

    private fun resumedActivity(): MainActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()

    private fun currentOrientation(): Int {
        var orientation = Configuration.ORIENTATION_UNDEFINED
        instrumentation.runOnMainSync {
            orientation = resumedActivity()?.resources?.configuration?.orientation ?: Configuration.ORIENTATION_UNDEFINED
        }
        return orientation
    }

    @Suppress("DEPRECATION")
    private fun keyboardTop(activity: MainActivity): Int? {
        var top: Int? = null
        instrumentation.runOnMainSync {
            val ime = WindowInsetsCompat.Type.ime()
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            if (insets?.isVisible(ime) == true) {
                val display = Point()
                activity.windowManager.defaultDisplay.getRealSize(display)
                top = display.y - insets.getInsets(ime).bottom
            }
        }
        return top
    }

    private fun setExpression(value: String) {
        val field = await("expression field") { nodes -> nodes.any { it.isEditable } }.first { it.isEditable }
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        assertTrue("Could not enter $value", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
    }

    private fun handle(label: String): Rect =
        checkNotNull(boundsFor(await(label) { boundsFor(it, label) != null }, label))

    private fun boundsFor(nodes: List<AccessibilityNodeInfo>, label: String): Rect? =
        nodes.firstOrNull { it.contentDescription?.toString() == label && it.isVisibleToUser }
            ?.let { Rect().also(it::getBoundsInScreen) }

    private fun adjustPanel(action: String) {
        click("Adjust panel")
        click(action)
    }

    private fun useTool(action: String) {
        click("Tools")
        awaitText("Display precision")
        scrollDownUntil(action)
        click(action)
    }

    private fun openGraphAnalysis() {
        val action = "Find roots and intersections"
        scrollDownUntil(action)
        click(action)
    }

    private fun scrollDownUntil(label: String) {
        repeat(5) {
            val nodes = await("calculator content") { it.isNotEmpty() }
            if (nodes.any { it.text?.toString() == label && it.isVisibleToUser }) return
            val scroller = nodes.firstOrNull { it.isScrollable }
                ?: error("No scrollable calculator content for $label")
            if (scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) SystemClock.sleep(350)
        }
        awaitText(label)
    }

    private fun isGraph(node: AccessibilityNodeInfo): Boolean =
        node.contentDescription?.toString() == "2D function plot. Horizontal x-axis and vertical y-axis."

    private fun viewportDescription(node: AccessibilityNodeInfo): String? =
        AccessibilityNodeInfoCompat.wrap(node).stateDescription?.toString()

    private fun graphState(description: String, predicate: (String) -> Boolean): String =
        await(description) { nodes ->
            nodes.any { isGraph(it) && viewportDescription(it)?.let(predicate) == true }
        }.firstNotNullOf { if (isGraph(it)) viewportDescription(it) else null }

    private fun graphNumbers(state: String): List<Double> =
        Regex("-?\\d+(?:\\.\\d+)?(?:E[+-]?\\d+)?").findAll(state).map { it.value.toDouble() }.toList()

    private fun touch(down: Long, action: Int, y: Float, vararg xs: Float) {
        val properties = Array(xs.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(xs.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = xs[index]
                this.y = y
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            down, SystemClock.uptimeMillis(), action, xs.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun evaluate(expected: String, vararg values: String, button: String = "Calculate") {
        values.forEachIndexed { index, value ->
            val field = await("${values.size} calculator fields") { nodes -> nodes.count { it.isEditable } == values.size }
                .filter { it.isEditable }[index]
            val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
            assertTrue("Could not set field $index", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            await("updated field $index") { nodes -> nodes.filter { it.isEditable }.getOrNull(index)?.text?.toString() == value }
        }
        click(button)
        awaitText(expected)
    }

    private fun click(label: String) {
        val node = await("button $label") { nodes -> nodes.any { button(it, label) != null } }
            .firstNotNullOf { button(it, label) }
        assertTrue("Could not click $label", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun button(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.text?.toString() != label && node.contentDescription?.toString() != label) return null
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null && !(candidate.refresh() && candidate.isClickable && candidate.isEnabled)) {
            candidate = candidate.parent
        }
        return candidate
    }

    private fun awaitText(expected: String) {
        await("text $expected") { nodes -> nodes.any { it.text?.toString() == expected } }
    }

    private fun await(description: String, predicate: (List<AccessibilityNodeInfo>) -> Boolean): List<AccessibilityNodeInfo> {
        val deadline = SystemClock.uptimeMillis() + 4_000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val nodes = if (root == null) emptyList() else descendants(root)
            if (predicate(nodes)) return nodes
            SystemClock.sleep(40)
        } while (SystemClock.uptimeMillis() < deadline)
        val controls = instrumentation.uiAutomation.rootInActiveWindow?.let(::descendants).orEmpty()
            .filter { it.isVisibleToUser }
            .mapNotNull { node -> node.text?.toString()?.let { "$it [clickable=${node.isClickable}]" } }
        error("Expected calculator UI state did not appear: $description. Visible labels: $controls")
    }

    private fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        if (node.packageName?.toString() == "com.omninumeris.app") add(node)
        for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(descendants(it)) }
    }
}
