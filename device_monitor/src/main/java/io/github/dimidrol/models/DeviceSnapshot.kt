package io.github.dimidrol.models

import io.github.dimidrol.BYTES_IN_MB

data class DeviceSnapshot(
    val tsMs: Long,
    val thermalStatus: ThermalLevel,
    val batteryTempC: Float?,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val cpuUsagePercent: Float?,
    val cpuUsagePerCore: List<Float>?,
    val cpuFreqKHz: List<Int>?,
    val memAvailBytes: Long?,
    val memThresholdBytes: Long?,
    val memLow: Boolean?,
    val storageFreeBytes: Long?,
    val storageTotalBytes: Long?,
    val networkType: NetworkType?,
    val thermalZones: List<ThermalZoneReading> = emptyList(),
    val frameMetrics: FrameMetricsSnapshot? = null,
    val networkTraffic: NetworkTrafficSnapshot? = null,
    val batteryPower: BatteryPowerSnapshot? = null,
    val uptimeMs: Long? = null,
    val batteryVoltageMv: Int? = null,
    val batteryHealth: BatteryHealth? = null,
    val batteryPlugType: PowerSource? = null
) {

    fun averageCpuUsage(): Float? = cpuUsagePerCore?.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    fun storageUsedBytes(): Long? {
        return storageTotalBytes?.let { total -> total - (storageFreeBytes ?: 0L) }?.coerceAtLeast(0L)
    }

    fun storageUsedPercent(): Float? {
        val total = storageTotalBytes ?: return null
        val used = storageUsedBytes() ?: return null
        return if (total <= 0) null else (used.toFloat() / total * 100f)
    }

    fun riskScore(): Int {
        var score = 0

        if (thermalStatus.hasThermalRisk()) score += 25
        if (batteryTempC != null && batteryTempC >= 45f) score += 12
        if (batteryLevel != null && batteryLevel < 25 && isCharging != true) score += 10
        if (memLow == true) score += 14
        if (storageFreeBytes != null && storageFreeBytes < (BYTES_IN_MB * 512)) score += 10

        val avgCpu = cpuUsagePercent ?: averageCpuUsage()
        if (avgCpu != null && avgCpu > 78f) score += 16

        val GPUjank = frameMetrics?.jankPercent ?: 0f
        if (GPUjank > 5f) score += 8

        return score.coerceAtMost(100)
    }

    fun health(): DeviceHealth = when (riskScore()) {
        in Int.MIN_VALUE until 20 -> DeviceHealth.NORMAL
        in 20 until 40 -> DeviceHealth.WARM
        in 40 until 70 -> DeviceHealth.DEGRADED
        else -> DeviceHealth.CRITICAL
    }

    fun toLogLine(): String {
        val avgCpuFreq = cpuFreqKHz?.filter { it > 0 }?.average()?.toInt()
        val avgCpuUsage = averageCpuUsage()

        return buildString {
            append("ts=").append(tsMs)
            append(" thermal=").append(thermalStatus)
            append(" cpuUsage=").append(cpuUsagePercent)
            append(" cpuAvg=").append(avgCpuUsage)
            append(" freqAvg=").append(avgCpuFreq)
            append(" memAvail=").append(memAvailBytes)
            append(" storageUsed=").append(storageUsedBytes())
            append(" storageFree=").append(storageFreeBytes)
            append(" net=").append(networkType)
            append(" risk=").append(riskScore())
            append(" uptime=").append(uptimeMs)
        }
    }
}
