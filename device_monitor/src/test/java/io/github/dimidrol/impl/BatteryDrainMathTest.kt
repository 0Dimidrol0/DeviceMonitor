package io.github.dimidrol.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatteryDrainMathTest {

    @Test
    fun calculatesDrainPercentPerHourFromChargeCounterDelta() {
        val drain = calculateBatteryDrainPercentPerHour(
            previousChargeCounterUaH = 3_200_000L,
            previousCapacityPercent = 80,
            previousTimestampMs = 0L,
            currentChargeCounterUaH = 3_000_000L,
            capacityPercent = 75,
            currentTimestampMs = 3_600_000L
        )

        assertEquals(5f, drain)
    }

    @Test
    fun returnsNullWhenInputsAreInsufficientOrCharging() {
        assertNull(
            calculateBatteryDrainPercentPerHour(
                previousChargeCounterUaH = null,
                previousCapacityPercent = 80,
                previousTimestampMs = 0L,
                currentChargeCounterUaH = 3_000_000L,
                capacityPercent = 75,
                currentTimestampMs = 3_600_000L
            )
        )

        assertNull(
            calculateBatteryDrainPercentPerHour(
                previousChargeCounterUaH = 3_000_000L,
                previousCapacityPercent = 75,
                previousTimestampMs = 0L,
                currentChargeCounterUaH = 3_050_000L,
                capacityPercent = 76,
                currentTimestampMs = 3_600_000L
            )
        )
    }

    @Test
    fun usesPreviousCapacityWhenCurrentCapacityIsMissing() {
        val drain = calculateBatteryDrainPercentPerHour(
            previousChargeCounterUaH = 4_000_000L,
            previousCapacityPercent = 80,
            previousTimestampMs = 0L,
            currentChargeCounterUaH = 3_800_000L,
            capacityPercent = null,
            currentTimestampMs = 1_800_000L
        )

        assertTrue(drain != null && drain > 0f)
    }
}
