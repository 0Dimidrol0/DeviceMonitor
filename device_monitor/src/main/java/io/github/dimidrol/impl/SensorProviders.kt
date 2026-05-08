package io.github.dimidrol.impl

import io.github.dimidrol.models.BatteryDrainSnapshot
import io.github.dimidrol.models.BatteryPowerSnapshot
import io.github.dimidrol.models.NetworkTrafficSnapshot
import io.github.dimidrol.models.NetworkType
import io.github.dimidrol.models.ThermalHeadroomSnapshot
import io.github.dimidrol.models.ThermalLevel
import io.github.dimidrol.models.ThermalZoneReading

internal interface CpuSensorProvider {
    fun readCpuUsagePercent(): Float?
    fun readCpuUsagePerCore(): List<Float>?
    fun readCpuFrequenciesKHz(): List<Int>?
}

internal interface ThermalSensorProvider {
    fun readThermalLevel(): ThermalLevel
    fun readThermalZones(): List<ThermalZoneReading>?
    fun readThermalHeadroom(status: ThermalLevel): ThermalHeadroomSnapshot?
}

internal data class BatterySensorSnapshot(
    val power: BatteryPowerSnapshot?,
    val drain: BatteryDrainSnapshot?
)

internal interface BatterySensorProvider {
    fun readBatterySnapshot(): BatterySensorSnapshot
}

internal interface NetworkSensorProvider {
    fun readNetworkType(): NetworkType
    fun readNetworkTraffic(): NetworkTrafficSnapshot?
}

internal class DelegatingCpuSensorProvider(
    private val cpuUsageReader: () -> Float?,
    private val cpuUsagePerCoreReader: () -> List<Float>?,
    private val cpuFrequenciesReader: () -> List<Int>?
) : CpuSensorProvider {
    override fun readCpuUsagePercent(): Float? = cpuUsageReader()
    override fun readCpuUsagePerCore(): List<Float>? = cpuUsagePerCoreReader()
    override fun readCpuFrequenciesKHz(): List<Int>? = cpuFrequenciesReader()
}

internal class DelegatingThermalSensorProvider(
    private val thermalLevelReader: () -> ThermalLevel,
    private val thermalZonesReader: () -> List<ThermalZoneReading>?,
    private val thermalHeadroomReader: (ThermalLevel) -> ThermalHeadroomSnapshot?
) : ThermalSensorProvider {
    override fun readThermalLevel(): ThermalLevel = thermalLevelReader()
    override fun readThermalZones(): List<ThermalZoneReading>? = thermalZonesReader()
    override fun readThermalHeadroom(status: ThermalLevel): ThermalHeadroomSnapshot? = thermalHeadroomReader(status)
}

internal class DelegatingBatterySensorProvider(
    private val batterySnapshotReader: () -> BatterySensorSnapshot
) : BatterySensorProvider {
    override fun readBatterySnapshot(): BatterySensorSnapshot = batterySnapshotReader()
}

internal class DelegatingNetworkSensorProvider(
    private val networkTypeReader: () -> NetworkType,
    private val networkTrafficReader: () -> NetworkTrafficSnapshot?
) : NetworkSensorProvider {
    override fun readNetworkType(): NetworkType = networkTypeReader()
    override fun readNetworkTraffic(): NetworkTrafficSnapshot? = networkTrafficReader()
}
