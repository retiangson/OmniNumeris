package com.omninumeris.app

import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.application.CalculatorHistoryEntry
import com.omninumeris.application.CalculatorHistoryResult
import com.omninumeris.application.CalculatorValueKind
import com.omninumeris.application.MathSolutionHistoryRead
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.TypedSolutionAnswer
import com.omninumeris.application.TypedSolutionKind
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.infrastructure.storage.EncryptedCalculatorHistory
import com.omninumeris.infrastructure.storage.EncryptedMathSolutionHistory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CalculatorHistoryStorageTest {
    @Test
    fun typedSolutionHistorySurvivesReopeningAndRejectsCalculatorCiphertext() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "solution-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            val history = EncryptedMathSolutionHistory(directory, Dispatchers.IO)
            val solution = TypedSolutionRecord(UUID.randomUUID(), "2*x+3=9", "2*x+3=7",
                TypedSolutionAnswer(TypedSolutionKind.Linear, "2"))
            assertEquals(MathSolutionSave.Saved, history.save(solution))
            val reopened = EncryptedMathSolutionHistory(directory, Dispatchers.IO)
            assertEquals(MathSolutionHistoryRead.Available(listOf(solution)), reopened.read())
            val calculator = EncryptedCalculatorHistory(directory, Dispatchers.IO)
            val entry = CalculatorHistoryEntry(UUID.randomUUID(), "Standard", "1/3", "1/3", CalculatorValueKind.Exact)
            assertEquals(CalculatorHistoryResult.Available(listOf(entry)), calculator.append(entry))

            val solverFile = File(directory, "typed-solution-history.v1")
            val calculatorBytes = File(directory, "calculator-history.v1").readBytes()
            solverFile.writeBytes(calculatorBytes)
            assertEquals(MathSolutionHistoryRead.Unavailable, reopened.read())
            assertEquals(MathSolutionSave.Unavailable, reopened.save(solution))
            assertArrayEquals(calculatorBytes, solverFile.readBytes())
            assertEquals(CalculatorHistoryResult.Available(listOf(entry)), calculator.read())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun historyReloadsAndRejectsTamperingWithoutOverwritingExistingBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "history-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            val history = EncryptedCalculatorHistory(directory, Dispatchers.IO)
            val expression = "739/1151+101/1151"
            val entry = CalculatorHistoryEntry(UUID.randomUUID(), "Standard", expression, "840/1151", CalculatorValueKind.Exact)
            assertEquals(CalculatorHistoryResult.Available(emptyList()), history.read())
            val expected = CalculatorHistoryResult.Available(listOf(entry))
            assertEquals(expected, history.append(entry))
            val reopened = EncryptedCalculatorHistory(directory, Dispatchers.IO)
            assertEquals(expected, reopened.read())

            val record = File(directory, "calculator-history.v1")
            val authenticated = record.readBytes()
            assertFalse(authenticated.toString(Charsets.ISO_8859_1).contains(expression))
            val corrupt = authenticated.copyOf()
            corrupt[corrupt.lastIndex] = (corrupt.last().toInt() xor 1).toByte()
            record.writeBytes(corrupt)
            assertEquals(CalculatorHistoryResult.Unavailable, reopened.read())
            assertEquals(CalculatorHistoryResult.Unavailable, reopened.append(entry.copy(id = UUID.randomUUID())))
            assertArrayEquals(corrupt, record.readBytes())
            record.writeBytes(authenticated)
            assertEquals(expected, reopened.read())
        } finally {
            directory.deleteRecursively()
        }
    }
}
