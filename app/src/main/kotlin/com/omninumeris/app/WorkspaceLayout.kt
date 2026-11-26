package com.omninumeris.app

internal enum class WorkspaceLayout {
    Stacked,
    Split;

    companion object {
        fun forWidth(availableWidthDp: Float): WorkspaceLayout =
            if (availableWidthDp >= 840f) Split else Stacked
    }
}
