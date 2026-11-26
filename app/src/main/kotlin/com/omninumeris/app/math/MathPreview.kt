package com.omninumeris.app.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omninumeris.app.R
import com.omninumeris.domain.math.MathExpression
import com.omninumeris.domain.math.MathInput
import com.omninumeris.domain.math.MathOperator

internal fun supportsStructuredPreview(input: MathInput): Boolean = when (input) {
    is MathInput.Expression -> withinPreviewDepth(input.root, 0)
    is MathInput.Equation -> withinPreviewDepth(input.left, 0) && withinPreviewDepth(input.right, 0)
}

private fun withinPreviewDepth(expression: MathExpression, depth: Int): Boolean {
    if (depth > 24) return false
    return when (expression) {
        is MathExpression.Number, MathExpression.Variable -> true
        is MathExpression.Negate -> withinPreviewDepth(expression.expression, depth + 1)
        is MathExpression.SquareRoot -> withinPreviewDepth(expression.expression, depth + 1)
        is MathExpression.Binary -> withinPreviewDepth(expression.left, depth + 1) &&
            withinPreviewDepth(expression.right, depth + 1)
    }
}

@Composable
internal fun MathPreview(input: MathInput) {
    val style = MaterialTheme.typography.titleMedium
    val description = when (input) {
        is MathInput.Expression -> spokenExpression(input.root)
        is MathInput.Equation -> stringResource(R.string.math_speak_equals,
            spokenExpression(input.left), spokenExpression(input.right))
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (input) {
            is MathInput.Expression -> ExpressionPreview(input.root, style)
            is MathInput.Equation -> {
                ExpressionPreview(input.left, style)
                Text("=", style = style)
                ExpressionPreview(input.right, style)
            }
        }
    }
}

@Composable
private fun ExpressionPreview(expression: MathExpression, style: TextStyle, group: Boolean = false) {
    if (group) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("(", style = style)
            ExpressionPreview(expression, style)
            Text(")", style = style)
        }
        return
    }
    when (expression) {
        is MathExpression.Number -> Text(expression.text, style = style)
        MathExpression.Variable -> Text("x", style = style)
        is MathExpression.Negate -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", style = style)
            ExpressionPreview(expression.expression, style, expression.expression.priority < expression.priority)
        }
        is MathExpression.SquareRoot -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("√", style = style)
            Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider()
                ExpressionPreview(expression.expression, style)
            }
        }
        is MathExpression.Binary -> BinaryPreview(expression, style)
    }
}

@Composable
private fun BinaryPreview(expression: MathExpression.Binary, style: TextStyle) {
    val smaller = style.copy(fontSize = (style.fontSize.value * 0.8f).coerceAtLeast(11f).sp)
    when (expression.operator) {
        MathOperator.Divide -> Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExpressionPreview(expression.left, smaller)
            HorizontalDivider()
            ExpressionPreview(expression.right, smaller)
        }
        MathOperator.Power -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp)) {
                ExpressionPreview(expression.left, style, expression.left.priority <= expression.priority)
            }
            ExpressionPreview(expression.right, smaller)
        }
        else -> Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressionPreview(expression.left, style, expression.left.priority < expression.priority)
            Text(operatorSymbol(expression.operator), style = style)
            ExpressionPreview(expression.right, style, expression.right.priority <= expression.priority)
        }
    }
}

@Composable
private fun spokenExpression(expression: MathExpression): String = when (expression) {
    is MathExpression.Number -> expression.text
    MathExpression.Variable -> "x"
    is MathExpression.Negate -> stringResource(R.string.math_speak_negative, spokenExpression(expression.expression))
    is MathExpression.SquareRoot -> stringResource(R.string.math_speak_root, spokenExpression(expression.expression))
    is MathExpression.Binary -> {
        val template = when (expression.operator) {
            MathOperator.Add -> R.string.math_speak_add
            MathOperator.Subtract -> R.string.math_speak_subtract
            MathOperator.Multiply -> R.string.math_speak_multiply
            MathOperator.Divide -> R.string.math_speak_divide
            MathOperator.Power -> R.string.math_speak_power
        }
        stringResource(template, spokenExpression(expression.left), spokenExpression(expression.right))
    }
}

private val MathExpression.priority: Int
    get() = when (this) {
        is MathExpression.Binary -> when (operator) {
            MathOperator.Add, MathOperator.Subtract -> 1
            MathOperator.Multiply -> 2
            MathOperator.Divide -> 5
            MathOperator.Power -> 4
        }
        is MathExpression.Negate -> 3
        else -> 5
    }

private fun operatorSymbol(operator: MathOperator): String = when (operator) {
    MathOperator.Add -> "+"
    MathOperator.Subtract -> "−"
    MathOperator.Multiply -> "×"
    MathOperator.Divide -> "÷"
    MathOperator.Power -> "^"
}
