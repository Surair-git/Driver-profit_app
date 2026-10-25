package com.surair.driverprofit.core

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val carBrand: String = "Fiat",
    val carModel: String = "Palio 2017",
    val carEngine: String = "1.4 Fire",
    /** Litros cada 100 km en ciudad. */
    val consumptionL100: Double = 9.5,
    /** Último precio del litro conocido, en pesos. 0 = todavía no cargado. */
    val lastPricePerLiter: Double = 0.0,
    val fuelType: String = "Súper",
    /** Gastos fijos mensuales (seguro, patente, service). Se prorratean por día. */
    val monthlyFixedCosts: Double = 0.0,
    /** Minutos sin movimiento para pausar solo. 0 = apagado. */
    val autoPauseMinutes: Int = 0,
    /** Punto de partida para el saldo estimado del tanque. */
    val tankBaselineLiters: Double = 0.0,
    val tankBaselineAt: Long = 0L,
)

@Serializable
data class FuelLoad(
    val id: String,
    val at: Long,
    val amount: Double,
    val pricePerLiter: Double,
    val liters: Double,
    val fuelType: String = "Súper",
    val station: String = "",
)

@Serializable
data class TripIncome(
    val id: String,
    val at: Long,
    val amount: Double,
)

@Serializable
enum class ShiftStatus { RUNNING, PAUSED, PENDING_CLOSE, CLOSED }

@Serializable
data class Shift(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val totalPausedMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val movingMs: Long = 0L,
    /** Consumo y precio tomados al momento del turno. */
    val consumptionL100: Double,
    val pricePerLiter: Double,
    /** Viajes cargados en vivo con el botón "+ Viaje". */
    val trips: List<TripIncome> = emptyList(),
    // Datos del resumen de DiDi al cerrar el turno.
    val fareTotal: Double = 0.0,
    val bonusTotal: Double = 0.0,
    val tipTotal: Double = 0.0,
    val otherTotal: Double = 0.0,
    val tripCount: Int = 0,
    val passengerKm: Double = 0.0,
    val didiPerKm: Double = 0.0,
    val notes: String = "",
    val status: ShiftStatus,
) {
    val isActive: Boolean get() = status == ShiftStatus.RUNNING || status == ShiftStatus.PAUSED
    val km: Double get() = distanceMeters / 1000.0
}

@Serializable
data class AppData(
    val version: Int = 1,
    val settings: Settings = Settings(),
    val shifts: List<Shift> = emptyList(),
    val fuelLoads: List<FuelLoad> = emptyList(),
) {
    val activeShift: Shift? get() = shifts.firstOrNull { it.isActive }
    val pendingShifts: List<Shift> get() = shifts.filter { it.status == ShiftStatus.PENDING_CLOSE }
}
