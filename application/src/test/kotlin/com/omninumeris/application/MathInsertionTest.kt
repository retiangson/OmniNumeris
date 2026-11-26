package com.omninumeris.application

import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.math.MathSourceAnchor
import com.omninumeris.domain.math.MathSourceRevision
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathInsertionTest {
    private val target = MathInsertionTarget(MathNoteDestination(id(), id()), id(), id())
    private val session = MathInsertionSession(id(), target)
    private val solver = SolveMath()
    private val reviewed = reviewed("2+3")
    private val verified = solver.solve(reviewed.input)
    private val notes = NoteFake(target)
    private val insert = InsertVerifiedMath(notes)

    @Test
    fun manualRequiresRequestAndSuggestionRequiresApproval() = runImmediate {
        assertEquals(MathInsertionDecision.AwaitingManualRequest, insert.onResult(session, reviewed, verified))
        assertTrue(insert.onResult(session, reviewed, verified, manuallyRequested = true) is MathInsertionDecision.Preview)
        val suggestion = session.copy(mode = MathInsertionMode.Suggestion)
        val preview = insert.onResult(suggestion, reviewed, verified) as MathInsertionDecision.Preview
        assertEquals("5", preview.proposal.answer.value)
        assertEquals(0, notes.attempts)
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged), insert.approve(preview.proposal, session))
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged),
            insert.approve(preview.proposal, suggestion.copy(resultId = id())))
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged),
            insert.approve(preview.proposal, suggestion.copy(target = target.copy(positionId = id()))))
        assertTrue(insert.approve(preview.proposal, suggestion) is MathInsertionCommit.Inserted)
        assertEquals(MathInsertionAuthorization.PreviewApproved, notes.lastAuthorization)
    }

    @Test
    fun automaticRequiresLiveOptInForTheSameDestinationAndRejectsUnverifiedOrMismatchedMath() = runImmediate {
        val automatic = session.copy(mode = MathInsertionMode.Automatic)
        val wrong = MathAutomaticInsertionGrant(id(), MathNoteDestination(id(), id()))
        assertTrue(insert.onResult(automatic.copy(automaticGrant = wrong), reviewed, verified) is MathInsertionDecision.Preview)
        val grant = MathAutomaticInsertionGrant(id(), target.destination)
        val enabled = automatic.copy(automaticGrant = grant)
        assertEquals(finished(MathInsertionProblem.UnverifiedResult),
            insert.onResult(enabled, reviewed, MathSolutionOutcome.Unverified("The answer is 5")))
        assertEquals(finished(MathInsertionProblem.ExpressionChanged), insert.onResult(enabled, reviewed("4+1"), verified))
        assertEquals(finished(MathInsertionProblem.ExpressionChanged),
            insert.onResult(enabled, reviewed.copy(candidate = reviewed.candidate.copy(text = "4+1")), verified))
        assertEquals(finished(MathInsertionProblem.NoEditableTarget), insert.onResult(enabled.copy(target = null), reviewed, verified))
        assertEquals(0, notes.attempts)
        assertEquals(finished(MathInsertionProblem.AutomaticGrantRevoked), insert.onResult(enabled, reviewed, verified))
        notes.activeGrant = grant
        val result = insert.onResult(enabled, reviewed, verified) as MathInsertionDecision.Finished
        assertTrue(result.result is MathInsertionCommit.Inserted)
        assertEquals(MathInsertionAuthorization.Automatic(grant), notes.lastAuthorization)
        assertEquals(result, InsertVerifiedMath(notes).onResult(enabled, reviewed, verified))
        assertEquals(listOf("Original note", "5"), notes.content)
    }

    @Test
    fun approvalReauthorizesAndRetriesDoNotDuplicateOrReapplyAnUndoneInsertion() = runImmediate {
        val sourceRev = MathSourceRevision(id(), id())
        val anchor = MathSourceAnchor.Ink(sourceRev, id(), listOf(id()))
        val ink = reviewed.copy(candidate = reviewed.candidate.copy(anchor = anchor))
        val suggestion = session.copy(mode = MathInsertionMode.Suggestion)
        val proposal = (insert.onResult(suggestion, ink, verified) as MathInsertionDecision.Preview).proposal
        assertEquals(anchor, proposal.source)
        listOf(MathInsertionProblem.Locked, MathInsertionProblem.ReadOnly, MathInsertionProblem.SourceUnavailable,
            MathInsertionProblem.RevisionChanged).forEach { problem ->
            notes.denial = problem
            assertEquals(MathInsertionCommit.Rejected(problem), insert.approve(proposal, suggestion))
            assertEquals(listOf("Original note"), notes.content)
        }
        notes.denial = null
        val receipt = insert.approve(proposal, suggestion) as MathInsertionCommit.Inserted
        val reopened = InsertVerifiedMath(notes)
        assertEquals(receipt, reopened.approve(proposal, suggestion))
        assertEquals(listOf("Original note", "5"), notes.content)
        notes.denial = MathInsertionProblem.Locked
        assertEquals(MathInsertionUndoResult.Rejected(MathInsertionProblem.Locked), reopened.undo(receipt.undo))
        notes.denial = null
        notes.content.add("Later edit")
        assertEquals(MathInsertionUndoResult.Restored, reopened.undo(receipt.undo))
        assertEquals(listOf("Original note", "Later edit"), notes.content)
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.OperationConflict), reopened.approve(proposal, suggestion))
    }

    private fun reviewed(text: String) = ReviewedMathInput(
        (MathParser.parse(text) as MathParseResult.Parsed).input,
        ResolveMathInput(emptySet()).typed(text),
    )

    private fun finished(problem: MathInsertionProblem) = MathInsertionDecision.Finished(MathInsertionCommit.Rejected(problem))

    private class NoteFake(private val target: MathInsertionTarget) : MathNoteInsertion {
        val content = mutableListOf("Original note")
        var attempts = 0
        var denial: MathInsertionProblem? = null
        var lastAuthorization: MathInsertionAuthorization? = null
        var activeGrant: MathAutomaticInsertionGrant? = null
        private var applied: MathInsertionProposal? = null
        private var undone = false

        override suspend fun insert(proposal: NoteInsertionProposal, authorization: MathInsertionAuthorization): MathInsertionCommit {
            check(proposal is MathInsertionProposal)
            attempts++
            lastAuthorization = authorization
            denial?.let { return MathInsertionCommit.Rejected(it) }
            if (authorization is MathInsertionAuthorization.Automatic && authorization.grant != activeGrant) {
                return MathInsertionCommit.Rejected(MathInsertionProblem.AutomaticGrantRevoked)
            }
            val previous = applied
            if (previous != null && (undone || previous.resultId != proposal.resultId ||
                    previous.target != proposal.target || previous.expression != proposal.expression ||
                    previous.source != proposal.source || previous.answer != proposal.answer)) {
                return MathInsertionCommit.Rejected(MathInsertionProblem.OperationConflict)
            }
            if (previous == null) {
                require(proposal.target == target)
                content.add(proposal.answer.value)
                applied = proposal
            }
            return MathInsertionCommit.Inserted(MathInsertionUndo(target.destination, proposal.resultId))
        }

        override suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult {
            denial?.let { return MathInsertionUndoResult.Rejected(it) }
            require(operation == MathInsertionUndo(target.destination, requireNotNull(applied).resultId))
            if (!undone) content.removeAt(1)
            undone = true
            return MathInsertionUndoResult.Restored
        }
    }

    private fun id(): UUID = UUID.randomUUID()
}

internal fun runImmediate(test: suspend () -> Unit) {
    var completion: Result<Unit>? = null
    test.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            completion = result
        }
    })
    checkNotNull(completion) { "The in-memory test adapter must complete synchronously" }.getOrThrow()
}
