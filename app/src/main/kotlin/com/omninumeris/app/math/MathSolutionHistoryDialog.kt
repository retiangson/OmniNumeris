package com.omninumeris.app.math

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.application.TypedSolutionKind
import java.util.UUID

@Composable
internal fun MathSolutionHistoryDialog(
    state: MathHistoryPanel,
    reopen: (UUID) -> Unit,
    close: () -> Unit,
    onOpenCalculator: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.math_history_title)) },
        text = {
            when (state) {
                MathHistoryPanel.Closed -> Unit
                MathHistoryPanel.Loading -> Text(stringResource(R.string.math_history_loading))
                MathHistoryPanel.Unavailable -> Text(stringResource(R.string.math_history_unavailable))
                is MathHistoryPanel.Reopening -> Text(stringResource(R.string.math_history_reopening))
                is MathHistoryPanel.Ready -> Column {
                    if (state.failedToOpen) Text(stringResource(R.string.math_history_reopen_failed))
                    if (state.records.isEmpty()) Text(stringResource(R.string.math_history_empty))
                    else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(state.records, key = { it.id.toString() }) { record ->
                            TextButton(onClick = { reopen(record.id) }) {
                                Column {
                                    Text(record.confirmed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(when (record.answer.kind) {
                                        TypedSolutionKind.Linear -> stringResource(R.string.math_solver_linear_solution, record.answer.value)
                                        TypedSolutionKind.Value -> stringResource(R.string.math_solver_exact_value, record.answer.value)
                                        TypedSolutionKind.Identity -> stringResource(R.string.math_solver_identity)
                                        TypedSolutionKind.Contradiction -> stringResource(R.string.math_solver_contradiction)
                                    }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.plot_features_close)) } },
        dismissButton = if (onOpenCalculator == null) null else {
            { TextButton(onClick = onOpenCalculator) { Text(stringResource(R.string.calculator_title)) } }
        },
    )
}
