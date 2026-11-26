package com.omninumeris.application

import com.omninumeris.domain.calculator.CalculationResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Vault interface for secure storage and access control
 */
interface Vault {
    /**
     * Check if the user is authorized to access protected content
     */
    fun isAuthorized(): Boolean

    /**
     * Store a calculation result securely in protected storage
     */
    suspend fun storeCalculationResult(result: CalculationResult)

    /**
     * Retrieve a calculation result from protected storage
     */
    suspend fun retrieveCalculationResult(id: String): CalculationResult?

    /**
     * Get the current vault state
     */
    val state: StateFlow<VaultState>
}

/**
 * Vault state for monitoring access and security status
 */
sealed class VaultState {
    object Uninitialized : VaultState()
    data class Authorized(val userId: String) : VaultState()
    object Unauthorized : VaultState()
    data class Error(val message: String) : VaultState()
}