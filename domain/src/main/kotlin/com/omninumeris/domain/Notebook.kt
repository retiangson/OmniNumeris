package com.omninumeris.domain

import com.omninumeris.domain.calculator.Radix
import java.util.UUID

enum class NotebookKind { Fixed, Infinite }

data class NotebookSummary(val id: UUID, val title: String, val kind: NotebookKind)

data class NotebookPage(
    val id: UUID,
    val notebook: NotebookSummary,
    val revision: UUID,
    val readOnly: Boolean,
    val position: Int,
    val pageCount: Int,
) {
    val fixed get() = notebook.kind == NotebookKind.Fixed

    companion object {
        const val WIDTH = 816f
        const val HEIGHT = 1056f
    }
}

data class PlacedNotebookText(
    val id: UUID,
    val x: Float,
    val y: Float,
    val text: String,
    val approximate: Boolean,
    val radix: Radix?,
    val verifiedExpression: String? = null,
    val hasInkSource: Boolean = false,
) {
    companion object {
        const val WIDTH = 240f
        const val HEIGHT = 96f
    }
}

data class NotebookViewport(val left: Float, val top: Float, val zoom: Float) {
    fun transform(anchorX: Float, anchorY: Float, panX: Float, panY: Float, scale: Float): NotebookViewport {
        val nextZoom = (zoom * scale).coerceIn(0.5f, 3f)
        return NotebookViewport(
            (left + anchorX / zoom - (anchorX + panX) / nextZoom).coerceIn(-LIMIT, LIMIT),
            (top + anchorY / zoom - (anchorY + panY) / nextZoom).coerceIn(-LIMIT, LIMIT),
            nextZoom,
        )
    }

    companion object {
        const val LIMIT = 1_000_000f
        val Initial = NotebookViewport(0f, 0f, 1f)
    }
}
