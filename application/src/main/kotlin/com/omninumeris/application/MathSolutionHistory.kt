package com.omninumeris.application

import com.omninumeris.domain.math.ExactMathResult
import com.omninumeris.domain.math.MathSourceAnchor
import java.util.UUID

enum class TypedSolutionKind { Value, Linear, Identity, Contradiction }

data class TypedSolutionAnswer(val kind: TypedSolutionKind, val value: String = "") {
    companion object {
        fun from(result: ExactMathResult): TypedSolutionAnswer? = when (result) {
            is ExactMathResult.Value -> TypedSolutionAnswer(TypedSolutionKind.Value, result.value.toString())
            is ExactMathResult.VerifiedLinearSolution -> TypedSolutionAnswer(TypedSolutionKind.Linear, result.value.toString())
            ExactMathResult.Identity -> TypedSolutionAnswer(TypedSolutionKind.Identity)
            ExactMathResult.Contradiction -> TypedSolutionAnswer(TypedSolutionKind.Contradiction)
            is ExactMathResult.Rejected -> null
        }
    }
}

data class TypedSolutionRecord(
    val id: UUID,
    val original: String,
    val confirmed: String,
    val answer: TypedSolutionAnswer,
    val engineVersion: Int = CURRENT_ENGINE,
) {
    companion object {
        const val CURRENT_ENGINE = 1
        const val MAX_ORIGINAL_LENGTH = 2048
    }
}

sealed interface MathSolutionHistoryRead {
    data class Available(val records: List<TypedSolutionRecord>) : MathSolutionHistoryRead
    data object Unavailable : MathSolutionHistoryRead
}

enum class MathSolutionSave { Saved, TooLarge, Unavailable }

interface MathSolutionHistory {
    suspend fun read(): MathSolutionHistoryRead
    suspend fun save(record: TypedSolutionRecord): MathSolutionSave
}

data class LinkedMathSolutionRecord(val anchor: MathSourceAnchor, val solution: TypedSolutionRecord)

sealed interface LinkedMathHistoryRead {
    data class Available(val records: List<LinkedMathSolutionRecord>) : LinkedMathHistoryRead
    data object Unavailable : LinkedMathHistoryRead
}

/** The source owner authorizes each operation and stores records inside the source's protected scope. */
interface LinkedMathSolutionHistory {
    /** Returns up to 32 records, newest first, for the exact supplied anchor. */
    suspend fun read(anchor: MathSourceAnchor): LinkedMathHistoryRead

    /** Recheck the source revision and live access atomically with the write. */
    suspend fun save(record: LinkedMathSolutionRecord): MathSolutionSave
}
