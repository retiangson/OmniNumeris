package com.omninumeris.app.math

import com.omninumeris.application.MathInputCandidates
import com.omninumeris.application.LocalMathExplanation
import com.omninumeris.application.LinkedMathHistoryRead
import com.omninumeris.application.LinkedMathSolutionHistory
import com.omninumeris.application.LinkedMathSolutionRecord
import com.omninumeris.application.MathInputProblem
import com.omninumeris.application.MathRecognitionAdapter
import com.omninumeris.application.MathRecognitionResult
import com.omninumeris.application.MathSolutionHistory
import com.omninumeris.application.MathSolutionHistoryRead
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.MathSolutionOutcome
import com.omninumeris.application.SolveMath
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.application.ResolveMathInput
import com.omninumeris.domain.math.MathInput
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.domain.math.MathPageRegion
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathSourceAnchor
import com.omninumeris.domain.math.MathSourceRevision
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MathInputViewModelTest {
    private val region = MathPageRegion(0.1, 0.2, 0.9, 0.8)
    private val revision = MathSourceRevision(UUID.randomUUID(), UUID.randomUUID())
    private val pdf = MathSourceAnchor.Pdf(revision, UUID.randomUUID(), region)
    private val capture = MathSourceAnchor.Capture(revision, UUID.randomUUID(), region)

    @Test
    fun onlyTheExplicitRequestUsesTheLocalProviderAndCannotSaveItsClaims() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var calls = 0
            val store = TestSolutionHistory()
            val solver = SolveMath(setOf(LocalMathExplanation {
                calls++
                "Verified by the app"
            }))
            val model = MathInputViewModel(ResolveMathInput(emptySet()), dispatcher, store, solver)
            fun solution() = (model.review.value as MathInputReview.Editing).solution
            model.beginTypedReview("x^2=4")
            model.reviewCandidate()
            runCurrent()
            model.confirmCandidate()
            model.solveConfirmed()
            runCurrent()
            val unsupported = solution() as MathSolutionReview.Explanation
            assertTrue(unsupported.outcome is MathSolutionOutcome.Unsupported)
            assertTrue(unsupported.offerLocalExplanation)
            assertEquals(0, calls)
            model.requestLocalExplanation()
            runCurrent()
            assertEquals(MathSolutionOutcome.Unverified("Verified by the app"),
                (solution() as MathSolutionReview.Explanation).outcome)
            assertEquals(1, calls)
            model.saveSolution()
            runCurrent()
            assertTrue(store.records.isEmpty())
            model.editCandidate("x+2=4")
            assertEquals(MathSolutionReview.Idle, solution())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun savedSolutionRestoresTheOriginalAndRechecksTheConfirmedExpression() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestSolutionHistory()
        Dispatchers.setMain(dispatcher)
        try {
            val model = MathInputViewModel(ResolveMathInput(emptySet()), dispatcher, store)
            model.beginTypedReview("2*x+3=9")
            model.editCandidate("2*x+3=7")
            model.reviewCandidate()
            runCurrent()
            model.confirmCandidate()
            model.solveConfirmed()
            runCurrent()
            model.saveSolution()
            runCurrent()
            val saved = store.records.single()
            assertEquals("2", saved.answer.value)
            assertEquals("2*x+3=9", saved.original)
            assertEquals("2*x+3=7", saved.confirmed)

            val reopened = MathInputViewModel(ResolveMathInput(emptySet()), dispatcher, store)
            reopened.showSolutionHistory()
            runCurrent()
            reopened.reopenSolution(saved.id)
            runCurrent()
            val review = reopened.review.value as MathInputReview.Editing
            assertEquals(MathReviewSource.Text(saved.original), review.sourcePreview)
            assertEquals(saved.confirmed, review.candidates.single().text)
            assertTrue(review.solution is MathSolutionReview.Explanation)
            reopened.dismissReview()

            store.records = listOf(saved.copy(answer = saved.answer.copy(value = "999")))
            reopened.showSolutionHistory()
            runCurrent()
            reopened.reopenSolution(saved.id)
            runCurrent()
            assertEquals(MathInputReview.Idle, reopened.review.value)
            assertTrue((reopened.history.value as MathHistoryPanel.Ready).failedToOpen)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun solveAndHintUseOnlyConfirmedAstAndDropLateResultsAfterEdit() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        try {
            val work = mutableListOf<Runnable>()
            val worker = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    work.add(block)
                }
            }
            val model = MathInputViewModel(ResolveMathInput(emptySet()), worker, TestSolutionHistory())
            fun editing() = model.review.value as MathInputReview.Editing
            fun finishWork() {
                runCurrent()
                work.removeAt(0).run()
                runCurrent()
            }
            model.beginTypedReview("2*x+3=7")
            model.solveConfirmed()
            assertEquals(MathSolutionReview.Idle, editing().solution)
            model.reviewCandidate()
            finishWork()
            assertTrue(model.confirmCandidate() != null)
            model.requestHint()
            finishWork()
            assertTrue(editing().solution is MathSolutionReview.Hint)
            model.solveConfirmed()
            finishWork()
            assertTrue((editing().solution as MathSolutionReview.Explanation).outcome is MathSolutionOutcome.Verified)
            model.editCandidate("2*x+3=9")
            assertEquals(MathSolutionReview.Idle, editing().solution)
            assertEquals(MathCorrectionCheck.Unchecked, editing().check)
            model.reviewCandidate()
            finishWork()
            assertTrue(model.confirmCandidate() != null)
            model.solveConfirmed()
            runCurrent()
            work.removeAt(0).run()
            model.editCandidate("2*x+3=11")
            runCurrent()
            assertEquals(MathSolutionReview.Idle, editing().solution)
            assertEquals(MathCorrectionCheck.Unchecked, editing().check)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun typedEquationUsesTheSameParserWithoutAvailabilityOfOtherSources() = runTest {
        val model = MathInputViewModel(ResolveMathInput(emptySet()), StandardTestDispatcher(testScheduler), TestSolutionHistory())
        assertEquals(setOf(MathInputSource.Typed), model.availableSources)
        val result = model.previewTyped("2*x+1 = 7") as MathParseResult.Parsed
        assertTrue(result.input is MathInput.Equation)
        assertTrue(model.previewTyped("x+") is MathParseResult.Rejected)
    }

    @Test
    fun choosingAndCorrectingPdfCandidatesPreservesRawTextAndTheSourceRevision() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val adapter = adapter(MathInputSource.Pdf) { anchor ->
                assertSame(pdf, anchor)
                MathRecognitionResult.Texts(listOf("x+", "\\frac{1}{2} = x"))
            }
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter)), dispatcher, TestSolutionHistory())
            model.recognize(MathSourceReviewSession(pdf))
            assertEquals(MathInputReview.Loading(MathInputSource.Pdf), model.review.value)
            runCurrent()
            fun editing() = model.review.value as MathInputReview.Editing
            assertEquals(2, editing().candidates.size)
            assertEquals(MathCorrectionCheck.Unchecked, editing().check)

            model.reviewCandidate()
            runCurrent()
            assertTrue(editing().check is MathCorrectionCheck.Invalid)
            model.editCandidate("x+1=3")
            model.selectCandidate(1)
            model.reviewCandidate()
            runCurrent()
            val firstConfirmed = checkNotNull(model.confirmCandidate())
            assertTrue(firstConfirmed.input is MathInput.Equation)
            assertSame(pdf, firstConfirmed.candidate.anchor)

            model.selectCandidate(0)
            assertEquals("x+1=3", editing().candidates.first().text)
            assertEquals("x+", editing().candidates.first().originalText)
            assertNull(model.confirmCandidate())
            model.reviewCandidate()
            runCurrent()
            assertSame(pdf, model.confirmCandidate()?.candidate?.anchor)
            model.dismissReview()
            assertEquals(MathInputReview.Idle, model.review.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exponentNeedsRecheckAfterEditingAndCancelDiscardsOnlyTheReview() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = MathInputViewModel(ResolveMathInput(emptySet()), dispatcher, TestSolutionHistory())
            model.beginTypedReview("x^2=27")
            fun editing() = model.review.value as MathInputReview.Editing
            assertEquals(MathReviewSource.Text("x^2=27"), editing().sourcePreview)
            model.editCandidate("x^")
            model.reviewCandidate()
            runCurrent()
            assertTrue(editing().check is MathCorrectionCheck.Invalid)
            assertEquals("x^", editing().candidates.single().text)
            assertNull(model.confirmCandidate())

            model.editCandidate("x^3=27")
            model.reviewCandidate()
            runCurrent()
            val confirmed = checkNotNull(model.confirmCandidate())
            assertEquals("x^3=27", confirmed.candidate.text)
            assertEquals("x^2=27", confirmed.candidate.originalText)
            assertTrue(editing().check is MathCorrectionCheck.Confirmed)
            model.editCandidate("x^4=27")
            assertEquals(MathCorrectionCheck.Unchecked, editing().check)
            assertNull(model.confirmCandidate())
            model.dismissReview()
            assertEquals(MathInputReview.Idle, model.review.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun editAndDismissSuppressCompletedChecksWaitingToPublish() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        try {
            val work = mutableListOf<Runnable>()
            val worker = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    work.add(block)
                }
            }
            val model = MathInputViewModel(ResolveMathInput(emptySet()), worker, TestSolutionHistory())
            model.beginTypedReview("x^2=9")
            for (dismiss in listOf(false, true)) {
                model.reviewCandidate()
                runCurrent()
                work.removeAt(0).run()
                if (dismiss) model.dismissReview() else model.editCandidate("x^3=9")
                runCurrent()
                assertNull(model.confirmCandidate())
                if (dismiss) assertEquals(MathInputReview.Idle, model.review.value)
                else assertEquals(MathCorrectionCheck.Unchecked, (model.review.value as MathInputReview.Editing).check)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun replacingARecognitionJobDiscardsTheOldResultAndKeepsTypedFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val pending = CompletableDeferred<MathRecognitionResult>()
            val model = MathInputViewModel(ResolveMathInput(setOf(
                adapter(MathInputSource.Camera) { pending.await() },
            )), dispatcher, TestSolutionHistory())
            model.recognize(MathSourceReviewSession(capture))
            runCurrent()
            model.recognize(MathSourceReviewSession(pdf))
            runCurrent()
            pending.complete(MathRecognitionResult.Texts(listOf("1/2")))
            runCurrent()
            assertEquals(MathInputReview.Failed(MathInputSource.Pdf, MathInputProblem.Unavailable), model.review.value)
            assertTrue(model.previewTyped("1/3") is MathParseResult.Parsed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lockedUnsupportedAndEmptyRecognitionDoNotProduceCandidates() = runTest {
        val inputs = listOf(
            MathRecognitionResult.Rejected(MathInputProblem.Locked) to MathInputProblem.Locked,
            MathRecognitionResult.Texts(listOf(" ", "")) to MathInputProblem.NoCandidates,
            MathRecognitionResult.Texts(List(9) { "1/2" }) to MathInputProblem.Unsupported,
            MathRecognitionResult.Texts(listOf("1".repeat(513))) to MathInputProblem.Unsupported,
        )
        for ((input, error) in inputs) {
            val resolve = ResolveMathInput(setOf(adapter(MathInputSource.Camera) { input }))
            assertEquals(MathInputCandidates.Rejected(error), resolve.recognize(capture))
        }
    }

    @Test
    fun cancelledEngineCallFinishesBeforeStartingAnotherRecognition() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val finishFirst = CompletableDeferred<Unit>()
            var calls = 0
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter(MathInputSource.Camera) {
                if (++calls == 1) withContext(NonCancellable) { finishFirst.await() }
                MathRecognitionResult.Texts(listOf("1/$calls"))
            })), dispatcher, TestSolutionHistory())
            model.recognize(MathSourceReviewSession(capture))
            runCurrent()
            model.recognize(MathSourceReviewSession(capture))
            runCurrent()
            assertEquals(1, calls)
            finishFirst.complete(Unit)
            runCurrent()
            assertEquals(2, calls)
            val review = model.review.value as MathInputReview.Editing
            assertEquals("1/2", review.candidates.single().text)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun revocationClearsACompletedMediaReviewAndKeepsTypedFallbackAvailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter(MathInputSource.Pdf) {
                MathRecognitionResult.Texts(listOf("2*x=8"))
            })), dispatcher, TestSolutionHistory())
            val selection = MathSourceReviewSession(pdf)
            model.recognize(selection, MathReviewSource.Text("selected page region"))
            runCurrent()
            model.reviewCandidate()
            runCurrent()
            model.confirmCandidate()
            model.solveConfirmed()
            runCurrent()
            val editing = model.review.value as MathInputReview.Editing
            assertEquals(MathReviewSource.Text("selected page region"), editing.sourcePreview)
            assertTrue(editing.solution is MathSolutionReview.Explanation)

            selection.revoke(MathSourceRevocation.Locked)
            runCurrent()
            assertEquals(MathInputReview.Failed(MathInputSource.Pdf, MathInputProblem.Locked), model.review.value)
            assertNull(model.confirmCandidate())
            model.beginTypedReview("1/3")
            assertEquals(MathInputSource.Typed, (model.review.value as MathInputReview.Editing).source)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun revokedOrSupersededMediaCannotPublishLateCandidates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val pending = CompletableDeferred<MathRecognitionResult>()
            var calls = 0
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter(MathInputSource.Camera) {
                calls++
                withContext(NonCancellable) { pending.await() }
            })), dispatcher, TestSolutionHistory())
            val selection = MathSourceReviewSession(capture)
            model.recognize(selection)
            runCurrent()
            selection.revoke(MathSourceRevocation.RevisionChanged)
            runCurrent()
            val failure = MathInputReview.Failed(MathInputSource.Camera, MathInputProblem.SourceChanged)
            assertEquals(failure, model.review.value)

            pending.complete(MathRecognitionResult.Texts(listOf("3/4")))
            runCurrent()
            assertEquals(failure, model.review.value)
            model.recognize(selection)
            runCurrent()
            assertEquals(1, calls)
            assertEquals(failure, model.review.value)
            model.beginTypedReview("1/3")
            selection.revoke(MathSourceRevocation.Locked)
            runCurrent()
            assertEquals(MathInputSource.Typed, (model.review.value as MathInputReview.Editing).source)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun confirmationRejectsRevokedAccessBeforeItsObserverRuns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter(MathInputSource.Pdf) {
                MathRecognitionResult.Texts(listOf("2*x=8"))
            })), dispatcher, TestSolutionHistory())
            val selection = MathSourceReviewSession(pdf)
            model.recognize(selection)
            runCurrent()
            model.reviewCandidate()
            runCurrent()
            assertTrue((model.review.value as MathInputReview.Editing).check is MathCorrectionCheck.Parsed)
            selection.revoke(MathSourceRevocation.AccessRevoked)
            assertNull(model.confirmCandidate())
            assertEquals(MathInputReview.Failed(MathInputSource.Pdf, MathInputProblem.Unavailable), model.review.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun adapter(
        kind: MathInputSource,
        recognize: suspend (MathSourceAnchor) -> MathRecognitionResult,
    ): MathRecognitionAdapter = object : MathRecognitionAdapter {
        override val source = kind
        override suspend fun recognize(anchor: MathSourceAnchor): MathRecognitionResult = recognize.invoke(anchor)
    }

    @Test
    fun linkedHistorySavesAndRechecksOnlyWithinTheExactSourceRevision() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val standalone = TestSolutionHistory()
            val linked = TestLinkedHistory()
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter(MathInputSource.Pdf) {
                MathRecognitionResult.Texts(listOf("2*x=9"))
            })), dispatcher, standalone)
            val selection = MathSourceReviewSession(pdf, linked)
            val preview = MathReviewSource.Text("Selected source region")
            model.recognize(selection, preview)
            runCurrent()
            model.editCandidate("2*x=8")
            model.reviewCandidate()
            runCurrent()
            model.confirmCandidate()
            model.saveSolution()
            runCurrent()
            assertTrue(linked.records.isEmpty())
            model.solveConfirmed()
            runCurrent()
            model.saveSolution()
            runCurrent()
            val saved = linked.records.single()
            assertEquals(pdf, saved.anchor)
            assertEquals("2*x=9", saved.solution.original)
            assertEquals("2*x=8", saved.solution.confirmed)
            assertEquals("4", saved.solution.answer.value)
            assertTrue(standalone.records.isEmpty())

            linked.records += saved.copy(anchor = pdf.copy(revision = revision.copy(revisionId = UUID.randomUUID())))
            linked.records += saved.copy(anchor = pdf.copy(pageId = UUID.randomUUID()))
            model.showSourceSolutionHistory()
            runCurrent()
            assertEquals(listOf(saved.solution), (model.history.value as MathHistoryPanel.Ready).records)
            assertEquals(pdf, linked.lastRead)
            model.reopenSolution(saved.solution.id)
            runCurrent()
            val reopened = model.review.value as MathInputReview.Editing
            assertEquals(pdf, reopened.candidates.single().anchor)
            assertEquals(preview, reopened.sourcePreview)
            assertTrue((reopened.solution as MathSolutionReview.Explanation).outcome is MathSolutionOutcome.Verified)

            model.showSourceSolutionHistory()
            runCurrent()
            selection.revoke(MathSourceRevocation.Locked)
            runCurrent()
            assertEquals(MathHistoryPanel.Closed, model.history.value)
            assertEquals(MathInputReview.Failed(MathInputSource.Pdf, MathInputProblem.Locked), model.review.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun revokedSourceCannotPublishResultsFromAHistoryReadAlreadyInFlight() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val pending = CompletableDeferred<LinkedMathHistoryRead>()
            val history = object : LinkedMathSolutionHistory {
                override suspend fun read(anchor: MathSourceAnchor) = withContext(NonCancellable) { pending.await() }
                override suspend fun save(record: LinkedMathSolutionRecord) = MathSolutionSave.Unavailable
            }
            val model = MathInputViewModel(ResolveMathInput(setOf(adapter(MathInputSource.Pdf) {
                MathRecognitionResult.Texts(listOf("2*x=8"))
            })), dispatcher, TestSolutionHistory())
            val selection = MathSourceReviewSession(pdf, history)
            model.recognize(selection)
            runCurrent()
            model.showSourceSolutionHistory()
            runCurrent()
            assertEquals(MathHistoryPanel.Loading, model.history.value)
            selection.revoke(MathSourceRevocation.RevisionChanged)
            runCurrent()
            pending.complete(LinkedMathHistoryRead.Available(emptyList()))
            runCurrent()
            assertEquals(MathHistoryPanel.Closed, model.history.value)
            assertEquals(MathInputReview.Failed(MathInputSource.Pdf, MathInputProblem.SourceChanged), model.review.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sourceSessionKeepsTheStrokeSelectionItWasAuthorizedFor() {
        val originalStroke = UUID.randomUUID()
        val selected = mutableListOf(originalStroke)
        val session = MathSourceReviewSession(MathSourceAnchor.Ink(revision, UUID.randomUUID(), selected))
        selected[0] = UUID.randomUUID()
        assertEquals(listOf(originalStroke), (session.anchor as MathSourceAnchor.Ink).strokeIds)
    }

    @Test
    fun inputEventsFromAnObsoleteReviewCannotEditItsReplacement() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = MathInputViewModel(ResolveMathInput(emptySet()), dispatcher, TestSolutionHistory())
            model.beginTypedReview("x^2=27")
            val old = model.review.value as MathInputReview.Editing
            model.editCandidate(old.reviewId, old.selected, "x^3=27")
            assertEquals(old.reviewId, (model.review.value as MathInputReview.Editing).reviewId)
            model.beginTypedReview("2*x=8")
            val current = model.review.value as MathInputReview.Editing
            model.editCandidate(old.reviewId, old.selected, "x^4=27")
            model.editCandidate(current.reviewId, 1, "3*x=8")
            assertSame(current, model.review.value)
            model.editCandidate(current.reviewId, current.selected, "2*x=10")
            assertEquals("2*x=10", (model.review.value as MathInputReview.Editing).candidates.single().text)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class TestSolutionHistory : MathSolutionHistory {
    var records = emptyList<TypedSolutionRecord>()

    override suspend fun read(): MathSolutionHistoryRead = MathSolutionHistoryRead.Available(records)

    override suspend fun save(record: TypedSolutionRecord): MathSolutionSave {
        records = listOf(record) + records
        return MathSolutionSave.Saved
    }
}

private class TestLinkedHistory : LinkedMathSolutionHistory {
    var records = emptyList<LinkedMathSolutionRecord>()
    var lastRead: MathSourceAnchor? = null

    override suspend fun read(anchor: MathSourceAnchor): LinkedMathHistoryRead {
        lastRead = anchor
        return LinkedMathHistoryRead.Available(records)
    }

    override suspend fun save(record: LinkedMathSolutionRecord): MathSolutionSave {
        records = listOf(record) + records
        return MathSolutionSave.Saved
    }
}

