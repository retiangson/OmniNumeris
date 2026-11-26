package com.omninumeris.app.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.application.CalculatorHistoryEntry
import com.omninumeris.application.CalculatorHistoryResult
import com.omninumeris.application.CalculatorValueKind
import com.omninumeris.domain.calculator.CalculationResult

@Composable
internal fun CalculatorTools(state: CalculatorState, model: CalculatorViewModel, onInsertResult: ((CalculationResult) -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    var precision by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val result = state.result
    val value = remember(result) { result?.canonicalValue() }
    val exact = result is CalculationResult.Exact

    Box {
        TextButton(onClick = { menu = true }) { Text(stringResource(R.string.calculator_tools)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            @Composable
            fun Item(label: Int, enabled: Boolean = true, action: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    enabled = enabled,
                    onClick = {
                        menu = false
                        action()
                    },
                )
            }
            Item(if (state.display.decimal) R.string.calculator_use_exact else R.string.calculator_use_decimal) {
                model.setDecimalDisplay(!state.display.decimal)
            }
            Item(R.string.calculator_display_precision) { precision = true }
            Item(if (exact) R.string.calculator_copy_exact else R.string.calculator_copy_result, value != null) {
                val copied = checkNotNull(value)
                val text = if (copied.approximate) context.getString(R.string.calculator_approximate, copied.text) else copied.text
                val clip = ClipData.newPlainText(context.getString(R.string.calculator_title), text)
                clip.description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                Toast.makeText(context, R.string.calculator_copied_result, Toast.LENGTH_SHORT).show()
            }
            Item(R.string.calculator_history) { model.showHistory() }
            if (onInsertResult != null) {
                Item(R.string.notebook_insert_result, value != null) { result?.let(onInsertResult) }
            }
            HorizontalDivider()
            Item(R.string.calculator_memory_store, exact, model::storeMemory)
            Item(R.string.calculator_memory_add, exact) { model.adjustMemory(subtract = false) }
            Item(R.string.calculator_memory_subtract, exact) { model.adjustMemory(subtract = true) }
            Item(R.string.calculator_memory_recall, state.memory != null, model::recallMemory)
            Item(R.string.calculator_memory_clear, state.memory != null, model::clearMemory)
        }
    }

    if (precision) {
        AlertDialog(
            onDismissRequest = { precision = false },
            title = { Text(stringResource(R.string.calculator_display_precision)) },
            text = {
                Column {
                    for (choice in CalculatorPrecision.entries) {
                        TextButton(
                            onClick = {
                                model.setPrecision(choice)
                                precision = false
                            },
                            enabled = state.display.precision != choice,
                        ) {
                            Text(stringResource(R.string.calculator_significant_digits, choice.digits))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { precision = false }) { Text(stringResource(R.string.plot_features_close)) }
            },
        )
    }
    if (state.historyOpen) CalculatorHistoryDialog(state.history, model::dismissHistory)
}

@Composable
private fun CalculatorHistoryDialog(history: CalculatorHistoryResult?, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.calculator_history)) },
        text = {
            when (history) {
                null -> Text(stringResource(R.string.calculator_history_loading))
                is CalculatorHistoryResult.Available -> {
                    if (history.entries.isEmpty()) Text(stringResource(R.string.calculator_history_empty))
                    else LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history.entries, key = { it.id.toString() }) { HistoryRow(it) }
                    }
                }
                else -> Text(stringResource(history.message))
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.plot_features_close)) } },
    )
}

@Composable
private fun HistoryRow(entry: CalculatorHistoryEntry) {
    val mode = CalculatorMode.entries.firstOrNull { it.name == entry.mode }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(mode?.let { stringResource(it.label) } ?: entry.mode, style = MaterialTheme.typography.titleSmall)
        Text(entry.input, maxLines = 4, overflow = TextOverflow.Ellipsis)
        val result = if (entry.kind == CalculatorValueKind.Approximate) {
            stringResource(R.string.calculator_approximate, entry.result)
        } else entry.result
        Text(result, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
    }
}

internal val CalculatorHistoryResult.message: Int
    get() = if (this == CalculatorHistoryResult.EntryTooLarge) R.string.calculator_history_entry_limit
    else R.string.calculator_history_unavailable
