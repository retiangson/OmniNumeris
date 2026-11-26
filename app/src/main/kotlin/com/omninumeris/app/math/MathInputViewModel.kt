package com.omninumeris.app.math

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import com.omninumeris.application.LinkedMathHistoryRead
import com.omninumeris.application.LinkedMathSolutionHistory
import com.omninumeris.application.LinkedMathSolutionRecord
import com.omninumeris.application.MathInputCandidate
import com.omninumeris.application.MathInputCandidates
import com.omninumeris.application.MathInputProblem
import com.omninumeris.application.LocalMathExplanation
import com.omninumeris.application.MathRecognitionAdapter
import com.omninumeris.application.MathSolutionHistory
import com.omninumeris.application.MathSolutionHistoryRead
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.MathSolutionOutcome
import com.omninumeris.application.ResolveMathInput
import com.omninumeris.application.ReviewedMathInput
import com.omninumeris.application.SolveMath
import com.omninumeris.application.TypedSolutionAnswer
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.ExactMathHint
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.infrastructure.storage.EncryptedMathSolutionHistory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface MathInputReview {
    data object Idle : MathInputReview
    data class Loading(val source: MathInputSource) : MathInputReview
    data class Failed(val source: MathInputSource, val problem: MathInputProblem) : MathInputReview
    data class Editing(
        val source: MathInputSource,
        val candidates: List<MathInputCandidate>,
        val sourcePreview: MathReviewSource?,
        val selected: Int = 0,
        val check: MathCorrectionCheck = MathCorrectionCheck.Unchecked,
        val solution: MathSolutionReview = MathSolutionReview.Idle,
        val saveStatus: MathHistorySaveStatus? = null,
        val hasSourceHistory: Boolean = false,
        val reviewId: UUID = UUID.randomUUID(),
    ) : MathInputReview
}

internal enum class MathHistorySaveStatus { Saving, Saved, TooLarge, Unavailable }

internal sealed interface MathHistoryPanel {
    data object Closed : MathHistoryPanel
    data object Loading : MathHistoryPanel
    data class Ready(val records: List<TypedSolutionRecord>, val failedToOpen: Boolean = false) : MathHistoryPanel
    data class Reopening(val records: List<TypedSolutionRecord>) : MathHistoryPanel
    data object Unavailable : MathHistoryPanel
}

internal sealed interface MathSolutionReview {
    data object Idle : MathSolutionReview
    data object Working : MathSolutionReview
    data class Hint(val hint: ExactMathHint) : MathSolutionReview
    data class Explanation(
        val outcome: MathSolutionOutcome,
        val offerLocalExplanation: Boolean = false,
    ) : MathSolutionReview
}

internal sealed interface MathReviewSource {
    data class Text(val value: String) : MathReviewSource
    data class Image(val bitmap: ImageBitmap) : MathReviewSource
    data class Ink(val strokes: List<com.omninumeris.domain.canvas.InkStroke>) : MathReviewSource
}

internal sealed interface MathCorrectionCheck {
    data object Unchecked : MathCorrectionCheck
    data object Checking : MathCorrectionCheck
    data class Invalid(val error: MathError) : MathCorrectionCheck
    data class Parsed(val reviewed: ReviewedMathInput) : MathCorrectionCheck
    data class Confirmed(val reviewed: ReviewedMathInput, val resultId: UUID = UUID.randomUUID()) : MathCorrectionCheck
}

@HiltViewModel
internal class MathInputViewModel @Inject constructor(
    private val resolve: ResolveMathInput,
    @param:MathInputDispatcher private val dispatcher: CoroutineDispatcher,
    private val solutionHistory: MathSolutionHistory,
    private val solver: SolveMath = SolveMath(),
) : ViewModel() {
    val availableSources = resolve.availableSources
    private val mutableReview = MutableStateFlow<MathInputReview>(MathInputReview.Idle)
    val review = mutableReview.asStateFlow()
    private val mutableHistory = MutableStateFlow<MathHistoryPanel>(MathHistoryPanel.Closed)
    val history = mutableHistory.asStateFlow()
    private val recognitionGate = Mutex()
    private val localExplanationGate = Mutex()
    private var recognition: Job? = null
    private var correction: Job? = null
    private var solving: Job? = null
    private var readingHistory: Job? = null
    private var mediaSession: MathSourceReviewSession? = null
    private var mediaPreview: MathReviewSource? = null
    private var watchingSource: Job? = null
    private var historySession: MathSourceReviewSession? = null
    private var savingMediaSolution: Job? = null

    fun previewTyped(text: String): MathParseResult = resolve.parse(resolve.typed(text))

    @MainThread
    fun beginTypedReview(text: String) {
        if (text.isBlank()) return
        dismissReview()
        val candidate = resolve.typed(text)
        mutableReview.value = MathInputReview.Editing(
            MathInputSource.Typed,
            listOf(candidate),
            MathReviewSource.Text(
                if (text.length <= MAX_SOURCE_TEXT) text else text.take(MAX_SOURCE_TEXT) + "…",
            ),
        )
    }

    @MainThread
    fun beginInkTranscription(
        selection: com.omninumeris.application.NotebookInkSelection.Selected,
        history: LinkedMathSolutionHistory,
    ) {
        dismissReview()
        val preview = MathReviewSource.Ink(selection.strokes)
        val session = MathSourceReviewSession(selection.anchor, history)
        mediaSession = session
        mediaPreview = preview
        mutableReview.value = MathInputReview.Editing(
            MathInputSource.Handwriting,
            listOf(resolve.typed("").copy(anchor = session.anchor)),
            preview,
            hasSourceHistory = true,
        )
    }

    @MainThread
    fun recognize(session: MathSourceReviewSession, sourcePreview: MathReviewSource? = null) {
        dismissReview()
        val anchor = session.anchor
        mediaSession = session
        if (!canUseSource(session)) return
        mediaPreview = when (sourcePreview) {
            is MathReviewSource.Image -> sourcePreview.takeIf {
                it.bitmap.width in 1..MAX_SOURCE_IMAGE_EDGE && it.bitmap.height in 1..MAX_SOURCE_IMAGE_EDGE
            }
            is MathReviewSource.Text -> sourcePreview.takeIf { it.value.length <= MAX_SOURCE_TEXT }
            is MathReviewSource.Ink -> sourcePreview
            null -> null
        }
        val loading = MathInputReview.Loading(anchor.source)
        mutableReview.value = loading
        watchingSource = viewModelScope.launch {
            session.revocation.filterNotNull().first()
            canUseSource(session)
        }
        recognition = viewModelScope.launch {
            val result = withContext(dispatcher) {
                recognitionGate.withLock {
                    val revoked = session.revocation.value
                    if (revoked != null) MathInputCandidates.Rejected(revoked.problem) else resolve.recognize(anchor)
                }
            }
            if (mutableReview.value !== loading || !canUseSource(session)) return@launch
            mutableReview.value = when (result) {
                is MathInputCandidates.Rejected -> {
                    clearMediaSession()
                    MathInputReview.Failed(anchor.source, result.problem)
                }
                is MathInputCandidates.Review -> MathInputReview.Editing(anchor.source, result.candidates, mediaPreview,
                    hasSourceHistory = session.history != null)
            }
        }
    }

    private fun canUseSource(session: MathSourceReviewSession): Boolean {
        if (mediaSession !== session) return false
        val revoked = session.revocation.value ?: return true
        dismissReview()
        mutableReview.value = MathInputReview.Failed(session.anchor.source, revoked.problem)
        return false
    }

    private fun canUseCurrentSource(): Boolean = mediaSession?.let(::canUseSource) ?: true

    private fun editableReview(): MathInputReview.Editing? =
        if (canUseCurrentSource()) mutableReview.value as? MathInputReview.Editing else null

    fun confirmedSolution(): MathInputReview.Editing? {
        val current = editableReview() ?: return null
        val solution = current.solution as? MathSolutionReview.Explanation ?: return null
        return current.takeIf {
            it.check is MathCorrectionCheck.Confirmed && solution.outcome is MathSolutionOutcome.Verified
        }
    }

    @MainThread
    fun selectCandidate(index: Int) {
        val current = editableReview() ?: return
        if (index !in current.candidates.indices || index == current.selected) return
        correction?.cancel()
        solving?.cancel()
        mutableReview.value = current.copy(selected = index, check = MathCorrectionCheck.Unchecked,
            solution = MathSolutionReview.Idle, saveStatus = null)
    }

    @MainThread
    fun editCandidate(text: String) {
        val current = editableReview() ?: return
        updateCandidate(current, text)
    }

    @MainThread
    fun editCandidate(reviewId: UUID, selected: Int, text: String) {
        val current = editableReview() ?: return
        if (current.reviewId != reviewId || current.selected != selected) return
        updateCandidate(current, text)
    }

    private fun updateCandidate(current: MathInputReview.Editing, text: String) {
        val candidates = current.candidates.toMutableList()
        val candidate = candidates[current.selected]
        if (candidate.text == text) return
        correction?.cancel()
        solving?.cancel()
        candidates[current.selected] = candidate.copy(text = text)
        mutableReview.value = current.copy(candidates = candidates.toList(), check = MathCorrectionCheck.Unchecked,
            solution = MathSolutionReview.Idle, saveStatus = null)
    }

    @MainThread
    fun reviewCandidate() {
        val current = editableReview() ?: return
        val candidate = current.candidates[current.selected]
        if (current.check == MathCorrectionCheck.Checking || current.check is MathCorrectionCheck.Confirmed ||
            candidate.text.isBlank()
        ) return
        val checking = current.copy(check = MathCorrectionCheck.Checking)
        mutableReview.value = checking
        correction = viewModelScope.launch {
            val result = withContext(dispatcher) { resolve.parse(candidate) }
            if (mutableReview.value !== checking || !canUseCurrentSource()) return@launch
            val check = when (result) {
                is MathParseResult.Parsed -> MathCorrectionCheck.Parsed(ReviewedMathInput(result.input, candidate))
                is MathParseResult.Rejected -> MathCorrectionCheck.Invalid(result.error)
            }
            mutableReview.value = checking.copy(check = check)
        }
    }

    @MainThread
    fun confirmCandidate(): ReviewedMathInput? {
        val current = editableReview() ?: return null
        val parsed = current.check as? MathCorrectionCheck.Parsed ?: return null
        mutableReview.value = current.copy(check = MathCorrectionCheck.Confirmed(parsed.reviewed))
        return parsed.reviewed
    }

    @MainThread
    fun requestHint() = solveWith { MathSolutionReview.Hint(ExactMath.hint(it.input)) }

    @MainThread
    fun solveConfirmed(onVerified: (MathInputReview.Editing) -> Unit = {}) = solveWith(onVerified) {
        val outcome = solver.solve(it.input)
        MathSolutionReview.Explanation(
            outcome,
            outcome is MathSolutionOutcome.Unsupported && it.candidate.anchor == null && solver.hasLocalExplanation,
        )
    }

    @MainThread
    fun requestLocalExplanation() {
        val current = editableReview() ?: return
        val explanation = current.solution as? MathSolutionReview.Explanation ?: return
        if (current.source != MathInputSource.Typed || !explanation.offerLocalExplanation) return
        solveWith {
            if (it.candidate.anchor != null) return@solveWith explanation
            MathSolutionReview.Explanation(localExplanationGate.withLock { solver.localExplanation(it.input) })
        }
    }

    private fun solveWith(
        onVerified: (MathInputReview.Editing) -> Unit = {},
        operation: suspend (ReviewedMathInput) -> MathSolutionReview,
    ) {
        val current = editableReview() ?: return
        val confirmed = current.check as? MathCorrectionCheck.Confirmed ?: return
        if (current.solution == MathSolutionReview.Working) return
        val working = current.copy(solution = MathSolutionReview.Working, saveStatus = null)
        mutableReview.value = working
        solving = viewModelScope.launch {
            val result = withContext(dispatcher) { operation(confirmed.reviewed) }
            if (mutableReview.value !== working || !canUseCurrentSource()) return@launch
            val solved = working.copy(solution = result)
            mutableReview.value = solved
            if (result is MathSolutionReview.Explanation && result.outcome is MathSolutionOutcome.Verified) {
                onVerified(solved)
            }
        }
    }

    @MainThread
    fun saveSolution() {
        val current = editableReview() ?: return
        if (current.saveStatus == MathHistorySaveStatus.Saving ||
            current.saveStatus == MathHistorySaveStatus.Saved
        ) return
        val confirmed = (current.check as? MathCorrectionCheck.Confirmed)?.reviewed ?: return
        val source = mediaSession
        val linkedHistory = source?.history
        val anchor = confirmed.candidate.anchor
        if (source == null) {
            if (current.source != MathInputSource.Typed || anchor != null) return
        } else if (linkedHistory == null || anchor != source.anchor || savingMediaSolution?.isActive == true) return
        val outcome = (current.solution as? MathSolutionReview.Explanation)?.outcome as? MathSolutionOutcome.Verified ?: return
        val result = outcome.proof.result
        val answer = TypedSolutionAnswer.from(result) ?: return
        val saving = current.copy(saveStatus = MathHistorySaveStatus.Saving)
        mutableReview.value = saving
        val job = viewModelScope.launch {
            val record = TypedSolutionRecord(UUID.randomUUID(), confirmed.candidate.originalText, confirmed.candidate.text, answer)
            val stored = if (source == null) solutionHistory.save(record) else {
                if (!canUseSource(source)) return@launch
                requireNotNull(linkedHistory).save(LinkedMathSolutionRecord(source.anchor, record))
            }
            if (mutableReview.value !== saving || !canUseCurrentSource()) return@launch
            val status = when (stored) {
                MathSolutionSave.Saved -> MathHistorySaveStatus.Saved
                MathSolutionSave.TooLarge -> MathHistorySaveStatus.TooLarge
                MathSolutionSave.Unavailable -> MathHistorySaveStatus.Unavailable
            }
            mutableReview.value = saving.copy(saveStatus = status)
        }
        if (source != null) savingMediaSolution = job
    }

    @MainThread
    fun showSolutionHistory() = showHistory(source = null)

    @MainThread
    fun showSourceSolutionHistory() {
        val source = mediaSession ?: return
        if (source.history == null || editableReview() == null) return
        showHistory(source)
    }

    private fun showHistory(source: MathSourceReviewSession?) {
        closeSolutionHistory()
        historySession = source
        mutableHistory.value = MathHistoryPanel.Loading
        readingHistory = viewModelScope.launch {
            val next = if (source == null) {
                when (val result = solutionHistory.read()) {
                    is MathSolutionHistoryRead.Available -> MathHistoryPanel.Ready(result.records)
                    MathSolutionHistoryRead.Unavailable -> MathHistoryPanel.Unavailable
                }
            } else {
                if (!canUseSource(source)) return@launch
                when (val result = requireNotNull(source.history).read(source.anchor)) {
                    is LinkedMathHistoryRead.Available -> {
                        val records = result.records.asSequence().take(MAX_LINKED_HISTORY_RECORDS)
                            .filter { it.anchor == source.anchor }.map { it.solution }
                            .filter { it.original.length <= MAX_SOURCE_TEXT && it.confirmed.length <= MathParser.MAX_INPUT_LENGTH }
                            .distinctBy { it.id }.toList()
                        MathHistoryPanel.Ready(records)
                    }
                    LinkedMathHistoryRead.Unavailable -> MathHistoryPanel.Unavailable
                }
            }
            if (!isActive || historySession !== source || mutableHistory.value != MathHistoryPanel.Loading ||
                source != null && !canUseSource(source)
            ) return@launch
            mutableHistory.value = next
        }
    }

    @MainThread
    fun reopenSolution(id: UUID) {
        val source = historySession
        if (source != null && !canUseSource(source)) return
        val history = mutableHistory.value as? MathHistoryPanel.Ready ?: return
        val record = history.records.firstOrNull { it.id == id } ?: return
        val opening = MathHistoryPanel.Reopening(history.records)
        mutableHistory.value = opening
        readingHistory = viewModelScope.launch {
            if (source != null) {
                val live = requireNotNull(source.history).read(source.anchor)
                if (mutableHistory.value !== opening || historySession !== source || !canUseSource(source)) return@launch
                if (live !is LinkedMathHistoryRead.Available) {
                    source.revoke(MathSourceRevocation.AccessRevoked)
                    canUseSource(source)
                    return@launch
                }
                if (live.records.none { it.anchor == source.anchor && it.solution == record }) {
                    mutableHistory.value = MathHistoryPanel.Unavailable
                    return@launch
                }
            }
            val restored = withContext(dispatcher) {
                if (record.engineVersion != TypedSolutionRecord.CURRENT_ENGINE) return@withContext null
                val candidate = MathInputCandidate(record.original, record.confirmed, source?.anchor)
                val parsed = resolve.parse(candidate) as? MathParseResult.Parsed ?: return@withContext null
                val explanation = solver.solve(parsed.input) as? MathSolutionOutcome.Verified ?: return@withContext null
                if (TypedSolutionAnswer.from(explanation.proof.result) != record.answer) return@withContext null
                MathInputReview.Editing(
                    source?.anchor?.source ?: MathInputSource.Typed, listOf(candidate), sourcePreview = null,
                    check = MathCorrectionCheck.Confirmed(ReviewedMathInput(parsed.input, candidate)),
                    solution = MathSolutionReview.Explanation(explanation),
                    saveStatus = MathHistorySaveStatus.Saved,
                    hasSourceHistory = source?.history != null,
                )
            }
            if (mutableHistory.value !== opening || historySession !== source || source != null && !canUseSource(source)) return@launch
            if (restored == null) {
                mutableHistory.value = history.copy(failedToOpen = true)
            } else {
                closeSolutionHistory()
                if (source == null) dismissReview() else {
                    correction?.cancel()
                    solving?.cancel()
                }
                val preview = if (source == null) MathReviewSource.Text(record.original) else mediaPreview
                mutableReview.value = restored.copy(sourcePreview = preview)
            }
        }
    }

    @MainThread
    fun closeSolutionHistory() {
        historySession = null
        readingHistory?.cancel()
        mutableHistory.value = MathHistoryPanel.Closed
    }

    @MainThread
    fun dismissReview() {
        clearMediaSession()
        recognition?.cancel()
        correction?.cancel()
        solving?.cancel()
        mutableReview.value = MathInputReview.Idle
    }

    private fun clearMediaSession() {
        if (historySession != null) closeSolutionHistory()
        mediaSession = null
        mediaPreview = null
        savingMediaSolution?.cancel()
        savingMediaSolution = null
        watchingSource?.cancel()
        watchingSource = null
    }

    private companion object {
        const val MAX_SOURCE_IMAGE_EDGE = 1024
        const val MAX_SOURCE_TEXT = MathParser.MAX_INPUT_LENGTH * 4
        const val MAX_LINKED_HISTORY_RECORDS = 32
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MathInputDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MathInputModule {
    @Multibinds
    abstract fun adapters(): Set<MathRecognitionAdapter>

    @Multibinds
    abstract fun localExplanations(): Set<LocalMathExplanation>

    companion object {
        @Provides
        fun resolveMathInput(adapters: Set<@JvmSuppressWildcards MathRecognitionAdapter>): ResolveMathInput =
            ResolveMathInput(adapters)

        @Provides
        @MathInputDispatcher
        fun dispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        fun solver(providers: Set<@JvmSuppressWildcards LocalMathExplanation>): SolveMath = SolveMath(providers)

        @Provides
        @Singleton
        fun solutionHistory(@ApplicationContext context: Context): MathSolutionHistory =
            EncryptedMathSolutionHistory(context.noBackupFilesDir, Dispatchers.IO)
    }
}
