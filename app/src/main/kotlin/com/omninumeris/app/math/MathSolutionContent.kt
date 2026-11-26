package com.omninumeris.app.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.application.MathSolutionOutcome
import com.omninumeris.domain.math.ExactMathExplanation
import com.omninumeris.domain.math.ExactMathHint
import com.omninumeris.domain.math.ExactMathResult
import com.omninumeris.domain.math.LinearEquality
import com.omninumeris.domain.math.LinearForm
import com.omninumeris.domain.math.LinearRule
import com.omninumeris.domain.math.LinearZeroCrossing
import com.omninumeris.domain.math.MathError
import com.omninumeris.domain.math.MathErrorCode
import com.omninumeris.domain.math.NumericRule
import com.omninumeris.domain.math.NumericStep

@Composable
internal fun MathSolutionContent(state: MathSolutionReview) {
    when (state) {
        MathSolutionReview.Idle -> Unit
        MathSolutionReview.Working -> SolutionStatus(stringResource(R.string.math_solver_working))
        is MathSolutionReview.Hint -> when (val hint = state.hint) {
            is ExactMathHint.Unavailable -> SolverError(hint.error)
            is ExactMathHint.Guidance -> SolutionStatus(stringResource(when (hint) {
                ExactMathHint.Guidance.InnermostOperation -> R.string.math_solver_hint_innermost
                ExactMathHint.Guidance.CollectVariables -> R.string.math_solver_hint_collect
                ExactMathHint.Guidance.IsolateVariable -> R.string.math_solver_hint_isolate
                ExactMathHint.Guidance.CompareSides -> R.string.math_solver_hint_compare
            }))
        }
        is MathSolutionReview.Explanation -> when (val outcome = state.outcome) {
            is MathSolutionOutcome.Verified -> {
                SolutionStatus(stringResource(R.string.math_verification_verified))
                ExplainVerified(outcome.proof)
            }
            is MathSolutionOutcome.Unverified -> UnverifiedExplanation(outcome.explanation)
            is MathSolutionOutcome.Unsupported -> SolverError(outcome.error)
            is MathSolutionOutcome.Error -> SolverError(outcome.error)
            MathSolutionOutcome.VerificationFailed -> SolutionStatus(stringResource(R.string.math_verification_failed))
            MathSolutionOutcome.LocalExplanationUnavailable ->
                SolutionStatus(stringResource(R.string.math_solver_local_explanation_unavailable))
        }
    }
}

@Composable
private fun UnverifiedExplanation(explanation: String) {
    val label = stringResource(R.string.math_verification_unverified)
    val announcement = stringResource(R.string.math_verification_unverified_announcement, label, explanation)
    Surface(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = announcement
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(explanation)
        }
    }
}

@Composable
private fun ExplainVerified(proof: ExactMathExplanation) {
    val alternative = proof.linearAlternative
    val linear = proof.result as? ExactMathResult.VerifiedLinearSolution
    if (alternative == null || linear == null) {
        Explain(proof)
        Text(stringResource(R.string.math_solver_no_alternative), style = MaterialTheme.typography.bodySmall)
        return
    }
    var showAlternative by remember(proof) { mutableStateOf(false) }
    TextButton(onClick = { showAlternative = !showAlternative }) {
        Text(stringResource(if (showAlternative) R.string.math_solver_show_balancing else R.string.math_solver_show_alternative))
    }
    if (showAlternative) ExplainZeroCrossing(alternative, linear) else Explain(proof)
}

@Composable
private fun ExplainZeroCrossing(alternative: LinearZeroCrossing, solution: ExactMathResult.VerifiedLinearSolution) {
    SolutionStatus(stringResource(R.string.math_solver_linear_solution, alternative.value.toString()))
    Text(stringResource(R.string.math_solver_zero_crossing), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.math_solver_difference_samples,
        alternative.differenceAtZero.toString(), alternative.differenceAtOne.toString()))
    Text(stringResource(R.string.math_solver_difference_slope, alternative.slope.toString()))
    Text(stringResource(R.string.math_solver_difference_root, alternative.value.toString()))
    Text(stringResource(R.string.math_solver_substitution,
        solution.substitution.left.toString(), solution.substitution.right.toString()))
}

@Composable
private fun Explain(explanation: ExactMathExplanation) {
    when (val result = explanation.result) {
        is ExactMathResult.Value -> {
            SolutionStatus(stringResource(R.string.math_solver_exact_value, result.value.toString()))
            for (step in explanation.numericSteps) Text(step.text())
        }
        is ExactMathResult.VerifiedLinearSolution -> {
            SolutionStatus(stringResource(R.string.math_solver_linear_solution, result.value.toString()))
            for (step in result.steps.filter { it.before != it.after }) {
                val rule = when (step.rule) {
                    LinearRule.CollectVariableTerms -> R.string.math_solver_collect_variables
                    LinearRule.CollectConstantTerms -> R.string.math_solver_collect_constants
                    LinearRule.DivideByCoefficient -> R.string.math_solver_divide_coefficient
                }
                Text(stringResource(rule), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.math_solver_transformation, step.before.text(), step.after.text()))
            }
            Text(stringResource(
                R.string.math_solver_substitution,
                result.substitution.left.toString(),
                result.substitution.right.toString(),
            ))
        }
        ExactMathResult.Identity -> SolutionStatus(stringResource(R.string.math_solver_identity))
        ExactMathResult.Contradiction -> SolutionStatus(stringResource(R.string.math_solver_contradiction))
        is ExactMathResult.Rejected -> error("A rejected result cannot carry a verified proof")
    }
}

@Composable
private fun SolverError(error: MathError) {
    SolutionStatus(stringResource(
        if (error.code == MathErrorCode.Unsupported) R.string.math_solver_unsupported else R.string.math_solver_error,
        error.detail,
    ))
}

@Composable
private fun SolutionStatus(message: String) {
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
}

private fun LinearEquality.text() = "${left.text()} = ${right.text()}"

private fun LinearForm.text(): String {
    if (coefficient.isZero) return constant.toString()
    val term = when (val number = coefficient.toString()) {
        "1" -> "x"
        "-1" -> "−x"
        else -> "$number·x"
    }
    if (constant.isZero) return term
    val value = constant.toString()
    return if (value.startsWith('-')) "$term − ${value.drop(1)}" else "$term + $value"
}

private fun NumericStep.text(): String {
    val operands = operands.map { it.toString() }
    val operation = when (rule) {
        NumericRule.Negate -> "−(${operands[0]})"
        NumericRule.SquareRoot -> "√(${operands[0]})"
        else -> {
            val operator = when (rule) {
                NumericRule.Add -> "+"
                NumericRule.Subtract -> "−"
                NumericRule.Multiply -> "×"
                NumericRule.Divide -> "÷"
                NumericRule.Power -> "^"
                NumericRule.Negate, NumericRule.SquareRoot -> error("Expected a binary operation")
            }
            "(${operands[0]}) $operator (${operands[1]})"
        }
    }
    return "$operation = $value"
}
