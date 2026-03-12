package io.github.dimidrol

data class DeviceMonitorConfig(
    val samplePeriodMs: Long = DEFAULT_SAMPLE_PERIOD_MS,
    val memoryThresholdMb: Long = DEFAULT_MEMORY_THRESHOLD_MB,
    val storageThresholdMb: Long = DEFAULT_STORAGE_THRESHOLD_MB,
    val cpuOverloadThresholdPercent: Float = DEFAULT_CPU_OVERLOAD_THRESHOLD_PERCENT,
    val batteryLowThresholdPercent: Int = DEFAULT_BATTERY_LOW_THRESHOLD_PERCENT,
    val batteryTemperatureThresholdC: Float = DEFAULT_BATTERY_TEMPERATURE_THRESHOLD_C,
    val enableThermal: Boolean = true,
    val enableBattery: Boolean = true,
    val enableCpu: Boolean = true,
    val enableMemory: Boolean = true,
    val enableStorage: Boolean = true,
    val enableNetwork: Boolean = true
) {

    fun toBuilder(): Builder = Builder().apply {
        samplePeriodMs(samplePeriodMs)
        memoryThresholdMb(memoryThresholdMb)
        storageThresholdMb(storageThresholdMb)
        cpuOverloadThresholdPercent(cpuOverloadThresholdPercent)
        batteryLowThresholdPercent(batteryLowThresholdPercent)
        batteryTemperatureThresholdC(batteryTemperatureThresholdC)
        enableThermal(enableThermal)
        enableBattery(enableBattery)
        enableCpu(enableCpu)
        enableMemory(enableMemory)
        enableStorage(enableStorage)
        enableNetwork(enableNetwork)
    }

    companion object {
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var samplePeriodMs: Long = DEFAULT_SAMPLE_PERIOD_MS
        private var memoryThresholdMb: Long = DEFAULT_MEMORY_THRESHOLD_MB
        private var storageThresholdMb: Long = DEFAULT_STORAGE_THRESHOLD_MB
        private var cpuOverloadThresholdPercent: Float = DEFAULT_CPU_OVERLOAD_THRESHOLD_PERCENT
        private var batteryLowThresholdPercent: Int = DEFAULT_BATTERY_LOW_THRESHOLD_PERCENT
        private var batteryTemperatureThresholdC: Float = DEFAULT_BATTERY_TEMPERATURE_THRESHOLD_C
        private var enableThermal: Boolean = true
        private var enableBattery: Boolean = true
        private var enableCpu: Boolean = true
        private var enableMemory: Boolean = true
        private var enableStorage: Boolean = true
        private var enableNetwork: Boolean = true

        fun samplePeriodMs(value: Long) = apply {
            samplePeriodMs = value
        }

        fun memoryThresholdMb(value: Long) = apply {
            memoryThresholdMb = value
        }

        fun storageThresholdMb(value: Long) = apply {
            storageThresholdMb = value
        }

        fun cpuOverloadThresholdPercent(value: Float) = apply {
            cpuOverloadThresholdPercent = value
        }

        fun batteryLowThresholdPercent(value: Int) = apply {
            batteryLowThresholdPercent = value
        }

        fun batteryTemperatureThresholdC(value: Float) = apply {
            batteryTemperatureThresholdC = value
        }

        fun enableThermal(enabled: Boolean) = apply {
            enableThermal = enabled
        }

        fun enableBattery(enabled: Boolean) = apply {
            enableBattery = enabled
        }

        fun enableCpu(enabled: Boolean) = apply {
            enableCpu = enabled
        }

        fun enableMemory(enabled: Boolean) = apply {
            enableMemory = enabled
        }

        fun enableStorage(enabled: Boolean) = apply {
            enableStorage = enabled
        }

        fun enableNetwork(enabled: Boolean) = apply {
            enableNetwork = enabled
        }

        fun build(): DeviceMonitorConfig {
            return DeviceMonitorConfig(
                samplePeriodMs = samplePeriodMs.coerceAtLeast(1L),
                memoryThresholdMb = memoryThresholdMb.coerceAtLeast(0L),
                storageThresholdMb = storageThresholdMb.coerceAtLeast(0L),
                cpuOverloadThresholdPercent = cpuOverloadThresholdPercent.coerceIn(0f, 100f),
                batteryLowThresholdPercent = batteryLowThresholdPercent.coerceIn(0, 100),
                batteryTemperatureThresholdC = batteryTemperatureThresholdC,
                enableThermal = enableThermal,
                enableBattery = enableBattery,
                enableCpu = enableCpu,
                enableMemory = enableMemory,
                enableStorage = enableStorage,
                enableNetwork = enableNetwork
            )
        }
    }
}
