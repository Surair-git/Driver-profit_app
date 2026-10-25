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
 * Convierte lecturas crudas de GPS en distancia recorrida, descartando el "temblor"
 * del GPS cuando el auto está parado y los saltos imposibles.
 */
class DistanceFilter(
    private val maxAccuracyMeters: Float = 30f,
    private val minMoveMeters: Double = 5.0,
    private val stationarySpeedMps: Float = 0.8f,
    private val maxSpeedMps: Double = 55.0,
    private val maxSegmentMillis: Long = 15_000L,
) {
    private var last: Fix? = null
    private var rejectedJumps = 0

    fun reset() {
        last = null
        rejectedJumps = 0
    }

    /** Devuelve el movimiento aceptado para esta lectura (0 si se descarta). */
    fun offer(fix: Fix): Movement {
        if (fix.accuracyMeters > maxAccuracyMeters) return NONE
        val prev = last
        if (prev == null) {
            last = fix
            return NONE
        }
        val dt = fix.timeMs - prev.timeMs
        if (dt <= 0) return NONE
        val d = haversineMeters(prev.lat, prev.lon, fix.lat, fix.lon)

        // Salto imposible: ignorar, salvo que se repita (entonces el anterior era el malo).
        if (d / (dt / 1000.0) > maxSpeedMps) {
            rejectedJumps++
            if (rejectedJumps >= 3) {
                last = fix
                rejectedJumps = 0
            }
            return NONE
        }
        rejectedJumps = 0

        val threshold = max(minMoveMeters, 0.75 * max(prev.accuracyMeters, fix.accuracyMeters))
        val speed = fix.speedMps
        val looksStationary = speed != null && speed < stationarySpeedMps && d < threshold * 2
        if (d < threshold || looksStationary) return NONE

        last = fix
        return Movement(d, minOf(dt, maxSegmentMillis))
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
