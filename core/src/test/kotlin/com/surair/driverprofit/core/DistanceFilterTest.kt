package com.surair.driverprofit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceFilterTest {
    // ~1 grado de latitud = 111 km, así que 0.00009 grados ~ 10 m
    private val degPer10m = 10.0 / 111_320.0

    @Test
    fun straightDriveAccumulatesDistance() {
        val f = DistanceFilter()
        var total = 0.0
        var t = 0L
        for (i in 0..100) {
            total += f.offer(Fix(-38.7 + i * degPer10m, -62.26, 8f, 5f, t)).meters
            t += 2000
        }
        assertEquals(1000.0, total, 15.0)
    }

    @Test
    fun jitterWhileStoppedIsIgnored() {
        val f = DistanceFilter()
        var total = 0.0
        var t = 0L
        val base = -38.7
        val jitter = listOf(0.0, 0.2, -0.3, 0.1, 0.35, -0.2, 0.0, 0.3)
        for (i in 0..200) {
            val j = jitter[i % jitter.size] * degPer10m
            total += f.offer(Fix(base + j, -62.26, 10f, 0f, t)).meters
            t += 2000
        }
        assertEquals(0.0, total, 0.001)
    }

    @Test
    fun badAccuracyAndJumpsAreDropped() {
        val f = DistanceFilter()
        var t = 0L
        f.offer(Fix(-38.7, -62.26, 8f, 5f, t)); t += 2000
        assertEquals(0.0, f.offer(Fix(-38.7 + 2 * degPer10m, -62.26, 80f, 5f, t)).meters); t += 2000
        // salto de 1 km en 2 s = 500 m/s: descartado
        assertEquals(0.0, f.offer(Fix(-38.7 + 100 * degPer10m, -62.26, 8f, 5f, t)).meters); t += 2000
        val m = f.offer(Fix(-38.7 + 2 * degPer10m, -62.26, 8f, 5f, t))
        assertTrue(m.meters > 15.0 && m.meters < 25.0)
    }

    @Test
    fun segmentTimeIsCapped() {
        val f = DistanceFilter()
        f.offer(Fix(-38.7, -62.26, 8f, 5f, 0L))
        val m = f.offer(Fix(-38.7 + 5 * degPer10m, -62.26, 8f, 5f, 60_000L))
        assertEquals(15_000L, m.millis)
    }
}
