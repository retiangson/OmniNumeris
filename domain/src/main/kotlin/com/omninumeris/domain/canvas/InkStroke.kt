package com.omninumeris.domain.canvas

import java.util.Collections
import java.util.UUID
import kotlin.math.abs

const val MAX_INK_SAMPLES = 8_192
const val MAX_INK_COORDINATE = 1_000_000f
const val MAX_INK_SAMPLE_DISTANCE = 8_192f

data class InkSample(val x: Float, val y: Float, val pressure: Float) {
    init {
        require(x in -MAX_INK_COORDINATE..MAX_INK_COORDINATE && y in -MAX_INK_COORDINATE..MAX_INK_COORDINATE)
        require(pressure in 0f..1f)
    }

    companion object {
        fun fromPointer(x: Float, y: Float, pressure: Float, stylus: Boolean): InkSample {
            val normalized = if (stylus && pressure.isFinite()) pressure.coerceIn(0f, 1f) else 0.5f
            return InkSample(x, y, normalized)
        }
    }
}

enum class InkTip(val widthScale: Float) {
    Fine(0.35f), Medium(0.6f), Bold(1f),
}

class InkStroke(val id: UUID, samples: List<InkSample>, val tip: InkTip = InkTip.Bold) {
    val samples: List<InkSample> = Collections.unmodifiableList(ArrayList(samples))

    init {
        require(this.samples.size in 1..MAX_INK_SAMPLES)
        for (index in 1 until this.samples.size) {
            val previous = this.samples[index - 1]
            val current = this.samples[index]
            require(abs(previous.x - current.x) <= MAX_INK_SAMPLE_DISTANCE &&
                abs(previous.y - current.y) <= MAX_INK_SAMPLE_DISTANCE)
        }
    }

    fun isNear(x: Float, y: Float, radius: Float): Boolean {
        val first = samples.first()
        val firstRadius = radius + inkWidth(first.pressure, tip) / 2
        if ((x - first.x).squared() + (y - first.y).squared() <= firstRadius.squared()) return true
        for (index in 1 until samples.size) {
            val from = samples[index - 1]
            val to = samples[index]
            val dx = to.x - from.x
            val dy = to.y - from.y
            val lengthSquared = dx.squared() + dy.squared()
            val position = if (lengthSquared == 0f) 0f else
                (((x - from.x) * dx + (y - from.y) * dy) / lengthSquared).coerceIn(0f, 1f)
            val distanceSquared = (x - from.x - position * dx).squared() + (y - from.y - position * dy).squared()
            val hitRadius = radius + inkWidth((from.pressure + to.pressure) / 2, tip) / 2
            if (distanceSquared <= hitRadius.squared()) return true
        }
        return false
    }
}

fun inkWidth(pressure: Float, tip: InkTip = InkTip.Bold): Float = (2f + pressure * 6f) * tip.widthScale

private fun Float.squared(): Float = this * this
