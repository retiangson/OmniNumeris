package com.omninumeris.domain.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactMathExplanationTest {
    @Test
    fun zeroCrossingMatchesTheVerifiedSolutionForBothSignsAndFractionalRoots() {
        val fixtures = listOf(
            "2*x+3=7" to listOf("-4", "-2", "2", "2"),
            "x/2+1=2*x" to listOf("1", "-1/2", "-3/2", "2/3"),
            "3*x+1=2*x+1" to listOf("0", "1", "1", "0"),
        )
        for ((source, expected) in fixtures) {
            val report = ExactMath.explain(input(source))
            val alternative = requireNotNull(report.linearAlternative)
            assertEquals(expected, listOf(
                alternative.differenceAtZero, alternative.differenceAtOne, alternative.slope, alternative.value,
            ).map { it.toString() })
            assertEquals((report.result as ExactMathResult.VerifiedLinearSolution).value, alternative.value)
        }
        for (source in listOf("2+3", "x=x", "x=x+1", "sqrt(x+2)=x", "(x^2-1)/(x-1)=0")) {
            assertNull(source, ExactMath.explain(input(source)).linearAlternative)
        }
    }

    @Test
    fun optionalSampleOutsideTheNumericBudgetDoesNotHideTheVerifiedBalance() {
        val coefficient = "((2^31)^32)^4*(2^31)^4*2^3"
        val report = ExactMath.explain(input("($coefficient)*x+($coefficient)=($coefficient)"))
        val solution = report.result as ExactMathResult.VerifiedLinearSolution
        assertEquals("0", solution.value.toString())
        assertEquals(solution.substitution.left, solution.substitution.right)
        assertNull(report.linearAlternative)
    }

    @Test
    fun linearExplanationIsOrderedAndChecksOriginalSides() {
        val solution = ExactMath.explain(input("2*x+3=7")).result as ExactMathResult.VerifiedLinearSolution
        assertEquals("2", solution.value.toString())
        assertEquals(listOf(LinearRule.CollectVariableTerms, LinearRule.CollectConstantTerms, LinearRule.DivideByCoefficient),
            solution.steps.map { it.rule })
        solution.steps.zipWithNext().forEach { (first, second) -> assertEquals(first.after, second.before) }
        assertEquals("7", solution.substitution.left.toString())
        assertEquals(solution.substitution.left, solution.substitution.right)
    }

    @Test
    fun exactArithmeticExplainsFractionsPercentagesRootsAndPowers() {
        val half = ExactMath.explain(input("1/3+1/6"))
        assertEquals(listOf(NumericRule.Divide, NumericRule.Divide, NumericRule.Add), half.numericSteps.map { it.rule })
        assertEquals("1/2", (half.result as ExactMathResult.Value).value.toString())
        val percentage = ExactMath.explain(input("15/100*80"))
        assertEquals("12", percentage.numericSteps.last().value.toString())
        val root = ExactMath.explain(input("sqrt(9/16)^2"))
        assertEquals(listOf(NumericRule.Divide, NumericRule.SquareRoot, NumericRule.Power), root.numericSteps.map { it.rule })
        assertEquals("9/16", (root.result as ExactMathResult.Value).value.toString())
    }

    @Test
    fun rejectedDomainAndUnsupportedEquationHaveNoAnswerOrPartialProof() {
        for (source in listOf("sqrt(-1)", "1/(2-2)", "sqrt(x+2)=x", "(x^2-1)/(x-1)=0")) {
            val report = ExactMath.explain(input(source))
            assertTrue(source, report.result is ExactMathResult.Rejected)
            assertTrue(report.numericSteps.isEmpty())
            assertTrue(ExactMath.hint(input(source)) is ExactMathHint.Unavailable)
        }
    }

    @Test
    fun hintsContainOnlyRuleGuidance() {
        assertEquals(ExactMathHint.Guidance.IsolateVariable, ExactMath.hint(input("2*x+3=7")))
        assertEquals(ExactMathHint.Guidance.CollectVariables, ExactMath.hint(input("2*x+3=x+5")))
        assertEquals(ExactMathHint.Guidance.CompareSides, ExactMath.hint(input("2*x+3=2*x+4")))
        assertEquals(ExactMathHint.Guidance.InnermostOperation, ExactMath.hint(input("2+3*4")))
        assertEquals(ExactMathResult.Identity, ExactMath.explain(input("x+1=1+x")).result)
        assertEquals(ExactMathResult.Contradiction, ExactMath.explain(input("x+1=2+x")).result)
    }

    private fun input(source: String) = (MathParser.parse(source) as MathParseResult.Parsed).input
}
