package com.omninumeris.app.calculator

import kotlin.math.max
import kotlin.math.min

internal data class CalculatorPanelFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class CalculatorPanelPlacement(
    val preferredWidth: Float = 440f,
    val preferredHeight: Float = 680f,
    val horizontalBias: Float = 1f,
    val verticalBias: Float = 0f,
) {
    fun frame(width: Float, height: Float): CalculatorPanelFrame {
        val panelWidth = preferredWidth.coerceIn(min(MIN_WIDTH, width), width)
        val panelHeight = preferredHeight.coerceIn(min(MIN_HEIGHT, height), height)
        return CalculatorPanelFrame(
            (width - panelWidth) * horizontalBias,
            (height - panelHeight) * verticalBias,
            panelWidth,
            panelHeight,
        )
    }

    fun move(width: Float, height: Float, dx: Float, dy: Float): CalculatorPanelPlacement {
        val frame = frame(width, height)
        return copy(
            horizontalBias = bias(frame.left + dx, width - frame.width, horizontalBias),
            verticalBias = bias(frame.top + dy, height - frame.height, verticalBias),
        )
    }

    fun resize(width: Float, height: Float, dx: Float, dy: Float): CalculatorPanelPlacement {
        val frame = frame(width, height)
        val panelWidth = (frame.width + dx).coerceIn(min(MIN_WIDTH, width), max(frame.width, width - frame.left))
        val panelHeight = (frame.height + dy).coerceIn(min(MIN_HEIGHT, height), max(frame.height, height - frame.top))
        return copy(
            preferredWidth = if (panelWidth == frame.width) preferredWidth else panelWidth,
            preferredHeight = if (panelHeight == frame.height) preferredHeight else panelHeight,
            horizontalBias = bias(frame.left, width - panelWidth, horizontalBias),
            verticalBias = bias(frame.top, height - panelHeight, verticalBias),
        )
    }

    private fun bias(position: Float, freeSpace: Float, previous: Float): Float =
        if (freeSpace > 0f) (position / freeSpace).coerceIn(0f, 1f) else previous

    private companion object {
        const val MIN_WIDTH = 264f
        const val MIN_HEIGHT = 240f
    }
}
