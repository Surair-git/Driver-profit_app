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
import androidx.core.content.IntentCompat
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
import com.surair.driverprofit.core.DidiReading
import com.surair.driverprofit.core.DistanceFilter
import com.surair.driverprofit.core.Fix
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.core.ShiftStatus

/**
 * Odómetro por GPS. Corre como servicio en primer plano con una notificación fija,
 * así sigue midiendo mientras DiDi está en pantalla o con la pantalla apagada.
 * Opcionalmente también lee el recuadro de ganancias del día de DiDi capturando la pantalla.
 */
class TrackingService : Service() {

    private enum class PausedBy { NONE, MANUAL, STOP, AUTO }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var notifications: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private val filter = DistanceFilter()
    private val repo get() = App.repo

    private var shiftId: String? = null
    private var distanceMeters = 0.0
    private var movingMs = 0L
    private var pausedBy = PausedBy.NONE
    private val paused get() = pausedBy != PausedBy.NONE
    private var locationMode = 0 // 0 = apagado, 1 = normal, 2 = bajo consumo (pausa inteligente)

    private var lastAccuracy: Float? = null
    private var lastSpeedKmh: Float? = null
    private var lastFixAt: Long? = null
    private var lastMovementAt = 0L
    private var lastPersistAt = 0L
    private var lastNotifyAt = 0L

    // Pregunta "¿se paró el auto?"
    private var stopPromptSince: Long? = null
    private var stopPromptAnswered = false

    // Lectura del recuadro de DiDi
    private var screenReader: ScreenReader? = null
    private var firstReadingDone = false
    private var lastCaptureRequestAt = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) onLocation(loc)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
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
            pausedBy = if (active.status == ShiftStatus.PAUSED) PausedBy.MANUAL else PausedBy.NONE
            filter.reset()
            lastMovementAt = System.currentTimeMillis()
            stopPromptSince = null
            stopPromptAnswered = false
            firstReadingDone = active.didiDayStart != null
        }

        val projectionCode = intent?.getIntExtra(EXTRA_PROJECTION_CODE, 0) ?: 0
        val projectionData = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_PROJECTION_DATA, Intent::class.java) }
        val wantsProjection = projectionData != null && projectionCode != 0

        if (!goForeground(withProjection = wantsProjection || screenReader?.isRunning == true)) return START_NOT_STICKY

        if (wantsProjection && screenReader?.isRunning != true) {
            val reader = ScreenReader(this) { amount, at -> handler.post { onDidiReading(amount, at) } }
            if (reader.start(projectionCode, projectionData!!)) {
                screenReader?.destroy()
                screenReader = reader
                lastCaptureRequestAt = 0L
            } else {
                reader.destroy()
            }
        }

        when (intent?.action) {
            ACTION_PAUSE -> pause(PausedBy.MANUAL)
            ACTION_PAUSE_STOP -> pause(PausedBy.STOP)
            ACTION_RESUME -> resume()
            ACTION_KEEP_GOING -> keepGoing()
            ACTION_STOP -> {
                stopShift()
                return START_NOT_STICKY
            }
        }
        applyLocationMode()
        publish()
        updateNotification(force = true)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopLocation()
        screenReader?.destroy()
        screenReader = null
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
            if (paused) {
                // Pausa inteligente: el auto volvió a moverse.
                if (pausedBy != PausedBy.MANUAL && repo.data.value.settings.smartResume) {
                    resume()
                    notifications.notify(NOTIFICATION_ID, buildNotification())
                }
            }
            if (!paused) {
                distanceMeters += m.meters
                movingMs += m.millis
            }
            lastMovementAt = now
            if (stopPromptSince != null) clearStopPrompt()
            stopPromptAnswered = false
        }
        publish()
        if (now - lastPersistAt > 20_000L) persist()
        if (now - lastNotifyAt > 5_000L) updateNotification(force = false)
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        val settings = repo.data.value.settings
        val stationaryMs = now - lastMovementAt
        val gpsAlive = lastFixAt?.let { now - it < 20_000L } ?: false

        if (!paused && gpsAlive) {
            val promptMs = settings.stopPromptSeconds * 1000L
            if (promptMs > 0 && stopPromptSince == null && !stopPromptAnswered && stationaryMs > promptMs) {
                showStopPrompt(now)
            }
            val autoMs = settings.autoPauseMinutes * 60_000L
            if (autoMs > 0 && stationaryMs > autoMs) pause(PausedBy.AUTO)
        }

        val reader = screenReader
        if (reader != null && reader.isRunning && !paused) {
            val interval = if (firstReadingDone) settings.didiCaptureMinutes.coerceAtLeast(1) * 60_000L else FIRST_READING_RETRY_MS
            if (now - lastCaptureRequestAt >= interval) {
                lastCaptureRequestAt = now
                reader.requestCapture()
            }
        }
    }

    private fun onDidiReading(amount: Double, at: Long) {
        val id = shiftId ?: return
        firstReadingDone = true
        repo.updateShift(id) { DidiReading.apply(it, amount, at) }
        publish()
        updateNotification(force = true)
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
            autoPaused = pausedBy == PausedBy.AUTO || pausedBy == PausedBy.STOP,
            accuracyMeters = lastAccuracy,
            speedKmh = lastSpeedKmh,
            lastFixAt = lastFixAt,
            stopPromptSince = stopPromptSince,
            capturing = screenReader?.isRunning == true,
        )
    }

    // ---- Pregunta de parada ----

    private fun showStopPrompt(now: Long) {
        stopPromptSince = now
        publish()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), flags)
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("¿Se paró el auto?")
            .setContentText("Hace ${repo.data.value.settings.stopPromptSeconds} s que no hay movimiento. Si pausás, deja de contar tiempo y km, y se reanuda solo cuando arranques.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Hace ${repo.data.value.settings.stopPromptSeconds} s que no hay movimiento. Si pausás, deja de contar tiempo y km, y se reanuda solo cuando arranques."))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(3 * 60_000L)
            .addAction(0, "Pausar", servicePending(ACTION_PAUSE_STOP, 4))
            .addAction(0, "Sigo en viaje", servicePending(ACTION_KEEP_GOING, 5))
            .build()
        notifications.notify(STOP_PROMPT_ID, n)
    }

    private fun clearStopPrompt() {
        stopPromptSince = null
        notifications.cancel(STOP_PROMPT_ID)
        publish()
    }

    private fun keepGoing() {
        stopPromptAnswered = true
        clearStopPrompt()
    }

    // ---- Pausa / reanudar ----

    private fun pause(by: PausedBy) {
        if (paused) return
        val id = shiftId ?: return
        val now = System.currentTimeMillis()
        pausedBy = by
        clearStopPrompt()
        repo.updateShift(id) {
            it.copy(status = ShiftStatus.PAUSED, pausedAt = now, distanceMeters = distanceMeters, movingMs = movingMs)
        }
        applyLocationMode()
        publish()
        updateNotification(force = true)
    }

    private fun resume() {
        if (!paused) return
        val id = shiftId ?: return
        val now = System.currentTimeMillis()
        val wasManual = pausedBy == PausedBy.MANUAL
        pausedBy = PausedBy.NONE
        stopPromptAnswered = false
        repo.updateShift(id) {
            val p = it.pausedAt
            it.copy(
                status = ShiftStatus.RUNNING,
                pausedAt = null,
                totalPausedMs = it.totalPausedMs + (if (p != null) now - p else 0L),
            )
        }
        if (wasManual) filter.reset()
        lastMovementAt = now
        applyLocationMode()
        publish()
        updateNotification(force = true)
    }

    private fun stopShift() {
        val id = shiftId
        val now = System.currentTimeMillis()
        handler.removeCallbacks(tick)
        stopLocation()
        notifications.cancel(STOP_PROMPT_ID)
        screenReader?.destroy()
        screenReader = null
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

    // ---- GPS ----

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Andando: GPS normal. Pausa por parada o automática con reanudación inteligente: GPS lento. Pausa manual: apagado. */
    private fun applyLocationMode() {
        val smart = repo.data.value.settings.smartResume
        val wanted = when {
            !paused -> 1
            pausedBy != PausedBy.MANUAL && smart -> 2
            else -> 0
        }
        if (wanted == locationMode) return
        stopLocation()
        if (wanted != 0) startLocation(lowPower = wanted == 2)
    }

    @Suppress("MissingPermission")
    private fun startLocation(lowPower: Boolean) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Sin permiso de ubicación; no se puede medir")
            return
        }
        val request = if (lowPower) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(4_000L)
                .build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
        }
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        locationMode = if (lowPower) 2 else 1
    }

    private fun stopLocation() {
        if (locationMode == 0) return
        fused.removeLocationUpdates(locationCallback)
        locationMode = 0
    }

    // ---- Notificación ----

    private fun createChannels() {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Turno activo", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Odómetro y costo de nafta del turno en curso"
                setShowBadge(false)
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Avisos del turno", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pregunta si el auto se paró para pausar el conteo"
            },
        )
    }

    private fun goForeground(withProjection: Boolean): Boolean {
        return try {
            var type = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                if (withProjection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
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
        val gross = if (shift != null) Calc.liveGross(shift) else 0.0

        val title = (if (paused) "Turno en pausa" else "Turno en marcha") + " · " + Fmt.km(km)
        val text = buildString {
            append("Nafta ").append(Fmt.money(cost))
            if (gross > 0) append(" · Bruto ").append(Fmt.money(gross)).append(" · Neto ").append(Fmt.money(gross - cost))
            if (paused && pausedBy != PausedBy.MANUAL) append(" · se reanuda solo al moverse")
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
        const val ALERT_CHANNEL_ID = "alerts"
        const val NOTIFICATION_ID = 1
        const val STOP_PROMPT_ID = 3
        private const val TICK_MS = 5_000L
        /** Hasta la primera lectura exitosa del recuadro se insiste seguido; después, cada N minutos. */
        private const val FIRST_READING_RETRY_MS = 20_000L

        const val ACTION_START = "com.surair.driverprofit.START"
        const val ACTION_PAUSE = "com.surair.driverprofit.PAUSE"
        const val ACTION_PAUSE_STOP = "com.surair.driverprofit.PAUSE_STOP"
        const val ACTION_RESUME = "com.surair.driverprofit.RESUME"
        const val ACTION_KEEP_GOING = "com.surair.driverprofit.KEEP_GOING"
        const val ACTION_STOP = "com.surair.driverprofit.STOP"
        const val ACTION_ATTACH_PROJECTION = "com.surair.driverprofit.ATTACH_PROJECTION"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"

        fun send(context: Context, action: String) {
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun start(context: Context) = send(context, ACTION_START)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun pauseFromStop(context: Context) = send(context, ACTION_PAUSE_STOP)
        fun keepGoing(context: Context) = send(context, ACTION_KEEP_GOING)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        /** Inicia el servicio con el permiso de captura de pantalla recién concedido. */
        fun startWithProjection(context: Context, resultCode: Int, data: Intent, action: String = ACTION_START) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(action)
                .putExtra(EXTRA_PROJECTION_CODE, resultCode)
                .putExtra(EXTRA_PROJECTION_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun attachProjection(context: Context, resultCode: Int, data: Intent) =
            startWithProjection(context, resultCode, data, ACTION_ATTACH_PROJECTION)
    }
}
