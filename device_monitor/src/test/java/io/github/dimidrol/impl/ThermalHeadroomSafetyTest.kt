package io.github.dimidrol.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThermalHeadroomSafetyTest {

    @Test
    fun keepsValidHeadroomValues() {
        assertEquals(0.25f, safeThermalHeadroomValue(0.25f))
        assertEquals(1.4f, safeThermalHeadroomValue(1.4f))
    }

    @Test
    fun dropsUnsupportedOrInvalidHeadroomValues() {
        assertNull(safeThermalHeadroomValue(null))
        assertNull(safeThermalHeadroomValue(Float.NaN))
        assertNull(safeThermalHeadroomValue(Float.POSITIVE_INFINITY))
        assertNull(safeThermalHeadroomValue(-0.1f))
    }
}
