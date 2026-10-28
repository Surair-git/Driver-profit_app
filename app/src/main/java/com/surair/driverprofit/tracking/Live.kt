package com.surair.driverprofit.tracking

import kotlinx.coroutines.flow.MutableStateFlow

/** Estado en vivo del turno que publica el servicio de GPS. */
data class LiveTracking(
    val shiftId: String,
    val distanceMeters: Double,
    val movingMs: Long,
    val paused: Boolean,
    val autoPaused: Boolean,
    val accuracyMeters: Float?,
    val speedKmh: Float?,
    val lastFixAt: Long?,
    /** Desde cuándo está abierta la pregunta "¿se paró el auto?"; null si no está abierta. */
    val stopPromptSince: Long? = null,
    /** Si la lectura del recuadro de DiDi está activa en este turno. */
    val capturing: Boolean = false,
    /** Último momento con movimiento real. */
    val lastMovementAt: Long? = null,
)

object Live {
    val tracking = MutableStateFlow<LiveTracking?>(null)
}

/** Pedido de iniciar un recorrido que llega desde una notificación y lo atiende la pantalla principal. */
object StartRequest {
    val pending = MutableStateFlow<com.surair.driverprofit.core.ShiftKind?>(null)
}
