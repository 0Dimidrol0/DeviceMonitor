package io.github.dimidrol

/**
 * Marketplace-inspired presets for common Android app types.
 *
 * Use [DeviceMonitorProfiles.config] when you want a production-ready baseline
 * without manual threshold tuning.
 */
enum class AppProfile {
    SOCIAL_SHORT_VIDEO,
    VIDEO_STREAMING_ON_DEMAND,
    LIVE_STREAMING_CREATOR,
    VIDEO_CALL_MESSAGING,
    REALTIME_GAMING,
    PHOTO_VIDEO_EDITING,
    MUSIC_AUDIO_STREAMING,
    SHOPPING_ECOMMERCE,
    MAPS_NAVIGATION_DELIVERY,
    PRODUCTIVITY_COLLABORATION
}

object DeviceMonitorProfiles {

    /**
     * Returns all built-in marketplace presets.
     */
    @JvmStatic
    fun allProfiles(): List<AppProfile> = AppProfile.values().toList()

    /**
     * Returns a ready-to-use [DeviceMonitorConfig] for the given [profile].
     */
    @JvmStatic
    fun config(profile: AppProfile): DeviceMonitorConfig {
        return apply(profile, DeviceMonitorConfig.builder()).build()
    }

    /**
     * Applies a preset to an existing [builder], so you can further override values.
     */
    @JvmStatic
    fun apply(
        profile: AppProfile,
        builder: DeviceMonitorConfig.Builder
    ): DeviceMonitorConfig.Builder {
        return when (profile) {
            AppProfile.SOCIAL_SHORT_VIDEO -> baseAdaptive(builder)
                .samplePeriodMs(2_000L)
                .memoryThresholdMb(384L)
                .storageThresholdMb(1_536L)
                .cpuOverloadThresholdPercent(78f)
                .batteryLowThresholdPercent(25)
                .batteryTemperatureThresholdC(43.5f)
                .recommendationCooldownMs(8_000L)
                .thermalHeadroomForecastSeconds(15)
                .thermalHeadroomLowThreshold(0.25f)
                .batteryDrainHighThresholdPercentPerHour(18f)
                .batteryDrainCriticalThresholdPercentPerHour(30f)
                .riskScoreEmaAlpha(0.45f)
                .healthSmoothingWindowSize(4)

            AppProfile.VIDEO_STREAMING_ON_DEMAND -> baseAdaptive(builder)
                .samplePeriodMs(2_500L)
                .memoryThresholdMb(320L)
                .storageThresholdMb(1_024L)
                .cpuOverloadThresholdPercent(82f)
                .batteryLowThresholdPercent(20)
                .batteryTemperatureThresholdC(44.5f)
                .recommendationCooldownMs(10_000L)
                .thermalHeadroomForecastSeconds(10)
                .thermalHeadroomLowThreshold(0.22f)
                .batteryDrainHighThresholdPercentPerHour(16f)
                .batteryDrainCriticalThresholdPercentPerHour(28f)
                .riskScoreEmaAlpha(0.35f)
                .healthSmoothingWindowSize(3)

            AppProfile.LIVE_STREAMING_CREATOR -> baseAdaptive(builder)
                .samplePeriodMs(1_500L)
                .memoryThresholdMb(512L)
                .storageThresholdMb(1_536L)
                .cpuOverloadThresholdPercent(74f)
                .batteryLowThresholdPercent(30)
                .batteryTemperatureThresholdC(42.5f)
                .recommendationCooldownMs(6_000L)
                .thermalHeadroomForecastSeconds(20)
                .thermalHeadroomLowThreshold(0.28f)
                .batteryDrainHighThresholdPercentPerHour(22f)
                .batteryDrainCriticalThresholdPercentPerHour(36f)
                .riskScoreEmaAlpha(0.5f)
                .healthSmoothingWindowSize(5)

            AppProfile.VIDEO_CALL_MESSAGING -> baseAdaptive(builder)
                .samplePeriodMs(2_000L)
                .memoryThresholdMb(384L)
                .storageThresholdMb(1_024L)
                .cpuOverloadThresholdPercent(76f)
                .batteryLowThresholdPercent(25)
                .batteryTemperatureThresholdC(42.5f)
                .recommendationCooldownMs(7_000L)
                .thermalHeadroomForecastSeconds(15)
                .thermalHeadroomLowThreshold(0.26f)
                .batteryDrainHighThresholdPercentPerHour(18f)
                .batteryDrainCriticalThresholdPercentPerHour(30f)
                .riskScoreEmaAlpha(0.45f)
                .healthSmoothingWindowSize(4)

            AppProfile.REALTIME_GAMING -> baseAdaptive(builder)
                .samplePeriodMs(1_200L)
                .memoryThresholdMb(640L)
                .storageThresholdMb(2_048L)
                .cpuOverloadThresholdPercent(70f)
                .batteryLowThresholdPercent(30)
                .batteryTemperatureThresholdC(42f)
                .recommendationCooldownMs(5_000L)
                .thermalHeadroomForecastSeconds(20)
                .thermalHeadroomLowThreshold(0.3f)
                .batteryDrainHighThresholdPercentPerHour(25f)
                .batteryDrainCriticalThresholdPercentPerHour(40f)
                .riskScoreEmaAlpha(0.55f)
                .healthSmoothingWindowSize(5)

            AppProfile.PHOTO_VIDEO_EDITING -> baseAdaptive(builder)
                .samplePeriodMs(2_000L)
                .memoryThresholdMb(768L)
                .storageThresholdMb(4_096L)
                .cpuOverloadThresholdPercent(74f)
                .batteryLowThresholdPercent(28)
                .batteryTemperatureThresholdC(43f)
                .recommendationCooldownMs(7_000L)
                .thermalHeadroomForecastSeconds(15)
                .thermalHeadroomLowThreshold(0.25f)
                .batteryDrainHighThresholdPercentPerHour(23f)
                .batteryDrainCriticalThresholdPercentPerHour(37f)
                .riskScoreEmaAlpha(0.5f)
                .healthSmoothingWindowSize(4)

            AppProfile.MUSIC_AUDIO_STREAMING -> baseAdaptive(builder)
                .samplePeriodMs(4_000L)
                .memoryThresholdMb(256L)
                .storageThresholdMb(1_024L)
                .cpuOverloadThresholdPercent(88f)
                .batteryLowThresholdPercent(18)
                .batteryTemperatureThresholdC(45f)
                .recommendationCooldownMs(12_000L)
                .thermalHeadroomForecastSeconds(8)
                .thermalHeadroomLowThreshold(0.2f)
                .batteryDrainHighThresholdPercentPerHour(12f)
                .batteryDrainCriticalThresholdPercentPerHour(24f)
                .riskScoreEmaAlpha(0.3f)
                .healthSmoothingWindowSize(3)

            AppProfile.SHOPPING_ECOMMERCE -> baseAdaptive(builder)
                .samplePeriodMs(5_000L)
                .memoryThresholdMb(320L)
                .storageThresholdMb(1_536L)
                .cpuOverloadThresholdPercent(90f)
                .batteryLowThresholdPercent(15)
                .batteryTemperatureThresholdC(45f)
                .recommendationCooldownMs(15_000L)
                .thermalHeadroomForecastSeconds(8)
                .thermalHeadroomLowThreshold(0.18f)
                .batteryDrainHighThresholdPercentPerHour(10f)
                .batteryDrainCriticalThresholdPercentPerHour(20f)
                .riskScoreEmaAlpha(0.25f)
                .healthSmoothingWindowSize(3)

            AppProfile.MAPS_NAVIGATION_DELIVERY -> baseAdaptive(builder)
                .samplePeriodMs(3_000L)
                .memoryThresholdMb(384L)
                .storageThresholdMb(1_536L)
                .cpuOverloadThresholdPercent(82f)
                .batteryLowThresholdPercent(22)
                .batteryTemperatureThresholdC(43.5f)
                .recommendationCooldownMs(9_000L)
                .thermalHeadroomForecastSeconds(12)
                .thermalHeadroomLowThreshold(0.24f)
                .batteryDrainHighThresholdPercentPerHour(16f)
                .batteryDrainCriticalThresholdPercentPerHour(30f)
                .riskScoreEmaAlpha(0.4f)
                .healthSmoothingWindowSize(4)

            AppProfile.PRODUCTIVITY_COLLABORATION -> baseAdaptive(builder)
                .samplePeriodMs(5_000L)
                .memoryThresholdMb(320L)
                .storageThresholdMb(1_024L)
                .cpuOverloadThresholdPercent(92f)
                .batteryLowThresholdPercent(15)
                .batteryTemperatureThresholdC(45f)
                .recommendationCooldownMs(15_000L)
                .thermalHeadroomForecastSeconds(8)
                .thermalHeadroomLowThreshold(0.18f)
                .batteryDrainHighThresholdPercentPerHour(10f)
                .batteryDrainCriticalThresholdPercentPerHour(20f)
                .riskScoreEmaAlpha(0.25f)
                .healthSmoothingWindowSize(3)
        }
    }

    private fun baseAdaptive(builder: DeviceMonitorConfig.Builder): DeviceMonitorConfig.Builder {
        return builder
            .enableRecommendations(true)
            .enableThermalHeadroom(true)
            .enableBatteryDrain(true)
            .enableMetricSmoothing(true)
            .enableThermal(true)
            .enableBattery(true)
            .enableCpu(true)
            .enableMemory(true)
            .enableStorage(true)
            .enableNetwork(true)
    }
}
