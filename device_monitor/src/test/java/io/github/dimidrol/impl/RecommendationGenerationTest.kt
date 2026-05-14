package io.github.dimidrol.impl

import io.github.dimidrol.DefaultRecommendationPolicy
import io.github.dimidrol.RecommendationContext
import io.github.dimidrol.models.BatteryDrainSnapshot
import io.github.dimidrol.models.DeviceHealth
import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.NetworkType
import io.github.dimidrol.models.RecommendationReason
import io.github.dimidrol.models.RecommendationSeverity
import io.github.dimidrol.models.ThermalHeadroomSnapshot
import io.github.dimidrol.models.ThermalLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationGenerationTest {

    @Test
    fun generatesReduceAndPauseRecommendationsByHealth() {
        val warm = DefaultRecommendationPolicy.recommend(
            context(health = DeviceHealth.WARM, previousHealth = DeviceHealth.NORMAL)
        )
        val degraded = DefaultRecommendationPolicy.recommend(
            context(health = DeviceHealth.DEGRADED, previousHealth = DeviceHealth.WARM)
        )
        val critical = DefaultRecommendationPolicy.recommend(
            context(health = DeviceHealth.CRITICAL, previousHealth = DeviceHealth.DEGRADED)
        )

        assertIs<DeviceRecommendation.ReduceWorkload>(warm)
        assertEquals(RecommendationReason.HEALTH_WARM, warm.reason)
        assertEquals(RecommendationSeverity.MEDIUM, warm.severity)
        assertEquals(0.8f, warm.suggestedScale)

        assertIs<DeviceRecommendation.ReduceWorkload>(degraded)
        assertEquals(RecommendationReason.HEALTH_DEGRADED, degraded.reason)
        assertEquals(RecommendationSeverity.HIGH, degraded.severity)
        assertEquals(0.55f, degraded.suggestedScale)

        assertIs<DeviceRecommendation.PauseWorkload>(critical)
        assertEquals(RecommendationReason.HEALTH_CRITICAL, critical.reason)
        assertEquals(RecommendationSeverity.CRITICAL, critical.severity)
    }

    @Test
    fun emitsResumeOnlyWhenRecoveredFromDegradedOrCritical() {
        val fromWarm = DefaultRecommendationPolicy.recommend(
            context(health = DeviceHealth.NORMAL, previousHealth = DeviceHealth.WARM)
        )
        val fromDegraded = DefaultRecommendationPolicy.recommend(
            context(health = DeviceHealth.NORMAL, previousHealth = DeviceHealth.DEGRADED)
        )
        val fromCritical = DefaultRecommendationPolicy.recommend(
            context(health = DeviceHealth.NORMAL, previousHealth = DeviceHealth.CRITICAL)
        )

        assertNull(fromWarm)
        assertIs<DeviceRecommendation.ResumeWorkload>(fromDegraded)
        assertEquals(RecommendationReason.HEALTH_RECOVERED, fromDegraded.reason)
        assertIs<DeviceRecommendation.ResumeWorkload>(fromCritical)
        assertEquals(RecommendationReason.HEALTH_RECOVERED, fromCritical.reason)
    }

    @Test
    fun delaysHeavyTaskWhenDrainIsCritical() {
        val recommendation = DefaultRecommendationPolicy.recommend(
            context(
                health = DeviceHealth.WARM,
                snapshot = baseSnapshot(
                    batteryDrain = BatteryDrainSnapshot(
                        currentMicroAmps = -420_000L,
                        voltageMv = 3800,
                        estimatedPowerWatts = 1.6f,
                        drainPercentPerHour = 40f,
                        estimatedTimeToEmptyMs = 2_000_000L
                    ),
                    isCharging = false
                )
            )
        )

        assertIs<DeviceRecommendation.DelayHeavyTask>(recommendation)
        assertEquals(RecommendationReason.BATTERY_DRAIN_HIGH, recommendation.reason)
        assertTrue(recommendation.retryAfterMs >= 10_000L)
    }

    @Test
    fun emitsHeadroomRecommendationWhenThermalHeadroomIsLow() {
        val recommendation = DefaultRecommendationPolicy.recommend(
            context(
                health = DeviceHealth.DEGRADED,
                snapshot = baseSnapshot(
                    thermalHeadroom = ThermalHeadroomSnapshot(
                        currentHeadroom = 0.12f,
                        forecastHeadroom = 0.09f,
                        forecastSeconds = 10,
                        status = ThermalLevel.SEVERE
                    )
                )
            )
        )

        assertIs<DeviceRecommendation.ReduceWorkload>(recommendation)
        assertEquals(RecommendationReason.THERMAL_HEADROOM_LOW, recommendation.reason)
        assertEquals(0.7f, recommendation.suggestedScale)
    }

    @Test
    fun throttleSuppressesDuplicatesUntilResetOrStateChange() {
        val throttle = RecommendationEventThrottle(cooldownMs = 1_000L)

        assertTrue(throttle.shouldEmit("critical", nowMs = 1_000L))
        assertFalse(throttle.shouldEmit("critical", nowMs = 1_200L))
        assertFalse(throttle.shouldEmit("critical", nowMs = 5_000L))

        assertTrue(throttle.shouldEmit("degraded", nowMs = 5_100L))
        assertFalse(throttle.shouldEmit("degraded", nowMs = 5_200L))

        assertFalse(throttle.shouldEmit(key = null, nowMs = 5_300L))
        assertTrue(throttle.shouldEmit("critical", nowMs = 7_000L))
    }

    private fun context(
        health: DeviceHealth,
        previousHealth: DeviceHealth? = null,
        snapshot: DeviceSnapshot = baseSnapshot()
    ): RecommendationContext {
        return RecommendationContext(
            snapshot = snapshot,
            currentHealth = health,
            previousHealth = previousHealth,
            thermalHeadroomLowThreshold = 0.2f,
            batteryDrainCriticalThresholdPercentPerHour = 35f,
            recommendationCooldownMs = 15_000L
        )
    }

    private fun baseSnapshot(
        thermalHeadroom: ThermalHeadroomSnapshot? = null,
        batteryDrain: BatteryDrainSnapshot? = null,
        isCharging: Boolean? = false
    ): DeviceSnapshot {
        return DeviceSnapshot(
            tsMs = 1L,
            thermalStatus = ThermalLevel.NONE,
            batteryTempC = 35f,
            batteryLevel = 80,
            isCharging = isCharging,
            cpuUsagePercent = 20f,
            cpuUsagePerCore = listOf(20f),
            cpuFreqKHz = listOf(1200),
            memAvailBytes = 512L * 1024L * 1024L,
            memThresholdBytes = 128L * 1024L * 1024L,
            memLow = false,
            storageFreeBytes = 2_000L * 1024L * 1024L,
            storageTotalBytes = 4_000L * 1024L * 1024L,
            networkType = NetworkType.WIFI,
            thermalHeadroom = thermalHeadroom,
            batteryDrain = batteryDrain
        )
    }
}
