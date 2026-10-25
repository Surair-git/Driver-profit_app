package com.surair.driverprofit.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class Summary(
    val shiftCount: Int,
    val km: Double,
    val passengerKm: Double,
    val gross: Double,
    val fuelLiters: Double,
    val fuelCost: Double,
    val tripCount: Int,
    val activeMs: Long,
) {
    val net: Double get() = gross - fuelCost
    val netPercent: Double? get() = Calc.netPercent(gross, net)
    val emptyKm: Double? get() = if (passengerKm > 0) (km - passengerKm).coerceAtLeast(0.0) else null
    val grossPerKm: Double? get() = if (km > 0) gross / km else null
    val netPerKm: Double? get() = if (km > 0) net / km else null
    val netPerHour: Double? get() = if (activeMs > 0) net / (activeMs / 3_600_000.0) else null

    companion object {
        val EMPTY = Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0L)
    }
}

enum class Period { DAY, WEEK, MONTH }

data class PeriodRow(
    val key: LocalDate,
    val label: String,
    val summary: Summary,
    val shifts: List<Shift>,
    /** Gastos fijos prorrateados para el período. */
    val fixedCosts: Double,
) {
    val netAfterFixed: Double get() = summary.net - fixedCosts
}

data class FuelLoadRow(
    val load: FuelLoad,
    /** Turnos entre esta carga y la siguiente. */
    val summary: Summary,
    /** Fin de la ventana: la carga siguiente o ahora. */
    val until: Long,
) {
    /** Cuánto del importe cargado ya se recuperó en bruto. */
    val recoveredPercent: Double? get() = if (load.amount > 0) summary.gross / load.amount * 100.0 else null
    val litersUsed: Double get() = summary.fuelLiters
}

object Reports {
    fun summarize(shifts: List<Shift>, now: Long = System.currentTimeMillis()): Summary {
        if (shifts.isEmpty()) return Summary.EMPTY
        return Summary(
            shiftCount = shifts.size,
            km = shifts.sumOf { it.km },
            passengerKm = shifts.sumOf { it.passengerKm },
            gross = shifts.sumOf { Calc.gross(it) },
            fuelLiters = shifts.sumOf { Calc.fuelLiters(it) },
            fuelCost = shifts.sumOf { Calc.fuelCost(it) },
            tripCount = shifts.sumOf { if (it.tripCount > 0) it.tripCount else it.trips.size },
            activeMs = shifts.sumOf { Calc.activeMs(it, now) },
        )
    }

    fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun periodKey(ms: Long, period: Period, zone: ZoneId): LocalDate {
        val d = localDate(ms, zone)
        return when (period) {
            Period.DAY -> d
            Period.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period.MONTH -> d.withDayOfMonth(1)
        }
    }

    fun periodLabel(key: LocalDate, period: Period, zone: ZoneId): String {
        val ms = key.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (period) {
            Period.DAY -> Fmt.longDate(ms, zone)
            Period.WEEK -> {
                val end = key.plusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                "Semana del ${Fmt.date(ms, zone)} al ${Fmt.date(end, zone)}"
            }
            Period.MONTH -> Fmt.month(ms, zone)
        }
    }

    fun fixedCostsFor(period: Period, key: LocalDate, monthly: Double): Double {
        if (monthly <= 0) return 0.0
        return when (period) {
            Period.DAY -> monthly / 30.0
            Period.WEEK -> monthly * 7.0 / 30.0
            Period.MONTH -> monthly
        }
    }

    /** Agrupa turnos cerrados (o pendientes) por período, del más reciente al más viejo. */
    fun byPeriod(
        shifts: List<Shift>,
        period: Period,
        monthlyFixedCosts: Double,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): List<PeriodRow> {
        val finished = shifts.filter { !it.isActive }
        return finished
            .groupBy { periodKey(it.startedAt, period, zone) }
            .toSortedMap(compareByDescending { it })
            .map { (key, list) ->
                PeriodRow(
                    key = key,
                    label = periodLabel(key, period, zone),
                    summary = summarize(list.sortedByDescending { it.startedAt }, now),
                    shifts = list.sortedByDescending { it.startedAt },
                    fixedCosts = fixedCostsFor(period, key, monthlyFixedCosts),
                )
            }
    }

    /** Qué pasó con cada carga de nafta, de la más reciente a la más vieja. */
    fun byFuelLoad(data: AppData, now: Long = System.currentTimeMillis()): List<FuelLoadRow> {
        val loads = data.fuelLoads.sortedBy { it.at }
        return loads.mapIndexed { i, load ->
            val until = loads.getOrNull(i + 1)?.at ?: now
            val shifts = data.shifts.filter { it.startedAt >= load.at && it.startedAt < until }
            FuelLoadRow(load, summarize(shifts, now), until)
        }.reversed()
    }

    fun today(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Summary {
        val today = localDate(now, zone)
        return summarize(shifts.filter { localDate(it.startedAt, zone) == today }, now)
    }

    fun thisWeek(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Summary {
        val week = periodKey(now, Period.WEEK, zone)
        return summarize(shifts.filter { periodKey(it.startedAt, Period.WEEK, zone) == week }, now)
    }
}
