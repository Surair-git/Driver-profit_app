package com.surair.driverprofit.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.surair.driverprofit.App
import com.surair.driverprofit.MainActivity
import com.surair.driverprofit.R
import com.surair.driverprofit.core.ShiftKind
import com.surair.driverprofit.ui.hasLocationPermission

/**
 * Inicio automático del turno: cuando DiDi pasa al frente, se conecta el cargador
 * o se conecta el Bluetooth del auto. Según el ajuste, pregunta con una notificación
 * o inicia el turno directamente.
 */
object AutoStart {
    private const val TAG = "AutoStart"
    const val CHANNEL_ID = "autostart"
    const val NOTIFICATION_ID = 2
    const val EXIT_NOTIFICATION_ID = 4
    private const val PREFS = "autostart"
    private const val KEY_LAST_AT = "lastAt"
    private const val KEY_LAST_EXIT_AT = "lastExitAt"
    /** Para sugerir terminar hace falta que el recorrido lleve este tiempo sin moverse. */
    private const val EXIT_STILL_MS = 8 * 60_000L
    /** No volver a sugerir dentro de este lapso, ni enseguida después de terminar un turno. */
    private const val COOLDOWN_MS = 15 * 60_000L

    fun isDidiPackage(pkg: String): Boolean = pkg.lowercase().contains("didi")

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, AppWatchService::class.java)
        val long = me.flattenToString()
        val short = me.flattenToShortString()
        return enabled.split(':').any { it.equals(long, ignoreCase = true) || it.equals(short, ignoreCase = true) }
    }

    fun onDidiOpened(context: Context) {
        val mode = App.repo.data.value.settings.autoStartMode
        if (mode == "off") return
        suggest(context, autoStart = mode == "auto", reason = "Abriste DiDi")
    }

    /** El teléfono va en un vehículo (acelerómetro). */
    fun onVehicleEnter(context: Context) {
        if (!App.repo.data.value.settings.vehicleDetection) return
        suggest(context, autoStart = false, reason = "Parece que arrancaste")
    }

    /** Bajó del vehículo. Solo sugiere terminar si el recorrido lleva un rato sin moverse. */
    fun onVehicleExit(context: Context) {
        if (!App.repo.data.value.settings.vehicleDetection) return
        val active = App.repo.data.value.activeShift ?: return
        val live = Live.tracking.value
        val now = System.currentTimeMillis()
        val still = live?.lastMovementAt?.let { now - it > EXIT_STILL_MS } ?: false
        if (live != null && live.shiftId == active.id && !still) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST_EXIT_AT, 0L) < COOLDOWN_MS) return
        prefs.edit().putLong(KEY_LAST_EXIT_AT, now).apply()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getForegroundService(
            context, 14, Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP), flags,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (active.isWork) "¿Terminaste el turno?" else "¿Terminaste el viaje?")
            .setContentText("Parece que bajaste del auto. Si seguís, ignorá este aviso.")
            .setContentIntent(PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), flags))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter(10 * 60_000L)
            .addAction(0, "Terminar", stop)
            .build()
        nm.notify(EXIT_NOTIFICATION_ID, n)
    }

    fun onCharger(context: Context) {
        if (!App.repo.data.value.settings.triggerOnCharger) return
        suggest(context, autoStart = false, reason = "Conectaste el cargador")
    }

    fun onBluetooth(context: Context, deviceName: String?) {
        val s = App.repo.data.value.settings
        if (!s.triggerOnBluetooth) return
        val filter = s.bluetoothDeviceName.trim()
        if (filter.isNotEmpty() && (deviceName == null || !deviceName.contains(filter, ignoreCase = true))) return
        suggest(context, autoStart = false, reason = "Se conectó ${deviceName ?: "el Bluetooth"}")
    }

    private fun suggest(context: Context, autoStart: Boolean, reason: String) {
        val data = App.repo.data.value
        if (data.activeShift != null) return
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST_AT, 0L) < COOLDOWN_MS) return
        val lastEnd = data.shifts.mapNotNull { it.endedAt }.maxOrNull() ?: 0L
        if (now - lastEnd < COOLDOWN_MS) return
        prefs.edit().putLong(KEY_LAST_AT, now).apply()

        if (autoStart && hasLocationPermission(context) && startShift(context, ShiftKind.WORK)) {
            notify(context, title = "Turno iniciado", text = "$reason. Ya se está midiendo el recorrido.", withActions = false)
        } else {
            notify(context, title = "¿Arrancás un recorrido?", text = reason, withActions = true)
        }
    }

    /** Crea el recorrido y levanta el servicio de GPS. Devuelve false si Android no dejó arrancarlo desde el fondo. */
    fun startShift(context: Context, kind: ShiftKind): Boolean {
        val repo = App.repo
        if (repo.data.value.activeShift != null) return true
        val shift = repo.startShift(kind)
        return try {
            TrackingService.start(context)
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "No se pudo iniciar el servicio desde el fondo", e)
            repo.deleteShift(shift.id)
            false
        }
    }

    fun cancelNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Inicio automático", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso para iniciar o terminar un recorrido al abrir DiDi, conectar el auto o detectar que vas en vehículo"
            },
        )
    }

    private fun notify(context: Context, title: String, text: String, withActions: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), flags)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter(10 * 60_000L)
        if (withActions) {
            // "Turno DiDi" abre la app, que pide el permiso de captura de pantalla y arranca.
            val work = PendingIntent.getActivity(
                context, 12,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_START_SHIFT, ShiftKind.WORK.name),
                flags,
            )
            // "Viaje personal" arranca directo, sin abrir nada.
            val personal = PendingIntent.getBroadcast(
                context, 13,
                Intent(context, StartShiftReceiver::class.java).putExtra(StartShiftReceiver.EXTRA_KIND, ShiftKind.PERSONAL.name),
                flags,
            )
            builder.addAction(0, "Turno DiDi", work)
            builder.addAction(0, "Viaje personal", personal)
        }
        nm.notify(NOTIFICATION_ID, builder.build())
    }
}
