package com.omninumeris.domain.math

import java.util.UUID

enum class MathInputSource { Typed, Handwriting, Camera, Book, Pdf }

data class MathSourceRevision(val contentId: UUID, val revisionId: UUID)

sealed interface MathSourceAnchor {
    val revision: MathSourceRevision
    val source: MathInputSource

    data class Ink(
        override val revision: MathSourceRevision,
        val pageId: UUID,
        val strokeIds: List<UUID>,
    ) : MathSourceAnchor {
        init {
            require(strokeIds.isNotEmpty())
        }
        override val source = MathInputSource.Handwriting
    }

    data class Capture(
        override val revision: MathSourceRevision,
        val assetId: UUID,
        val region: MathPageRegion,
    ) : MathSourceAnchor {
        override val source = MathInputSource.Camera
    }

    data class Book(
        override val revision: MathSourceRevision,
        val locator: String,
        val quote: String,
    ) : MathSourceAnchor {
        init {
            require(locator.isNotBlank())
        }
        override val source = MathInputSource.Book
    }

    data class Pdf(
        override val revision: MathSourceRevision,
        val pageId: UUID,
        val region: MathPageRegion,
    ) : MathSourceAnchor {
        override val source = MathInputSource.Pdf
    }
}

/** Fractional coordinates in the original source orientation, independent of viewport transforms. */
data class MathPageRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(left >= 0.0 && left < right && right <= 1.0)
        require(top >= 0.0 && top < bottom && bottom <= 1.0)
    }
}
