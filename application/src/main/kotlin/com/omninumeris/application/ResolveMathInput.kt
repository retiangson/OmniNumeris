package com.omninumeris.application

import com.omninumeris.domain.math.MathInput
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.math.MathSourceAnchor
import java.util.concurrent.CancellationException

enum class MathInputProblem { Unavailable, Locked, SourceChanged, Unsupported, NoCandidates, RecognitionFailed }

sealed interface MathRecognitionResult {
    data class Texts(val values: List<String>) : MathRecognitionResult
    data class Rejected(val problem: MathInputProblem) : MathRecognitionResult
}

/** Reads a revision through the host's local, authorized source engine. */
interface MathRecognitionAdapter {
    val source: MathInputSource
    suspend fun recognize(anchor: MathSourceAnchor): MathRecognitionResult
}

data class MathInputCandidate(
    val originalText: String,
    val text: String,
    val anchor: MathSourceAnchor?,
)

sealed interface MathInputCandidates {
    data class Review(val candidates: List<MathInputCandidate>) : MathInputCandidates
    data class Rejected(val problem: MathInputProblem) : MathInputCandidates
}

data class ReviewedMathInput(val input: MathInput, val candidate: MathInputCandidate)

class ResolveMathInput(adapters: Set<MathRecognitionAdapter>) {
    private val bySource = adapters.associateBy(MathRecognitionAdapter::source)
    val availableSources: Set<MathInputSource> get() = bySource.keys + MathInputSource.Typed

    init {
        require(bySource.size == adapters.size && MathInputSource.Typed !in bySource)
    }

    fun typed(text: String): MathInputCandidate = MathInputCandidate(text, text, null)

    suspend fun recognize(anchor: MathSourceAnchor): MathInputCandidates {
        val adapter = bySource[anchor.source] ?: return MathInputCandidates.Rejected(MathInputProblem.Unavailable)
        val result = try {
            adapter.recognize(anchor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return MathInputCandidates.Rejected(MathInputProblem.RecognitionFailed)
        }
        return when (result) {
            is MathRecognitionResult.Rejected -> MathInputCandidates.Rejected(result.problem)
            is MathRecognitionResult.Texts -> toCandidates(anchor, result.values)
        }
    }

    fun parse(candidate: MathInputCandidate): MathParseResult = MathParser.parse(candidate.text)

    private fun toCandidates(anchor: MathSourceAnchor, texts: List<String>): MathInputCandidates {
        if (texts.size > MAX_CANDIDATES || texts.any { it.length > MathParser.MAX_INPUT_LENGTH }) {
            return MathInputCandidates.Rejected(MathInputProblem.Unsupported)
        }
        val candidates = texts.filterNot(String::isBlank).map { text -> MathInputCandidate(text, text, anchor) }
        return if (candidates.isEmpty()) MathInputCandidates.Rejected(MathInputProblem.NoCandidates)
            else MathInputCandidates.Review(candidates)
    }

    private companion object {
        const val MAX_CANDIDATES = 8
    }
}
