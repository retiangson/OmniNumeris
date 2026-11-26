package com.omninumeris.app.math

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.domain.math.MathParseResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MathEditorScreen(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onEdit: ((TextFieldValue) -> TextFieldValue) -> Unit,
    previewTyped: (String) -> MathParseResult,
    availableSources: Set<MathInputSource>,
    onReview: () -> Unit,
    onHistory: () -> Unit,
    onOpenCalculator: () -> Unit,
    onBack: () -> Unit,
) {
    val parsed = remember(value.text) { value.text.takeIf { it.isNotBlank() }?.let(previewTyped) }
    val error = (parsed as? MathParseResult.Rejected)?.error
    var showSources by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().imePadding().fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.math_editor_back)) }
                    Text(
                        stringResource(R.string.math_editor_title),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { showSources = true }) {
                        Text(stringResource(R.string.math_input_sources))
                    }
                    val calculator = stringResource(R.string.calculator_title)
                    IconButton(
                        onClick = onOpenCalculator,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = calculator },
                    ) {
                        Text("▦", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    label = { Text(stringResource(R.string.math_expression)) },
                    placeholder = { Text(stringResource(R.string.math_expression_hint)) },
                    trailingIcon = {
                        if (value.text.isNotBlank()) {
                            TextButton(onClick = onReview) { Text(stringResource(R.string.math_source_open_review)) }
                        }
                    },
                    isError = error != null,
                    supportingText = error?.let { problem ->
                        {
                            val position = problem.position
                            Text(if (position == null) problem.detail else
                                stringResource(R.string.math_expression_error, position + 1, problem.detail))
                        }
                    },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                val showPreview = parsed is MathParseResult.Parsed && supportsStructuredPreview(parsed.input)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (showPreview) {
                        Text(stringResource(R.string.math_preview), Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onHistory) { Text(stringResource(R.string.math_history_title)) }
                }
                if (parsed is MathParseResult.Parsed && showPreview) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).heightIn(min = 54.dp).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MathPreview(parsed.input)
                        }
                    }
                } else if (parsed is MathParseResult.Parsed) {
                    Text(stringResource(R.string.math_preview_limit))
                }
                MathTemplateControls(onEdit)
                Column(Modifier.fillMaxWidth()) {
                    for (keys in arithmeticKeys.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (key in keys) {
                                EditorKey(
                                    key,
                                    keyDescriptions[key]?.let { stringResource(it) } ?: key,
                                    Modifier.weight(1f),
                                ) { onEdit { it.insertMath(key) } }
                            }
                            repeat(4 - keys.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (showSources) {
        MathInputSourcesDialog(availableSources) { showSources = false }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MathTemplateControls(onEdit: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((template, label) in templateKeys) {
            OutlinedButton(onClick = { onEdit { it.insertTemplate(template) } }) {
                Text(stringResource(label))
            }
        }
        EditorKey("←", stringResource(R.string.math_cursor_left)) { onEdit { it.moveCursor(false) } }
        EditorKey("→", stringResource(R.string.math_cursor_right)) { onEdit { it.moveCursor(true) } }
        EditorKey("⌫", stringResource(R.string.math_delete)) { onEdit { it.deletePrevious() } }
    }
}

@Composable
private fun EditorKey(label: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.semantics { if (description != label) contentDescription = description },
    ) {
        Text(label)
    }
}

private val templateKeys = listOf(
    MathTemplate.Fraction to R.string.math_fraction,
    MathTemplate.Power to R.string.math_power,
    MathTemplate.Root to R.string.math_root,
)

private val arithmeticKeys = listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ".", "=", "+", "x", "(", ")")

private val keyDescriptions = mapOf(
    "÷" to R.string.math_divide,
    "×" to R.string.math_multiply,
    "−" to R.string.math_minus,
    "." to R.string.math_decimal,
    "=" to R.string.math_equals,
    "+" to R.string.math_plus,
    "(" to R.string.math_open_parenthesis,
    ")" to R.string.math_close_parenthesis,
)
