package io.github.dimidrol.models

import android.os.BatteryManager

enum class BatteryHealth {
    UNKNOWN,
    COLD,
    DEAD,
    GOOD,
    OVERHEAT,
    OVER_VOLTAGE,
    UNSPECIFIED_FAILURE;

    companion object {
        fun fromAndroid(value: Int?): BatteryHealth {
            return when (value) {
                BatteryManager.BATTERY_HEALTH_COLD -> COLD
                BatteryManager.BATTERY_HEALTH_DEAD -> DEAD
                BatteryManager.BATTERY_HEALTH_GOOD -> GOOD
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> OVERHEAT
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> OVER_VOLTAGE
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> UNSPECIFIED_FAILURE
                else -> UNKNOWN
            }
        }
    }
}
