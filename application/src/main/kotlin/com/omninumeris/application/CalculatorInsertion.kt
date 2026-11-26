package com.omninumeris.application

import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Radix
import java.math.BigDecimal
import java.util.UUID

data class CalculatorCanonicalValue(val kind: CalculatorValueKind, val text: String, val radix: Radix? = null)

/** Full calculator output independent of any screen precision or approximation marker. */
fun CalculationResult.calculatorCanonicalValue(): CalculatorCanonicalValue? = when (this) {
    is CalculationResult.Exact -> CalculatorCanonicalValue(CalculatorValueKind.Exact, value.toString())
    is CalculationResult.Approximate -> if (value.isFinite()) {
        CalculatorCanonicalValue(CalculatorValueKind.Approximate, BigDecimal.valueOf(value).stripTrailingZeros().toString())
    } else null
    is CalculationResult.Decimal -> CalculatorCanonicalValue(CalculatorValueKind.Decimal, value.stripTrailingZeros().toPlainString())
    is CalculationResult.IntegerText -> CalculatorCanonicalValue(CalculatorValueKind.Integer, value, radix)
    is CalculationResult.Matrix -> CalculatorCanonicalValue(
        CalculatorValueKind.Matrix,
        rows.joinToString("\n") { it.joinToString(", ", "[", "]") },
    )
    is CalculationResult.GraphExpression, is CalculationResult.Rejected -> null
}

data class CalculatorInsertionSession(
    val resultId: UUID,
    val target: MathInsertionTarget?,
    val result: CalculationResult?,
)

class CalculatorInsertionProposal internal constructor(
    override val resultId: UUID,
    override val target: MathInsertionTarget,
    val value: CalculatorCanonicalValue,
) : NoteInsertionProposal

sealed interface CalculatorInsertionReview {
    data class Preview(val proposal: CalculatorInsertionProposal) : CalculatorInsertionReview
    data class Rejected(val problem: MathInsertionProblem) : CalculatorInsertionReview
}

class InsertCalculatorResult(private val notes: MathNoteInsertion) {
    fun preview(session: CalculatorInsertionSession): CalculatorInsertionReview {
        val target = session.target ?: return CalculatorInsertionReview.Rejected(MathInsertionProblem.NoEditableTarget)
        val result = session.result ?: return CalculatorInsertionReview.Rejected(MathInsertionProblem.NoInsertableResult)
        if (result is CalculationResult.Decimal && result.value.hasOversizedPlainText()) {
            return CalculatorInsertionReview.Rejected(MathInsertionProblem.ResultTooLarge)
        }
        val value = result.calculatorCanonicalValue()
            ?: return CalculatorInsertionReview.Rejected(MathInsertionProblem.NoInsertableResult)
        if (value.text.length > MAX_NOTE_RESULT_TEXT) {
            return CalculatorInsertionReview.Rejected(MathInsertionProblem.ResultTooLarge)
        }
        return CalculatorInsertionReview.Preview(CalculatorInsertionProposal(session.resultId, target, value))
    }

    suspend fun approve(proposal: CalculatorInsertionProposal, current: CalculatorInsertionSession): MathInsertionCommit {
        if (current.resultId != proposal.resultId || current.target != proposal.target) {
            return MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged)
        }
        val currentPreview = preview(current) as? CalculatorInsertionReview.Preview
        if (currentPreview?.proposal?.value != proposal.value) {
            return MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged)
        }
        return notes.insert(proposal, MathInsertionAuthorization.PreviewApproved)
    }

    suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult = notes.undo(operation)

    private fun BigDecimal.hasOversizedPlainText(): Boolean {
        if (signum() == 0) return false
        if (scale() <= -MAX_NOTE_RESULT_TEXT) return true
        val normalized = stripTrailingZeros()
        val scale = normalized.scale().toLong()
        val digits = normalized.precision().toLong()
        val magnitude = when {
            scale <= 0 -> digits - scale
            digits > scale -> digits + 1
            else -> scale + 2
        }
        return magnitude + (if (signum() < 0) 1L else 0L) > MAX_NOTE_RESULT_TEXT
    }

    private companion object {
        const val MAX_NOTE_RESULT_TEXT = 8_192
    }
}
