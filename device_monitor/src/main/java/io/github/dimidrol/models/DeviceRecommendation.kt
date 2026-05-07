package io.github.dimidrol.models

enum class RecommendationReason {
    HEALTH_WARM,
    HEALTH_DEGRADED,
    HEALTH_CRITICAL,
    HEALTH_RECOVERED,
    THERMAL_HEADROOM_LOW,
    BATTERY_DRAIN_HIGH
}

enum class RecommendationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

sealed class DeviceRecommendation {
    data class ReduceWorkload(
        val reason: RecommendationReason,
        val severity: RecommendationSeverity,
        val suggestedScale: Float
    ) : DeviceRecommendation()

    data class PauseWorkload(
        val reason: RecommendationReason,
        val severity: RecommendationSeverity
    ) : DeviceRecommendation()

    data class ResumeWorkload(
        val reason: RecommendationReason
    ) : DeviceRecommendation()

    data class DelayHeavyTask(
        val reason: RecommendationReason,
        val retryAfterMs: Long
    ) : DeviceRecommendation()
}
