package io.github.dimidrol.impl

import io.github.dimidrol.WorkloadType
import io.github.dimidrol.models.DeviceRecommendation
import io.github.dimidrol.models.DeviceSnapshot
import io.github.dimidrol.models.NetworkType
import io.github.dimidrol.models.RecommendationReason
import io.github.dimidrol.models.RecommendationSeverity
import io.github.dimidrol.models.ThermalLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkloadSessionReportTest {

    @Test
    fun reportIncludesDurationRiskWarningsAndRecommendations() {
        val session = DeviceMonitorImpl.MonitorWorkloadSession(
            name = "encoder",
            type = WorkloadType.MEDIA_PROCESSING
        )

        session.start()
        session.recordSnapshot(buildSnapshot(riskThermal = ThermalLevel.SEVERE, cpuUsage = 92f))
        session.recordWarning()
        session.recordWarning()
        session.recordRecommendation(
            DeviceRecommendation.ReduceWorkload(
                reason = RecommendationReason.HEALTH_DEGRADED,
                severity = RecommendationSeverity.HIGH,
                suggestedScale = 0.55f
            )
        )

        val report = session.stop()

        assertEquals("encoder", report.name)
        assertEquals(WorkloadType.MEDIA_PROCESSING, report.type)
        assertTrue(report.durationMs >= 0L)
        assertEquals(2, report.warningCount)
        assertEquals(1, report.recommendations.size)
        assertTrue(report.maxRiskScore > 0)
    }

    private fun buildSnapshot(riskThermal: ThermalLevel, cpuUsage: Float): DeviceSnapshot {
        return DeviceSnapshot(
            tsMs = 0L,
            thermalStatus = riskThermal,
            batteryTempC = 47f,
            batteryLevel = 12,
            isCharging = false,
            cpuUsagePercent = cpuUsage,
            cpuUsagePerCore = listOf(cpuUsage),
            cpuFreqKHz = listOf(1000),
            memAvailBytes = 40L,
            memThresholdBytes = 100L,
            memLow = true,
            storageFreeBytes = 100L,
            storageTotalBytes = 1_000L,
            networkType = NetworkType.WIFI
        )
    }
}
