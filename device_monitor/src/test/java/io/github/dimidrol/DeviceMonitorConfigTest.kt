package io.github.dimidrol

import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.RecommendationReason
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
            .recommendationCooldownMs(-100L)
            .thermalHeadroomForecastSeconds(-1)
            .thermalHeadroomLowThreshold(-0.4f)
            .batteryDrainHighThresholdPercentPerHour(-5f)
            .batteryDrainCriticalThresholdPercentPerHour(2f)
            .riskScoreEmaAlpha(5f)
            .healthSmoothingWindowSize(0)
            .enableCpu(false)
            .enableRecommendations(false)
            .build()

        assertEquals(1L, config.samplePeriodMs)
        assertEquals(512L, config.memoryThresholdMb)
        assertEquals(128L, config.storageThresholdMb)
        assertEquals(100f, config.cpuOverloadThresholdPercent)
        assertEquals(5, config.batteryLowThresholdPercent)
        assertEquals(60f, config.batteryTemperatureThresholdC)
        assertEquals(0L, config.recommendationCooldownMs)
        assertEquals(0, config.thermalHeadroomForecastSeconds)
        assertEquals(0f, config.thermalHeadroomLowThreshold)
        assertEquals(0f, config.batteryDrainHighThresholdPercentPerHour)
        assertEquals(2f, config.batteryDrainCriticalThresholdPercentPerHour)
        assertEquals(1f, config.riskScoreEmaAlpha)
        assertEquals(1, config.healthSmoothingWindowSize)
        assertEquals(false, config.enableCpu)
        assertEquals(false, config.enableRecommendations)
    }

    @Test
    fun toBuilderKeepsValues() {
        val customPolicy = RecommendationPolicy { _ ->
            DeviceRecommendation.ResumeWorkload(RecommendationReason.HEALTH_RECOVERED)
        }
        val original = DeviceMonitorConfig(
            samplePeriodMs = 5000L,
            memoryThresholdMb = 1024L,
            storageThresholdMb = 2048L,
            enableRecommendations = false,
            recommendationCooldownMs = 2_500L,
            thermalHeadroomForecastSeconds = 15,
            thermalHeadroomLowThreshold = 0.15f,
            batteryDrainHighThresholdPercentPerHour = 18f,
            batteryDrainCriticalThresholdPercentPerHour = 28f,
            riskScoreEmaAlpha = 0.5f,
            healthSmoothingWindowSize = 5,
            recommendationPolicy = customPolicy,
            enableNetwork = false
        )

        val rebuilt = original.toBuilder().build()

        assertEquals(original, rebuilt)
        assertEquals(customPolicy, rebuilt.recommendationPolicy)
    }
}
