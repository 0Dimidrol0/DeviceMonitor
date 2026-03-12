package io.github.dimidrol.models

import android.os.BatteryManager

enum class PowerSource {
    UNKNOWN,
    AC,
    USB,
    WIRELESS,
    BATTERY;

    companion object {
        fun fromAndroid(value: Int?): PowerSource {
            return when (value) {
                BatteryManager.BATTERY_PLUGGED_AC -> AC
                BatteryManager.BATTERY_PLUGGED_USB -> USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> WIRELESS
                BatteryManager.BATTERY_PLUGGED_DOCK -> BATTERY
                else -> UNKNOWN
            }
        }
    }
}
