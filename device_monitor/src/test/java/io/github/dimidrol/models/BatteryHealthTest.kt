package io.github.dimidrol.models

import android.os.BatteryManager
import kotlin.test.Test
import kotlin.test.assertEquals

class BatteryHealthTest {

    @Test
    fun mapsKnownHealthValues() {
        assertEquals(BatteryHealth.GOOD, BatteryHealth.fromAndroid(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals(BatteryHealth.OVERHEAT, BatteryHealth.fromAndroid(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals(BatteryHealth.UNKNOWN, BatteryHealth.fromAndroid(null))
    }
}
