package com.surair.driverprofit.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.surair.driverprofit.MainActivity
import com.surair.driverprofit.core.ShiftKind

/** Botones de las notificaciones de inicio automático. */
class StartShiftReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoStart.cancelNotification(context)
        val kind = runCatching { ShiftKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }.getOrDefault(ShiftKind.WORK)
        if (!AutoStart.startShift(context, kind)) {
            // Si no se pudo desde el fondo, abrimos la app: al volver al frente levanta el servicio.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_START_SHIFT, kind.name),
            )
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
    }
}
