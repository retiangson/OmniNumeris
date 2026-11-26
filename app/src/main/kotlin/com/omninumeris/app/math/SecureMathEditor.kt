package com.omninumeris.app.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omninumeris.app.R
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.CalculatorUseCase

/**
 * Secure math editor component that integrates with protected storage
 */
@Composable
fun SecureMathEditor(
    viewModel: SecureMathInputViewModel,
    onBack: () -> Unit,
    onOpenCalculator: () -> Unit,
    onInsertResult: ((CalculationResult) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Secure Math Editor",
                style = MaterialTheme.typography.headlineSmall
            )

            // Secure input field for math expressions
            var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text("Enter math expression") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false
                ),
                visualTransformation = VisualTransformation.None,
                singleLine = false
            )

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Process the math expression securely
                        viewModel.processExpression(textFieldValue.text)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Process Expression")
                }

                Button(
                    onClick = onOpenCalculator,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Calculator")
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Workspace")
                }
            }

            // Display results if available
            when (val currentState = state.value) {
                is SecureMathInputState.Success -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Result: ${currentState.result}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is SecureMathInputState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${currentState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    // Idle state - no result to display
                }
            }
        }
    }
}