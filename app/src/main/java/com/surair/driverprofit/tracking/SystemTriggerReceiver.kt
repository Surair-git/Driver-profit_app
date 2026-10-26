package com.surair.driverprofit.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat

/** Cargador conectado o Bluetooth conectado: avisar para iniciar el turno. */
class SystemTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> AutoStart.onCharger(context)
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val name = try {
                    device?.name
                } catch (e: SecurityException) {
                    null
                }
                AutoStart.onBluetooth(context, name)
            }
        }
    }
}
