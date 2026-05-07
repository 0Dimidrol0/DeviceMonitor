package io.github.dimidrol

import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.DeviceSnapshot

data class WorkloadReport(
    val name: String,
    val type: WorkloadType,
    val durationMs: Long,
    val startSnapshot: DeviceSnapshot?,
    val endSnapshot: DeviceSnapshot?,
    val maxRiskScore: Int,
    val warningCount: Int,
    val recommendations: List<DeviceRecommendation>
)
