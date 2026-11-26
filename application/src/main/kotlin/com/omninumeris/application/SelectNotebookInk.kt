package com.omninumeris.application

import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.canvas.InkLasso
import com.omninumeris.domain.canvas.InkLassoHits
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.math.MathSourceAnchor

sealed interface NotebookInkSelection {
    data class Selected(val anchor: MathSourceAnchor.Ink, val strokes: List<InkStroke>) : NotebookInkSelection
    data object Empty : NotebookInkSelection
    data object TooComplex : NotebookInkSelection
    data object Unavailable : NotebookInkSelection
}

class SelectNotebookInk(private val notebooks: NotebookStore) {
    suspend operator fun invoke(
        page: NotebookPage,
        lasso: InkLasso,
        checkCancelled: () -> Unit = {},
    ): NotebookInkSelection {
        val bounds = lasso.bounds
        val region = NotebookInkRegion(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        val snapshot = when (val read = notebooks.ink(page, region)) {
            is NotebookRead.Available -> read.value
            NotebookRead.Unavailable -> return NotebookInkSelection.Unavailable
        }
        return when (val hits = lasso.select(snapshot.strokes, checkCancelled)) {
            InkLassoHits.TooComplex -> NotebookInkSelection.TooComplex
            is InkLassoHits.Selected -> {
                if (hits.strokes.isEmpty()) return NotebookInkSelection.Empty
                val anchor = MathSourceAnchor.Ink(snapshot.sourceRevision, page.id, hits.strokes.map(InkStroke::id))
                NotebookInkSelection.Selected(anchor, hits.strokes)
            }
        }
    }
}
