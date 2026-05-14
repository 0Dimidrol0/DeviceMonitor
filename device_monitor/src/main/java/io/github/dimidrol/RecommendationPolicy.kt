package io.github.dimidrol

import io.github.dimidrol.models.DeviceHealth
import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.RecommendationReason
import io.github.dimidrol.models.RecommendationSeverity

data class RecommendationContext(
    val snapshot: DeviceSnapshot,
    val currentHealth: DeviceHealth,
    val previousHealth: DeviceHealth?,
    val thermalHeadroomLowThreshold: Float,
    val batteryDrainCriticalThresholdPercentPerHour: Float,
    val recommendationCooldownMs: Long
)

fun interface RecommendationPolicy {
    fun recommend(context: RecommendationContext): DeviceRecommendation?
}

object DefaultRecommendationPolicy : RecommendationPolicy {

    override fun recommend(context: RecommendationContext): DeviceRecommendation? {
        val snapshot = context.snapshot

        if (context.currentHealth == DeviceHealth.CRITICAL) {
            return DeviceRecommendation.PauseWorkload(
                reason = RecommendationReason.HEALTH_CRITICAL,
                severity = RecommendationSeverity.CRITICAL
            )
        }

        if (context.currentHealth == DeviceHealth.NORMAL &&
            (context.previousHealth == DeviceHealth.DEGRADED || context.previousHealth == DeviceHealth.CRITICAL)
        ) {
            return DeviceRecommendation.ResumeWorkload(
                reason = RecommendationReason.HEALTH_RECOVERED
            )
        }

        val drainPercentPerHour = snapshot.batteryDrain?.drainPercentPerHour
        if (drainPercentPerHour != null &&
            snapshot.isCharging != true &&
            drainPercentPerHour >= context.batteryDrainCriticalThresholdPercentPerHour
        ) {
            return DeviceRecommendation.DelayHeavyTask(
                reason = RecommendationReason.BATTERY_DRAIN_HIGH,
                retryAfterMs = context.recommendationCooldownMs.coerceAtLeast(10_000L)
            )
        }

        val thermalLow = listOfNotNull(
            snapshot.thermalHeadroom?.currentHeadroom,
            snapshot.thermalHeadroom?.forecastHeadroom
        ).any { it <= context.thermalHeadroomLowThreshold }
        if (thermalLow && context.currentHealth != DeviceHealth.NORMAL) {
            return DeviceRecommendation.ReduceWorkload(
                reason = RecommendationReason.THERMAL_HEADROOM_LOW,
                severity = RecommendationSeverity.HIGH,
                suggestedScale = 0.7f
            )
        }

        return when (context.currentHealth) {
            DeviceHealth.WARM -> DeviceRecommendation.ReduceWorkload(
                reason = RecommendationReason.HEALTH_WARM,
                severity = RecommendationSeverity.MEDIUM,
                suggestedScale = 0.8f
            )

            DeviceHealth.DEGRADED -> DeviceRecommendation.ReduceWorkload(
                reason = RecommendationReason.HEALTH_DEGRADED,
                severity = RecommendationSeverity.HIGH,
                suggestedScale = 0.55f
            )

            DeviceHealth.NORMAL,
            DeviceHealth.CRITICAL -> null
        }
    }
}
