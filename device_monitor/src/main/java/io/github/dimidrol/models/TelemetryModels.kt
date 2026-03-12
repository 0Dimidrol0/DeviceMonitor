package io.github.dimidrol.models

data class ThermalZoneReading(
    val name: String,
    val type: String?,
    val temperatureC: Float?
)

data class FrameMetricsSnapshot(
    val frameDurationMs: Float,
    val jankPercent: Float,
    val sampleCount: Int
)

data class NetworkTrafficSnapshot(
    val txBytes: Long?,
    val rxBytes: Long?,
    val txBytesDelta: Long?,
    val rxBytesDelta: Long?,
    val periodMs: Long
)

data class BatteryPowerSnapshot(
    val currentMicroAmps: Long?,
    val chargeCounter: Long?,
    val capacityPercent: Int?,
    val timestampMs: Long
)
