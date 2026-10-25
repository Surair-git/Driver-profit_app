package com.surair.driverprofit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FmtTest {
    @Test
    fun money() {
        assertEquals("$15.000", Fmt.money(15_000.0))
        assertEquals("$0", Fmt.money(0.0))
        assertEquals("-$1.200", Fmt.money(-1200.0))
        assertEquals("$1.234.568", Fmt.money(1_234_567.6))
    }

    @Test
    fun decimals() {
        assertEquals("42,3", Fmt.dec(42.34))
        assertEquals("1.000,0", Fmt.dec(1000.0))
        assertEquals("9,50", Fmt.dec(9.5, 2))
        assertEquals("66%", Fmt.pct(65.8))
        assertEquals("3 h 05 min", Fmt.duration(3 * 3_600_000L + 5 * 60_000L))
        assertEquals("12 min", Fmt.duration(12 * 60_000L))
        assertEquals("1:02:03", Fmt.clock(3_723_000L))
    }

    @Test
    fun parse() {
        assertEquals(15_000.0, Fmt.parseNumber("15.000"))
        assertEquals(15_000.0, Fmt.parseNumber("$ 15.000"))
        assertEquals(15_000.0, Fmt.parseNumber("15000"))
        assertEquals(9.5, Fmt.parseNumber("9,5"))
        assertEquals(9.5, Fmt.parseNumber("9.5"))
        assertEquals(1811.5, Fmt.parseNumber("1.811,5"))
        assertEquals(1811.5, Fmt.parseNumber("1811.5"))
        assertNull(Fmt.parseNumber(""))
        assertNull(Fmt.parseNumber("abc"))
    }
}
