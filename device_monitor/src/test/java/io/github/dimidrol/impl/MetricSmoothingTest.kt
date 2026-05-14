package io.github.dimidrol.impl

import io.github.dimidrol.models.DeviceHealth
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricSmoothingTest {

    @Test
    fun emaProducesExpectedProgression() {
        val ema = ExponentialMovingAverage(alpha = 0.5f)

        assertEquals(10f, ema.update(10f))
        assertEquals(15f, ema.update(20f))
        assertEquals(17.5f, ema.update(20f))
    }

    @Test
    fun rollingHealthSmootherUsesWindowAndConservativeTieBreak() {
        val smoother = RollingHealthSmoother(windowSize = 3)

        assertEquals(DeviceHealth.NORMAL, smoother.add(DeviceHealth.NORMAL))
        assertEquals(DeviceHealth.WARM, smoother.add(DeviceHealth.WARM))
        assertEquals(DeviceHealth.WARM, smoother.add(DeviceHealth.WARM))
        assertEquals(DeviceHealth.WARM, smoother.add(DeviceHealth.NORMAL))
        assertEquals(DeviceHealth.DEGRADED, smoother.add(DeviceHealth.DEGRADED))
    }

    @Test
    fun healthFromRiskScoreMapsRanges() {
        assertEquals(DeviceHealth.NORMAL, healthFromRiskScore(0f))
        assertEquals(DeviceHealth.WARM, healthFromRiskScore(25f))
        assertEquals(DeviceHealth.DEGRADED, healthFromRiskScore(60f))
        assertEquals(DeviceHealth.CRITICAL, healthFromRiskScore(85f))
    }
}
