package io.github.dimidrol.impl

import io.github.dimidrol.models.DeviceHealth
import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.RecommendationReason
import io.github.dimidrol.models.RecommendationSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationGenerationTest {

    @Test
    fun generatesReduceAndPauseRecommendationsByHealth() {
        val warm = recommendationForHealth(DeviceHealth.WARM, previousHealth = DeviceHealth.NORMAL)
        val degraded = recommendationForHealth(DeviceHealth.DEGRADED, previousHealth = DeviceHealth.WARM)
        val critical = recommendationForHealth(DeviceHealth.CRITICAL, previousHealth = DeviceHealth.DEGRADED)

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
        val fromWarm = recommendationForHealth(DeviceHealth.NORMAL, previousHealth = DeviceHealth.WARM)
        val fromDegraded = recommendationForHealth(DeviceHealth.NORMAL, previousHealth = DeviceHealth.DEGRADED)
        val fromCritical = recommendationForHealth(DeviceHealth.NORMAL, previousHealth = DeviceHealth.CRITICAL)

        assertNull(fromWarm)
        assertIs<DeviceRecommendation.ResumeWorkload>(fromDegraded)
        assertEquals(RecommendationReason.HEALTH_RECOVERED, fromDegraded.reason)
        assertIs<DeviceRecommendation.ResumeWorkload>(fromCritical)
        assertEquals(RecommendationReason.HEALTH_RECOVERED, fromCritical.reason)
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
}
