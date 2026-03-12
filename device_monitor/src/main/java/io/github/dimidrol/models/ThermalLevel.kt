package io.github.dimidrol.models

enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN;

    companion object {
        fun fromInt(value: Int?): ThermalLevel {
            return when (value) {
                0 -> NONE
                1 -> LIGHT
                2 -> MODERATE
                3 -> SEVERE
                4 -> CRITICAL
                5 -> EMERGENCY
                6 -> SHUTDOWN
                else -> UNKNOWN
            }
        }
    }
}

fun ThermalLevel.hasThermalRisk(): Boolean =
    this == ThermalLevel.MODERATE ||
            this == ThermalLevel.SEVERE ||
            this == ThermalLevel.CRITICAL ||
            this == ThermalLevel.EMERGENCY ||
            this == ThermalLevel.SHUTDOWN