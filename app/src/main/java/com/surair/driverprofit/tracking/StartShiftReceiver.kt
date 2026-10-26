package com.surair.driverprofit.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.surair.driverprofit.MainActivity

/** Botón "Iniciar turno" de la notificación de inicio automático. */
class StartShiftReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoStart.cancelNotification(context)
        if (!AutoStart.startShift(context)) {
            // Si no se pudo desde el fondo, abrimos la app: al volver al frente levanta el servicio.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_START_SHIFT, true),
            )
        }
    }
}
