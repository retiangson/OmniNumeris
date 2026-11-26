package com.omninumeris.app.calculator

import com.omninumeris.domain.calculator.AngleUnit
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.math.MathErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorInputTest {
    @Test
    fun scientificParsesPiAndPreservesErrors() {
        val draft = CalculatorDraft(main = "π/2", angle = AngleUnit.Radians)
        val sine = evaluateDraft(CalculatorMode.Scientific, draft) as CalculationResult.Approximate
        assertEquals(1.0, sine.value, 1e-12)
        val error = evaluateDraft(CalculatorMode.Scientific, draft.copy(main = "1/0")) as CalculationResult.Rejected
        assertEquals(MathErrorCode.DivisionByZero, error.error.code)
    }

    @Test
    fun matrixInputsSupportRowsAndRejectEmptyValues() {
        val matrix = CalculatorDraft(main = "1,2\n3,4", second = "5\n6")
        val result = evaluateDraft(CalculatorMode.Matrices, matrix) as CalculationResult.Matrix
        assertEquals(listOf("17", "39"), result.rows.map { it.single().toString() })
        val error = evaluateDraft(CalculatorMode.Matrices, matrix.copy(main = "1,\n3,4")) as CalculationResult.Rejected
        assertEquals(MathErrorCode.Syntax, error.error.code)
    }

    @Test
    fun formRejectsUnboundedAndIncompleteInputs() {
        val oversized = evaluateDraft(CalculatorMode.Statistics, CalculatorDraft(main = "1,".repeat(5_000)))
            as CalculationResult.Rejected
        assertEquals(MathErrorCode.ComplexityLimit, oversized.error.code)
        val incomplete = evaluateDraft(CalculatorMode.Financial, CalculatorDraft(main = "1000"))
            as CalculationResult.Rejected
        assertEquals(MathErrorCode.Syntax, incomplete.error.code)
    }
}
