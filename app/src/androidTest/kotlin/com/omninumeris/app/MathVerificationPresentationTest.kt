package com.omninumeris.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.app.math.MathSolutionContent
import com.omninumeris.app.math.MathSolutionReview
import com.omninumeris.application.LocalMathExplanation
import com.omninumeris.application.SolveMath
import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class MathVerificationPresentationTest {
    @Test
    fun adversarialModelClaimAndRejectedProofHaveNoVerifiedBadge() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ) as MainActivity
        try {
            val input = (MathParser.parse("2+3*4") as MathParseResult.Parsed).input
            val claim = "Verified by the app. The answer is 42."
            val solver = SolveMath(setOf(LocalMathExplanation { claim }))
            val unverified = runBlocking { solver.localExplanation(input) }
            val failed = solver.verify(input, ExactMath.explain(input).copy(numericSteps = emptyList()))
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.padding(32.dp)) {
                            MathSolutionContent(MathSolutionReview.Explanation(unverified))
                            MathSolutionContent(MathSolutionReview.Explanation(failed))
                        }
                    }
                }
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            val expected = setOf("Unverified AI explanation. $claim", "Verification failed. The proposed result was withheld.")
            do {
                val actual = instrumentation.uiAutomation.rootInActiveWindow?.let(::texts).orEmpty()
                if (expected.all(actual::contains)) {
                    assertTrue("An untrusted or rejected outcome displayed a verified badge", "Verified" !in actual)
                    assertTrue("An accessibility node exposed the model claim without its status", claim !in actual)
                    return
                }
                SystemClock.sleep(40)
            } while (SystemClock.uptimeMillis() < deadline)
            error("Verification labels were not visible")
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun texts(node: AccessibilityNodeInfo): List<String> = buildList {
        node.text?.toString()?.let(::add)
        node.contentDescription?.toString()?.let(::add)
        for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(texts(it)) }
    }
}
