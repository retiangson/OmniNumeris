package com.omninumeris.app.math

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omninumeris.app.R
import com.omninumeris.app.notebook.NotebookInkPreview
import com.omninumeris.application.MathInputProblem
import com.omninumeris.domain.math.MathInputSource
import com.omninumeris.application.MathSolutionOutcome
import java.util.UUID

@Composable
internal fun MathReviewDialogs(
    model: MathInputViewModel,
    onOpenCalculator: (() -> Unit)? = null,
    insertion: @Composable (MathInputReview.Editing) -> Unit = {},
    onVerifiedSolve: (MathInputReview.Editing) -> Unit = {},
    onConfirmedTyped: (String) -> Unit,
) {
    val review by model.review.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    if (history != MathHistoryPanel.Closed) {
        MathSolutionHistoryDialog(history, model::reopenSolution, model::closeSolutionHistory, onOpenCalculator)
    } else if (review != MathInputReview.Idle) {
        RecognitionReviewDialog(
            review = review,
            select = model::selectCandidate,
            edit = model::editCandidate,
            accept = model::reviewCandidate,
            confirm = {
                val candidate = model.confirmCandidate()?.candidate
                if (candidate != null && candidate.anchor == null) onConfirmedTyped(candidate.text)
            },
            dismiss = model::dismissReview,
            onHint = model::requestHint,
            onSolve = { model.solveConfirmed(onVerifiedSolve) },
            onLocalExplanation = model::requestLocalExplanation,
            onSave = model::saveSolution,
            onSourceHistory = model::showSourceSolutionHistory,
            onOpenCalculator = onOpenCalculator,
            insertion = insertion,
        )
    }
}

@Composable
internal fun MathInputSourcesDialog(available: Set<MathInputSource>, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.math_input_sources)) },
        text = {
            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (source in MathInputSource.entries) {
                    val label = stringResource(source.label)
                    Text(when {
                        source == MathInputSource.Typed -> stringResource(R.string.math_source_typed_ready, label)
                        source in available -> stringResource(R.string.math_source_open_from_workspace, label)
                        else -> stringResource(R.string.math_source_unavailable, label)
                    })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.math_source_continue_typing)) }
        },
    )
}

@Composable
internal fun RecognitionReviewDialog(
    review: MathInputReview,
    select: (Int) -> Unit,
    edit: (UUID, Int, String) -> Unit,
    accept: () -> Unit,
    confirm: () -> Unit,
    dismiss: () -> Unit,
    onHint: () -> Unit,
    onSolve: () -> Unit,
    onLocalExplanation: () -> Unit,
    onSave: () -> Unit,
    onSourceHistory: () -> Unit = {},
    onOpenCalculator: (() -> Unit)? = null,
    insertion: @Composable (MathInputReview.Editing) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            if (onOpenCalculator == null) {
                Text(stringResource(R.string.math_source_review_title))
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.math_source_review_title), Modifier.weight(1f))
                    val calculator = stringResource(R.string.calculator_title)
                    IconButton(onClick = onOpenCalculator, modifier = Modifier.semantics { contentDescription = calculator }) {
                        val color = MaterialTheme.colorScheme.onSurface
                        Canvas(Modifier.size(20.dp)) {
                            val scale = size.width / 20f
                            drawRoundRect(color, Offset(3f * scale, scale), Size(14f * scale, 18f * scale),
                                CornerRadius(2f * scale), style = Stroke(1.5f * scale))
                            drawLine(color, Offset(6f * scale, 6f * scale), Offset(14f * scale, 6f * scale), 1.5f * scale)
                            for (x in listOf(7f, 13f)) {
                                for (y in listOf(11f, 15f)) drawCircle(color, scale, Offset(x * scale, y * scale))
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (review) {
                    MathInputReview.Idle -> Unit
                    is MathInputReview.Loading -> Text(
                        stringResource(R.string.math_source_recognizing, stringResource(review.source.label)),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    is MathInputReview.Failed -> Text(
                        stringResource(review.problem.label),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    is MathInputReview.Editing -> {
                        insertion(review)
                        ReviewCandidate(review, select, edit, onSave, onLocalExplanation, onSourceHistory)
                    }
                }
            }
        },
        confirmButton = {
            if (review is MathInputReview.Editing) {
                if (review.check is MathCorrectionCheck.Confirmed) {
                    Row {
                        TextButton(onClick = onHint, enabled = review.solution != MathSolutionReview.Working) {
                            Text(stringResource(R.string.math_solver_hint))
                        }
                        TextButton(onClick = onSolve, enabled = review.solution != MathSolutionReview.Working) {
                            Text(stringResource(R.string.math_solver_solve))
                        }
                    }
                } else if (review.check is MathCorrectionCheck.Parsed) {
                    TextButton(onClick = confirm) { Text(stringResource(R.string.math_source_confirm)) }
                } else {
                    TextButton(
                        onClick = accept,
                        enabled = review.check != MathCorrectionCheck.Checking &&
                            review.candidates[review.selected].text.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.math_source_review_accept))
                    }
                }
            }
        },
        dismissButton = {
            val confirmed = review is MathInputReview.Editing && review.check is MathCorrectionCheck.Confirmed
            TextButton(onClick = dismiss) {
                Text(stringResource(if (confirmed) R.string.math_source_close_review else R.string.math_source_cancel_review))
            }
        },
    )
}

@Composable
private fun ReviewCandidate(
    review: MathInputReview.Editing,
    select: (Int) -> Unit,
    edit: (UUID, Int, String) -> Unit,
    save: () -> Unit,
    requestLocalExplanation: () -> Unit,
    showSourceHistory: () -> Unit,
) {
    Text(stringResource(review.source.label), style = MaterialTheme.typography.labelLarge)
    if (review.hasSourceHistory) {
        TextButton(onClick = showSourceHistory) { Text(stringResource(R.string.math_history_title)) }
    }
    val solution = review.solution as? MathSolutionReview.Explanation
    if ((review.source == MathInputSource.Typed || review.hasSourceHistory) && solution?.outcome is MathSolutionOutcome.Verified) {
        when (review.saveStatus) {
            MathHistorySaveStatus.Saving -> ReviewStatus(R.string.math_history_saving)
            MathHistorySaveStatus.Saved -> ReviewStatus(R.string.math_history_saved)
            else -> {
                when (review.saveStatus) {
                    MathHistorySaveStatus.TooLarge -> ReviewStatus(R.string.math_history_too_large)
                    MathHistorySaveStatus.Unavailable -> ReviewStatus(R.string.math_history_unavailable)
                    else -> Unit
                }
                TextButton(onClick = save) { Text(stringResource(R.string.math_history_save)) }
            }
        }
    }
    MathSolutionContent(review.solution)
    if (solution?.offerLocalExplanation == true) {
        TextButton(onClick = requestLocalExplanation) { Text(stringResource(R.string.math_solver_explain_locally)) }
    }
    if (review.candidates.size > 1) {
        Text(stringResource(R.string.math_source_candidate_number, review.selected + 1, review.candidates.size))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { select(review.selected - 1) }, enabled = review.selected > 0) {
                Text(stringResource(R.string.math_source_previous))
            }
            TextButton(
                onClick = { select(review.selected + 1) },
                enabled = review.selected < review.candidates.lastIndex,
            ) {
                Text(stringResource(R.string.math_source_next))
            }
        }
    }
    val candidate = review.candidates[review.selected]
    ReviewOriginal(review.sourcePreview, candidate.originalText, review.source)
    key(review.reviewId, review.selected) {
        var field by remember { mutableStateOf(TextFieldValue(candidate.text, TextRange(candidate.text.length))) }
        val update = { value: TextFieldValue ->
            field = value
            edit(review.reviewId, review.selected, value.text)
        }
        val error = (review.check as? MathCorrectionCheck.Invalid)?.error
        OutlinedTextField(
            value = field,
            onValueChange = update,
            label = { Text(stringResource(R.string.math_source_editable_expression)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            isError = error != null,
            supportingText = error?.let { problem ->
                {
                    val position = problem.position
                    Text(if (position == null) problem.detail else
                        stringResource(R.string.math_expression_error, position + 1, problem.detail))
                }
            },
        )
        MathTemplateControls { change -> update(change(field)) }
    }
    val message = when (review.check) {
        MathCorrectionCheck.Checking -> R.string.math_source_checking
        is MathCorrectionCheck.Parsed -> R.string.math_source_parsed
        is MathCorrectionCheck.Confirmed -> R.string.math_source_confirmed
        MathCorrectionCheck.Unchecked, is MathCorrectionCheck.Invalid -> null
    }
    if (message != null) {
        Text(stringResource(message), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun ReviewStatus(label: Int) {
    Text(stringResource(label), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun ReviewOriginal(preview: MathReviewSource?, originalText: String, source: MathInputSource) {
    if (preview != null) {
        val label = if (source == MathInputSource.Typed) R.string.math_source_original_expression
            else R.string.math_source_original_selection
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        when (preview) {
            is MathReviewSource.Text -> OriginalText(preview.value)
            is MathReviewSource.Image -> Image(
                bitmap = preview.bitmap,
                contentDescription = stringResource(R.string.math_source_original_image),
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                contentScale = ContentScale.Fit,
            )
            is MathReviewSource.Ink -> {
                NotebookInkPreview(preview.strokes, stringResource(R.string.math_source_original_image))
                Text(stringResource(R.string.notebook_ink_transcribe))
            }
        }
    }
    if (source != MathInputSource.Typed && preview !is MathReviewSource.Ink) {
        Text(stringResource(R.string.math_source_original_recognition), style = MaterialTheme.typography.labelLarge)
        OriginalText(originalText)
    }
}

@Composable
private fun OriginalText(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(value, Modifier.heightIn(max = 88.dp).verticalScroll(rememberScrollState()).padding(10.dp))
    }
}

private val MathInputSource.label: Int
    get() = when (this) {
        MathInputSource.Typed -> R.string.math_source_typed
        MathInputSource.Handwriting -> R.string.math_source_handwriting
        MathInputSource.Camera -> R.string.math_source_camera
        MathInputSource.Book -> R.string.math_source_book
        MathInputSource.Pdf -> R.string.math_source_pdf
    }

private val MathInputProblem.label: Int
    get() = when (this) {
        MathInputProblem.Unavailable -> R.string.math_source_unavailable_review
        MathInputProblem.Locked -> R.string.math_source_locked
        MathInputProblem.SourceChanged -> R.string.math_source_changed
        MathInputProblem.Unsupported -> R.string.math_source_unsupported
        MathInputProblem.NoCandidates -> R.string.math_source_no_candidates
        MathInputProblem.RecognitionFailed -> R.string.math_source_failed
    }
