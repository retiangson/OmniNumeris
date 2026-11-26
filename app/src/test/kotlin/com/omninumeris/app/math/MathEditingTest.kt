package com.omninumeris.app.math

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MathEditingTest {
    @Test
    fun fractionTemplateLeavesCaretInTheFirstEmptyArgument() {
        val inserted = TextFieldValue("2+3", TextRange(2)).insertTemplate(MathTemplate.Fraction)
        assertEquals("2+\\frac{}{}3", inserted.text)
        assertEquals(TextRange(8), inserted.selection)
        assertEquals("2+\\frac{1}{}3", inserted.insertMath("1").text)
    }

    @Test
    fun templatesPreserveSelectedAndSurroundingText() {
        val selected = TextFieldValue("4+12-3", TextRange(2, 4))
        val fraction = selected.insertTemplate(MathTemplate.Fraction)
        assertEquals("4+\\frac{12}{}-3", fraction.text)
        assertEquals("4+\\frac{12}{5}-3", fraction.insertMath("5").text)
        assertEquals("4+\\sqrt{12}-3", selected.insertTemplate(MathTemplate.Root).text)
        val power = selected.insertTemplate(MathTemplate.Power)
        assertEquals("4+(12)^{2}-3", power.insertMath("2").text)
    }

    @Test
    fun cursorAndDeleteRespectSelectionAndUnicodeCharacters() {
        val input = TextFieldValue("2🙂+4", TextRange(3))
        assertEquals(TextRange(1), input.moveCursor(false).selection)
        assertEquals("2+4", input.deletePrevious().text)
        val selection = TextFieldValue("1+2+3", TextRange(3, 1))
        assertEquals(TextRange(1), selection.moveCursor(false).selection)
        assertEquals(TextRange(3), selection.moveCursor(true).selection)
        assertEquals("1+3", selection.deletePrevious().text)
    }
}
