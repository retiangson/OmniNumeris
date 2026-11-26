package com.omninumeris.domain.math

sealed interface MathExpression {
    data class Number(val text: String) : MathExpression
    data object Variable : MathExpression
    data class Negate(val expression: MathExpression) : MathExpression
    data class SquareRoot(val expression: MathExpression) : MathExpression
    data class Binary(val operator: MathOperator, val left: MathExpression, val right: MathExpression) : MathExpression
}

enum class MathOperator { Add, Subtract, Multiply, Divide, Power }

sealed interface MathInput {
    data class Expression(val root: MathExpression) : MathInput
    data class Equation(val left: MathExpression, val right: MathExpression) : MathInput
}

enum class MathErrorCode { Syntax, Unsupported, DivisionByZero, RealDomain, ComplexityLimit, VerificationFailed }

data class MathError(val code: MathErrorCode, val detail: String, val position: Int? = null)

sealed interface MathParseResult {
    data class Parsed(val input: MathInput) : MathParseResult
    data class Rejected(val error: MathError) : MathParseResult
}

internal class MathRejection(val error: MathError) : RuntimeException(error.detail)
