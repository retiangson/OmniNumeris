package com.omninumeris.domain.math

sealed interface ExactMathResult {
    data class Value(val value: ExactRational) : ExactMathResult
    data class VerifiedLinearSolution(
        val value: ExactRational,
        val steps: List<LinearStep>,
        val substitution: LinearSubstitution,
    ) : ExactMathResult
    data object Identity : ExactMathResult
    data object Contradiction : ExactMathResult
    data class Rejected(val error: MathError) : ExactMathResult
}

data class LinearForm(val coefficient: ExactRational, val constant: ExactRational)

data class LinearEquality(val left: LinearForm, val right: LinearForm)

enum class LinearRule { CollectVariableTerms, CollectConstantTerms, DivideByCoefficient }

data class LinearStep(val rule: LinearRule, val before: LinearEquality, val after: LinearEquality)
data class LinearSubstitution(val left: ExactRational, val right: ExactRational)

data class LinearZeroCrossing(
    val differenceAtZero: ExactRational,
    val differenceAtOne: ExactRational,
    val slope: ExactRational,
    val value: ExactRational,
)

enum class NumericRule { Negate, SquareRoot, Add, Subtract, Multiply, Divide, Power }
data class NumericStep(val rule: NumericRule, val operands: List<ExactRational>, val value: ExactRational)
data class ExactMathExplanation(
    val result: ExactMathResult,
    val numericSteps: List<NumericStep> = emptyList(),
    val linearAlternative: LinearZeroCrossing? = null,
)

sealed interface ExactMathHint {
    enum class Guidance : ExactMathHint { InnermostOperation, CollectVariables, IsolateVariable, CompareSides }
    data class Unavailable(val error: MathError) : ExactMathHint
}

object ExactMath {
    internal fun evaluateNumeric(expression: MathExpression): ExactRational = evaluate(expression, null)

    internal fun exactValueAt(expression: MathExpression, x: ExactRational): ExactRational? = try {
        evaluate(expression, x)
    } catch (_: MathRejection) {
        null
    }

    fun analyze(text: String): ExactMathResult = when (val parsed = MathParser.parse(text)) {
        is MathParseResult.Rejected -> ExactMathResult.Rejected(parsed.error)
        is MathParseResult.Parsed -> analyze(parsed.input)
    }

    fun explain(input: MathInput): ExactMathExplanation = when (input) {
        is MathInput.Equation -> {
            val result = analyze(input)
            val alternative = if (result is ExactMathResult.VerifiedLinearSolution) zeroCrossing(input, result.value) else null
            ExactMathExplanation(result, linearAlternative = alternative)
        }
        is MathInput.Expression -> try {
            val steps = mutableListOf<NumericStep>()
            val value = evaluate(input.root, null, steps)
            ExactMathExplanation(ExactMathResult.Value(value), steps.toList())
        } catch (rejected: MathRejection) {
            ExactMathExplanation(ExactMathResult.Rejected(rejected.error))
        }
    }

    fun hint(input: MathInput): ExactMathHint = try {
        when (input) {
            is MathInput.Expression -> {
                evaluateNumeric(input.root)
                ExactMathHint.Guidance.InnermostOperation
            }
            is MathInput.Equation -> {
                val left = normalize(input.left)
                val right = normalize(input.right)
                when {
                    left.coefficient == right.coefficient -> ExactMathHint.Guidance.CompareSides
                    !left.coefficient.isZero && right.coefficient.isZero -> ExactMathHint.Guidance.IsolateVariable
                    else -> ExactMathHint.Guidance.CollectVariables
                }
            }
        }
    } catch (rejected: MathRejection) {
        ExactMathHint.Unavailable(rejected.error)
    }

    internal fun analyze(input: MathInput): ExactMathResult = try {
        when (input) {
            is MathInput.Expression -> ExactMathResult.Value(evaluate(input.root, null))
            is MathInput.Equation -> solve(input)
        }
    } catch (rejected: MathRejection) {
        ExactMathResult.Rejected(rejected.error)
    }

    private fun solve(equation: MathInput.Equation): ExactMathResult {
        val normalized = LinearEquality(normalize(equation.left), normalize(equation.right))
        val difference = normalized.left - normalized.right
        if (difference.coefficient.isZero) {
            return if (difference.constant.isZero) ExactMathResult.Identity else ExactMathResult.Contradiction
        }

        val variableTerm = LinearForm(normalized.right.coefficient, ExactRational.ZERO)
        val collectedVariables = LinearEquality(normalized.left - variableTerm, normalized.right - variableTerm)
        val constantTerm = constant(collectedVariables.left.constant)
        val collectedConstants = LinearEquality(
            collectedVariables.left - constantTerm, collectedVariables.right - constantTerm,
        )
        val divisor = collectedConstants.left.coefficient
        val divided = LinearEquality(
            collectedConstants.left.divideBy(divisor),
            collectedConstants.right.divideBy(divisor),
        )
        val steps = listOf(
            LinearStep(LinearRule.CollectVariableTerms, normalized, collectedVariables),
            LinearStep(LinearRule.CollectConstantTerms, collectedVariables, collectedConstants),
            LinearStep(LinearRule.DivideByCoefficient, collectedConstants, divided),
        )
        val value = divided.right.constant
        val substitution = LinearSubstitution(evaluate(equation.left, value), evaluate(equation.right, value))
        if (!preservesDifference(steps[0]) || !preservesDifference(steps[1]) ||
            !validDivision(steps[2], divisor) || divided.left != LinearForm(ExactRational.ONE, ExactRational.ZERO) ||
            !divided.right.coefficient.isZero || substitution.left != substitution.right) {
            reject(MathErrorCode.VerificationFailed, "The proposed solution could not be verified")
        }
        return ExactMathResult.VerifiedLinearSolution(value, steps, substitution)
    }

    /** The primary solve already established that the original difference is affine. */
    private fun zeroCrossing(equation: MathInput.Equation, expected: ExactRational): LinearZeroCrossing? = try {
        fun differenceAt(value: ExactRational): ExactRational =
            evaluate(equation.left, value) - evaluate(equation.right, value)

        val atZero = differenceAt(ExactRational.ZERO)
        val atOne = differenceAt(ExactRational.ONE)
        val slope = atOne - atZero
        val root = if (slope.isZero) null else -atZero / slope
        if (root != null && root == expected && differenceAt(root).isZero) LinearZeroCrossing(atZero, atOne, slope, root) else null
    } catch (_: MathRejection) {
        null
    }

    private fun preservesDifference(step: LinearStep): Boolean =
        step.before.left - step.before.right == step.after.left - step.after.right

    private fun validDivision(step: LinearStep, divisor: ExactRational): Boolean =
        !divisor.isZero && (step.after.left - step.after.right).times(divisor) == step.before.left - step.before.right

    private fun normalize(expression: MathExpression): LinearForm = when (expression) {
        is MathExpression.Number -> constant(ExactRational.literal(expression.text))
        MathExpression.Variable -> LinearForm(ExactRational.ONE, ExactRational.ZERO)
        is MathExpression.Negate -> normalize(expression.expression).times(-ExactRational.ONE)
        is MathExpression.SquareRoot -> {
            val operand = normalize(expression.expression)
            if (!operand.coefficient.isZero) {
                reject(MathErrorCode.Unsupported, "Square roots containing x require domain-aware nonlinear solving")
            }
            constant(operand.constant.squareRoot())
        }

        is MathExpression.Binary -> {
            val a = normalize(expression.left)
            val b = normalize(expression.right)
            when (expression.operator) {
                MathOperator.Add -> a + b
                MathOperator.Subtract -> a - b
                MathOperator.Multiply -> when {
                    a.coefficient.isZero -> b.times(a.constant)
                    b.coefficient.isZero -> a.times(b.constant)
                    else -> reject(MathErrorCode.Unsupported, "Nonlinear products are outside this solver")
                }
                MathOperator.Divide -> {
                    if (!b.coefficient.isZero) {
                        reject(MathErrorCode.Unsupported, "A variable denominator requires a domain restriction")
                    }
                    a.divideBy(b.constant)
                }
                MathOperator.Power -> when {
                    !b.coefficient.isZero -> reject(MathErrorCode.Unsupported, "Variable exponents are outside this solver")
                    a.coefficient.isZero -> constant(a.constant.raisedTo(b.constant))
                    b.constant == ExactRational.ONE -> a
                    else -> reject(MathErrorCode.Unsupported, "This variable power requires nonlinear or domain-aware solving")
                }
            }
        }
    }

    private fun evaluate(
        expression: MathExpression,
        value: ExactRational?,
        steps: MutableList<NumericStep>? = null,
    ): ExactRational = when (expression) {
        is MathExpression.Number -> ExactRational.literal(expression.text)
        MathExpression.Variable -> value ?: reject(MathErrorCode.Unsupported, "Supply an equation to solve for x")
        is MathExpression.Negate -> {
            val operand = evaluate(expression.expression, value, steps)
            (-operand).also { steps?.add(NumericStep(NumericRule.Negate, listOf(operand), it)) }
        }
        is MathExpression.SquareRoot -> {
            val operand = evaluate(expression.expression, value, steps)
            operand.squareRoot().also { steps?.add(NumericStep(NumericRule.SquareRoot, listOf(operand), it)) }
        }
        is MathExpression.Binary -> {
            val a = evaluate(expression.left, value, steps)
            val b = evaluate(expression.right, value, steps)
            val result = when (expression.operator) {
                MathOperator.Add -> a + b
                MathOperator.Subtract -> a - b
                MathOperator.Multiply -> a * b
                MathOperator.Divide -> a / b
                MathOperator.Power -> a.raisedTo(b)
            }
            if (steps != null) {
                val rule = when (expression.operator) {
                    MathOperator.Add -> NumericRule.Add
                    MathOperator.Subtract -> NumericRule.Subtract
                    MathOperator.Multiply -> NumericRule.Multiply
                    MathOperator.Divide -> NumericRule.Divide
                    MathOperator.Power -> NumericRule.Power
                }
                steps.add(NumericStep(rule, listOf(a, b), result))
            }
            result
        }
    }

    private fun constant(value: ExactRational): LinearForm = LinearForm(ExactRational.ZERO, value)

    private operator fun LinearForm.plus(other: LinearForm): LinearForm =
        LinearForm(coefficient + other.coefficient, constant + other.constant)

    private operator fun LinearForm.minus(other: LinearForm): LinearForm =
        LinearForm(coefficient - other.coefficient, constant - other.constant)

    private fun LinearForm.times(factor: ExactRational): LinearForm =
        LinearForm(coefficient * factor, constant * factor)

    private fun LinearForm.divideBy(divisor: ExactRational): LinearForm =
        LinearForm(coefficient / divisor, constant / divisor)
}
