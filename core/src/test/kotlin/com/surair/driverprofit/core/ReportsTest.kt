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
    fun personalTripsCountFuelButNotEarnings() {
        val work = shift("a", at(2026, 10, 19, 9), 50.0, 20_000.0)
        val personal = shift("p", at(2026, 10, 19, 15), 30.0, 0.0).copy(kind = ShiftKind.PERSONAL, passengerKm = 0.0, tripCount = 0)
        val s = Reports.summarize(listOf(work, personal))
        assertEquals(1, s.shiftCount)
        assertEquals(50.0, s.km, 0.001)
        assertEquals(20_000.0, s.gross, 0.001)
        assertEquals(5_000.0, s.fuelCost, 0.001)
        assertEquals(1, s.personalCount)
        assertEquals(30.0, s.personalKm, 0.001)
        assertEquals(3_000.0, s.personalFuelCost, 0.001)
        assertEquals(80.0, s.totalKm, 0.001)
        // El tanque y el consumo observado sí cuentan todo
        val load = FuelLoad("l1", at(2026, 10, 19, 8), 15_000.0, 1000.0, 15.0)
        val data = AppData(shifts = listOf(work, personal), fuelLoads = listOf(load))
        assertEquals(15.0 - 8.0, Calc.tankBalanceLiters(data, at(2026, 10, 20, 0))!!, 0.001)
    }

    @Test
    fun dailyNetFillsEmptyDays() {
        val shifts = listOf(
            shift("a", at(2026, 10, 19, 9), 50.0, 20_000.0),
            shift("b", at(2026, 10, 21, 9), 10.0, 5_000.0),
        )
        val week = Reports.dailyNet(shifts, java.time.LocalDate.of(2026, 10, 19), 7, zone)
        assertEquals(7, week.size)
        assertEquals(15_000.0, week[0], 0.001)
        assertEquals(0.0, week[1], 0.001)
        assertEquals(4_000.0, week[2], 0.001)
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
    fun csvIsVerticalWithoutBom() {
        val shifts = listOf(shift("a", at(2026, 10, 19, 9), 50.0, 20_000.0))
        val csv = Csv.shifts(shifts, zone)
        assertEquals("Campo;Valor", csv.lines().first())
        assertEquals(true, csv.contains("Km recorridos;50,0\n"))
        assertEquals(true, csv.contains("Bruto;20000\n"))
        assertEquals(true, csv.contains("Costo nafta;5000\n"))
        assertEquals(true, csv.contains("Neto;15000\n"))
        assertEquals(true, csv.contains("Neto sobre bruto (%);75,0\n"))
        assertEquals(true, csv.contains("Id;a\n"))
        val daily = Csv.dailySummary(shifts, 0.0, zone)
        assertEquals(2, daily.trim().lines().size)
        assertEquals(true, daily.lines()[1].endsWith(";20000;5000;15000;75,0;15000;0,0;0"))
    }
}
