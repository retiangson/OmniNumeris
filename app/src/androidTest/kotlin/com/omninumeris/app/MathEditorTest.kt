package com.omninumeris.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.app.math.MathInputViewModel
import com.omninumeris.app.math.MathReviewDialogs
import com.omninumeris.app.math.MathReviewSource
import com.omninumeris.app.math.MathSourceReviewSession
import com.omninumeris.app.math.MathSourceRevocation
import com.omninumeris.application.LinkedMathHistoryRead
import com.omninumeris.application.LinkedMathSolutionHistory
import com.omninumeris.application.LinkedMathSolutionRecord
import com.omninumeris.application.MathRecognitionAdapter
import com.omninumeris.application.MathRecognitionResult
import com.omninumeris.application.MathSolutionHistory
import com.omninumeris.application.MathSolutionHistoryRead
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.ResolveMathInput
import com.omninumeris.application.TypedSolutionAnswer
import com.omninumeris.application.TypedSolutionKind
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.domain.math.MathPageRegion
import com.omninumeris.domain.math.MathSourceAnchor
import com.omninumeris.domain.math.MathSourceRevision
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MathEditorTest {
    @Test
    fun sharedCalculatorReturnsToTheSameCorrectionAndHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Calculator")
            enter("13/26")
            click("Calculate")
            find { it.text?.toString() == "1/2" }
            click("Close calculator")

            click("Math editor")
            find { it.text?.toString() == "Input sources" }
            enter("x^2=27")
            click("Review expression")
            find { !it.isEditable && it.text?.toString() == "Original expression" }
            enter("x^3=27")
            click("Calculator")
            find { it.text?.toString() == "1/2" }
            click("Collapse calculator")
            find { it.isEditable && it.text?.toString() == "x^3=27" }
            find { !it.isEditable && it.text?.toString() == "x^2=27" }
            click("Cancel review")
            find { it.isEditable && it.text?.toString() == "x^2=27" }

            click("Solution history")
            find { it.text?.toString() == "Close" }
            click("Calculator")
            find { it.text?.toString() == "1/2" }
            find { it.isEditable && it.text?.toString() == "13/26" }
            click("Close calculator")
            click("Close")
            find { it.isEditable && it.text?.toString() == "x^2=27" }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun replacingAnOpenReviewReplacesTheDisplayedDraftBeforeVerification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ) as MainActivity
        val model = MathInputViewModel(ResolveMathInput(emptySet()), Dispatchers.Default, object : MathSolutionHistory {
            override suspend fun read() = MathSolutionHistoryRead.Unavailable
            override suspend fun save(record: TypedSolutionRecord) = MathSolutionSave.Unavailable
        })
        try {
            instrumentation.runOnMainSync {
                activity.setContent { MaterialTheme { MathReviewDialogs(model) {} } }
                model.beginTypedReview("x^2=27")
            }
            enter("x^3=27")
            instrumentation.runOnMainSync { model.beginTypedReview("2*x=8") }
            find { it.isEditable && it.text?.toString() == "2*x=8" }
            click("Check expression")
            click("Confirm expression")
            click("Solve")
            find { it.text?.toString() == "Verified" }
            find { it.text?.toString() == "x = 4" }
        } finally {
            instrumentation.runOnMainSync {
                model.dismissReview()
                activity.finish()
            }
        }
    }

    @Test
    fun linkedHistoryReopensTheSourceReviewAndDisappearsWhenItLocks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ) as MainActivity
        val anchor = MathSourceAnchor.Pdf(
            MathSourceRevision(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), MathPageRegion(0.0, 0.0, 1.0, 1.0),
        )
        val record = LinkedMathSolutionRecord(anchor, TypedSolutionRecord(
            UUID.randomUUID(), "2*x=9", "2*x=8", TypedSolutionAnswer(TypedSolutionKind.Linear, "4"),
        ))
        val history = object : LinkedMathSolutionHistory {
            override suspend fun read(anchor: MathSourceAnchor) = LinkedMathHistoryRead.Available(listOf(record))
            override suspend fun save(record: LinkedMathSolutionRecord) = MathSolutionSave.Unavailable
        }
        val adapter = object : MathRecognitionAdapter {
            override val source = MathInputSource.Pdf
            override suspend fun recognize(anchor: MathSourceAnchor) = MathRecognitionResult.Texts(listOf("2*x=9"))
        }
        val model = MathInputViewModel(ResolveMathInput(setOf(adapter)), Dispatchers.Default, object : MathSolutionHistory {
            override suspend fun read() = MathSolutionHistoryRead.Unavailable
            override suspend fun save(record: TypedSolutionRecord) = MathSolutionSave.Unavailable
        })
        val selection = MathSourceReviewSession(anchor, history)
        val privatePreview = "Selected private source"
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme { MathReviewDialogs(model) { error("A source record must remain outside the typed draft") } }
                }
                model.recognize(selection, MathReviewSource.Text(privatePreview))
            }
            find { it.text?.toString() == privatePreview }
            click("Solution history")
            click("2*x=8")
            find { it.text?.toString() == "Verified" }
            find { it.text?.toString() == "x = 4" }
            click("Solution history")
            find { it.text?.toString() == "2*x=8" }
            selection.revoke(MathSourceRevocation.Locked)
            find { it.text?.toString() == "This source is locked." }
            val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            assertTrue(search(root) { it.text?.toString() in setOf(privatePreview, "2*x=8", "x = 4") } == null)
        } finally {
            instrumentation.runOnMainSync {
                model.dismissReview()
                activity.finish()
            }
        }
    }

    @Test
    fun solverShowsHintBeforeAnswerAndVerifiesLinearSteps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Math editor")
            enter("2*x+3=7")
            click("Review expression")
            find { it.text?.toString() == "Original expression" }
            click("Check expression")
            click("Confirm expression")
            click("Hint")
            find { it.text?.toString()?.startsWith("Move the constant away") == true }
            val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            assertTrue(search(root) { it.text?.toString() == "x = 2" } == null)
            click("Solve")
            find { it.text?.toString() == "Verified" }
            find { it.text?.toString() == "x = 2" }
            find { it.text?.toString() == "Subtract the left-hand constant from both sides." }
            find { it.text?.toString() == "Substitution in the original equation gives 7 = 7." }
            click("Show alternative method")
            find { it.text?.toString() == "Find where the difference is zero" }
            find { it.text?.toString() == "The slope is f(1) − f(0) = 2." }
            find { it.text?.toString() == "Divide −f(0) by the slope to get x = 2." }
            click("Show balancing method")
            find { it.text?.toString() == "Subtract the left-hand constant from both sides." }
            click("Save solution")
            find { it.text?.toString() == "Solution saved." }
            click("Close review")
            enter("3+4")
            click("Solution history")
            click("2*x+3=7")
            find { it.text?.toString() == "Review expression" && !it.isEditable }
            find { it.text?.toString() == "Substitution in the original equation gives 7 = 7." }
            click("Show alternative method")
            find { it.text?.toString() == "Divide −f(0) by the slope to get x = 2." }
            click("Close review")
            find { it.isEditable && it.text?.toString() == "3+4" }
            click("Review expression")
            click("Check expression")
            click("Confirm expression")
            click("Solve")
            find { it.text?.toString() == "Alternative method unavailable." }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun correctionKeepsOriginalAndRequiresConfirmationOfTheCurrentDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Math editor")
            enter("x^2=27")
            click("Review expression")
            find { !it.isEditable && it.text?.toString() == "Original expression" }
            find { !it.isEditable && it.text?.toString() == "x^2=27" }
            enter("x^")
            click("Check expression")
            find { it.text?.toString()?.contains("Expected a number") == true }
            find { it.isEditable && it.text?.toString() == "x^" }

            enter("x^3=27")
            click("Check expression")
            find { it.text?.toString() == "Expression parsed successfully." }
            enter("x^4=27")
            find { it.text?.toString() == "Check expression" }
            click("Cancel review")
            find { it.isEditable && it.text?.toString() == "x^2=27" }

            click("Review expression")
            find { it.text?.toString() == "Original expression" }
            enter("x^3=27")
            click("Check expression")
            click("Confirm expression")
            find { it.text?.toString() == "Expression confirmed." }
            click("Close review")
            find { it.isEditable && it.text?.toString() == "x^3=27" }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun mathInputReportsAvailableSourcesAndKeepsTypedDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            click("Math editor")
            val expression = "2*x+1=7"
            assertTrue(find { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expression)
            }))
            click("Input sources")
            find { it.text?.toString() == "Typed · type or paste an expression" }
            for (source in listOf("Handwriting", "Camera", "Book", "PDF")) {
                find { it.text?.toString() == "$source · unavailable in this build" }
            }
            click("Return to typing")
            assertEquals(expression, find { it.isEditable }.text.toString())
            find { it.text?.toString() == "Preview" }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    @Test
    fun keyboardAndClipboardProduceEditableOfflinePreview() {
        assumeTrue("Clipboard instrumentation requires a dedicated emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        try {
            click("Math editor")
            click("Fraction")
            click("1")
            click("Move cursor right")
            click("Move cursor right")
            click("2")
            find { it.isEditable && it.text?.toString() == "\\frac{1}{2}" }
            find { it.contentDescription?.toString() == "(1) over (2)" }

            val editable = find { it.isEditable }
            assertTrue(editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }))
            val empty = find { it.isEditable }
            assertTrue(empty.performAction(AccessibilityNodeInfo.ACTION_FOCUS) || empty.isFocused)
            instrumentation.runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("test", "2^{3^{2}}")) }
            assertTrue(find { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_PASTE))
            find { it.contentDescription?.toString() == "(2) to the power of ((3) to the power of (2))" }
            assertEquals("2^{3^{2}}", find { it.isEditable }.text.toString())

            val unsupported = "\\sin{x}"
            assertTrue(find { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, unsupported)
            }))
            find { it.text?.toString()?.contains("supported command") == true }
            assertEquals(unsupported, find { it.isEditable }.text.toString())
            click("Back")
            find { it.text?.toString() == "OmniNumeris" }
            click("Math editor")
            find { it.isEditable && it.text?.toString() == unsupported }
        } finally {
            instrumentation.runOnMainSync {
                clipboard.clearPrimaryClip()
                activity.finish()
            }
        }
    }

    private fun click(label: String) {
        var node: AccessibilityNodeInfo? = find {
            it.text?.toString() == label || it.contentDescription?.toString() == label
        }
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Could not click $label", node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun enter(text: String) {
        val editable = find { it.isEditable && it.isVisibleToUser }
        assertTrue(editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        find { it.isEditable && it.isVisibleToUser && it.text?.toString() == text }
    }

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 4_000
        do {
            val node = automation.rootInActiveWindow?.let { search(it, predicate) }
            if (node != null) return node
            SystemClock.sleep(40)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Expected math editor accessibility node was not found")
    }

    private fun search(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node.packageName?.toString() == "com.omninumeris.app" && predicate(node)) return node
        for (index in 0 until node.childCount) {
            val match = node.getChild(index)?.let { search(it, predicate) }
            if (match != null) return match
        }
        return null
    }
}
