package com.omninumeris.domain

import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathLatexParserTest {
    @Test
    fun `braced fractions roots and exponents use the typed AST with correct precedence`() {
        equivalent("\\frac{1}{2}+\\sqrt{49}", "1/2+sqrt(49)")
        equivalent("\\frac{x+3}{2}=2^{1+2^3}", "(x+3)/2=2^(1+2^3)")
        equivalent("\\frac{2}{3}^{2}", "(2/3)^2")
        equivalent("-2^{2} \\times 4 \\div 8 \\cdot x", "-2^2*4/8*x")
    }

    @Test
    fun `seeded nested commands match explicit typed grouping`() {
        val random = Random(702)
        repeat(80) {
            val a = random.nextInt(1, 40)
            val b = random.nextInt(1, 40)
            val exponent = random.nextInt(1, 4)
            equivalent(
                "\\frac{\\sqrt{$a}+$b}{x^{2^{$exponent}}}",
                "(sqrt($a)+$b)/(x^(2^$exponent))",
            )
        }
    }

    @Test
    fun `unsupported commands and malformed groups report original input positions`() {
        rejected("\\sin{x}", MathErrorCode.Unsupported, 0)
        rejected("\\sqrt [3]{8}", MathErrorCode.Unsupported, 6)
        rejected("2 \\timesx", MathErrorCode.Unsupported, 2)
        rejected("\\frac{1}2", MathErrorCode.Syntax, 8)
        rejected("2^{3", MathErrorCode.Syntax, 4)
        rejected("\\frac{1}{}", MathErrorCode.Syntax, 9)
        rejected("\\sqrt{".repeat(36) + "1" + "}".repeat(36), MathErrorCode.ComplexityLimit)
    }

    private fun equivalent(latex: String, typed: String) {
        val expected = MathParser.parse(typed)
        assertTrue("$typed should be supported", expected is MathParseResult.Parsed)
        assertEquals(latex, expected, MathParser.parse(latex))
    }

    private fun rejected(text: String, code: MathErrorCode, offset: Int? = null) {
        val result = MathParser.parse(text) as MathParseResult.Rejected
        assertEquals(text, code, result.error.code)
        if (offset != null) assertEquals(text, offset, result.error.position)
    }
}
