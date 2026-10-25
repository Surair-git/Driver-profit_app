package com.surair.driverprofit.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsTest {
    private val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private fun at(y: Int, m: Int, d: Int, h: Int) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun shift(id: String, start: Long, km: Double, fare: Double) = Shift(
        id = id, startedAt = start, endedAt = start + 3_600_000, distanceMeters = km * 1000,
        consumptionL100 = 10.0, pricePerLiter = 1000.0, fareTotal = fare, passengerKm = km / 2, tripCount = 3,
        status = ShiftStatus.CLOSED,
    )

    @Test
    fun groupsByDayWeekMonth() {
        val shifts = listOf(
            shift("a", at(2026, 10, 19, 9), 50.0, 20_000.0),  // lunes
            shift("b", at(2026, 10, 19, 18), 30.0, 10_000.0),
            shift("c", at(2026, 10, 25, 20), 40.0, 15_000.0), // domingo, misma semana
            shift("d", at(2026, 11, 2, 10), 10.0, 5_000.0),  // otro mes
        )
        val days = Reports.byPeriod(shifts, Period.DAY, 0.0, zone)
        assertEquals(3, days.size)
        assertEquals(80.0, days.last().summary.km, 0.001)
        assertEquals(30_000.0, days.last().summary.gross, 0.001)
        assertEquals(8_000.0, days.last().summary.fuelCost, 0.001)

        val weeks = Reports.byPeriod(shifts, Period.WEEK, 30_000.0, zone)
        assertEquals(2, weeks.size)
        assertEquals(3, weeks.last().shifts.size)
        assertEquals(7_000.0, weeks.last().fixedCosts, 0.001)

        val months = Reports.byPeriod(shifts, Period.MONTH, 0.0, zone)
        assertEquals(2, months.size)
        assertEquals("Noviembre 2026", months.first().label)
    }

    @Test
    fun fuelLoadWindows() {
        val l1 = FuelLoad("l1", at(2026, 10, 19, 8), 15_000.0, 1500.0, 10.0)
        val l2 = FuelLoad("l2", at(2026, 10, 22, 8), 15_000.0, 1500.0, 10.0)
        val shifts = listOf(
            shift("a", at(2026, 10, 19, 9), 50.0, 20_000.0),
            shift("b", at(2026, 10, 23, 9), 30.0, 10_000.0),
        )
        val rows = Reports.byFuelLoad(AppData(shifts = shifts, fuelLoads = listOf(l1, l2)), at(2026, 10, 25, 0))
        assertEquals("l2", rows[0].load.id)
        assertEquals(30.0, rows[0].summary.km, 0.001)
        assertEquals(50.0, rows[1].summary.km, 0.001)
        assertEquals(133.33, rows[1].recoveredPercent!!, 0.01)
    }

    @Test
    fun csvHasOneRowPerShift() {
        val shifts = listOf(shift("a", at(2026, 10, 19, 9), 50.0, 20_000.0))
        val csv = Csv.shifts(shifts, zone)
        assertEquals(2, csv.trim().lines().size)
    }
}
