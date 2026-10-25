package com.surair.driverprofit.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.surair.driverprofit.App
import com.surair.driverprofit.MainActivity
import com.surair.driverprofit.R
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.DistanceFilter
import com.surair.driverprofit.core.Fix
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.core.ShiftStatus

/**
 * Odómetro por GPS. Corre como servicio en primer plano con una notificación fija,
 * así sigue midiendo mientras DiDi está en pantalla o con la pantalla apagada.
 */
class TrackingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var notifications: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private val filter = DistanceFilter()
    private val repo get() = App.repo

    private var shiftId: String? = null
    private var distanceMeters = 0.0
    private var movingMs = 0L
    private var paused = false
    private var autoPaused = false
    private var requestingUpdates = false

    private var lastAccuracy: Float? = null
    private var lastSpeedKmh: Float? = null
    private var lastFixAt: Long? = null
    private var lastMovementAt = 0L
    private var lastPersistAt = 0L
    private var lastNotifyAt = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) onLocation(loc)
        }
    }

    private val autoPauseTick = object : Runnable {
        override fun run() {
            checkAutoPause()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = repo.data.value.activeShift
        if (active == null) {
            Live.tracking.value = null
            stopSelf()
            return START_NOT_STICKY
        }
        if (shiftId != active.id) {
            // Primer arranque, o el sistema reinició el servicio: retomar desde lo guardado.
            shiftId = active.id
            distanceMeters = active.distanceMeters
            movingMs = active.movingMs
            paused = active.status == ShiftStatus.PAUSED
            autoPaused = false
            filter.reset()
            lastMovementAt = System.currentTimeMillis()
        }
        if (!goForeground()) return START_NOT_STICKY

        when (intent?.action) {
            ACTION_PAUSE -> pause(auto = false)
            ACTION_RESUME -> resume()
            ACTION_STOP -> {
                stopShift()
                return START_NOT_STICKY
            }
        }
        if (paused) stopLocation() else startLocation()
        publish()
        updateNotification(force = true)
        handler.removeCallbacks(autoPauseTick)
        handler.postDelayed(autoPauseTick, 30_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoPauseTick)
        stopLocation()
        super.onDestroy()
    }

    // ---- Estado ----

    private fun onLocation(loc: Location) {
        val now = System.currentTimeMillis()
        val fix = Fix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 999f,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            timeMs = loc.time,
        )
        lastAccuracy = if (loc.hasAccuracy()) loc.accuracy else null
        lastSpeedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else null
        lastFixAt = now

        val m = filter.offer(fix)
        if (m.meters > 0) {
            distanceMeters += m.meters
            movingMs += m.millis
            lastMovementAt = now
        }
        publish()
        if (now - lastPersistAt > 20_000L) persist()
        if (now - lastNotifyAt > 5_000L) updateNotification(force = false)
    }

    private fun persist() {
        val id = shiftId ?: return
        lastPersistAt = System.currentTimeMillis()
        repo.updateShift(id) { it.copy(distanceMeters = distanceMeters, movingMs = movingMs) }
    }

    private fun publish() {
        val id = shiftId ?: return
        Live.tracking.value = LiveTracking(
            shiftId = id,
            distanceMeters = distanceMeters,
            movingMs = movingMs,
            paused = paused,
            autoPaused = autoPaused,
            accuracyMeters = lastAccuracy,
            speedKmh = lastSpeedKmh,
            lastFixAt = lastFixAt,
        )
    }

    private fun pause(auto: Boolean) {
        if (paused) return
        val id = shiftId ?: return
        val now = System.currentTimeMillis()
        paused = true
        autoPaused = auto
        stopLocation()
        repo.updateShift(id) {
            it.copy(status = ShiftStatus.PAUSED, pausedAt = now, distanceMeters = distanceMeters, movingMs = movingMs)
        }
        publish()
        updateNotification(force = true)
    }

    private fun resume() {
        if (!paused) return
        val id = shiftId ?: return
        val now = System.currentTimeMillis()
        paused = false
        autoPaused = false
        repo.updateShift(id) {
            val p = it.pausedAt
            it.copy(
                status = ShiftStatus.RUNNING,
                pausedAt = null,
                totalPausedMs = it.totalPausedMs + (if (p != null) now - p else 0L),
            )
        }
        filter.reset()
        lastMovementAt = now
        startLocation()
        publish()
        updateNotification(force = true)
    }

    private fun stopShift() {
        val id = shiftId
        val now = System.currentTimeMillis()
        handler.removeCallbacks(autoPauseTick)
        stopLocation()
        if (id != null) {
            repo.updateShift(id) {
                var s = it.copy(distanceMeters = distanceMeters, movingMs = movingMs)
                val p = s.pausedAt
                if (p != null) s = s.copy(totalPausedMs = s.totalPausedMs + (now - p), pausedAt = null)
                s.copy(status = ShiftStatus.PENDING_CLOSE, endedAt = now)
            }
        }
        shiftId = null
        Live.tracking.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun checkAutoPause() {
        val minutes = repo.data.value.settings.autoPauseMinutes
        if (minutes <= 0 || paused) return
        if (System.currentTimeMillis() - lastMovementAt > minutes * 60_000L) pause(auto = true)
    }

    // ---- GPS ----

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun startLocation() {
        if (requestingUpdates) return
        if (!hasLocationPermission()) {
            Log.w(TAG, "Sin permiso de ubicación; no se puede medir")
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        requestingUpdates = true
    }

    private fun stopLocation() {
        if (!requestingUpdates) return
        fused.removeLocationUpdates(locationCallback)
        requestingUpdates = false
    }

    // ---- Notificación ----

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Turno activo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Odómetro y costo de nafta del turno en curso"
            setShowBadge(false)
        }
        notifications.createNotificationChannel(channel)
    }

    private fun goForeground(): Boolean {
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo pasar a primer plano", e)
            stopSelf()
            false
        }
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifyAt < 5_000L) return
        lastNotifyAt = now
        notifications.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun servicePending(action: String, code: Int): PendingIntent {
        val intent = Intent(this, TrackingService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getForegroundService(this, code, intent, flags)
    }

    private fun buildNotification(): Notification {
        val shift = repo.data.value.activeShift
        val km = distanceMeters / 1000.0
        val cost = if (shift != null) Calc.fuelCost(km, shift.consumptionL100, shift.pricePerLiter) else 0.0
        val gross = if (shift != null) Calc.tripsTotal(shift) else 0.0

        val title = (if (paused) "Turno en pausa" else "Turno en marcha") + " · " + Fmt.km(km)
        val text = buildString {
            append("Nafta ").append(Fmt.money(cost))
            if (gross > 0) append(" · Bruto ").append(Fmt.money(gross)).append(" · Neto ").append(Fmt.money(gross - cost))
            if (paused && autoPaused) append(" · pausado solo por falta de movimiento")
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (paused) {
            builder.addAction(0, "Reanudar", servicePending(ACTION_RESUME, 2))
        } else {
            builder.addAction(0, "Pausar", servicePending(ACTION_PAUSE, 1))
        }
        builder.addAction(0, "Terminar", servicePending(ACTION_STOP, 3))
        return builder.build()
    }

    companion object {
        private const val TAG = "TrackingService"
        const val CHANNEL_ID = "tracking"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.surair.driverprofit.START"
        const val ACTION_PAUSE = "com.surair.driverprofit.PAUSE"
        const val ACTION_RESUME = "com.surair.driverprofit.RESUME"
        const val ACTION_STOP = "com.surair.driverprofit.STOP"

        fun send(context: Context, action: String) {
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun start(context: Context) = send(context, ACTION_START)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)
    }
}
