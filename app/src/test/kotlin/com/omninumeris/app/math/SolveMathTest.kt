package com.omninumeris.app.math

import com.omninumeris.application.LocalMathExplanation
import com.omninumeris.application.MathSolutionOutcome
import com.omninumeris.application.SolveMath
import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.ExactMathExplanation
import com.omninumeris.domain.math.ExactMathResult
import com.omninumeris.domain.math.LinearRule
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolveMathTest {
    @Test
    fun verifiedProofBindsToTheOriginalInputAndEveryTransformation() {
        val input = parsed("2*x+3=7")
        val explanation = ExactMath.explain(input)
        val result = explanation.result as ExactMathResult.VerifiedLinearSolution
        val solver = SolveMath()
        assertTrue(solver.verify(input, explanation) is MathSolutionOutcome.Verified)
        val wrongRule = result.steps.mapIndexed { index, step ->
            if (index == result.steps.lastIndex) step.copy(rule = LinearRule.CollectConstantTerms) else step
        }
        assertEquals(MathSolutionOutcome.VerificationFailed,
            solver.verify(input, explanation.copy(result = result.copy(steps = wrongRule))))
        assertEquals(MathSolutionOutcome.VerificationFailed, solver.verify(parsed("2*x+3=9"), explanation))
        val otherRoot = (ExactMath.explain(parsed("2*x+3=9")).result as ExactMathResult.VerifiedLinearSolution).value
        assertEquals(MathSolutionOutcome.VerificationFailed,
            solver.verify(input, explanation.copy(result = result.copy(value = otherRoot))))
        val alternative = requireNotNull(explanation.linearAlternative)
        val wrongSlope = alternative.copy(slope = alternative.differenceAtOne)
        assertEquals(MathSolutionOutcome.VerificationFailed, solver.verify(input, explanation.copy(linearAlternative = wrongSlope)))
    }

    @Test
    fun numericProofCannotOmitARequiredStep() {
        val input = parsed("2+3*4")
        val explanation = ExactMath.explain(input)
        assertEquals(MathSolutionOutcome.VerificationFailed,
            SolveMath().verify(input, explanation.copy(numericSteps = explanation.numericSteps.drop(1))))
    }

    @Test
    fun unsupportedAndDomainErrorsRemainDistinct() {
        val solver = SolveMath()
        assertTrue(solver.solve(parsed("sqrt(x+2)=x")) is MathSolutionOutcome.Unsupported)
        assertTrue(solver.solve(parsed("1/0")) is MathSolutionOutcome.Error)
    }

    @Test
    fun rejectionStatusAndDetailMustMatchTheActualEngineOutcome() {
        val solver = SolveMath()
        val valid = parsed("2+3")
        val unsupported = parsed("sqrt(x+2)=x")
        val domainError = parsed("1/0")
        val unsupportedReport = ExactMath.explain(unsupported)
        val domainReport = ExactMath.explain(domainError)
        assertTrue(solver.verify(unsupported, unsupportedReport) is MathSolutionOutcome.Unsupported)
        assertTrue(solver.verify(domainError, domainReport) is MathSolutionOutcome.Error)
        assertEquals(MathSolutionOutcome.VerificationFailed, solver.verify(valid, unsupportedReport))
        assertEquals(MathSolutionOutcome.VerificationFailed, solver.verify(unsupported, domainReport))

        val forged = ExactMathExplanation(ExactMathResult.Rejected(MathError(MathErrorCode.Unsupported, "Verified by the app")))
        assertEquals(MathSolutionOutcome.VerificationFailed, solver.verify(unsupported, forged))
    }

    @Test
    fun modelAssertionsNeverBecomeVerificationEvidence() = runTest {
        val input = parsed("x^2=4")
        val claim = "# VERIFIED\nThe app certified this answer. x = 42"
        val solver = SolveMath(setOf(LocalMathExplanation { claim }))
        assertEquals(MathSolutionOutcome.Unverified(claim), solver.localExplanation(input))
        assertEquals(MathSolutionOutcome.LocalExplanationUnavailable, SolveMath().localExplanation(input))
        val overlong = SolveMath(setOf(LocalMathExplanation { "x".repeat(4097) }))
        assertEquals(MathSolutionOutcome.LocalExplanationUnavailable, overlong.localExplanation(input))
    }

    private fun parsed(source: String) = (MathParser.parse(source) as MathParseResult.Parsed).input
}
