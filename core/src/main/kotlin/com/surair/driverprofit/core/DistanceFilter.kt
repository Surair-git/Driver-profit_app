package com.surair.driverprofit.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    /** Velocidad informada por el GPS en m/s, o null si no la tiene. */
    val speedMps: Float?,
    val timeMs: Long,
)

data class Movement(val meters: Double, val millis: Long)

/**
 * Convierte lecturas crudas de GPS en distancia recorrida.
 *
 * La regla central: solo se suman metros mientras el auto está "en movimiento", y para
 * entrar en ese estado hacen falta dos lecturas seguidas con velocidad de marcha. Con el
 * auto parado la posición tiembla unos metros entre lecturas; como el ancla no se mueve
 * mientras estamos detenidos, ese temblor no suma nada.
 */
class DistanceFilter(
    private val maxAccuracyMeters: Float = 30f,
    private val minMoveMeters: Double = 3.0,
    /** Velocidad para considerar que arrancó (unos 4 km/h). */
    private val startSpeedMps: Double = 1.2,
    /** Velocidad por debajo de la cual se considera detenido (unos 2 km/h). */
    private val stopSpeedMps: Double = 0.6,
    private val maxSpeedMps: Double = 55.0,
    private val maxSegmentMillis: Long = 15_000L,
    private val fixesToStart: Int = 2,
    private val fixesToStop: Int = 2,
) {
    /** Última posición desde la que se mide distancia. Parado, no cambia. */
    private var anchor: Fix? = null
    private var previous: Fix? = null
    private var movingCount = 0
    private var stationaryCount = 0
    private var rejectedJumps = 0

    var isMoving: Boolean = false
        private set

    fun reset() {
        anchor = null
        previous = null
        movingCount = 0
        stationaryCount = 0
        rejectedJumps = 0
        isMoving = false
    }

    /** Devuelve el movimiento aceptado para esta lectura (0 si se descarta o el auto está parado). */
    fun offer(fix: Fix): Movement {
        if (fix.accuracyMeters > maxAccuracyMeters) return NONE
        val prev = previous
        val anc = anchor
        if (prev == null || anc == null) {
            previous = fix
            anchor = fix
            return NONE
        }
        val dt = fix.timeMs - prev.timeMs
        if (dt <= 0) return NONE

        val dPrev = haversineMeters(prev.lat, prev.lon, fix.lat, fix.lon)
        val impliedSpeed = dPrev / (dt / 1000.0)
        if (impliedSpeed > maxSpeedMps) {
            // Salto imposible: ignorar, salvo que se repita (entonces el anterior era el malo).
            rejectedJumps++
            if (rejectedJumps >= 3) {
                previous = fix
                anchor = fix
                rejectedJumps = 0
            }
            return NONE
        }
        rejectedJumps = 0
        previous = fix

        // Velocidad: la del GPS si la informa; si no, la que se deduce de la distancia.
        val speed = fix.speedMps?.toDouble() ?: impliedSpeed
        if (speed >= startSpeedMps) {
            movingCount++
            stationaryCount = 0
        } else if (speed < stopSpeedMps) {
            stationaryCount++
            movingCount = 0
        } else {
            // Zona gris: no cambia el estado.
            movingCount = 0
            stationaryCount = 0
        }

        if (!isMoving) {
            if (movingCount >= fixesToStart) {
                isMoving = true
            } else {
                return NONE
            }
        } else if (stationaryCount >= fixesToStop) {
            isMoving = false
            return NONE
        }

        // En movimiento: sumar lo recorrido desde el ancla.
        val d = haversineMeters(anc.lat, anc.lon, fix.lat, fix.lon)
        val threshold = max(minMoveMeters, 0.5 * max(anc.accuracyMeters, fix.accuracyMeters))
        if (d < threshold) return NONE
        val elapsed = fix.timeMs - anc.timeMs
        anchor = fix
        return Movement(d, minOf(elapsed, maxSegmentMillis))
    }

    companion object {
        val NONE = Movement(0.0, 0L)
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
