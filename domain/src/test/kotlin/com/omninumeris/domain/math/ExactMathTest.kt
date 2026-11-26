package com.omninumeris.domain.math

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactMathTest {
    @Test
    fun `labelled expressions reproduce exact results or controlled errors`() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/math-typed-corpus.tsv"))
            .bufferedReader(Charsets.UTF_8).use { it.readLines() }
        for (line in lines.drop(1)) {
            val (id, input, kind, expected) = line.split('\t')
            val outcome = when (val actual = ExactMath.analyze(input)) {
                is ExactMathResult.Value -> "value" to actual.value.toString()
                is ExactMathResult.VerifiedLinearSolution -> "solution" to actual.value.toString()
                ExactMathResult.Identity -> "identity" to "-"
                ExactMathResult.Contradiction -> "contradiction" to "-"
                is ExactMathResult.Rejected -> "error" to actual.error.code.name
            }
            assertEquals("$id $input", kind to expected, outcome)
        }
    }

    @Test
    fun `seeded linear equations return a checked continuous step sequence`() {
        val random = Random(281)
        repeat(100) {
            val a = random.nextInt(1, 30)
            val b = random.nextInt(-20, 21)
            val solution = random.nextInt(-50, 51)
            val c = random.nextInt(31, 70)
            val d = (a - c) * solution + b
            val result = ExactMath.analyze("$a*x+($b)=$c*x+($d)") as ExactMathResult.VerifiedLinearSolution

            assertEquals(solution.toString(), result.value.toString())
            assertEquals(LinearRule.entries, result.steps.map(LinearStep::rule))
            assertEquals(result.steps[0].after, result.steps[1].before)
            assertEquals(result.steps[1].after, result.steps[2].before)
            assertEquals(result.value, result.steps.last().after.right.constant)
        }
    }

    @Test
    fun `bounded parser rejects excessive size and nesting`() {
        val oversized = "1+".repeat(260) + "1"
        val nested = "(".repeat(40) + "1" + ")".repeat(40)
        for (input in listOf(oversized, nested, "2^100")) {
            assertTrue(ExactMath.analyze(input) is ExactMathResult.Rejected)
        }
        val rejected = ExactMath.analyze(nested) as ExactMathResult.Rejected
        assertEquals(MathErrorCode.ComplexityLimit, rejected.error.code)
    }
}
