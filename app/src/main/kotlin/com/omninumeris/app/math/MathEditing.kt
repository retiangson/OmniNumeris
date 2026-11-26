package com.omninumeris.app.math

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class MathTemplate { Fraction, Power, Root }

internal fun TextFieldValue.insertMath(text: String): TextFieldValue = replace(text, text.length)

internal fun TextFieldValue.insertTemplate(template: MathTemplate): TextFieldValue {
    val selected = text.substring(selection.min, selection.max)
    return when (template) {
        MathTemplate.Fraction -> replace("\\frac{$selected}{}", if (selected.isEmpty()) 6 else 8 + selected.length)
        MathTemplate.Power -> {
            val base = if (selected.isEmpty()) "" else "($selected)"
            val prefix = "$base^{"
            replace("$prefix}", prefix.length)
        }
        MathTemplate.Root -> replace("\\sqrt{$selected}", if (selected.isEmpty()) 6 else 7 + selected.length)
    }
}

internal fun TextFieldValue.deletePrevious(): TextFieldValue {
    if (!selection.collapsed) return replace("", 0)
    val cursor = selection.start
    if (cursor == 0) return this
    val previous = text.offsetByCodePoints(cursor, -1)
    return copy(selection = TextRange(previous, cursor), composition = null).replace("", 0)
}

internal fun TextFieldValue.moveCursor(right: Boolean): TextFieldValue {
    val cursor = when {
        !selection.collapsed -> if (right) selection.max else selection.min
        right && selection.end < text.length -> text.offsetByCodePoints(selection.end, 1)
        !right && selection.start > 0 -> text.offsetByCodePoints(selection.start, -1)
        else -> selection.start
    }
    return copy(selection = TextRange(cursor), composition = null)
}

private fun TextFieldValue.replace(replacement: String, relativeCursor: Int): TextFieldValue =
    TextFieldValue(
        text.replaceRange(selection.min, selection.max, replacement),
        selection = TextRange(selection.min + relativeCursor),
    )
