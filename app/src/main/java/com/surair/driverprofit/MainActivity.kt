package com.surair.driverprofit

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.surair.driverprofit.core.DidiParser
import com.surair.driverprofit.core.ShiftKind
import com.surair.driverprofit.tracking.StartRequest
import com.surair.driverprofit.tracking.Ocr
import com.surair.driverprofit.tracking.OcrInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.surair.driverprofit.tracking.TrackingService
import com.surair.driverprofit.ui.AppRoot
import com.surair.driverprofit.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleSharedImage(intent)
        setContent {
            val data by App.repo.data.collectAsState()
            AppTheme(themeMode = data.settings.themeMode) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedImage(intent)
    }

    /** Captura del desglose de DiDi compartida con la app: se le reconoce el texto y se precarga el cierre. */
    private fun handleSharedImage(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        intent.action = null
        val repo = App.repo
        val target = repo.data.value.let { d ->
            d.pendingShifts.firstOrNull() ?: d.shifts.filter { !it.isActive }.maxByOrNull { it.startedAt }
        }
        if (target == null) {
            Toast.makeText(this, "No hay ningún turno al que cargarle estos datos", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Leyendo la captura…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            }
            if (bitmap == null) {
                Toast.makeText(this@MainActivity, "No se pudo abrir la imagen", Toast.LENGTH_LONG).show()
                return@launch
            }
            Ocr.recognize(
                bitmap,
                onResult = { lines ->
                    val breakdown = DidiParser.breakdown(lines)
                    if (breakdown.isEmpty) {
                        Toast.makeText(this@MainActivity, "No encontré importes de DiDi en la captura", Toast.LENGTH_LONG).show()
                    } else {
                        OcrInbox.prefill.value = breakdown
                        OcrInbox.openShiftId.value = target.id
                    }
                },
                onError = { Toast.makeText(this@MainActivity, "No se pudo leer la captura", Toast.LENGTH_LONG).show() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Pedido de inicio que llega desde una notificación: lo atiende la pantalla principal,
        // que pide los permisos y el consentimiento de captura si hacen falta.
        intent?.getStringExtra(EXTRA_START_SHIFT)?.let { kindName ->
            intent.removeExtra(EXTRA_START_SHIFT)
            runCatching { ShiftKind.valueOf(kindName) }.getOrNull()?.let { StartRequest.pending.value = it }
        }
        // Si quedó un turno activo guardado pero el servicio no está corriendo
        // (por ejemplo, el sistema lo mató), lo volvemos a levantar.
        if (App.repo.data.value.activeShift != null) {
            TrackingService.start(this)
        }
    }

    companion object {
        const val EXTRA_START_SHIFT = "start_shift"
    }
}
