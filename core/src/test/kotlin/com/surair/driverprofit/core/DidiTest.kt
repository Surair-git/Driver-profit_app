package com.surair.driverprofit.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DidiTest {
    private val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, 25, h, m, 0, 0, zone).plusDays((d - 25).toLong()).toInstant().toEpochMilli()
    private val base = Shift(id = "s", startedAt = at(25, 20), consumptionL100 = 9.5, pricePerLiter = 1800.0, status = ShiftStatus.RUNNING)

    @Test
    fun readingsAccumulateFromFirstValue() {
        var s = DidiReading.apply(base, 12_000.0, at(25, 20, 1), zone)
        assertEquals(0.0, s.didiGross)
        s = DidiReading.apply(s, 20_500.0, at(25, 20, 30), zone)
        assertEquals(8_500.0, s.didiGross)
    }

    @Test
    fun midnightResetKeepsEarlierEarnings() {
        var s = DidiReading.apply(base, 10_000.0, at(25, 22), zone)
        s = DidiReading.apply(s, 40_000.0, at(25, 23, 50), zone)
        s = DidiReading.apply(s, 0.0, at(26, 0, 5), zone)
        assertEquals(30_000.0, s.didiGross)
        s = DidiReading.apply(s, 7_000.0, at(26, 0, 40), zone)
        assertEquals(37_000.0, s.didiGross)
    }

    @Test
    fun suspiciousReadingsAreIgnored() {
        var s = DidiReading.apply(base, 10_000.0, at(25, 20), zone)
        s = DidiReading.apply(s, 30_000.0, at(25, 20, 10), zone)
        // bajó sin cambiar de día: cifra mal leída
        s = DidiReading.apply(s, 3_000.0, at(25, 20, 20), zone)
        assertEquals(20_000.0, s.didiGross)
        // subió demasiado de golpe: cifra mal leída
        s = DidiReading.apply(s, 930_000.0, at(25, 20, 30), zone)
        assertEquals(20_000.0, s.didiGross)
    }

    @Test
    fun parseAmounts() {
        assertEquals(0.0, DidiParser.parseAmount("$0"))
        assertEquals(12_500.0, DidiParser.parseAmount("$12.500 ▾"))
        assertEquals(41_000.0, DidiParser.parseAmount("Tarifa del viaje $41.000 >"))
        assertEquals(1_811.5, DidiParser.parseAmount("$ 1.811,50"))
        assertNull(DidiParser.parseAmount("Conectarse"))
    }

    @Test
    fun dailyAmountTakesTopCenteredPill() {
        val lines = listOf(
            OcrLine("Bahía Blanca", 400, 110, 580, 150),
            OcrLine("$12.500", 300, 100, 430, 170),
            OcrLine("$200.000", 50, 1200, 400, 1240),
        )
        assertEquals(12_500.0, DidiParser.dailyAmount(lines, 720, 1600))
        assertNull(DidiParser.dailyAmount(lines.drop(1).drop(0).filter { it.text != "$12.500" }, 720, 1600))
    }

    @Test
    fun breakdownMatchesLabelsToAmounts() {
        val lines = listOf(
            OcrLine("Desglose de ganancias", 50, 680, 430, 720),
            OcrLine("Tarifa del viaje", 50, 760, 230, 800),
            OcrLine("$41.000 >", 560, 760, 680, 800),
            OcrLine("Premio", 50, 835, 140, 875),
            OcrLine("$9.100 >", 580, 835, 680, 875),
            OcrLine("Propina", 50, 910, 140, 950),
            OcrLine("$0 >", 620, 910, 680, 950),
            OcrLine("$1.000", 95, 1395, 190, 1435),
            OcrLine("Ganancias por km", 65, 1445, 220, 1510),
            OcrLine("$4.200", 310, 1395, 410, 1435),
            OcrLine("Ganancias por viaje", 280, 1445, 440, 1510),
            OcrLine("$16.000", 520, 1395, 630, 1435),
            OcrLine("Ganancias por hora", 500, 1445, 650, 1510),
        )
        val b = DidiParser.breakdown(lines)
        assertEquals(41_000.0, b.fare)
        assertEquals(9_100.0, b.bonus)
        assertEquals(0.0, b.tip)
        assertEquals(1_000.0, b.perKm)
        assertEquals(4_200.0, b.perTrip)
        assertEquals(16_000.0, b.perHour)
        assertNull(b.refund)
    }
}
