package io.github.dimidrol.models

import io.github.dimidrol.DEFAULT_BATTERY_LOW_THRESHOLD_PERCENT
import io.github.dimidrol.DEFAULT_BATTERY_TEMPERATURE_THRESHOLD_C
import io.github.dimidrol.DEFAULT_CPU_OVERLOAD_THRESHOLD_PERCENT

sealed class DeviceWarningEvent {

    data class ThermalChanged(val from: ThermalLevel, val to: ThermalLevel) : DeviceWarningEvent() {
        val hasThermalRisk: Boolean
            get() = to.hasThermalRisk()
    }

    data class MemoryLow(val availBytes: Long, private val thresholdBytes: Long = 1024 * 1024 * 128) : DeviceWarningEvent() {
        val hasMemoryRisk: Boolean
            get() = availBytes < thresholdBytes
    }

    data class StorageLow(
        val freeBytes: Long,
        private val thresholdBytes: Long = 1024 * 1024 * 1024,
        val totalBytes: Long? = null
    ) : DeviceWarningEvent() {
        val isStorageRisk: Boolean
            get() = freeBytes < thresholdBytes
    }

    data class BatteryLow(
        val levelPercent: Int,
        val thresholdPercent: Int = DEFAULT_BATTERY_LOW_THRESHOLD_PERCENT,
        val isCharging: Boolean?
    ) : DeviceWarningEvent() {
        val hasBatteryRisk: Boolean
            get() = levelPercent < thresholdPercent
    }

    data class BatteryTemperatureHigh(
        val temperatureC: Float,
        val thresholdC: Float = DEFAULT_BATTERY_TEMPERATURE_THRESHOLD_C
    ) : DeviceWarningEvent() {
        val hasTemperatureRisk: Boolean
            get() = temperatureC >= thresholdC
    }

    data class CpuOverload(
        val usagePercent: Float,
        val thresholdPercent: Float = DEFAULT_CPU_OVERLOAD_THRESHOLD_PERCENT,
        val coreCount: Int
    ) : DeviceWarningEvent() {
        val isOverloaded: Boolean
            get() = usagePercent >= thresholdPercent
    }
}
