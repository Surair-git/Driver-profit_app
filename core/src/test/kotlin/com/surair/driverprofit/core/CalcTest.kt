package com.surair.driverprofit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CalcTest {
    private fun shift(km: Double, cons: Double = 9.5, price: Double = 1800.0, fare: Double = 0.0, trips: List<Double> = emptyList()) =
        Shift(
            id = "s", startedAt = 0L, endedAt = 3_600_000L, distanceMeters = km * 1000, consumptionL100 = cons,
            pricePerLiter = price, fareTotal = fare, status = ShiftStatus.CLOSED,
            trips = trips.mapIndexed { i, a -> TripIncome("t$i", 0L, a) },
        )

    @Test
    fun litersAndKmFromAmount() {
        assertEquals(8.333, Calc.litersFromAmount(15_000.0, 1800.0), 0.001)
        assertEquals(87.72, Calc.kmForAmount(15_000.0, 1800.0, 9.5), 0.01)
        assertEquals(171.0, Calc.costPerKm(9.5, 1800.0), 0.001)
        assertEquals(0.0, Calc.litersFromAmount(100.0, 0.0))
    }

    @Test
    fun netAndPercent() {
        val s = shift(km = 100.0, fare = 50_000.0)
        assertEquals(17_100.0, Calc.fuelCost(s), 0.001)
        assertEquals(32_900.0, Calc.net(s), 0.001)
        assertEquals(65.8, Calc.netPercent(s)!!, 0.01)
    }

    @Test
    fun grossFallsBackToLiveTrips() {
        val s = shift(km = 10.0, trips = listOf(2200.0, 3100.0))
        assertEquals(5300.0, Calc.gross(s))
        val closed = s.copy(fareTotal = 6000.0)
        assertEquals(6000.0, Calc.gross(closed))
    }

    @Test
    fun activeMsExcludesPauses() {
        val running = Shift(id = "a", startedAt = 0L, consumptionL100 = 9.5, pricePerLiter = 1.0, status = ShiftStatus.RUNNING, totalPausedMs = 60_000)
        assertEquals(240_000L, Calc.activeMs(running, 300_000L))
        val paused = running.copy(status = ShiftStatus.PAUSED, pausedAt = 200_000L)
        assertEquals(140_000L, Calc.activeMs(paused, 300_000L))
    }

    @Test
    fun passengerKmFromDidi() {
        assertEquals(50.1, Calc.passengerKmFromDidi(50_100.0, 1000.0), 0.001)
        assertEquals(0.0, Calc.passengerKmFromDidi(50_100.0, 0.0))
        val s = shift(km = 80.0, fare = 50_100.0).copy(passengerKm = 50.1)
        assertEquals(29.9, Calc.emptyKm(s)!!, 0.001)
    }

    @Test
    fun tankBalance() {
        val load = FuelLoad("l1", at = 1000L, amount = 15_000.0, pricePerLiter = 1800.0, liters = 8.333)
        val before = shift(km = 100.0).copy(id = "before", startedAt = 500L)
        val after = shift(km = 50.0).copy(id = "after", startedAt = 2000L)
        val data = AppData(shifts = listOf(before, after), fuelLoads = listOf(load))
        // Solo cuenta lo consumido después de la primera carga: 50 km * 9,5 / 100 = 4,75 L
        assertEquals(8.333 - 4.75, Calc.tankBalanceLiters(data, 3000L)!!, 0.001)
        assertNull(Calc.tankBalanceLiters(AppData(shifts = listOf(before)), 3000L))
        val adjusted = data.copy(settings = Settings(tankBaselineLiters = 20.0, tankBaselineAt = 2500L))
        assertEquals(20.0, Calc.tankBalanceLiters(adjusted, 3000L)!!, 0.001)
    }

    @Test
    fun observedConsumption() {
        val l1 = FuelLoad("l1", at = 1000L, amount = 0.0, pricePerLiter = 0.0, liters = 20.0)
        val l2 = FuelLoad("l2", at = 5000L, amount = 0.0, pricePerLiter = 0.0, liters = 10.0)
        val s = shift(km = 200.0).copy(startedAt = 2000L)
        val data = AppData(shifts = listOf(s), fuelLoads = listOf(l1, l2))
        val obs = Calc.observedConsumption(data)
        assertNotNull(obs)
        assertEquals(10.0, obs, 0.001)
        assertNull(Calc.observedConsumption(data.copy(shifts = listOf(s.copy(distanceMeters = 50_000.0)))))
    }
}
