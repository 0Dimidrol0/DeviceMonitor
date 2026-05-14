package io.github.dimidrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceMonitorProfilesTest {

    @Test
    fun hasTenMarketplaceProfiles() {
        assertEquals(10, DeviceMonitorProfiles.allProfiles().size)
    }

    @Test
    fun eachProfileBuildsValidAdaptiveConfig() {
        DeviceMonitorProfiles.allProfiles().forEach { profile ->
            val config = DeviceMonitorProfiles.config(profile)

            assertTrue(config.samplePeriodMs >= 1L)
            assertTrue(config.enableRecommendations)
            assertTrue(config.enableThermalHeadroom)
            assertTrue(config.enableBatteryDrain)
            assertTrue(config.enableMetricSmoothing)
            assertTrue(
                config.batteryDrainCriticalThresholdPercentPerHour >=
                    config.batteryDrainHighThresholdPercentPerHour
            )
        }
    }

    @Test
    fun realtimeGamingProfileIsAggressive() {
        val config = DeviceMonitorProfiles.config(AppProfile.REALTIME_GAMING)

        assertTrue(config.samplePeriodMs <= 1_500L)
        assertTrue(config.cpuOverloadThresholdPercent <= 72f)
        assertTrue(config.batteryTemperatureThresholdC <= 42.5f)
        assertTrue(config.batteryDrainCriticalThresholdPercentPerHour >= 35f)
    }
}
