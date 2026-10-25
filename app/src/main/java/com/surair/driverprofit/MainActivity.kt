package com.surair.driverprofit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.surair.driverprofit.tracking.TrackingService
import com.surair.driverprofit.ui.AppRoot
import com.surair.driverprofit.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Si quedó un turno activo guardado pero el servicio no está corriendo
        // (por ejemplo, el sistema lo mató), lo volvemos a levantar.
        if (App.repo.data.value.activeShift != null) {
            TrackingService.start(this)
        }
    }
}
