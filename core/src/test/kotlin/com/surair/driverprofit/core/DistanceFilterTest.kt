package com.surair.driverprofit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(f.isMoving)
    }

    @Test
    fun driveWithoutReportedSpeedUsesImpliedSpeed() {
        val f = DistanceFilter()
        var total = 0.0
        var t = 0L
        for (i in 0..50) {
            total += f.offer(Fix(-38.7 + i * degPer10m, -62.26, 8f, null, t)).meters
            t += 2000
        }
        assertEquals(500.0, total, 15.0)
    }

    @Test
    fun jitterWhileStoppedIsIgnoredEvenWithBigJumps() {
        val f = DistanceFilter()
        var total = 0.0
        var t = 0L
        val base = -38.7
        val jitter = listOf(0.0, 0.8, -1.2, 0.3, 1.5, -0.9, 0.0, 2.0, -1.7, 0.4)
        for (i in 0..300) {
            val j = jitter[i % jitter.size] * degPer10m
            total += f.offer(Fix(base + j, -62.26, 14f, 0f, t)).meters
            t += 2000
        }
        assertEquals(0.0, total, 0.001)
        assertFalse(f.isMoving)
    }

    @Test
    fun stopAndGoCountsOnlyRealMovement() {
        val f = DistanceFilter()
        var total = 0.0
        var t = 0L
        var lat = -38.7
        // 20 lecturas andando
        repeat(20) { lat += degPer10m; total += f.offer(Fix(lat, -62.26, 8f, 5f, t)).meters; t += 2000 }
        // 60 lecturas parado (2 minutos) con temblor
        val stopLat = lat
        repeat(60) { i -> val j = (if (i % 2 == 0) 0.6 else -0.6) * degPer10m; total += f.offer(Fix(stopLat + j, -62.26, 12f, 0f, t)).meters; t += 2000 }
        assertFalse(f.isMoving)
        val afterStop = total
        // vuelve a andar
        repeat(20) { lat += degPer10m; total += f.offer(Fix(lat, -62.26, 8f, 5f, t)).meters; t += 2000 }
        assertEquals(afterStop, total - 200.0, 25.0)
        assertEquals(400.0, total, 30.0)
    }

    @Test
    fun badAccuracyAndJumpsAreDropped() {
        val f = DistanceFilter()
        var t = 0L
        var total = 0.0
        repeat(3) { i -> total += f.offer(Fix(-38.7 + i * degPer10m, -62.26, 8f, 5f, t)).meters; t += 2000 }
        assertEquals(0.0, f.offer(Fix(-38.7 + 5 * degPer10m, -62.26, 80f, 5f, t)).meters); t += 2000
        // salto de 1 km en 2 s = 500 m/s: descartado
        assertEquals(0.0, f.offer(Fix(-38.7 + 100 * degPer10m, -62.26, 8f, 5f, t)).meters); t += 2000
        val m = f.offer(Fix(-38.7 + 4 * degPer10m, -62.26, 8f, 5f, t))
        assertTrue(m.meters > 15.0 && m.meters < 25.0, "got ${m.meters}")
    }

    @Test
    fun segmentTimeIsCapped() {
        val f = DistanceFilter()
        var t = 0L
        repeat(3) { i -> f.offer(Fix(-38.7 + i * degPer10m, -62.26, 8f, 5f, t)); t += 2000 }
        val m = f.offer(Fix(-38.7 + 8 * degPer10m, -62.26, 8f, 5f, t + 60_000L))
        assertEquals(15_000L, m.millis)
    }
}
