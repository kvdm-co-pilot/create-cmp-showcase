package com.kvdm.fuelled.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {

    @Test
    fun pad2_pads_single_digits() {
        assertEquals("07", pad2(7))
        assertEquals("00", pad2(0))
        assertEquals("15", pad2(15))
    }

    @Test
    fun clockLabel_formats_minutes_of_day() {
        assertEquals("09:15", clockLabel(9 * 60 + 15))
        assertEquals("00:00", clockLabel(0))
        assertEquals("23:59", clockLabel(23 * 60 + 59))
        // wraps past midnight and never goes negative
        assertEquals("00:30", clockLabel(24 * 60 + 30))
        assertEquals("23:30", clockLabel(-30))
    }

    @Test
    fun fixed_renders_stable_decimals() {
        // Binary-exact inputs only — 0.1-style values are not representable and would make
        // these assertions depend on the platform's double formatting.
        assertEquals("12.5", fixed(12.5, 1))
        assertEquals("0.3", fixed(0.25, 1)) // half rounds away from zero
        assertEquals("-0.8", fixed(-0.75, 1))
        assertEquals("3.00", fixed(3.0, 2))
        assertEquals("13", fixed(12.5, 0))
        assertEquals("0.0", fixed(0.0, 1))
    }
}
