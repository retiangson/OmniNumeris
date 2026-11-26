package com.omninumeris.application

import com.omninumeris.domain.NotebookKind
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.NotebookSummary
import com.omninumeris.domain.PlacedNotebookText
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkPoint
import com.omninumeris.domain.math.MathSourceRevision
import java.util.UUID

data class NotebookMathPreference(
    val mode: MathInsertionMode = MathInsertionMode.Manual,
    val grant: MathAutomaticInsertionGrant? = null,
)

data class NotebookInkRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class NotebookInkSnapshot(
    val sourceRevision: MathSourceRevision,
    val strokes: List<InkStroke>,
)

data class NotebookPageMoveUndo(
    val notebookId: UUID,
    val pageId: UUID,
    val neighborId: UUID,
    val positionAfterMove: Int,
    val positionBeforeMove: Int,
)

data class NotebookPageMove(val page: NotebookPage, val undo: NotebookPageMoveUndo)

enum class NotebookPagePlacement { End, AfterCurrent, DuplicateCurrent }

data class NotebookPageThumbnail(
    val pageId: UUID,
    val ink: List<List<InkPoint>>,
    val results: List<InkPoint>,
)

sealed interface NotebookResultSource {
    data class Ink(val page: NotebookPage, val selection: NotebookInkSelection.Selected) : NotebookResultSource
    data object Unavailable : NotebookResultSource
    data object Changed : NotebookResultSource
}

sealed interface NotebookRead<out T> {
    data class Available<T>(val value: T) : NotebookRead<T>
    data object Unavailable : NotebookRead<Nothing>
}

interface NotebookStore {
    suspend fun list(): NotebookRead<List<NotebookSummary>>
    suspend fun create(title: String, kind: NotebookKind): NotebookRead<NotebookPage>
    suspend fun open(notebookId: UUID, position: Int = 0): NotebookRead<NotebookPage>
    suspend fun openById(notebookId: UUID, pageId: UUID): NotebookRead<NotebookPage>
    suspend fun thumbnail(notebookId: UUID, position: Int): NotebookRead<NotebookPageThumbnail>
    suspend fun resultSource(page: NotebookPage, resultId: UUID): NotebookResultSource
    suspend fun addPage(current: NotebookPage, placement: NotebookPagePlacement = NotebookPagePlacement.End): NotebookRead<NotebookPage>
    suspend fun movePage(current: NotebookPage, offset: Int): NotebookRead<NotebookPageMove>
    suspend fun undoPageMove(undo: NotebookPageMoveUndo): NotebookRead<NotebookPage>
    suspend fun deletePage(current: NotebookPage): NotebookRead<NotebookPage>
    suspend fun restoreLastDeletedPage(notebookId: UUID): NotebookRead<NotebookPage>
    suspend fun hasDeletedPages(notebookId: UUID): Boolean
    suspend fun setReadOnly(page: NotebookPage, readOnly: Boolean): NotebookRead<NotebookPage>
    suspend fun mathPreference(page: NotebookPage): NotebookRead<NotebookMathPreference>
    suspend fun setMathMode(page: NotebookPage, mode: MathInsertionMode): NotebookRead<NotebookPage>
    suspend fun appendInk(page: NotebookPage, stroke: InkStroke): NotebookRead<NotebookPage>
    suspend fun ink(page: NotebookPage, region: NotebookInkRegion): NotebookRead<NotebookInkSnapshot>
    suspend fun visible(pageId: UUID, left: Float, top: Float, right: Float, bottom: Float): NotebookRead<List<PlacedNotebookText>>
    suspend fun lastUndo(pageId: UUID): MathInsertionUndo?
}
