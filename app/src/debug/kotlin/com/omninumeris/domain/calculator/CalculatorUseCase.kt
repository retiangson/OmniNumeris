package com.omninumeris.domain.calculator

/**
 * Interface for calculator use case to enable dependency injection and testing
 */
interface CalculatorUseCase {
    /**
     * Calculate a mathematical expression
     */
    fun calculate(expression: String): CalculationResult
}

/**
 * Implementation of the calculator use case using the domain's static Calculator
 */
class DefaultCalculatorUseCase : CalculatorUseCase {
    override fun calculate(expression: String): CalculationResult {
        return Calculator.calculate(Calculation.Standard(expression))
    }
}
