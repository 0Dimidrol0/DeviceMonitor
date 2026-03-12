package io.github.dimidrol.models

import io.github.dimidrol.BYTES_IN_MB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceSnapshotTest {

    @Test
    fun healthySnapshotIsNormal() {
        val snapshot = DeviceSnapshot(
            tsMs = 0,
            thermalStatus = ThermalLevel.NONE,
            batteryTempC = 30f,
            batteryLevel = 92,
            isCharging = true,
            cpuUsagePercent = 12f,
            cpuUsagePerCore = listOf(10f, 13f),
            cpuFreqKHz = emptyList(),
            memAvailBytes = 256 * BYTES_IN_MB,
            memThresholdBytes = 512 * 1024,
            memLow = false,
            storageFreeBytes = 512 * BYTES_IN_MB,
            storageTotalBytes = 100 * BYTES_IN_MB,
            networkType = NetworkType.WIFI,
            thermalZones = emptyList(),
            frameMetrics = null,
            networkTraffic = null,
            batteryPower = null,
            uptimeMs = 5_000L,
            batteryVoltageMv = 3800,
            batteryHealth = BatteryHealth.GOOD,
            batteryPlugType = PowerSource.BATTERY
        )

        assertEquals(0, snapshot.riskScore())
        assertEquals(DeviceHealth.NORMAL, snapshot.health())
    }

    @Test
    fun overloadedSnapshotIsCritical() {
        val snapshot = DeviceSnapshot(
            tsMs = 0,
            thermalStatus = ThermalLevel.CRITICAL,
            batteryTempC = 50f,
            batteryLevel = 10,
            isCharging = false,
            cpuUsagePercent = 92f,
            cpuUsagePerCore = listOf(90f, 95f, 88f),
            cpuFreqKHz = listOf(1000),
            memAvailBytes = 32 * 1024 * 1024L,
            memThresholdBytes = 64 * 1024 * 1024L,
            memLow = true,
            storageFreeBytes = 50 * 1024 * 1024L,
            storageTotalBytes = 200 * BYTES_IN_MB,
            networkType = NetworkType.CELLULAR,
            thermalZones = listOf(ThermalZoneReading("thermal_zone0", "cpu", 45f)),
            frameMetrics = FrameMetricsSnapshot(16f, 25f, 10),
            networkTraffic = NetworkTrafficSnapshot(1_000L, 2_000L, 100L, 200L, 1_000L),
            batteryPower = null,
            uptimeMs = 10_000L,
            batteryVoltageMv = 3700,
            batteryHealth = BatteryHealth.OVERHEAT,
            batteryPlugType = PowerSource.USB
        )

        assertTrue(snapshot.riskScore() >= 85)
        assertEquals(DeviceHealth.CRITICAL, snapshot.health())
        assertEquals(91f, snapshot.averageCpuUsage())
    }

    @Test
    fun storageUsedPercentIsCalculatedFromTotals() {
        val snapshot = DeviceSnapshot(
            tsMs = 0,
            thermalStatus = ThermalLevel.UNKNOWN,
            batteryTempC = null,
            batteryLevel = null,
            isCharging = null,
            cpuUsagePercent = null,
            cpuUsagePerCore = null,
            cpuFreqKHz = null,
            memAvailBytes = null,
            memThresholdBytes = null,
            memLow = null,
            storageFreeBytes = 300 * BYTES_IN_MB,
            storageTotalBytes = 600 * BYTES_IN_MB,
            networkType = NetworkType.NONE,
            thermalZones = emptyList(),
            frameMetrics = null,
            networkTraffic = null,
            batteryPower = null,
            uptimeMs = null,
            batteryVoltageMv = null,
            batteryHealth = null,
            batteryPlugType = null
        )

        assertEquals(50f, snapshot.storageUsedPercent())
    }
}
