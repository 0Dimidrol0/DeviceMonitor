package io.github.dimidrol

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceMonitorConfigTest {

    @Test
    fun builderAppliesLimits() {
        val config = DeviceMonitorConfig.builder()
            .samplePeriodMs(0)
            .memoryThresholdMb(512)
            .storageThresholdMb(128)
            .cpuOverloadThresholdPercent(150f)
            .batteryLowThresholdPercent(5)
            .batteryTemperatureThresholdC(60f)
            .enableCpu(false)
            .build()

        assertEquals(1L, config.samplePeriodMs)
        assertEquals(512L, config.memoryThresholdMb)
        assertEquals(128L, config.storageThresholdMb)
        assertEquals(100f, config.cpuOverloadThresholdPercent)
        assertEquals(5, config.batteryLowThresholdPercent)
        assertEquals(60f, config.batteryTemperatureThresholdC)
        assertEquals(false, config.enableCpu)
    }

    @Test
    fun toBuilderKeepsValues() {
        val original = DeviceMonitorConfig(
            samplePeriodMs = 5000L,
            memoryThresholdMb = 1024L,
            storageThresholdMb = 2048L,
            enableNetwork = false
        )

        val rebuilt = original.toBuilder().build()

        assertEquals(original, rebuilt)
    }
}
