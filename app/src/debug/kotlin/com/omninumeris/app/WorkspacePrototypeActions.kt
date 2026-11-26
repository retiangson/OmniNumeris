package com.omninumeris.app

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.io.File

@Composable
internal fun WorkspacePrototypeActions(onOpenCanvas: () -> Unit) {
    TextButton(onClick = onOpenCanvas) {
        Text(stringResource(R.string.canvas_prototype))
    }
}

@Composable
internal fun WorkspaceCanvas(onBack: () -> Unit, onOpenCalculator: () -> Unit) {
    val context = LocalContext.current
    val directory = remember(context) { File(context.noBackupFilesDir, "canvas-prototype") }
    CanvasPrototypeScreen(directory, onBack, onOpenCalculator)
}
