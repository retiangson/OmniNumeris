package com.omninumeris.application

import com.omninumeris.domain.math.ExactMath
import com.omninumeris.domain.math.ExactMathExplanation
import com.omninumeris.domain.math.ExactMathResult
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.MathInput
import java.util.concurrent.CancellationException

sealed interface MathSolutionOutcome {
    class Verified internal constructor(val input: MathInput, val proof: ExactMathExplanation) : MathSolutionOutcome
    data class Unverified(val explanation: String) : MathSolutionOutcome
    data class Unsupported(val error: MathError) : MathSolutionOutcome
    data class Error(val error: MathError) : MathSolutionOutcome
    data object VerificationFailed : MathSolutionOutcome
    data object LocalExplanationUnavailable : MathSolutionOutcome
}

/** A local explanation provider has no contract for asserting a verification result. */
fun interface LocalMathExplanation {
    suspend fun explain(input: MathInput): String?
}

class SolveMath(providers: Set<LocalMathExplanation> = emptySet()) {
    private val local = providers.singleOrNull()
    val hasLocalExplanation: Boolean get() = local != null

    init {
        require(providers.size <= 1)
    }

    fun solve(input: MathInput): MathSolutionOutcome = verify(input, ExactMath.explain(input))

    fun verify(input: MathInput, reported: ExactMathExplanation): MathSolutionOutcome {
        val replayed = ExactMath.explain(input)
        if (reported != replayed) return MathSolutionOutcome.VerificationFailed
        return when (val outcome = replayed.result) {
            is ExactMathResult.Rejected -> when (outcome.error.code) {
                MathErrorCode.Unsupported -> MathSolutionOutcome.Unsupported(outcome.error)
                MathErrorCode.VerificationFailed -> MathSolutionOutcome.VerificationFailed
                else -> MathSolutionOutcome.Error(outcome.error)
            }
            else -> MathSolutionOutcome.Verified(input, replayed)
        }
    }

    suspend fun localExplanation(input: MathInput): MathSolutionOutcome {
        val text = try {
            local?.explain(input)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return if (text.isNullOrBlank() || text.length > 4096) MathSolutionOutcome.LocalExplanationUnavailable
        else MathSolutionOutcome.Unverified(text)
    }
}
