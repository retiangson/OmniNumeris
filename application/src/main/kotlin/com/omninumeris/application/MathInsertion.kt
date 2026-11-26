package com.omninumeris.application

import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.math.MathSourceAnchor
import java.util.Collections
import java.util.UUID

enum class MathInsertionMode { Manual, Suggestion, Automatic }

data class MathNoteDestination(val notebookId: UUID, val noteId: UUID)

data class MathInsertionTarget(
    val destination: MathNoteDestination,
    val revisionId: UUID,
    val positionId: UUID,
)

data class MathAutomaticInsertionGrant(val id: UUID, val destination: MathNoteDestination)

/** The host supplies the current result and target when processing a result or approving its preview. */
data class MathInsertionSession(
    val resultId: UUID,
    val target: MathInsertionTarget?,
    val mode: MathInsertionMode = MathInsertionMode.Manual,
    val automaticGrant: MathAutomaticInsertionGrant? = null,
)

sealed interface NoteInsertionProposal {
    val resultId: UUID
    val target: MathInsertionTarget
}

class MathInsertionProposal internal constructor(
    override val resultId: UUID,
    override val target: MathInsertionTarget,
    val expression: String,
    val answer: TypedSolutionAnswer,
    val source: MathSourceAnchor?,
    val mode: MathInsertionMode,
) : NoteInsertionProposal

fun TypedSolutionAnswer.notebookText(): String = when (kind) {
    TypedSolutionKind.Value -> value
    TypedSolutionKind.Linear -> "x = $value"
    TypedSolutionKind.Identity -> "x ∈ ℝ"
    TypedSolutionKind.Contradiction -> "∅"
}

data class MathInsertionUndo(val destination: MathNoteDestination, val operationId: UUID)

sealed interface MathInsertionAuthorization {
    data object PreviewApproved : MathInsertionAuthorization
    data class Automatic(val grant: MathAutomaticInsertionGrant) : MathInsertionAuthorization
}

enum class MathInsertionProblem {
    NoEditableTarget, UnverifiedResult, ExpressionChanged, SelectionChanged, Locked, ReadOnly,
    SourceUnavailable, RevisionChanged, AutomaticGrantRevoked, OperationConflict, Unavailable,
    NoInsertableResult, ResultTooLarge, NoSpaceInTarget,
}

sealed interface MathInsertionCommit {
    data class Inserted(val undo: MathInsertionUndo) : MathInsertionCommit
    data class Rejected(val problem: MathInsertionProblem) : MathInsertionCommit
}

sealed interface MathInsertionUndoResult {
    data object Restored : MathInsertionUndoResult
    data class Rejected(val problem: MathInsertionProblem) : MathInsertionUndoResult
}

interface MathNoteInsertion {
    /**
     * Atomically authorize the destination, placement, source and any automatic grant against live policy.
     * Compare the revision and persist the change, audit and undo under the result ID in one transaction.
     * An authorized replay of the same operation returns its receipt without a second insertion.
     * Reuse of an ID with different content or placement must fail. An undone operation must not reinsert.
     */
    suspend fun insert(
        proposal: NoteInsertionProposal,
        authorization: MathInsertionAuthorization,
    ): MathInsertionCommit

    /** Reauthorize and reverse this operation atomically, preserving intervening unrelated edits. */
    suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult
}

sealed interface MathInsertionDecision {
    data object AwaitingManualRequest : MathInsertionDecision
    data class Preview(val proposal: MathInsertionProposal) : MathInsertionDecision
    data class Finished(val result: MathInsertionCommit) : MathInsertionDecision
}

class InsertVerifiedMath(private val notes: MathNoteInsertion) {
    suspend fun onResult(
        session: MathInsertionSession,
        reviewed: ReviewedMathInput,
        outcome: MathSolutionOutcome,
        manuallyRequested: Boolean = false,
    ): MathInsertionDecision {
        if (session.mode == MathInsertionMode.Manual && !manuallyRequested) {
            return MathInsertionDecision.AwaitingManualRequest
        }
        val proof = outcome as? MathSolutionOutcome.Verified
            ?: return rejected(MathInsertionProblem.UnverifiedResult)
        val expression = reviewed.candidate.text
        if (proof.input != reviewed.input || MathParser.parse(expression) != MathParseResult.Parsed(reviewed.input)) {
            return rejected(MathInsertionProblem.ExpressionChanged)
        }
        val answer = TypedSolutionAnswer.from(proof.proof.result)
            ?: return rejected(MathInsertionProblem.UnverifiedResult)
        val target = session.target ?: return rejected(MathInsertionProblem.NoEditableTarget)
        val source = when (val anchor = reviewed.candidate.anchor) {
            is MathSourceAnchor.Ink -> anchor.copy(strokeIds = Collections.unmodifiableList(ArrayList(anchor.strokeIds)))
            else -> anchor
        }
        val proposal = MathInsertionProposal(session.resultId, target, expression, answer, source, session.mode)
        val grant = session.automaticGrant
        return if (session.mode == MathInsertionMode.Automatic && grant?.destination == target.destination) {
            MathInsertionDecision.Finished(notes.insert(proposal, MathInsertionAuthorization.Automatic(grant)))
        } else {
            MathInsertionDecision.Preview(proposal)
        }
    }

    suspend fun approve(proposal: MathInsertionProposal, current: MathInsertionSession): MathInsertionCommit {
        if (current.resultId != proposal.resultId || current.target != proposal.target || current.mode != proposal.mode) {
            return MathInsertionCommit.Rejected(MathInsertionProblem.SelectionChanged)
        }
        return notes.insert(proposal, MathInsertionAuthorization.PreviewApproved)
    }

    suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult = notes.undo(operation)

    private fun rejected(problem: MathInsertionProblem) =
        MathInsertionDecision.Finished(MathInsertionCommit.Rejected(problem))
}
