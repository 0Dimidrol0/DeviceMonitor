package io.github.dimidrol.impl

import io.github.dimidrol.common.DEFAULT_BOOLEAN

internal class WarningEventThrottle {

    private var memoryLowReported = DEFAULT_BOOLEAN
    private var storageLowReported = DEFAULT_BOOLEAN
    private var batteryLowReported = DEFAULT_BOOLEAN
    private var batteryTempReported = DEFAULT_BOOLEAN
    private var cpuOverloadReported = DEFAULT_BOOLEAN
    private var thermalHeadroomReported = DEFAULT_BOOLEAN
    private var batteryDrainReported = DEFAULT_BOOLEAN

    fun reset() {
        memoryLowReported = DEFAULT_BOOLEAN
        storageLowReported = DEFAULT_BOOLEAN
        batteryLowReported = DEFAULT_BOOLEAN
        batteryTempReported = DEFAULT_BOOLEAN
        cpuOverloadReported = DEFAULT_BOOLEAN
        thermalHeadroomReported = DEFAULT_BOOLEAN
        batteryDrainReported = DEFAULT_BOOLEAN
    }

    fun shouldEmitMemoryLow(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = memoryLowReported
        ) { memoryLowReported = it }
    }

    fun shouldEmitStorageLow(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = storageLowReported
        ) { storageLowReported = it }
    }

    fun shouldEmitBatteryLow(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = batteryLowReported
        ) { batteryLowReported = it }
    }

    fun shouldEmitBatteryTempHigh(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = batteryTempReported
        ) { batteryTempReported = it }
    }

    fun shouldEmitCpuOverload(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = cpuOverloadReported
        ) { cpuOverloadReported = it }
    }

    fun shouldEmitThermalHeadroomLow(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = thermalHeadroomReported
        ) { thermalHeadroomReported = it }
    }

    fun shouldEmitBatteryDrainHigh(isRiskActive: Boolean): Boolean {
        return shouldEmitOnTransition(
            isRiskActive = isRiskActive,
            isAlreadyReported = batteryDrainReported
        ) { batteryDrainReported = it }
    }

    private inline fun shouldEmitOnTransition(
        isRiskActive: Boolean,
        isAlreadyReported: Boolean,
        updateReported: (Boolean) -> Unit
    ): Boolean {
        if (!isRiskActive) {
            updateReported(DEFAULT_BOOLEAN)
            return DEFAULT_BOOLEAN
        }

        if (isAlreadyReported) {
            return DEFAULT_BOOLEAN
        }

        updateReported(true)
        return true
    }
}
