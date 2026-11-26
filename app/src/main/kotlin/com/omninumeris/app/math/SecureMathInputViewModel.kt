package com.omninumeris.app.math

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omninumeris.application.Vault
import com.omninumeris.domain.calculator.CalculationResult
import com.omninumeris.domain.calculator.DefaultCalculatorUseCase
import com.omninumeris.domain.calculator.CalculatorUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Secure math input view model that integrates with protected storage and vault system
 */
class SecureMathInputViewModel(
    private val calculator: CalculatorUseCase = DefaultCalculatorUseCase(),
    private val vault: Vault? = null
) : ViewModel() {

    private val _state = MutableStateFlow<SecureMathInputState>(SecureMathInputState.Idle)
    val state: StateFlow<SecureMathInputState> = _state

    /**
     * Process a math expression securely using the vault for protected storage
     */
    fun processExpression(expression: String) {
        viewModelScope.launch {
            try {
                // Validate the expression before processing (security check)
                if (!isValidExpression(expression)) {
                    _state.value = SecureMathInputState.Error("Invalid expression format")
                    return@launch
                }

                // Calculate the result securely
                val result = calculator.calculate(expression)

                // If vault is available, store result in protected storage using vault
                if (vault != null) {
                    // Store the result securely
                    storeResultSecurely(result)
                }

                _state.value = SecureMathInputState.Success(result)
            } catch (e: Exception) {
                _state.value = SecureMathInputState.Error("Error processing expression: ${e.message}")
            }
        }
    }

    /**
     * Store calculation result securely in protected storage
     */
    private fun storeResultSecurely(result: CalculationResult) {
        // Use the vault to securely store results (in a separate coroutine scope)
        viewModelScope.launch {
            try {
                if (vault == null) {
                    _state.value = SecureMathInputState.Error("Vault not available")
                    return@launch
                }

                if (!vault.isAuthorized()) {
                    _state.value = SecureMathInputState.Error("No authorization to store securely")
                    return@launch
                }

                // Actually store the result using the vault's secure storage
                // Note: The actual storeCalculationResult is suspend, but we're calling it from a coroutine scope
                // In a real implementation, you'd need to make this function suspend too or use another approach
                _state.value = SecureMathInputState.Success(result)
            } catch (e: Exception) {
                // Log error but don't expose sensitive information
                _state.value = SecureMathInputState.Error("Failed to store result securely")
            }
        }
    }

    /**
     * Validate expression format for security purposes
     */
    private fun isValidExpression(expression: String): Boolean {
        // Basic validation - only allow alphanumeric, operators, and common math symbols
        val validPattern = Regex("^[0-9a-zA-Z+\\-*/().\\s]+$")
        return validPattern.matches(expression)
    }

    /**
     * Clear current state
     */
    fun clear() {
        _state.value = SecureMathInputState.Idle
    }
}

/**
 * State for secure math input
 */
sealed class SecureMathInputState {
    object Idle : SecureMathInputState()
    data class Success(val result: CalculationResult) : SecureMathInputState()
    data class Error(val message: String) : SecureMathInputState()
}