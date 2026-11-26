package com.omninumeris.application

import java.util.UUID

enum class CalculatorValueKind { Exact, Approximate, Decimal, Integer, Matrix }

data class CalculatorHistoryEntry(
    val id: UUID,
    val mode: String,
    val input: String,
    val result: String,
    val kind: CalculatorValueKind,
)

sealed interface CalculatorHistoryResult {
    data class Available(val entries: List<CalculatorHistoryEntry>) : CalculatorHistoryResult
    data object EntryTooLarge : CalculatorHistoryResult
    data object Unavailable : CalculatorHistoryResult
}

interface CalculatorHistory {
    suspend fun read(): CalculatorHistoryResult
    suspend fun append(entry: CalculatorHistoryEntry): CalculatorHistoryResult
}
