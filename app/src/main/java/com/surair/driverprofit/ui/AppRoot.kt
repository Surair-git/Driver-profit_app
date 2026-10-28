package com.surair.driverprofit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.surair.driverprofit.App
import com.surair.driverprofit.tracking.OcrInbox

enum class MainTab(val label: String, val icon: ImageVector) {
    TURNO("Turno", Icons.Filled.DirectionsCar),
    NAFTA("Nafta", Icons.Filled.LocalGasStation),
    HISTORIAL("Historial", Icons.Filled.History),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

@Composable
fun AppRoot() {
    val data by App.repo.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(MainTab.TURNO) }
    var editingShiftId by rememberSaveable { mutableStateOf<String?>(null) }

    // Una captura de DiDi compartida con la app abre el cierre del turno con los datos leídos.
    val requestedShiftId by OcrInbox.openShiftId.collectAsState()
    LaunchedEffect(requestedShiftId) {
        val id = requestedShiftId ?: return@LaunchedEffect
        OcrInbox.openShiftId.value = null
        editingShiftId = id
    }

    val editing = editingShiftId?.let { id -> data.shifts.firstOrNull { it.id == id } }
    if (editing != null) {
        CloseShiftScreen(shift = editing, settings = data.settings, onDone = { editingShiftId = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            MainTab.TURNO -> HomeScreen(data = data, onOpenShift = { editingShiftId = it }, modifier = m)
            MainTab.NAFTA -> FuelScreen(data = data, modifier = m)
            MainTab.HISTORIAL -> HistoryScreen(data = data, onOpenShift = { editingShiftId = it }, modifier = m)
            MainTab.AJUSTES -> SettingsScreen(data = data, modifier = m)
        }
    }
}
