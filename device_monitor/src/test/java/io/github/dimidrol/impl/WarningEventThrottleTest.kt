package io.github.dimidrol.impl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarningEventThrottleTest {

    @Test
    fun emitsOnlyOnRiskTransitions() {
        val throttle = WarningEventThrottle()

        assertFalse(throttle.shouldEmitMemoryLow(isRiskActive = false))
        assertTrue(throttle.shouldEmitMemoryLow(isRiskActive = true))
        assertFalse(throttle.shouldEmitMemoryLow(isRiskActive = true))
        assertFalse(throttle.shouldEmitMemoryLow(isRiskActive = false))
        assertTrue(throttle.shouldEmitMemoryLow(isRiskActive = true))

        assertTrue(throttle.shouldEmitStorageLow(isRiskActive = true))
        assertFalse(throttle.shouldEmitStorageLow(isRiskActive = true))
        assertFalse(throttle.shouldEmitStorageLow(isRiskActive = false))
        assertTrue(throttle.shouldEmitStorageLow(isRiskActive = true))
    }

    @Test
    fun resetAllowsWarningsAgain() {
        val throttle = WarningEventThrottle()

        assertTrue(throttle.shouldEmitBatteryLow(isRiskActive = true))
        assertFalse(throttle.shouldEmitBatteryLow(isRiskActive = true))
        throttle.reset()
        assertTrue(throttle.shouldEmitBatteryLow(isRiskActive = true))

        assertTrue(throttle.shouldEmitCpuOverload(isRiskActive = true))
        assertFalse(throttle.shouldEmitCpuOverload(isRiskActive = true))
        throttle.reset()
        assertTrue(throttle.shouldEmitCpuOverload(isRiskActive = true))
    }

    @Test
    fun clearingRiskRearmsTemperatureWarning() {
        val throttle = WarningEventThrottle()

        assertTrue(throttle.shouldEmitBatteryTempHigh(isRiskActive = true))
        assertFalse(throttle.shouldEmitBatteryTempHigh(isRiskActive = true))
        assertFalse(throttle.shouldEmitBatteryTempHigh(isRiskActive = false))
        assertTrue(throttle.shouldEmitBatteryTempHigh(isRiskActive = true))
    }
}
