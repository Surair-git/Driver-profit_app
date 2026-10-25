package com.surair.driverprofit.core

import kotlin.math.max

object Calc {
    fun litersFromAmount(amount: Double, pricePerLiter: Double): Double =
        if (pricePerLiter > 0) amount / pricePerLiter else 0.0

    fun costPerKm(consumptionL100: Double, pricePerLiter: Double): Double =
        consumptionL100 / 100.0 * pricePerLiter

    fun litersForKm(km: Double, consumptionL100: Double): Double = km * consumptionL100 / 100.0

    fun fuelCost(km: Double, consumptionL100: Double, pricePerLiter: Double): Double =
        litersForKm(km, consumptionL100) * pricePerLiter

    fun kmForLiters(liters: Double, consumptionL100: Double): Double =
        if (consumptionL100 > 0) liters / consumptionL100 * 100.0 else 0.0

    fun kmForAmount(amount: Double, pricePerLiter: Double, consumptionL100: Double): Double =
        kmForLiters(litersFromAmount(amount, pricePerLiter), consumptionL100)

    fun netPercent(gross: Double, net: Double): Double? =
        if (gross > 0) net / gross * 100.0 else null

    /** Tiempo de turno sin contar pausas. */
    fun activeMs(shift: Shift, now: Long): Long {
        val end = shift.endedAt ?: now
        var paused = shift.totalPausedMs
        val pausedAt = shift.pausedAt
        if (pausedAt != null && shift.endedAt == null) paused += now - pausedAt
        return max(0L, end - shift.startedAt - paused)
    }

    fun tripsTotal(shift: Shift): Double = shift.trips.sumOf { it.amount }

    /** Bruto: lo cargado al cerrar; si todavía no se cerró, la suma de viajes en vivo. */
    fun gross(shift: Shift): Double {
        val fields = shift.fareTotal + shift.bonusTotal + shift.tipTotal + shift.otherTotal
        return if (fields > 0) fields else tripsTotal(shift)
    }

    fun fuelLiters(shift: Shift): Double = litersForKm(shift.km, shift.consumptionL100)

    fun fuelCost(shift: Shift): Double = fuelCost(shift.km, shift.consumptionL100, shift.pricePerLiter)

    fun net(shift: Shift): Double = gross(shift) - fuelCost(shift)

    fun netPercent(shift: Shift): Double? = netPercent(gross(shift), net(shift))

    /** Km sin pasajero, si se cargaron los km con pasajero. */
    fun emptyKm(shift: Shift): Double? =
        if (shift.passengerKm > 0) max(0.0, shift.km - shift.passengerKm) else null

    /** Km con pasajero deducidos del dato "Ganancias por km" de DiDi. */
    fun passengerKmFromDidi(gross: Double, didiPerKm: Double): Double =
        if (didiPerKm > 0 && gross > 0) gross / didiPerKm else 0.0

    /** Saldo estimado del tanque en litros. null si no hay ninguna carga registrada. */
    fun tankBalanceLiters(data: AppData, now: Long = System.currentTimeMillis()): Double? {
        val s = data.settings
        val firstLoadAt = data.fuelLoads.minOfOrNull { it.at } ?: return null
        val from = if (s.tankBaselineAt > 0) s.tankBaselineAt else firstLoadAt
        val loaded = data.fuelLoads.filter { it.at >= from }.sumOf { it.liters }
        val consumed = data.shifts.filter { it.startedAt >= from }.sumOf { fuelLiters(it) }
        val baseline = if (s.tankBaselineAt > 0) s.tankBaselineLiters else 0.0
        return baseline + loaded - consumed
    }

    /**
     * Consumo observado (L/100 km) a partir de las cargas y los km medidos.
     * Asume que entre la primera y la última carga el tanque quedó en un nivel parecido.
     */
    fun observedConsumption(data: AppData, minKm: Double = 150.0): Double? {
        val loads = data.fuelLoads.sortedBy { it.at }
        if (loads.size < 2) return null
        val first = loads.first().at
        val last = loads.last().at
        val liters = loads.dropLast(1).sumOf { it.liters }
        val km = data.shifts.filter { it.startedAt >= first && it.startedAt < last }.sumOf { it.km }
        if (km < minKm || liters <= 0) return null
        return liters / km * 100.0
    }
}
