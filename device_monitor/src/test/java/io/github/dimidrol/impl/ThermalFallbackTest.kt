package io.github.dimidrol.impl

import io.github.dimidrol.models.ThermalLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class ThermalFallbackTest {

    @Test
    fun returnsUnknownWhenTemperatureIsMissing() {
        assertEquals(ThermalLevel.UNKNOWN, inferThermalLevelFromBatteryTemp(null))
    }

    @Test
    fun mapsBatteryTemperatureToThermalLevels() {
        assertEquals(ThermalLevel.NONE, inferThermalLevelFromBatteryTemp(36.5f))
        assertEquals(ThermalLevel.LIGHT, inferThermalLevelFromBatteryTemp(39f))
        assertEquals(ThermalLevel.MODERATE, inferThermalLevelFromBatteryTemp(42f))
        assertEquals(ThermalLevel.SEVERE, inferThermalLevelFromBatteryTemp(45f))
        assertEquals(ThermalLevel.CRITICAL, inferThermalLevelFromBatteryTemp(48f))
        assertEquals(ThermalLevel.EMERGENCY, inferThermalLevelFromBatteryTemp(51f))
    }
}
