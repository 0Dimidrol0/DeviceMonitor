package io.github.dimidrol.impl

internal class WarningEventThrottle {

    private var memoryLowReported = false
    private var storageLowReported = false
    private var batteryLowReported = false
    private var batteryTempReported = false
    private var cpuOverloadReported = false
    private var thermalHeadroomReported = false
    private var batteryDrainReported = false

    fun reset() {
        memoryLowReported = false
        storageLowReported = false
        batteryLowReported = false
        batteryTempReported = false
        cpuOverloadReported = false
        thermalHeadroomReported = false
        batteryDrainReported = false
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
            updateReported(false)
            return false
        }

        if (isAlreadyReported) {
            return false
        }

        updateReported(true)
        return true
    }
}
