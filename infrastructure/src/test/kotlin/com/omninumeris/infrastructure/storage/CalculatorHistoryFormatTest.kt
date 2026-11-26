package com.omninumeris.infrastructure.storage

import com.omninumeris.application.CalculatorHistoryEntry
import com.omninumeris.application.CalculatorValueKind
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CalculatorHistoryFormatTest {
    private val entry = CalculatorHistoryEntry(UUID.randomUUID(), "Standard", "1/3+1/6", "1/2", CalculatorValueKind.Exact)

    @Test
    fun preservesCanonicalValuesAndOrder() {
        val approximate = entry.copy(id = UUID.randomUUID(), result = "0.12345", kind = CalculatorValueKind.Approximate)
        val entries = listOf(approximate, entry)
        assertEquals(entries, CalculatorHistoryFormat.decode(CalculatorHistoryFormat.encode(entries)))
    }

    @Test
    fun rejectsOversizedFieldsAndUnrecognizedRecords() {
        assertFalse(CalculatorHistoryFormat.accepts(entry.copy(input = "x".repeat(8193))))
        assertThrows(IllegalArgumentException::class.java) {
            CalculatorHistoryFormat.encode(List(CalculatorHistoryFormat.MAX_ENTRIES + 1) { entry })
        }
        val encoded = CalculatorHistoryFormat.encode(listOf(entry))
        assertThrows(IllegalArgumentException::class.java) { CalculatorHistoryFormat.decode(encoded + 0) }
        encoded[3] = 2
        assertThrows(IllegalArgumentException::class.java) { CalculatorHistoryFormat.decode(encoded) }
    }
}
