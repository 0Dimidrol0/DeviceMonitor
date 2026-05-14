package io.github.dimidrol.impl

import io.github.dimidrol.common.DEFAULT_FLOAT
import io.github.dimidrol.common.DEFAULT_INT
import io.github.dimidrol.models.DeviceHealth

internal class ExponentialMovingAverage(
    alpha: Float
) {
    private var alpha: Float = alpha.coerceIn(DEFAULT_FLOAT, 1f)
    private var lastValue: Float? = null

    fun updateAlpha(alpha: Float) {
        this.alpha = alpha.coerceIn(DEFAULT_FLOAT, 1f)
    }

    fun reset() {
        lastValue = null
    }

    fun update(value: Float): Float {
        val current = lastValue?.let { previous ->
            (this.alpha * value) + ((1f - this.alpha) * previous)
        } ?: value
        lastValue = current
        return current
    }
}

internal class RollingHealthSmoother(
    windowSize: Int
) {
    private var windowSize = windowSize.coerceAtLeast(DEFAULT_INT + 1)
    private val values = ArrayDeque<DeviceHealth>()

    fun updateWindowSize(windowSize: Int) {
        this.windowSize = windowSize.coerceAtLeast(DEFAULT_INT + 1)
        while (values.size > this.windowSize) {
            values.removeFirst()
        }
    }

    fun reset() {
        values.clear()
    }

    fun add(value: DeviceHealth): DeviceHealth {
        values.addLast(value)
        while (values.size > windowSize) {
            values.removeFirst()
        }

        val counts = values.groupingBy { it }.eachCount()
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<DeviceHealth, Int>> { it.value }
                    .thenByDescending { it.key.ordinal }
            )
            .firstOrNull()
            ?.key
            ?: value
    }
}

internal fun healthFromRiskScore(score: Float): DeviceHealth {
    return when (score.toInt()) {
        in Int.MIN_VALUE until 20 -> DeviceHealth.NORMAL
        in 20 until 40 -> DeviceHealth.WARM
        in 40 until 70 -> DeviceHealth.DEGRADED
        else -> DeviceHealth.CRITICAL
    }
}
