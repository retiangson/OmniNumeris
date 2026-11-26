package com.omninumeris.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.application.LinkedMathHistoryRead
import com.omninumeris.application.LinkedMathSolutionRecord
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.TypedSolutionAnswer
import com.omninumeris.application.TypedSolutionKind
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.application.InsertVerifiedMath
import com.omninumeris.application.MathInputCandidate
import com.omninumeris.application.MathInsertionCommit
import com.omninumeris.application.MathInsertionDecision
import com.omninumeris.application.MathInsertionMode
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionSession
import com.omninumeris.application.MathInsertionTarget
import com.omninumeris.application.MathInsertionUndoResult
import com.omninumeris.application.MathNoteDestination
import com.omninumeris.application.NotebookInkRegion
import com.omninumeris.application.NotebookInkSelection
import com.omninumeris.application.NotebookRead
import com.omninumeris.application.NotebookResultSource
import com.omninumeris.application.NotebookPagePlacement
import com.omninumeris.application.ReviewedMathInput
import com.omninumeris.application.SolveMath
import com.omninumeris.application.SelectNotebookInk
import com.omninumeris.domain.NotebookKind
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkLasso
import com.omninumeris.domain.canvas.InkPoint
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTip
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.math.MathSourceAnchor
import com.omninumeris.domain.math.MathSourceRevision
import com.omninumeris.infrastructure.storage.RoomNotebookStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class NotebookInsertionPolicyTest {
    @Test
    fun deletedPageIsUnavailableUntilRestoredWithItsOriginalInkAnchor() = runBlocking {
        assumeTrue("This workflow creates persistent fixture notebooks", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val store = RoomNotebookStore.open(InstrumentationRegistry.getInstrumentation().targetContext)
        val first = (store.create("Deleted page ${UUID.randomUUID()}", NotebookKind.Fixed) as NotebookRead.Available).value
        assertEquals(NotebookRead.Unavailable, store.deletePage(first))
        val written = (store.appendInk(first, InkStroke(UUID.randomUUID(), listOf(InkSample(18f, 20f, 0.6f))))
            as NotebookRead.Available).value
        val active = (store.setMathMode(written, MathInsertionMode.Automatic) as NotebookRead.Available).value
        val lasso = rectangle(5.0, 5.0, 25.0, 25.0)
        val anchor = (SelectNotebookInk(store)(active, lasso) as NotebookInkSelection.Selected).anchor
        val targetPage = (store.addPage(active) as NotebookRead.Available).value
        val third = (store.addPage(targetPage) as NotebookRead.Available).value
        val fourth = (store.addPage(third) as NotebookRead.Available).value
        val remaining = (store.deletePage(active) as NotebookRead.Available).value
        assertEquals(targetPage.id, remaining.id)
        assertEquals(targetPage.id, (store.thumbnail(first.notebook.id, 0) as NotebookRead.Available).value.pageId)
        assertEquals(NotebookRead.Unavailable, store.openById(first.notebook.id, first.id))
        assertEquals(NotebookRead.Unavailable, store.ink(active, NotebookInkRegion(0f, 0f, 30f, 30f)))
        assertEquals(NotebookRead.Unavailable, store.visible(first.id, 0f, 0f, 30f, 30f))
        val reviewed = ReviewedMathInput((MathParser.parse("7+2") as MathParseResult.Parsed).input,
            MathInputCandidate("7+2", "7+2", anchor))
        val writer = InsertVerifiedMath(store)
        val session = MathInsertionSession(UUID.randomUUID(), remaining.target(), MathInsertionMode.Suggestion)
        val proposal = (writer.onResult(session, reviewed, SolveMath().solve(reviewed.input)) as MathInsertionDecision.Preview).proposal
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.SourceUnavailable), writer.approve(proposal, session))
        assertEquals(true, store.hasDeletedPages(first.notebook.id))
        val restored = (store.restoreLastDeletedPage(first.notebook.id) as NotebookRead.Available).value
        assertEquals(first.id, restored.id)
        assertEquals(listOf(first.id, targetPage.id, third.id, fourth.id), (0..3).map { position ->
            (store.open(first.notebook.id, position) as NotebookRead.Available).value.id
        })
        assertNotEquals(active.revision, restored.revision)
        assertEquals(anchor, (SelectNotebookInk(store)(restored, lasso) as NotebookInkSelection.Selected).anchor)
        val preference = (store.mathPreference(restored) as NotebookRead.Available).value
        assertEquals(MathInsertionMode.Suggestion, preference.mode)
        assertNull(preference.grant)
        assertEquals(false, store.hasDeletedPages(first.notebook.id))
        assertEquals(NotebookRead.Unavailable, store.restoreLastDeletedPage(first.notebook.id))
    }

    @Test
    fun insertedAndDuplicatedPagesAreIndependentAndPreserveOrder() = runBlocking {
        assumeTrue("This workflow creates persistent fixture notebooks", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val store = RoomNotebookStore.open(InstrumentationRegistry.getInstrumentation().targetContext)
        val first = (store.create("Page copy ${UUID.randomUUID()}", NotebookKind.Fixed) as NotebookRead.Available).value
        val stroke = InkStroke(UUID.randomUUID(), listOf(InkSample(18f, 20f, 0.6f)))
        val inkPage = (store.appendInk(first, stroke) as NotebookRead.Available).value
        val active = (store.setMathMode(inkPage, MathInsertionMode.Automatic) as NotebookRead.Available).value
        val lasso = rectangle(5.0, 5.0, 28.0, 28.0)
        val anchor = (SelectNotebookInk(store)(active, lasso) as NotebookInkSelection.Selected).anchor
        val reviewed = ReviewedMathInput((MathParser.parse("7+2") as MathParseResult.Parsed).input,
            MathInputCandidate("7+2", "7+2", anchor))
        val notes = InsertVerifiedMath(store)
        val session = MathInsertionSession(UUID.randomUUID(), active.target(), MathInsertionMode.Automatic)
        val proof = SolveMath().solve(reviewed.input)
        val preview = (notes.onResult(session, reviewed, proof) as MathInsertionDecision.Preview).proposal
        val receipt = notes.approve(preview, session) as MathInsertionCommit.Inserted
        val original = (store.openById(first.notebook.id, first.id) as NotebookRead.Available).value
        val tail = (store.addPage(original) as NotebookRead.Available).value
        val inserted = (store.addPage(original, NotebookPagePlacement.AfterCurrent) as NotebookRead.Available).value
        val copy = (store.addPage(original, NotebookPagePlacement.DuplicateCurrent) as NotebookRead.Available).value
        val order = (0..3).map { (store.open(first.notebook.id, it) as NotebookRead.Available).value.id }
        assertEquals(listOf(first.id, copy.id, inserted.id, tail.id), order)
        assertEquals(NotebookRead.Unavailable, store.addPage(tail, NotebookPagePlacement.AfterCurrent))
        assertEquals(MathInsertionMode.Manual, (store.mathPreference(copy) as NotebookRead.Available).value.mode)
        assertNull((store.mathPreference(copy) as NotebookRead.Available).value.grant)
        val copiedInk = SelectNotebookInk(store)(copy, lasso) as NotebookInkSelection.Selected
        assertEquals(stroke.samples, copiedInk.strokes.single().samples)
        assertNotEquals(anchor.strokeIds, copiedInk.anchor.strokeIds)
        assertNotEquals(anchor.revision, copiedInk.anchor.revision)
        val copiedResult = requireNotNull(store.lastUndo(copy.id))
        assertNotEquals(receipt.undo.operationId, copiedResult.operationId)
        assertEquals(MathInsertionUndoResult.Restored, notes.undo(copiedResult))
        assertEquals(receipt.undo, store.lastUndo(first.id))
        assertNull(store.lastUndo(copy.id))
    }

    @Test
    fun reorderingThreePagesPreservesInkAnchorAndSupportsUndo() = runBlocking {
        assumeTrue("This workflow creates persistent fixture notebooks", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RoomNotebookStore.open(context)
        val first = (store.create("Page order ${UUID.randomUUID()}", NotebookKind.Fixed) as NotebookRead.Available).value
        val stroke = InkStroke(UUID.randomUUID(), listOf(InkSample(15f, 15f, 0.5f)))
        val inkPage = (store.appendInk(first, stroke) as NotebookRead.Available).value
        val select = SelectNotebookInk(store)
        val lasso = rectangle(5.0, 5.0, 25.0, 25.0)
        val anchor = (select(inkPage, lasso) as NotebookInkSelection.Selected).anchor
        val second = (store.addPage(inkPage) as NotebookRead.Available).value
        val third = (store.addPage(second) as NotebookRead.Available).value
        val middle = (store.movePage(inkPage, 1) as NotebookRead.Available).value
        val last = (store.movePage(middle.page, 1) as NotebookRead.Available).value
        val reopened = RoomNotebookStore.open(context)
        suspend fun orderedPageIds() = (0..2).map { position ->
            (reopened.open(first.notebook.id, position) as NotebookRead.Available).value.id
        }
        assertEquals(listOf(second.id, third.id, first.id), orderedPageIds())
        val preview = (reopened.thumbnail(first.notebook.id, 2) as NotebookRead.Available).value
        assertEquals(first.id, preview.pageId)
        assertEquals(15.0, preview.ink.single().single().x, 0.0)
        assertEquals(15.0, preview.ink.single().single().y, 0.0)
        val loaded = (reopened.openById(first.notebook.id, first.id) as NotebookRead.Available).value
        assertEquals(inkPage.revision, loaded.revision)
        assertEquals(anchor, (SelectNotebookInk(reopened)(loaded, lasso) as NotebookInkSelection.Selected).anchor)
        assertEquals(NotebookRead.Unavailable, reopened.undoPageMove(middle.undo))
        val restored = (reopened.undoPageMove(last.undo) as NotebookRead.Available).value
        assertEquals(listOf(second.id, first.id, third.id), orderedPageIds())
        assertEquals(anchor, (SelectNotebookInk(reopened)(restored, lasso) as NotebookInkSelection.Selected).anchor)
        assertEquals(NotebookRead.Unavailable, reopened.undoPageMove(last.undo))
    }

    @Test
    fun authorizesPersistedInkOnlyInItsNotebookAndAtItsCurrentRevision() = runBlocking {
        assumeTrue("This workflow creates persistent fixture notebooks", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RoomNotebookStore.open(context)
        val page = (store.create("Ink source ${UUID.randomUUID()}", NotebookKind.Fixed) as NotebookRead.Available).value
        val stroke = InkStroke(UUID.randomUUID(), listOf(InkSample(10f, 20f, 0.2f), InkSample(30f, 40f, 0.8f)), InkTip.Fine)
        val updated = (store.appendInk(page, stroke) as NotebookRead.Available).value
        assertEquals(NotebookRead.Unavailable, store.appendInk(page, stroke))

        val reopenedStore = RoomNotebookStore.open(context)
        val reopened = (reopenedStore.open(page.notebook.id) as NotebookRead.Available).value
        assertEquals(updated, reopened)
        val snapshot = (reopenedStore.ink(reopened, NotebookInkRegion(0f, 0f, 50f, 50f)) as NotebookRead.Available).value
        assertEquals(stroke.id, snapshot.strokes.single().id)
        assertEquals(stroke.samples, snapshot.strokes.single().samples)
        assertEquals(stroke.tip, snapshot.strokes.single().tip)
        val selectInk = SelectNotebookInk(reopenedStore)
        assertEquals(NotebookInkSelection.Empty, selectInk(reopened, rectangle(60.0, 60.0, 80.0, 80.0)))
        val selected = selectInk(reopened, rectangle(5.0, 15.0, 35.0, 45.0)) as NotebookInkSelection.Selected
        val anchor = selected.anchor
        assertEquals(MathSourceAnchor.Ink(snapshot.sourceRevision, page.id, listOf(stroke.id)), anchor)
        val records = List(34) {
            LinkedMathSolutionRecord(anchor, TypedSolutionRecord(UUID.randomUUID(), "", "2+3", TypedSolutionAnswer(TypedSolutionKind.Value, "5")))
        }
        records.forEach { assertEquals(MathSolutionSave.Saved, reopenedStore.save(it)) }
        assertEquals(MathSolutionSave.Saved, reopenedStore.save(records.last()))
        val history = RoomNotebookStore.open(context).read(anchor) as LinkedMathHistoryRead.Available
        assertEquals(records.takeLast(32).asReversed(), history.records)
        val forged = anchor.copy(revision = anchor.revision.copy(contentId = UUID.randomUUID()))
        assertEquals(LinkedMathHistoryRead.Unavailable, reopenedStore.read(forged))
        assertEquals(MathSolutionSave.Unavailable, reopenedStore.save(records.last().copy(anchor = forged)))
        val reviewed = ReviewedMathInput(
            (MathParser.parse("2+3") as MathParseResult.Parsed).input,
            MathInputCandidate("2+3", "2+3", anchor),
        )
        val proof = SolveMath().solve(reviewed.input)
        val insert = InsertVerifiedMath(reopenedStore)
        val session = MathInsertionSession(UUID.randomUUID(), reopened.target(), MathInsertionMode.Suggestion)
        val preview = (insert.onResult(session, reviewed, proof) as MathInsertionDecision.Preview).proposal
        val receipt = insert.approve(preview, session) as MathInsertionCommit.Inserted
        assertEquals(receipt, insert.approve(preview, session))
        val source = reopenedStore.resultSource(reopened, receipt.undo.operationId) as NotebookResultSource.Ink
        assertEquals(reopened.id, source.page.id)
        assertEquals(anchor, source.selection.anchor)
        assertEquals(stroke.samples, source.selection.strokes.single().samples)

        val foreign = (store.create("Other notebook ${UUID.randomUUID()}", NotebookKind.Fixed) as NotebookRead.Available).value
        assertEquals(NotebookResultSource.Unavailable, store.resultSource(foreign, receipt.undo.operationId))
        val otherSession = session.copy(resultId = UUID.randomUUID(), target = foreign.target())
        val otherPreview = (insert.onResult(otherSession, reviewed, proof) as MathInsertionDecision.Preview).proposal
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.SourceUnavailable), insert.approve(otherPreview, otherSession))
        assertNull(store.lastUndo(foreign.id))

        val afterInsert = (store.open(page.notebook.id) as NotebookRead.Available).value
        val second = InkStroke(UUID.randomUUID(), listOf(InkSample(100f, 120f, 0.5f)))
        val revised = (store.appendInk(afterInsert, second) as NotebookRead.Available).value
        assertEquals(NotebookResultSource.Changed, store.resultSource(revised, receipt.undo.operationId))
        assertEquals(LinkedMathHistoryRead.Unavailable, store.read(anchor))
        assertEquals(MathSolutionSave.Unavailable, store.save(records.last()))
        val staleSession = session.copy(resultId = UUID.randomUUID(), target = revised.target())
        val stale = (insert.onResult(staleSession, reviewed, proof) as MathInsertionDecision.Preview).proposal
        assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.RevisionChanged), insert.approve(stale, staleSession))
        assertEquals(MathInsertionUndoResult.Restored, insert.undo(receipt.undo))
        assertEquals(NotebookResultSource.Unavailable, store.resultSource(revised, receipt.undo.operationId))
        val afterUndo = (store.open(page.notebook.id) as NotebookRead.Available).value
        val finalInk = (store.ink(afterUndo, NotebookInkRegion(0f, 0f, 150f, 150f)) as NotebookRead.Available).value
        assertEquals(listOf(stroke.id, second.id), finalInk.strokes.map(InkStroke::id))
    }

    @Test
    fun commitsRecheckGrantAndRejectUnregisteredSource() = runBlocking {
        assumeTrue("This workflow creates a persistent fixture notebook", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RoomNotebookStore.open(context)
        val insert = InsertVerifiedMath(store)
        val page = (store.create("Policy fixture ${UUID.randomUUID()}", NotebookKind.Infinite) as NotebookRead.Available).value
        val enabled = (store.setMathMode(page, MathInsertionMode.Automatic) as NotebookRead.Available).value
        val grant = requireNotNull((store.mathPreference(enabled) as NotebookRead.Available).value.grant)
        val reviewed = ReviewedMathInput(
            (MathParser.parse("2+3") as MathParseResult.Parsed).input,
            MathInputCandidate("2+3", "2+3", null),
        )
        val proof = SolveMath().solve(reviewed.input)
        val session = MathInsertionSession(UUID.randomUUID(), enabled.target(), MathInsertionMode.Automatic, grant)
        val source = MathSourceAnchor.Ink(MathSourceRevision(enabled.notebook.id, enabled.revision), enabled.id, listOf(UUID.randomUUID()))
        val anchored = reviewed.copy(candidate = reviewed.candidate.copy(anchor = source))
        assertEquals(rejected(MathInsertionProblem.SourceUnavailable), insert.onResult(session, anchored, proof))
        assertNull(store.lastUndo(page.id))

        val committed = insert.onResult(session, reviewed, proof) as MathInsertionDecision.Finished
        val receipt = committed.result as MathInsertionCommit.Inserted
        assertEquals(committed, insert.onResult(session, reviewed, proof))
        val reopened = (store.open(page.notebook.id) as NotebookRead.Available).value
        val readOnly = (store.setReadOnly(reopened, true) as NotebookRead.Available).value
        assertEquals(rejected(MathInsertionProblem.ReadOnly), insert.onResult(session, reviewed, proof))
        assertEquals(MathInsertionUndoResult.Rejected(MathInsertionProblem.ReadOnly), insert.undo(receipt.undo))
        val editable = (store.setReadOnly(readOnly, false) as NotebookRead.Available).value
        assertEquals(rejected(MathInsertionProblem.AutomaticGrantRevoked), insert.onResult(session, reviewed, proof))
        val reenabled = (store.setMathMode(editable, MathInsertionMode.Automatic) as NotebookRead.Available).value
        val newGrant = requireNotNull((store.mathPreference(reenabled) as NotebookRead.Available).value.grant)
        assertNotEquals(grant.id, newGrant.id)
        assertEquals(rejected(MathInsertionProblem.AutomaticGrantRevoked), insert.onResult(session, reviewed, proof))
        assertEquals(MathInsertionUndoResult.Restored, insert.undo(receipt.undo))
        assertNull(store.lastUndo(page.id))
    }

    private fun rejected(problem: MathInsertionProblem) = MathInsertionDecision.Finished(MathInsertionCommit.Rejected(problem))

    private fun rectangle(left: Double, top: Double, right: Double, bottom: Double): InkLasso = requireNotNull(
        InkLasso.create(listOf(InkPoint(left, top), InkPoint(right, top), InkPoint(right, bottom), InkPoint(left, bottom))),
    )

    private fun NotebookPage.target() = MathInsertionTarget(MathNoteDestination(notebook.id, id), revision, id)
}
