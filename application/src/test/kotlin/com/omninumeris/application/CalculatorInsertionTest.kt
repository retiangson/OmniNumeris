package com.omninumeris.application

import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.Calculator
import com.omninumeris.domain.calculator.Radix
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorInsertionTest {
    private val target = MathInsertionTarget(MathNoteDestination(id(), id()), id(), id())
    private val half = Calculator.calculate(Calculation.Standard("1/3+1/6"))
    private val session = CalculatorInsertionSession(id(), target, half)
    private val notes = RecordingNotes()
    private val insert = InsertCalculatorResult(notes)

    @Test
    fun exactResultAndFormattingMetadataSurvivePreviewAndExplicitApproval() = runImmediate {
        val proposal = preview(session)
        assertEquals(CalculatorCanonicalValue(CalculatorValueKind.Exact, "1/2"), proposal.value)
        assertEquals(target, proposal.target)
        assertEquals(emptyList<CalculatorInsertionProposal>(), notes.writes)
        val hexadecimal = preview(session.copy(result = CalculationResult.IntegerText("FF", Radix.Hexadecimal)))
        assertEquals(CalculatorCanonicalValue(CalculatorValueKind.Integer, "FF", Radix.Hexadecimal), hexadecimal.value)
        val approximate = preview(session.copy(result = CalculationResult.Approximate(1.23456789)))
        assertEquals(CalculatorCanonicalValue(CalculatorValueKind.Approximate, "1.23456789"), approximate.value)
        val written = insert.approve(proposal, session)
        assertTrue(written is MathInsertionCommit.Inserted)
        assertEquals(listOf(proposal), notes.writes)
    }

    @Test
    fun missingAndUnsupportedResultsAndOversizedValuesCannotBecomeProposals() {
        assertEquals(CalculatorInsertionReview.Rejected(MathInsertionProblem.NoEditableTarget), insert.preview(session.copy(target = null)))
        for (result in listOf(
            null,
            Calculator.calculate(Calculation.Standard("1/0")),
            Calculator.calculate(Calculation.Graph("x^2")),
            CalculationResult.Approximate(Double.NaN),
        )) {
            assertEquals(CalculatorInsertionReview.Rejected(MathInsertionProblem.NoInsertableResult), insert.preview(session.copy(result = result)))
        }
        val expandedDecimal = CalculationResult.Decimal(BigDecimal("1E+1000000"))
        assertEquals(CalculatorInsertionReview.Rejected(MathInsertionProblem.ResultTooLarge), insert.preview(session.copy(result = expandedDecimal)))
    }

    @Test
    fun staleApprovalDoesNotWriteAndLockedOrReadOnlyAdapterDecisionsReachCaller() = runImmediate {
        val proposal = preview(session)
        for (changed in listOf(
            session.copy(resultId = id()),
            session.copy(target = target.copy(revisionId = id())),
            session.copy(target = target.copy(positionId = id())),
            session.copy(result = Calculator.calculate(Calculation.Standard("1/3"))),
        )) {
            assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged), insert.approve(proposal, changed))
        }
        assertEquals(emptyList<CalculatorInsertionProposal>(), notes.writes)
        for (problem in listOf(MathInsertionProblem.Locked, MathInsertionProblem.ReadOnly)) {
            notes.denial = problem
            assertEquals(MathInsertionCommit.Rejected(problem), insert.approve(proposal, session))
        }
        notes.denial = null
        val receipt = insert.approve(proposal, session) as MathInsertionCommit.Inserted
        assertEquals(listOf(proposal), notes.writes)
        notes.denial = MathInsertionProblem.Locked
        assertEquals(MathInsertionUndoResult.Rejected(MathInsertionProblem.Locked), insert.undo(receipt.undo))
        notes.denial = null
        assertEquals(MathInsertionUndoResult.Restored, insert.undo(receipt.undo))
        assertTrue(notes.writes.isEmpty())
    }

    private fun preview(session: CalculatorInsertionSession) =
        (insert.preview(session) as CalculatorInsertionReview.Preview).proposal

    private class RecordingNotes : MathNoteInsertion {
        val writes = mutableListOf<CalculatorInsertionProposal>()
        var denial: MathInsertionProblem? = null

        override suspend fun insert(proposal: NoteInsertionProposal, authorization: MathInsertionAuthorization): MathInsertionCommit {
            check(proposal is CalculatorInsertionProposal)
            check(authorization == MathInsertionAuthorization.PreviewApproved)
            denial?.let { return MathInsertionCommit.Rejected(it) }
            writes.add(proposal)
            return MathInsertionCommit.Inserted(MathInsertionUndo(proposal.target.destination, proposal.resultId))
        }

        override suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult {
            denial?.let { return MathInsertionUndoResult.Rejected(it) }
            check(writes.removeAll { it.resultId == operation.operationId && it.target.destination == operation.destination })
            return MathInsertionUndoResult.Restored
        }
    }

    private fun id(): UUID = UUID.randomUUID()
}
