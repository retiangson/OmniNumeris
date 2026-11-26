package com.omninumeris.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.omninumeris.app.calculator.CalculatorHost
import com.omninumeris.app.calculator.CalculatorViewModel
import com.omninumeris.app.math.MathEditorScreen
import com.omninumeris.app.math.MathInputViewModel
import com.omninumeris.app.math.MathReviewDialogs
import com.omninumeris.app.notebook.NotebookScreen
import com.omninumeris.app.notebook.NotebookMathInsertion
import com.omninumeris.app.notebook.NotebookViewModel
import com.omninumeris.application.LinkedMathSolutionHistory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var notebookInkHistory: LinkedMathSolutionHistory
    private val workspace: WorkspaceViewModel by viewModels()
    private val calculator: CalculatorViewModel by viewModels()
    private val mathInput: MathInputViewModel by viewModels()
    private val notebook: NotebookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val status by workspace.status.collectAsStateWithLifecycle()
                var editorOpen by rememberSaveable { mutableStateOf(false) }
                var canvasOpen by rememberSaveable { mutableStateOf(false) }
                var notebookOpen by rememberSaveable { mutableStateOf(false) }
                var calculatorOpenedFromReview by rememberSaveable { mutableStateOf(false) }
                var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
                var notebookDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
                fun closeEditor() {
                    mathInput.dismissReview()
                    mathInput.closeSolutionHistory()
                    calculatorOpenedFromReview = false
                    notebook.cancelMathPreview()
                    editorOpen = false
                }
                BackHandler(enabled = canvasOpen) { canvasOpen = false }
                val notebookState = if (notebookOpen) notebook.state.collectAsStateWithLifecycle().value else null
                val showingEditor = editorOpen && (!notebookOpen || notebookState?.page != null)
                BackHandler(enabled = showingEditor) { closeEditor() }
                fun closeNotebook() {
                    if (notebookState?.busy == true) return
                    if (notebookState?.page != null) {
                        notebook.closePage()
                    } else {
                        closeEditor()
                        notebookOpen = false
                    }
                }
                BackHandler(enabled = notebookOpen && !showingEditor) { closeNotebook() }
                CalculatorHost(calculator, onInsertResult = if (!showingEditor && notebookState?.page != null) notebook::preview else null) { openCalculator, calculatorExpanded ->
                    if (calculatorOpenedFromReview && !calculatorExpanded) {
                        SideEffect { calculatorOpenedFromReview = false }
                    }
                    if (notebookState != null && !showingEditor) {
                        NotebookScreen(notebook, notebookState, ::closeNotebook, openCalculator, onOpenMath = {
                            notebookDraft = TextFieldValue()
                            editorOpen = true
                        }, onReviewInk = { selection ->
                            notebookDraft = TextFieldValue()
                            mathInput.beginInkTranscription(selection, notebookInkHistory)
                            editorOpen = true
                        }) {
                            notebook.approve(calculator.state.value.result)
                        }
                    } else if (canvasOpen) {
                        WorkspaceCanvas(onBack = { canvasOpen = false }, onOpenCalculator = openCalculator)
                    } else if (showingEditor) {
                        MathEditorScreen(
                            value = if (notebookState == null) draft else notebookDraft,
                            onChange = { if (notebookState == null) draft = it else notebookDraft = it },
                            onEdit = { transform ->
                                if (notebookState == null) draft = transform(draft) else notebookDraft = transform(notebookDraft)
                            },
                            previewTyped = mathInput::previewTyped,
                            availableSources = mathInput.availableSources,
                            onReview = {
                                calculatorOpenedFromReview = false
                                mathInput.beginTypedReview(if (notebookState == null) draft.text else notebookDraft.text)
                            },
                            onHistory = {
                                calculatorOpenedFromReview = false
                                mathInput.showSolutionHistory()
                            },
                            onOpenCalculator = openCalculator,
                            onBack = { closeEditor() },
                        )
                        if (!calculatorOpenedFromReview || !calculatorExpanded) {
                            MathReviewDialogs(
                                mathInput,
                                onOpenCalculator = {
                                    calculatorOpenedFromReview = true
                                    openCalculator()
                                },
                                insertion = { review ->
                                    if (notebookState != null) NotebookMathInsertion(
                                        notebookState, review,
                                        onPreview = { notebook.previewMath(mathInput.confirmedSolution()) },
                                        onApprove = { notebook.approveMath(mathInput.confirmedSolution()) },
                                        onCancel = notebook::cancelMathPreview,
                                    )
                                },
                                onVerifiedSolve = { review ->
                                    if (notebookState != null) notebook.previewMath(review, manuallyRequested = false)
                                },
                            ) { text ->
                                val confirmed = TextFieldValue(text, TextRange(text.length))
                                if (notebookState == null) draft = confirmed else notebookDraft = confirmed
                            }
                        }
                    } else {
                        WorkspaceScreen(
                            status = status,
                            onRetry = workspace::retry,
                            onOpenMath = { editorOpen = true },
                            onOpenCanvas = { canvasOpen = true },
                            onOpenNotebooks = { notebookOpen = true },
                            onOpenCalculator = openCalculator,
                        )
                    }
                }
            }
        }
    }
}
