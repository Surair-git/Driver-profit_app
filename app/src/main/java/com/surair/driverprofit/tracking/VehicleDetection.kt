package com.surair.driverprofit.tracking

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.surair.driverprofit.App

/**
 * Detección de "va en un vehículo" con el acelerómetro, a cargo de los servicios de Google.
 * Despierta a la app aunque esté cerrada; casi no gasta batería porque no usa GPS.
 */
object VehicleDetection {
    private const val TAG = "VehicleDetection"
    private const val REQUEST_CODE = 30

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, VehicleReceiver::class.java).setAction(VehicleReceiver.ACTION)
        // Mutable: el sistema completa el intent con el resultado de la detección.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** Registra o quita la detección según el ajuste y el permiso. Es seguro llamarla varias veces. */
    @Suppress("MissingPermission")
    fun sync(context: Context) {
        val enabled = App.repo.data.value.settings.vehicleDetection
        val client = ActivityRecognition.getClient(context)
        val pi = pendingIntent(context)
        if (enabled && hasPermission(context)) {
            val transitions = listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            )
            client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi)
                .addOnFailureListener { Log.w(TAG, "No se pudo registrar la detección de vehículo", it) }
        } else {
            client.removeActivityTransitionUpdates(pi)
        }
    }
}

class VehicleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (event in result.transitionEvents) {
            if (event.activityType != DetectedActivity.IN_VEHICLE) continue
            when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> AutoStart.onVehicleEnter(context)
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> AutoStart.onVehicleExit(context)
            }
        }
    }

    companion object {
        const val ACTION = "com.surair.driverprofit.VEHICLE_TRANSITION"
    }
}

/** Después de reiniciar el teléfono hay que volver a registrar la detección. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) VehicleDetection.sync(context)
    }
}
