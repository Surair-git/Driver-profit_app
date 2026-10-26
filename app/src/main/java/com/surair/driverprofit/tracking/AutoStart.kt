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
    private const val PREFS = "autostart"
    private const val KEY_LAST_AT = "lastAt"
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

        if (autoStart && hasLocationPermission(context) && startShift(context)) {
            notify(context, title = "Turno iniciado", text = "$reason. Ya se está midiendo el recorrido.", withAction = false)
        } else {
            notify(context, title = "¿Iniciás turno?", text = reason, withAction = true)
        }
    }

    /** Crea el turno y levanta el servicio de GPS. Devuelve false si Android no dejó arrancarlo desde el fondo. */
    fun startShift(context: Context): Boolean {
        val repo = App.repo
        if (repo.data.value.activeShift != null) return true
        val shift = repo.startShift()
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

    private fun notify(context: Context, title: String, text: String, withAction: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Inicio automático", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso para iniciar el turno al abrir DiDi o conectar el auto"
            },
        )
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
        if (withAction) {
            val start = PendingIntent.getBroadcast(context, 11, Intent(context, StartShiftReceiver::class.java), flags)
            builder.addAction(0, "Iniciar turno", start)
        }
        nm.notify(NOTIFICATION_ID, builder.build())
    }
}
