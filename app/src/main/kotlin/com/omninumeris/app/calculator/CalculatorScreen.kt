package com.omninumeris.app.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.domain.calculator.AngleUnit
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.MeasurementUnit
import com.omninumeris.domain.calculator.Radix
import com.omninumeris.domain.calculator.ScientificOperation
import com.omninumeris.domain.calculator.Statistic

@Composable
internal fun CalculatorContent(
    state: CalculatorState,
    onMode: (CalculatorMode) -> Unit,
    onEdit: ((CalculatorDraft) -> CalculatorDraft) -> Unit,
    onCalculate: () -> Unit,
    onRemovePlot: (Int) -> Unit,
    onTransformPlot: (Double, Double, Double, Double, Double) -> Unit,
    onResetPlot: () -> Unit,
    onAnalyzePlot: () -> Unit,
    onCancelGraphSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Choice(R.string.calculator_mode, state.mode, CalculatorMode.entries, { stringResource(it.label) }, onMode)
        ModeFields(state.mode, state.draft, onEdit)
        Button(
            onClick = onCalculate,
            enabled = !state.calculating &&
                (state.mode != CalculatorMode.Graphing || state.sampledViewport == state.viewport),
        ) {
            Text(stringResource(if (state.mode == CalculatorMode.Graphing) R.string.plot_run else R.string.calculator_run))
        }
        if (state.calculating) {
            Text(
                stringResource(R.string.calculator_calculating),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        state.result?.let { ResultText(it, state.display) }
        state.memoryError?.let { Text(it.detail, color = MaterialTheme.colorScheme.error) }
        state.historyWriteProblem?.let {
            Text(stringResource(it.message), color = MaterialTheme.colorScheme.error)
        }
        if (state.mode == CalculatorMode.Graphing && state.plots.isNotEmpty()) {
            CalculatorPlots(state, onRemovePlot, onTransformPlot, onResetPlot, onAnalyzePlot, onCancelGraphSearch)
        }
    }
}

@Composable
private fun ModeFields(mode: CalculatorMode, draft: CalculatorDraft, edit: ((CalculatorDraft) -> CalculatorDraft) -> Unit) {
    val mainLabel = when (mode) {
        CalculatorMode.Standard, CalculatorMode.Graphing -> R.string.math_expression
        CalculatorMode.Scientific -> R.string.calculator_operand
        CalculatorMode.Programmer -> R.string.calculator_integer
        CalculatorMode.Financial -> R.string.calculator_principal
        CalculatorMode.Statistics -> R.string.calculator_values
        CalculatorMode.Matrices -> R.string.calculator_left_matrix
        CalculatorMode.Units -> R.string.calculator_value
    }
    Field(mainLabel, draft.main, multiline = mode == CalculatorMode.Matrices) { value -> edit { it.copy(main = value) } }
    when (mode) {
        CalculatorMode.Standard, CalculatorMode.Graphing -> Unit
        CalculatorMode.Scientific -> {
            Choice(R.string.calculator_operation, draft.scientific, ScientificOperation.entries, {
                when (it) {
                    ScientificOperation.SquareRoot -> stringResource(R.string.math_root)
                    ScientificOperation.NaturalLog -> stringResource(R.string.calculator_natural_log)
                    else -> it.name
                }
            }) { value -> edit { it.copy(scientific = value) } }
            if (draft.scientific in angleOperations) {
                Choice(R.string.calculator_angle, draft.angle, AngleUnit.entries, { it.name }) { value ->
                    edit { it.copy(angle = value) }
                }
            }
        }
        CalculatorMode.Programmer -> {
            Choice(R.string.calculator_source_base, draft.sourceRadix, Radix.entries, { it.name }) { value ->
                edit { it.copy(sourceRadix = value) }
            }
            Choice(R.string.calculator_target_base, draft.targetRadix, Radix.entries, { it.name }) { value ->
                edit { it.copy(targetRadix = value) }
            }
        }
        CalculatorMode.Financial -> {
            Field(R.string.calculator_period_rate, draft.second) { value -> edit { it.copy(second = value) } }
            Field(R.string.calculator_periods, draft.third) { value -> edit { it.copy(third = value) } }
            Text(stringResource(R.string.calculator_compound_help), style = MaterialTheme.typography.bodySmall)
        }
        CalculatorMode.Statistics -> {
            Choice(R.string.calculator_statistic, draft.statistic, Statistic.entries, { it.name }) { value ->
                edit { it.copy(statistic = value) }
            }
        }
        CalculatorMode.Matrices -> {
            Field(R.string.calculator_right_matrix, draft.second, multiline = true) { value -> edit { it.copy(second = value) } }
            Text(stringResource(R.string.calculator_matrix_help), style = MaterialTheme.typography.bodySmall)
        }
        CalculatorMode.Units -> {
            Choice(R.string.calculator_source_unit, draft.sourceUnit, MeasurementUnit.entries, { it.name }) { value ->
                edit { it.copy(sourceUnit = value) }
            }
            Choice(R.string.calculator_target_unit, draft.targetUnit, MeasurementUnit.entries, { it.name }) { value ->
                edit { it.copy(targetUnit = value) }
            }
        }
    }
}

@Composable
private fun Field(label: Int, text: String, multiline: Boolean = false, edit: (String) -> Unit) {
    OutlinedTextField(text, edit, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, singleLine = !multiline, maxLines = if (multiline) 6 else 1)
}

@Composable
private fun <T> Choice(label: Int, selected: T, items: List<T>, title: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) {
            Text(stringResource(R.string.calculator_choice, stringResource(label), title(selected)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (item in items) {
                DropdownMenuItem(text = { Text(title(item)) }, onClick = {
                    open = false
                    onSelect(item)
                })
            }
        }
    }
}

@Composable
private fun ResultText(result: CalculationResult, settings: CalculatorDisplaySettings) {
    val value = remember(result, settings) { result.display(settings) }
    val text = when {
        value != null -> if (value.approximate) stringResource(R.string.calculator_approximate, value.text) else value.text
        result is CalculationResult.Rejected -> result.error.detail
        else -> stringResource(R.string.plot_ready)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (result is CalculationResult.Rejected) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

internal val CalculatorMode.label: Int
    get() = when (this) {
        CalculatorMode.Standard -> R.string.calculator_standard
        CalculatorMode.Scientific -> R.string.calculator_scientific
        CalculatorMode.Programmer -> R.string.calculator_programmer
        CalculatorMode.Financial -> R.string.calculator_financial
        CalculatorMode.Statistics -> R.string.calculator_statistics
        CalculatorMode.Matrices -> R.string.calculator_matrices
        CalculatorMode.Graphing -> R.string.calculator_graphing
        CalculatorMode.Units -> R.string.calculator_units
    }

private val angleOperations = setOf(ScientificOperation.Sin, ScientificOperation.Cos, ScientificOperation.Tan)
