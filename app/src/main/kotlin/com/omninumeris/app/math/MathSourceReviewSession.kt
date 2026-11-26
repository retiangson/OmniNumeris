package com.omninumeris.app.math

import com.omninumeris.application.MathInputProblem
import com.omninumeris.application.LinkedMathSolutionHistory
import com.omninumeris.domain.math.MathSourceAnchor
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class MathSourceRevocation(val problem: MathInputProblem) {
    Locked(MathInputProblem.Locked),
    RevisionChanged(MathInputProblem.SourceChanged),
    AccessRevoked(MathInputProblem.Unavailable),
}

/** One authorized revision selection. The source owner invalidates it on lock, change, or loss of access. */
internal class MathSourceReviewSession(
    anchor: MathSourceAnchor,
    val history: LinkedMathSolutionHistory? = null,
) {
    val anchor = when (anchor) {
        is MathSourceAnchor.Ink -> anchor.copy(strokeIds = Collections.unmodifiableList(ArrayList(anchor.strokeIds)))
        else -> anchor
    }
    private val mutableRevocation = MutableStateFlow<MathSourceRevocation?>(null)
    val revocation = mutableRevocation.asStateFlow()

    fun revoke(reason: MathSourceRevocation) {
        mutableRevocation.compareAndSet(null, reason)
    }
}
