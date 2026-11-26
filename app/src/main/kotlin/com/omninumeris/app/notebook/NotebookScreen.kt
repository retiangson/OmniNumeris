package com.omninumeris.app.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omninumeris.app.R
import com.omninumeris.app.math.MathInputReview
import com.omninumeris.app.math.MathCorrectionCheck
import com.omninumeris.app.math.MathSolutionReview
import com.omninumeris.application.CalculatorValueKind
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionMode
import com.omninumeris.application.MathSolutionOutcome
import com.omninumeris.application.NotebookRead
import com.omninumeris.application.NotebookInkRegion
import com.omninumeris.application.NotebookInkSnapshot
import com.omninumeris.application.NotebookInkSelection
import com.omninumeris.application.NotebookPagePlacement
import com.omninumeris.application.NotebookResultSource
import com.omninumeris.application.notebookText
import com.omninumeris.domain.NotebookKind
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.NotebookViewport
import com.omninumeris.domain.PlacedNotebookText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotebookScreen(
    model: NotebookViewModel,
    state: NotebookState,
    onBack: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenMath: () -> Unit,
    onReviewInk: (NotebookInkSelection.Selected) -> Unit,
    onApprove: () -> Unit,
) {
    LaunchedEffect(model) { model.load() }
    val page = state.page
    val ready = !state.busy && state.pendingInk.isEmpty()
    var title by rememberSaveable { mutableStateOf("") }
    var choosingMathMode by remember { mutableStateOf(false) }
    var managingPages by remember(page?.id) { mutableStateOf(false) }
    var confirmingDeletion by remember(page?.id, page?.revision) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBack, enabled = ready) { Text(stringResource(R.string.math_editor_back)) }
                if (page != null) {
                    TextButton(onClick = onOpenCalculator) { Text(stringResource(R.string.calculator_title)) }
                    TextButton(onClick = onOpenMath, enabled = ready) { Text(stringResource(R.string.math_editor_title)) }
                    TextButton(onClick = model::toggleReadOnly, enabled = ready) {
                        Text(stringResource(if (page.readOnly) R.string.notebook_enable_editing else R.string.notebook_disable_editing))
                    }
                    TextButton(onClick = model::undo, enabled = state.canUndo && ready) { Text(stringResource(R.string.notebook_undo)) }
                }
            }
            Text(page?.notebook?.title ?: stringResource(R.string.notebooks_title), style = MaterialTheme.typography.headlineSmall)
            if (state.busy) Text(stringResource(R.string.notebook_working))
            if (page == null) {
                OutlinedTextField(title, { if (it.length <= 100) title = it },
                    label = { Text(stringResource(R.string.notebook_title_field)) }, singleLine = true)
                FlowRow {
                    for (kind in NotebookKind.entries) {
                        TextButton(onClick = { model.create(title, kind) }, enabled = !state.busy && title.isNotBlank()) {
                            Text(stringResource(if (kind == NotebookKind.Fixed) R.string.notebook_new_fixed else R.string.notebook_new_infinite))
                        }
                    }
                }
                LazyColumn {
                    items(state.notebooks, key = { it.id.toString() }) { notebook ->
                        TextButton(onClick = { model.open(notebook.id) }, enabled = !state.busy) { Text(notebook.title) }
                    }
                }
            } else {
                Text(stringResource(if (page.fixed) R.string.notebook_fixed_page else R.string.notebook_infinite_canvas))
                val preference = state.mathPreference
                if (preference != null) {
                    TextButton(onClick = { choosingMathMode = true }, enabled = ready && !page.readOnly) {
                        Text(stringResource(R.string.notebook_math_mode, stringResource(preference.mode.label)))
                    }
                }
                if (page.fixed) {
                    Text(stringResource(R.string.notebook_page_number, page.position + 1, page.pageCount))
                    FlowRow {
                        TextButton(onClick = { model.turnPage(-1) }, enabled = ready && page.position > 0) {
                            Text(stringResource(R.string.notebook_previous_page))
                        }
                        TextButton(onClick = { model.turnPage(1) }, enabled = ready && page.position + 1 < page.pageCount) {
                            Text(stringResource(R.string.notebook_next_page))
                        }
                        TextButton(onClick = { model.addPage() }, enabled = ready && !page.readOnly) {
                            Text(stringResource(R.string.notebook_add_page))
                        }
                        TextButton(onClick = { managingPages = true },
                            enabled = ready && (!page.readOnly || page.pageCount > 1 || state.canRestorePage)) {
                            Text(stringResource(R.string.notebook_manage_pages))
                        }
                    }
                }
                key(page.id) { NotebookCanvas(model, state, page, onReviewInk, Modifier.weight(1f)) }
            }
        }
    }
    if (managingPages && page?.fixed == true) {
        AlertDialog(
            onDismissRequest = { managingPages = false },
            title = { Text(stringResource(R.string.notebook_manage_pages)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NotebookPageThumbnails(model, page, ready) { pageId ->
                        managingPages = false
                        model.openPageById(page.notebook.id, pageId)
                    }
                    TextButton(onClick = {
                        managingPages = false
                        model.addPage(NotebookPagePlacement.AfterCurrent)
                    }, enabled = ready && !page.readOnly) {
                        Text(stringResource(R.string.notebook_page_insert_after))
                    }
                    TextButton(onClick = {
                        managingPages = false
                        model.addPage(NotebookPagePlacement.DuplicateCurrent)
                    }, enabled = ready && !page.readOnly) {
                        Text(stringResource(R.string.notebook_page_duplicate))
                    }
                    TextButton(onClick = {
                        managingPages = false
                        model.movePage(-1)
                    }, enabled = ready && !page.readOnly && page.position > 0) {
                        Text(stringResource(R.string.notebook_page_move_earlier))
                    }
                    TextButton(onClick = {
                        managingPages = false
                        model.movePage(1)
                    }, enabled = ready && !page.readOnly && page.position + 1 < page.pageCount) {
                        Text(stringResource(R.string.notebook_page_move_later))
                    }
                    TextButton(onClick = {
                        managingPages = false
                        model.undoPageMove()
                    }, enabled = ready && state.pageMoveUndo != null) {
                        Text(stringResource(R.string.notebook_undo_page_move))
                    }
                    TextButton(onClick = {
                        managingPages = false
                        confirmingDeletion = true
                    }, enabled = ready && !page.readOnly && page.pageCount > 1) {
                        Text(stringResource(R.string.notebook_delete_page))
                    }
                    if (state.canRestorePage) {
                        TextButton(onClick = {
                            managingPages = false
                            model.undoPageDeletion()
                        }, enabled = ready) {
                            Text(stringResource(R.string.notebook_restore_page))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { managingPages = false }) { Text(stringResource(R.string.notebook_close_pages)) }
            },
        )
    }
    if (confirmingDeletion && page != null) {
        AlertDialog(
            onDismissRequest = { confirmingDeletion = false },
            title = { Text(stringResource(R.string.notebook_confirm_page_deletion, page.position + 1)) },
            text = { Text(stringResource(R.string.notebook_confirm_page_deletion_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeletion = false
                    model.deletePage()
                }, enabled = ready && !page.readOnly && page.pageCount > 1) {
                    Text(stringResource(R.string.notebook_confirm_delete_page))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeletion = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    state.resultSource?.let { source ->
        AlertDialog(
            onDismissRequest = model::closeResultSource,
            title = { Text(stringResource(R.string.notebook_result_source_title)) },
            text = {
                when (source) {
                    is NotebookResultSource.Ink -> NotebookInkPreview(
                        source.selection.strokes, stringResource(R.string.notebook_result_source_preview),
                    )
                    NotebookResultSource.Changed -> Text(stringResource(R.string.notebook_result_source_changed))
                    NotebookResultSource.Unavailable -> Text(stringResource(R.string.notebook_result_source_unavailable))
                }
            },
            confirmButton = {
                if (source is NotebookResultSource.Ink) {
                    TextButton(onClick = {
                        model.closeResultSource()
                        model.openPageById(source.page.notebook.id, source.page.id)
                    }, enabled = ready) { Text(stringResource(R.string.notebook_result_source_page, source.page.position + 1)) }
                }
            },
            dismissButton = {
                TextButton(onClick = model::closeResultSource) { Text(stringResource(R.string.notebook_result_source_close)) }
            },
        )
    }
    if (choosingMathMode && page?.readOnly == false) {
        AlertDialog(
            onDismissRequest = { choosingMathMode = false },
            title = { Text(stringResource(R.string.notebook_math_mode_title)) },
            text = { Text(stringResource(R.string.notebook_math_mode_help)) },
            confirmButton = {
                FlowRow {
                    for (mode in MathInsertionMode.entries) {
                        TextButton(onClick = {
                            choosingMathMode = false
                            model.setMathMode(mode)
                        }) { Text(stringResource(mode.label)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { choosingMathMode = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    state.proposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = model::cancelPreview,
            title = { Text(stringResource(R.string.notebook_insert_result)) },
            text = {
                val text = proposal.value.text
                Text(if (proposal.value.kind == CalculatorValueKind.Approximate)
                    stringResource(R.string.calculator_approximate, text) else text, maxLines = 8, overflow = TextOverflow.Ellipsis)
            },
            confirmButton = { TextButton(onClick = onApprove) { Text(stringResource(R.string.notebook_insert_confirm)) } },
            dismissButton = { TextButton(onClick = model::cancelPreview) { Text(stringResource(R.string.notebook_insert_cancel)) } },
        )
    }
    state.problem?.let { problem ->
        AlertDialog(
            onDismissRequest = model::dismissProblem,
            text = { Text(stringResource(problem.message)) },
            confirmButton = { TextButton(onClick = model::dismissProblem) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
internal fun NotebookMathInsertion(
    state: NotebookState,
    review: MathInputReview.Editing,
    onPreview: () -> Unit,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
) {
    val solution = review.solution as? MathSolutionReview.Explanation ?: return
    if (solution.outcome !is MathSolutionOutcome.Verified) return
    val resultId = (review.check as? MathCorrectionCheck.Confirmed)?.resultId ?: return
    state.problem?.let { Text(stringResource(it.message), color = MaterialTheme.colorScheme.error) }
    if (state.insertedMathId == resultId) {
        Text(stringResource(R.string.notebook_math_inserted))
        return
    }
    val proposal = state.mathProposal?.takeIf { it.resultId == resultId }
    if (proposal == null) {
        TextButton(onClick = onPreview, enabled = !state.busy) { Text(stringResource(R.string.notebook_insert_result)) }
    } else {
        Text(stringResource(R.string.notebook_math_preview, proposal.answer.notebookText()))
        Row {
            TextButton(onClick = onApprove, enabled = !state.busy) { Text(stringResource(R.string.notebook_insert_confirm)) }
            TextButton(onClick = onCancel, enabled = !state.busy) { Text(stringResource(R.string.notebook_insert_cancel)) }
        }
    }
}

private val MathInsertionProblem.message: Int
    get() = when (this) {
        MathInsertionProblem.ReadOnly -> R.string.notebook_read_only
        MathInsertionProblem.NoSpaceInTarget -> R.string.notebook_page_full
        MathInsertionProblem.SourceUnavailable -> R.string.notebook_math_source_unavailable
        MathInsertionProblem.AutomaticGrantRevoked -> R.string.notebook_math_grant_revoked
        MathInsertionProblem.SelectionChanged, MathInsertionProblem.RevisionChanged -> R.string.notebook_selection_changed
        else -> R.string.notebook_unavailable
    }

private val MathInsertionMode.label: Int
    get() = when (this) {
        MathInsertionMode.Manual -> R.string.notebook_math_manual
        MathInsertionMode.Suggestion -> R.string.notebook_math_suggestion
        MathInsertionMode.Automatic -> R.string.notebook_math_automatic
    }

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NotebookCanvas(
    model: NotebookViewModel,
    state: NotebookState,
    page: NotebookPage,
    onReviewInk: (NotebookInkSelection.Selected) -> Unit,
    modifier: Modifier,
) {
    var viewport by remember { mutableStateOf(NotebookViewport.Initial) }
    var mode by remember { mutableStateOf(NotebookCanvasMode.Navigate) }
    var trace by remember { mutableStateOf(emptyList<Offset>()) }
    val density = LocalDensity.current.density
    FlowRow {
        for (choice in NotebookCanvasMode.entries) {
            val label = when (choice) {
                NotebookCanvasMode.Navigate -> R.string.notebook_canvas_navigate
                NotebookCanvasMode.Draw -> R.string.notebook_canvas_draw
                NotebookCanvasMode.Select -> R.string.notebook_canvas_select
            }
            TextButton(onClick = {
                mode = choice
                model.clearInkSelection()
            }, enabled = (!page.readOnly || choice != NotebookCanvasMode.Draw) && mode != choice) {
                Text(stringResource(label))
            }
        }
        TextButton(onClick = { viewport = NotebookViewport.Initial }) { Text(stringResource(R.string.notebook_reset_view)) }
    }
    if (state.inkWriteFailed) {
        Text(stringResource(R.string.notebook_ink_save_failed))
        FlowRow {
            TextButton(onClick = model::flushInk) { Text(stringResource(R.string.notebook_ink_retry)) }
            TextButton(onClick = model::discardPendingInk) { Text(stringResource(R.string.notebook_ink_discard)) }
        }
    }
    when (val selection = state.inkSelection) {
        is NotebookInkSelection.Selected -> TextButton(
            onClick = { onReviewInk(selection); model.clearInkSelection() },
            enabled = !state.busy && state.pendingInk.isEmpty(),
        ) { Text(stringResource(R.string.notebook_review_ink)) }
        NotebookInkSelection.Empty -> Text(stringResource(R.string.notebook_ink_empty))
        NotebookInkSelection.Unavailable, NotebookInkSelection.TooComplex ->
            Text(stringResource(R.string.notebook_ink_selection_unavailable))
        null -> Unit
    }
    val interaction = if (mode == NotebookCanvasMode.Navigate) {
        Modifier.pointerInput(density) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                viewport = viewport.transform(centroid.x / density, centroid.y / density, pan.x / density, pan.y / density, zoom)
            }
        }
    } else Modifier.notebookInkInput(mode, viewport, page, density, { trace = it }, model::queueInk, model::selectInk)
    val canvasLabel = stringResource(R.string.notebook_canvas_description)
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)
        .semantics { contentDescription = canvasLabel }.then(interaction)) {
        val right = viewport.left + maxWidth.value / viewport.zoom
        val bottom = viewport.top + maxHeight.value / viewport.zoom
        val objects by produceState<NotebookRead<List<PlacedNotebookText>>>(NotebookRead.Available(emptyList()),
            page.revision, viewport, right, bottom) {
            if (right > viewport.left && bottom > viewport.top) {
                value = model.visible(page, viewport.left, viewport.top, right, bottom)
            }
        }
        val ink by produceState<NotebookRead<NotebookInkSnapshot>?>(null, page.revision, viewport, right, bottom) {
            if (right > viewport.left && bottom > viewport.top) {
                value = model.ink(page, NotebookInkRegion(viewport.left, viewport.top, right, bottom))
            }
        }
        if (page.fixed) {
            Box(Modifier.absoluteOffset((-viewport.left * viewport.zoom).dp, (-viewport.top * viewport.zoom).dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size((NotebookPage.WIDTH * viewport.zoom).dp, (NotebookPage.HEIGHT * viewport.zoom).dp)
                .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline))
        }
        val inkColor = MaterialTheme.colorScheme.onSurface
        val selectionColor = MaterialTheme.colorScheme.primary
        val selected = (state.inkSelection as? NotebookInkSelection.Selected)?.anchor?.strokeIds?.toSet().orEmpty()
        val strokes = (ink as? NotebookRead.Available)?.value?.strokes.orEmpty()
        Canvas(Modifier.fillMaxSize()) {
            val scale = density * viewport.zoom
            val origin = Offset(-viewport.left * scale, -viewport.top * scale)
            (strokes + state.pendingInk).distinctBy { it.id }.forEach {
                drawNotebookStroke(it, origin, scale, if (it.id in selected) selectionColor else inkColor)
            }
            trace.zipWithNext { from, to -> drawLine(selectionColor, from, to, strokeWidth = 2.dp.toPx()) }
        }
        if (ink == NotebookRead.Unavailable) Text(stringResource(R.string.notebook_ink_selection_unavailable))
        when (val result = objects) {
            is NotebookRead.Available -> for (item in result.value) key(item.id) {
                val radix = item.radix
                val expression = item.verifiedExpression
                val display = when {
                    expression != null -> stringResource(R.string.notebook_verified_math, expression, item.text)
                    item.approximate -> stringResource(R.string.calculator_approximate, item.text)
                    radix != null -> stringResource(R.string.notebook_value_base, item.text, radix.base)
                    else -> item.text
                }
                val sourceAction = if (item.hasInkSource && mode == NotebookCanvasMode.Navigate) {
                    Modifier.clickable(enabled = !state.busy, onClickLabel = stringResource(R.string.notebook_result_source_title)) {
                        model.inspectResultSource(item.id)
                    }
                } else Modifier
                Text(display, Modifier
                    .absoluteOffset(((item.x - viewport.left) * viewport.zoom).dp, ((item.y - viewport.top) * viewport.zoom).dp)
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size((PlacedNotebookText.WIDTH * viewport.zoom).dp, (PlacedNotebookText.HEIGHT * viewport.zoom).dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).then(sourceAction).padding(8.dp),
                    fontSize = (16f * viewport.zoom).sp,
                    textDecoration = if (item.hasInkSource && mode == NotebookCanvasMode.Navigate) TextDecoration.Underline else null,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            NotebookRead.Unavailable -> Text(stringResource(R.string.notebook_unavailable))
        }
    }
}
