package io.github.dimidrol.impl

import io.github.dimidrol.models.ThermalLevel

internal fun inferThermalLevelFromBatteryTemp(tempC: Float?): ThermalLevel {
    if (tempC == null) return ThermalLevel.UNKNOWN

    return when {
        tempC < 38f -> ThermalLevel.NONE
        tempC < 41f -> ThermalLevel.LIGHT
        tempC < 44f -> ThermalLevel.MODERATE
        tempC < 47f -> ThermalLevel.SEVERE
        tempC < 50f -> ThermalLevel.CRITICAL
        else -> ThermalLevel.EMERGENCY
    }
}
