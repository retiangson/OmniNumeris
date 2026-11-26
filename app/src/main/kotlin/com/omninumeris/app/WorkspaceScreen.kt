package com.omninumeris.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkspaceScreen(
    status: WorkspaceStatus,
    onRetry: () -> Unit,
    onOpenMath: () -> Unit,
    onOpenCanvas: () -> Unit,
    onOpenNotebooks: () -> Unit,
    onOpenCalculator: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val contentModifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)
            when (WorkspaceLayout.forWidth(maxWidth.value)) {
                WorkspaceLayout.Stacked -> Column(
                    modifier = contentModifier.widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    WorkspaceTitle(onOpenMath, onOpenCanvas, onOpenNotebooks, onOpenCalculator)
                    BuildStatus(status, onRetry)
                }

                WorkspaceLayout.Split -> Row(
                    modifier = contentModifier.widthIn(max = 960.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) { WorkspaceTitle(onOpenMath, onOpenCanvas, onOpenNotebooks, onOpenCalculator) }
                    Box(modifier = Modifier.weight(1f)) { BuildStatus(status, onRetry) }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTitle(onOpenMath: () -> Unit, onOpenCanvas: () -> Unit, onOpenNotebooks: () -> Unit, onOpenCalculator: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.workspace_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onOpenMath) { Text(stringResource(R.string.math_editor_title)) }
        TextButton(onClick = onOpenCalculator) { Text(stringResource(R.string.calculator_title)) }
        TextButton(onClick = onOpenNotebooks) { Text(stringResource(R.string.notebooks_title)) }
        WorkspacePrototypeActions(onOpenCanvas)
    }
}

@Composable
private fun BuildStatus(status: WorkspaceStatus, onRetry: () -> Unit) {
    val statusMessage = when (status) {
        WorkspaceStatus.Checking -> R.string.checking_workspace_storage
        WorkspaceStatus.Ready -> R.string.build_status
        WorkspaceStatus.StorageUnavailable -> R.string.workspace_storage_unavailable
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(statusMessage),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status == WorkspaceStatus.StorageUnavailable) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_workspace_storage)) }
            }
        }
    }
}
