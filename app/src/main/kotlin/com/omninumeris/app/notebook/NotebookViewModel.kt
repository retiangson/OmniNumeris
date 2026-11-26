package com.omninumeris.app.notebook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omninumeris.application.CalculatorInsertionProposal
import com.omninumeris.application.CalculatorInsertionReview
import com.omninumeris.application.CalculatorInsertionSession
import com.omninumeris.application.InsertCalculatorResult
import com.omninumeris.application.InsertVerifiedMath
import com.omninumeris.application.LinkedMathSolutionHistory
import com.omninumeris.application.MathInsertionCommit
import com.omninumeris.application.MathInsertionDecision
import com.omninumeris.application.MathInsertionMode
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionProposal
import com.omninumeris.application.MathInsertionSession
import com.omninumeris.application.MathInsertionTarget
import com.omninumeris.application.MathInsertionUndoResult
import com.omninumeris.application.MathNoteDestination
import com.omninumeris.application.NotebookRead
import com.omninumeris.application.NotebookMathPreference
import com.omninumeris.application.NotebookPageMoveUndo
import com.omninumeris.application.NotebookPagePlacement
import com.omninumeris.application.NotebookInkRegion
import com.omninumeris.application.NotebookInkSelection
import com.omninumeris.application.NotebookResultSource
import com.omninumeris.application.NotebookStore
import com.omninumeris.application.SelectNotebookInk
import com.omninumeris.app.math.MathCorrectionCheck
import com.omninumeris.app.math.MathInputDispatcher
import com.omninumeris.app.math.MathInputReview
import com.omninumeris.app.math.MathSolutionReview
import com.omninumeris.domain.NotebookKind
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.NotebookSummary
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.canvas.InkLasso
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.infrastructure.storage.RoomNotebookStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class NotebookState(
    val notebooks: List<NotebookSummary> = emptyList(),
    val page: NotebookPage? = null,
    val busy: Boolean = false,
    val problem: MathInsertionProblem? = null,
    val proposal: CalculatorInsertionProposal? = null,
    val canUndo: Boolean = false,
    val mathProposal: MathInsertionProposal? = null,
    val insertedMathId: UUID? = null,
    val mathPreference: NotebookMathPreference? = null,
    val pageMoveUndo: NotebookPageMoveUndo? = null,
    val canRestorePage: Boolean = false,
    val resultSource: NotebookResultSource? = null,
    val pendingInk: List<InkStroke> = emptyList(),
    val inkSelection: NotebookInkSelection? = null,
    val inkWriteFailed: Boolean = false,
)

@HiltViewModel
internal class NotebookViewModel @Inject constructor(
    private val store: NotebookStore,
    private val insertion: InsertCalculatorResult,
    private val verifiedMath: InsertVerifiedMath,
    private val selectInk: SelectNotebookInk,
    @param:MathInputDispatcher private val worker: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotebookState())
    val state = mutableState.asStateFlow()
    private var writingInk = false

    fun queueInk(pageId: UUID, stroke: InkStroke) {
        val current = state.value
        if (current.page?.id != pageId || current.page.readOnly || (current.busy && !writingInk)) return
        if (current.pendingInk.size >= MAX_QUEUED_STROKES) {
            fail(MathInsertionProblem.Unavailable)
            return
        }
        change { it.copy(pendingInk = it.pendingInk + stroke, inkSelection = null) }
        if (!current.inkWriteFailed) flushInk()
    }

    fun flushInk() = perform(allowPendingInk = true) {
        writingInk = true
        change { it.copy(inkWriteFailed = false) }
        try {
            while (true) {
                val current = state.value
                val stroke = current.pendingInk.firstOrNull() ?: break
                val page = current.page ?: break
                when (val saved = store.appendInk(page, stroke)) {
                    is NotebookRead.Available -> change {
                        it.copy(page = saved.value, pendingInk = it.pendingInk.drop(1), mathProposal = null)
                    }
                    NotebookRead.Unavailable -> {
                        change { it.copy(inkWriteFailed = true) }
                        break
                    }
                }
            }
        } finally {
            writingInk = false
        }
    }

    fun discardPendingInk() {
        if (!state.value.busy) change { it.copy(pendingInk = emptyList(), inkWriteFailed = false) }
    }

    suspend fun ink(page: NotebookPage, region: NotebookInkRegion) = store.ink(page, region)

    fun selectInk(lasso: InkLasso) = perform {
        val page = state.value.page ?: return@perform
        if (state.value.pendingInk.isNotEmpty()) return@perform
        val selection = withContext(worker) { selectInk(page, lasso) { ensureActive() } }
        change { it.copy(inkSelection = selection) }
    }

    fun clearInkSelection() = change { it.copy(inkSelection = null) }

    fun load() = perform {
        when (val result = store.list()) {
            is NotebookRead.Available -> change { it.copy(notebooks = result.value) }
            NotebookRead.Unavailable -> fail()
        }
    }

    fun create(title: String, kind: NotebookKind) = perform {
        when (val result = store.create(title, kind)) {
            is NotebookRead.Available -> {
                change { it.copy(notebooks = (it.notebooks + result.value.notebook).sortedBy(NotebookSummary::title)) }
                show(result.value)
            }
            NotebookRead.Unavailable -> fail()
        }
    }

    fun open(id: UUID) = perform { openPage(id) }

    fun openPageById(notebookId: UUID, pageId: UUID) = perform {
        if (state.value.page?.notebook?.id != notebookId) return@perform
        when (val result = store.openById(notebookId, pageId)) {
            is NotebookRead.Available -> show(result.value)
            NotebookRead.Unavailable -> fail()
        }
    }

    suspend fun thumbnail(notebookId: UUID, position: Int) = store.thumbnail(notebookId, position)

    fun inspectResultSource(resultId: UUID) = perform {
        val page = state.value.page ?: return@perform
        val source = store.resultSource(page, resultId)
        change { it.copy(resultSource = source) }
    }

    fun closeResultSource() = change { it.copy(resultSource = null) }

    fun turnPage(offset: Int) = perform {
        val page = state.value.page ?: return@perform
        val position = page.position + offset
        if (position in 0 until page.pageCount) openPage(page.notebook.id, position)
    }

    fun addPage(placement: NotebookPagePlacement = NotebookPagePlacement.End) = perform {
        val page = state.value.page ?: return@perform
        when (val result = store.addPage(page, placement)) {
            is NotebookRead.Available -> show(result.value)
            NotebookRead.Unavailable -> fail()
        }
    }

    fun movePage(offset: Int) = perform {
        val page = state.value.page ?: return@perform
        when (val result = store.movePage(page, offset)) {
            is NotebookRead.Available -> {
                show(result.value.page)
                change { it.copy(pageMoveUndo = result.value.undo) }
            }
            NotebookRead.Unavailable -> fail()
        }
    }

    fun undoPageMove() = perform {
        val undo = state.value.pageMoveUndo ?: return@perform
        when (val result = store.undoPageMove(undo)) {
            is NotebookRead.Available -> {
                show(result.value)
                change { it.copy(pageMoveUndo = null) }
            }
            NotebookRead.Unavailable -> fail()
        }
    }

    fun deletePage() = perform {
        val page = state.value.page ?: return@perform
        when (val result = store.deletePage(page)) {
            is NotebookRead.Available -> {
                show(result.value)
                change { it.copy(pageMoveUndo = null) }
            }
            NotebookRead.Unavailable -> fail()
        }
    }

    fun undoPageDeletion() = perform {
        val page = state.value.page ?: return@perform
        when (val result = store.restoreLastDeletedPage(page.notebook.id)) {
            is NotebookRead.Available -> {
                show(result.value)
                change { it.copy(pageMoveUndo = null) }
            }
            NotebookRead.Unavailable -> fail()
        }
    }

    fun closePage() {
        if (state.value.busy || state.value.pendingInk.isNotEmpty()) return
        change { it.copy(page = null, proposal = null, canUndo = false, problem = null, mathPreference = null,
            mathProposal = null, inkSelection = null, pageMoveUndo = null, canRestorePage = false, resultSource = null) }
    }

    fun toggleReadOnly() = perform {
        val page = state.value.page ?: return@perform
        when (val result = store.setReadOnly(page, !page.readOnly)) {
            is NotebookRead.Available -> show(result.value)
            NotebookRead.Unavailable -> fail()
        }
    }

    fun setMathMode(mode: MathInsertionMode) = perform {
        val page = state.value.page ?: return@perform
        when (val result = store.setMathMode(page, mode)) {
            is NotebookRead.Available -> show(result.value)
            NotebookRead.Unavailable -> fail()
        }
    }

    suspend fun visible(page: NotebookPage, left: Float, top: Float, right: Float, bottom: Float) =
        store.visible(page.id, left, top, right, bottom)

    fun preview(result: CalculationResult) {
        if (state.value.busy) return
        val page = state.value.page ?: return
        if (page.readOnly) {
            fail(MathInsertionProblem.ReadOnly)
            return
        }
        when (val review = insertion.preview(CalculatorInsertionSession(UUID.randomUUID(), page.target(), result))) {
            is CalculatorInsertionReview.Preview -> change { it.copy(proposal = review.proposal, problem = null) }
            is CalculatorInsertionReview.Rejected -> fail(review.problem)
        }
    }

    fun cancelPreview() = change { it.copy(proposal = null) }

    fun previewMath(review: MathInputReview.Editing?, manuallyRequested: Boolean = true) = perform {
        val page = state.value.page ?: return@perform fail(MathInsertionProblem.NoEditableTarget)
        val preference = state.value.mathPreference ?: return@perform fail()
        if (!manuallyRequested && preference.mode == MathInsertionMode.Manual) return@perform
        if (page.readOnly) return@perform fail(MathInsertionProblem.ReadOnly)
        val confirmed = review?.check as? MathCorrectionCheck.Confirmed ?: return@perform
        val solution = review.solution as? MathSolutionReview.Explanation ?: return@perform
        if (confirmed.resultId == state.value.insertedMathId) return@perform
        val session = MathInsertionSession(
            confirmed.resultId, page.target(), preference.mode, preference.grant.takeUnless { manuallyRequested },
        )
        when (val decision = verifiedMath.onResult(session, confirmed.reviewed, solution.outcome, manuallyRequested)) {
            is MathInsertionDecision.Preview -> change { it.copy(mathProposal = decision.proposal) }
            is MathInsertionDecision.Finished -> finishMath(decision.result, page, confirmed.resultId)
            MathInsertionDecision.AwaitingManualRequest -> Unit
        }
    }

    fun cancelMathPreview() = change { it.copy(mathProposal = null, problem = null) }

    fun approveMath(review: MathInputReview.Editing?) = perform {
        val proposal = state.value.mathProposal ?: return@perform
        cancelMathPreview()
        val page = state.value.page ?: return@perform fail(MathInsertionProblem.NoEditableTarget)
        val confirmed = review?.check as? MathCorrectionCheck.Confirmed
            ?: return@perform fail(MathInsertionProblem.SelectionChanged)
        val preference = state.value.mathPreference ?: return@perform fail()
        val outcome = verifiedMath.approve(proposal, MathInsertionSession(confirmed.resultId, page.target(), preference.mode))
        finishMath(outcome, page, confirmed.resultId)
    }

    private suspend fun finishMath(outcome: MathInsertionCommit, page: NotebookPage, id: UUID) {
        when (outcome) {
            is MathInsertionCommit.Inserted -> {
                change { it.copy(insertedMathId = id) }
                refreshPage(page)
            }
            is MathInsertionCommit.Rejected -> fail(outcome.problem)
        }
    }

    fun dismissProblem() = change { it.copy(problem = null) }

    fun approve(result: CalculationResult?) = perform {
        val current = state.value
        val proposal = current.proposal ?: return@perform
        val page = current.page ?: return@perform
        cancelPreview()
        val session = CalculatorInsertionSession(proposal.resultId, page.target(), result)
        when (val outcome = insertion.approve(proposal, session)) {
            is MathInsertionCommit.Inserted -> refreshPage(page)
            is MathInsertionCommit.Rejected -> fail(outcome.problem)
        }
    }

    fun undo() = perform {
        val page = state.value.page ?: return@perform
        val operation = store.lastUndo(page.id) ?: return@perform fail()
        when (val outcome = insertion.undo(operation)) {
            MathInsertionUndoResult.Restored -> refreshPage(page)
            is MathInsertionUndoResult.Rejected -> fail(outcome.problem)
        }
    }

    private suspend fun openPage(id: UUID, position: Int = 0) {
        when (val result = store.open(id, position)) {
            is NotebookRead.Available -> show(result.value)
            NotebookRead.Unavailable -> fail()
        }
    }

    private suspend fun show(page: NotebookPage) {
        val canUndo = !page.readOnly && store.lastUndo(page.id) != null
        val preference = (store.mathPreference(page) as? NotebookRead.Available)?.value
        val canRestore = page.fixed && store.hasDeletedPages(page.notebook.id)
        change { it.copy(page = page, canUndo = canUndo, mathPreference = preference, mathProposal = null,
            inkSelection = it.inkSelection.takeIf { _ -> it.page?.id == page.id }, canRestorePage = canRestore, resultSource = null) }
    }

    private suspend fun refreshPage(page: NotebookPage) {
        when (val result = store.openById(page.notebook.id, page.id)) {
            is NotebookRead.Available -> show(result.value)
            NotebookRead.Unavailable -> fail()
        }
    }

    private fun perform(allowPendingInk: Boolean = false, action: suspend () -> Unit) {
        if (state.value.busy || (!allowPendingInk && state.value.pendingInk.isNotEmpty())) return
        change { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                change { it.copy(busy = false) }
            }
        }
    }

    private fun fail(problem: MathInsertionProblem = MathInsertionProblem.Unavailable) = change { it.copy(problem = problem) }

    private inline fun change(update: (NotebookState) -> NotebookState) {
        mutableState.value = update(mutableState.value)
    }

    private fun NotebookPage.target() = MathInsertionTarget(MathNoteDestination(notebook.id, id), revision, id)

    private companion object {
        const val MAX_QUEUED_STROKES = 32
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object NotebookModule {
    @Provides @Singleton
    fun roomNotebook(@ApplicationContext context: Context): RoomNotebookStore = RoomNotebookStore.open(context)

    @Provides fun notebooks(store: RoomNotebookStore): NotebookStore = store

    @Provides fun insertResult(store: RoomNotebookStore): InsertCalculatorResult = InsertCalculatorResult(store)

    @Provides fun insertVerifiedMath(store: RoomNotebookStore): InsertVerifiedMath = InsertVerifiedMath(store)

    @Provides fun notebookInkSelection(store: NotebookStore): SelectNotebookInk = SelectNotebookInk(store)

    @Provides fun notebookInkHistory(store: RoomNotebookStore): LinkedMathSolutionHistory = store
}
