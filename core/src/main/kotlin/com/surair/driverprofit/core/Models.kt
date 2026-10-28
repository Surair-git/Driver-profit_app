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
    /** Minutos sin movimiento para pausar solo, sin preguntar. 0 = apagado. */
    val autoPauseMinutes: Int = 0,
    /** Segundos sin movimiento antes de preguntar "¿se paró el auto?". 0 = no preguntar. */
    val stopPromptSeconds: Int = 30,
    /** Si se pausó por una parada, reanudar solo cuando el auto vuelve a moverse. */
    val smartResume: Boolean = true,
    /** Detectar con el acelerómetro que el teléfono va en un vehículo y preguntar si arrancó un recorrido. */
    val vehicleDetection: Boolean = false,
    /** Leer el recuadro de ganancias del día de DiDi capturando la pantalla. */
    val didiCaptureEnabled: Boolean = true,
    /** Cada cuántos minutos leer el recuadro. */
    val didiCaptureMinutes: Int = 5,
    /** Punto de partida para el saldo estimado del tanque. */
    val tankBaselineLiters: Double = 0.0,
    val tankBaselineAt: Long = 0L,
    /** "system", "dark" o "light". */
    val themeMode: String = "system",
    /** Qué hacer cuando DiDi pasa al frente: "off", "ask" (notificación) o "auto" (iniciar solo). */
    val autoStartMode: String = "off",
    /** Avisar con una notificación al conectar el cargador. */
    val triggerOnCharger: Boolean = false,
    /** Avisar al conectarse un dispositivo Bluetooth (el del auto). */
    val triggerOnBluetooth: Boolean = false,
    /** Si no está vacío, solo dispositivos cuyo nombre contenga este texto. */
    val bluetoothDeviceName: String = "",
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

/** Turno de trabajo con DiDi, o uso personal del auto (gasta nafta pero no entra en la ganancia). */
@Serializable
enum class ShiftKind { WORK, PERSONAL }

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
    val kind: ShiftKind = ShiftKind.WORK,
    /** Km cargados a mano (sin GPS), por ejemplo un viaje hecho con la app cerrada. */
    val manual: Boolean = false,
    // Lectura del recuadro "ganancias del día" de DiDi durante el turno.
    /** Lo que marcaba el recuadro en la primera lectura del turno (o 0 después de medianoche). */
    val didiDayStart: Double? = null,
    /** Última lectura del recuadro. */
    val didiDayLast: Double? = null,
    /** Lo ganado antes de que el recuadro se reiniciara a las 00:00. */
    val didiBeforeReset: Double = 0.0,
    val didiLastReadAt: Long? = null,
) {
    val isActive: Boolean get() = status == ShiftStatus.RUNNING || status == ShiftStatus.PAUSED
    val isWork: Boolean get() = kind == ShiftKind.WORK
    val km: Double get() = distanceMeters / 1000.0
    /** Bruto del turno según el recuadro de DiDi; 0 si todavía no hubo lectura. */
    val didiGross: Double get() {
        val start = didiDayStart ?: return 0.0
        val last = didiDayLast ?: return 0.0
        return (didiBeforeReset + (last - start)).coerceAtLeast(0.0)
    }
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
