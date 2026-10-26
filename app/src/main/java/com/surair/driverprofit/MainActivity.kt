package com.surair.driverprofit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.surair.driverprofit.tracking.TrackingService
import com.surair.driverprofit.ui.AppRoot
import com.surair.driverprofit.ui.AppTheme
import com.surair.driverprofit.ui.hasLocationPermission

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
    }

    override fun onResume() {
        super.onResume()
        // Pedido de inicio automático que no se pudo atender desde el fondo.
        if (intent?.getBooleanExtra(EXTRA_START_SHIFT, false) == true) {
            intent.removeExtra(EXTRA_START_SHIFT)
            if (App.repo.data.value.activeShift == null && hasLocationPermission(this)) {
                App.repo.startShift()
            }
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
