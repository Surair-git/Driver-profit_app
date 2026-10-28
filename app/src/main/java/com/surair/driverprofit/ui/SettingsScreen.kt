package com.surair.driverprofit.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.surair.driverprofit.tracking.AutoStart
import com.surair.driverprofit.tracking.VehicleDetection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.surair.driverprofit.App
import com.surair.driverprofit.BuildConfig
import com.surair.driverprofit.core.AppData
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.export.CsvExport

@Composable
private fun AutoStartSection(data: AppData) {
    val context = LocalContext.current
    val repo = App.repo
    val s = data.settings
    val cs = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityOn by remember { mutableStateOf(AutoStart.isAccessibilityEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessibilityOn = AutoStart.isAccessibilityEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        repo.updateSettings { it.copy(triggerOnBluetooth = granted) }
    }
    var btName by remember(s.bluetoothDeviceName) { mutableStateOf(s.bluetoothDeviceName) }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        repo.updateSettings { it.copy(vehicleDetection = granted) }
        VehicleDetection.sync(context)
    }

    SectionTitle("Inicio automático")
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Detectar que voy en el auto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Usa el acelerómetro (sin GPS, casi sin batería) y funciona con la app cerrada. Tarda de 30 s a un par de minutos en darse cuenta y te pregunta: Turno DiDi o Viaje personal. Al bajar del auto tras un rato quieto, sugiere terminar. Pide el permiso \"Actividad física\".",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Switch(
            checked = s.vehicleDetection,
            onCheckedChange = { on ->
                if (!on) {
                    repo.updateSettings { it.copy(vehicleDetection = false) }
                    VehicleDetection.sync(context)
                } else if (!VehicleDetection.hasPermission(context)) {
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    repo.updateSettings { it.copy(vehicleDetection = true) }
                    VehicleDetection.sync(context)
                }
            },
        )
    }

    Text("Cuando abrís DiDi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val options = listOf("off" to "Nada", "ask" to "Preguntar", "auto" to "Iniciar solo")
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = s.autoStartMode == key,
                onClick = { repo.updateSettings { it.copy(autoStartMode = key) } },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label) }
        }
    }
    if (s.autoStartMode != "off") {
        InfoCard(tone = if (accessibilityOn) CardTone.Primary else CardTone.Warning) {
            Text(
                if (accessibilityOn) "Detección de DiDi activada" else "Falta activar la detección de DiDi",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (accessibilityOn) "Driver Profit se entera cuando DiDi pasa al frente. No lee el contenido de la pantalla."
                else "Tocá el botón, buscá \"Driver Profit: detectar DiDi\" y activalo. En Android 13 o más nuevo, si aparece bloqueado, primero andá a Información de la app, menú de tres puntos, \"Permitir configuración restringida\".",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!accessibilityOn) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    runCatching { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Abrir Accesibilidad") }
            }
        }
        if (s.autoStartMode == "auto") {
            Text(
                "Si Android no deja arrancar el GPS desde el fondo, en vez de iniciar solo te llega la notificación para iniciarlo con un toque.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Avisar al conectar el cargador", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Notificación con el botón \"Iniciar turno\" cada vez que enchufás el celular.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked = s.triggerOnCharger, onCheckedChange = { on -> repo.updateSettings { it.copy(triggerOnCharger = on) } })
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Avisar al conectar el Bluetooth del auto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Pide el permiso de Bluetooth para saber qué dispositivo se conectó.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(
            checked = s.triggerOnBluetooth,
            onCheckedChange = { on ->
                if (!on) {
                    repo.updateSettings { it.copy(triggerOnBluetooth = false) }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    repo.updateSettings { it.copy(triggerOnBluetooth = true) }
                }
            },
        )
    }
    if (s.triggerOnBluetooth) {
        OutlinedTextField(
            value = btName,
            onValueChange = { btName = it; repo.updateSettings { st -> st.copy(bluetoothDeviceName = it) } },
            label = { Text("Solo si el nombre contiene (opcional)") },
            supportingText = { Text("Por ejemplo \"Palio\" o \"Car\". Vacío = cualquier dispositivo Bluetooth.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        "Las notificaciones traen dos botones: Turno DiDi (abre la app y pide el permiso de captura) y Viaje personal (arranca directo). No se avisa si ya hay un recorrido en marcha, ni dentro de los 15 minutos de un aviso o de un recorrido terminado.",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
}

@Composable
fun SettingsScreen(data: AppData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = App.repo
    val s = data.settings

    var brand by remember(s) { mutableStateOf(s.carBrand) }
    var model by remember(s) { mutableStateOf(s.carModel) }
    var engine by remember(s) { mutableStateOf(s.carEngine) }
    var consumption by remember(s) { mutableStateOf(Fmt.dec(s.consumptionL100, 1)) }
    var price by remember(s) { mutableStateOf(s.lastPricePerLiter.toInput()) }
    var fuelType by remember(s) { mutableStateOf(s.fuelType) }
    var fixed by remember(s) { mutableStateOf(s.monthlyFixedCosts.toInput()) }
    var autoPause by remember(s) { mutableStateOf(if (s.autoPauseMinutes > 0) s.autoPauseMinutes.toString() else "") }
    var stopPrompt by remember(s) { mutableStateOf(if (s.stopPromptSeconds > 0) s.stopPromptSeconds.toString() else "") }
    var saved by remember { mutableStateOf(false) }

    val consumptionValue = Fmt.parseNumber(consumption) ?: s.consumptionL100
    val priceValue = parseOrZero(price)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SectionTitle("Tema")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf("system" to "Sistema", "dark" to "Oscuro", "light" to "Claro")
            options.forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = s.themeMode == key,
                    onClick = { repo.updateSettings { it.copy(themeMode = key) } },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) { Text(label) }
            }
        }
        Text(
            "Oscuro es el tablero nocturno, pensado para mirar de reojo manejando. Claro es la libreta diurna, más cómoda para revisar en casa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Auto")
        OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = engine, onValueChange = { engine = it }, label = { Text("Motor") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionTitle("Consumo y nafta")
        NumberField(
            "Consumo en ciudad", consumption, { consumption = it }, decimal = true, suffix = "L/100 km",
            supporting = "Palio 1.4 Fire: fichas oficiales dan 8,9 en ciudad; con paradas y espera, 9,5 es un buen punto de partida.",
        )
        NumberField("Precio del litro", price, { price = it }, prefix = "$",
            supporting = if (priceValue > 0) "Costo por km: ${Fmt.money(Calc.costPerKm(consumptionValue, priceValue))}" else "Se actualiza solo con cada carga que registres")
        OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text("Tipo de nafta") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionTitle("Otros gastos (opcional)")
        NumberField("Gastos fijos por mes", fixed, { fixed = it }, prefix = "$",
            supporting = "Seguro, patente, service. Se prorratea por día en el historial. Dejalo vacío si solo querés ver nafta.")

        SectionTitle("Paradas")
        NumberField("Preguntar si el auto se paró después de", stopPrompt, { stopPrompt = it }, suffix = "seg",
            supporting = "Tiempo de gracia para semáforos. Pasado ese tiempo sin movimiento, pregunta si pausar. Vacío o 0 = no preguntar.")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reanudar solo al moverse", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Después de pausar por una parada, el GPS sigue escuchando despacio y el turno se reanuda cuando el auto arranca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.smartResume, onCheckedChange = { on -> repo.updateSettings { it.copy(smartResume = on) } })
        }
        NumberField("Pausar sin preguntar después de", autoPause, { autoPause = it }, suffix = "min",
            supporting = "Vacío o 0 = apagado. Por si no respondés la pregunta y querés que pause igual.")

        SectionTitle("Lectura del recuadro de DiDi")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Leer las ganancias del día de DiDi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Al iniciar el turno Android pide permiso para capturar la pantalla. Se lee solo la franja de arriba, en el celular, sin internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.didiCaptureEnabled, onCheckedChange = { on -> repo.updateSettings { it.copy(didiCaptureEnabled = on) } })
        }
        if (s.didiCaptureEnabled) {
            Text("Leer cada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(5, 10, 15)
                options.forEachIndexed { i, minutes ->
                    SegmentedButton(
                        selected = s.didiCaptureMinutes == minutes,
                        onClick = { repo.updateSettings { it.copy(didiCaptureMinutes = minutes) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    ) { Text("$minutes min") }
                }
            }
            Text("Los viajes anotados a mano siguen disponibles; si hay lectura de DiDi, manda esa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = {
                repo.updateSettings {
                    it.copy(
                        carBrand = brand.trim(),
                        carModel = model.trim(),
                        carEngine = engine.trim(),
                        consumptionL100 = if (consumptionValue > 0) consumptionValue else it.consumptionL100,
                        lastPricePerLiter = priceValue,
                        fuelType = fuelType.trim().ifEmpty { "Súper" },
                        monthlyFixedCosts = parseOrZero(fixed),
                        autoPauseMinutes = autoPause.trim().toIntOrNull() ?: 0,
                        stopPromptSeconds = stopPrompt.trim().toIntOrNull() ?: 0,
                    )
                }
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Guardar ajustes") }
        if (saved) Text("Guardado.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)

        SectionTitle("Batería")
        Text(
            "Para que Android no corte el GPS en turnos largos, poné la app como “Sin restricciones” en la optimización de batería.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Abrir optimización de batería") }

        AutoStartSection(data)

        SectionTitle("Datos")
        Text(
            "Todo se guarda en este teléfono. Exportá cada tanto para tener respaldo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { CsvExport.share(context, data) }, modifier = Modifier.fillMaxWidth()) { Text("Exportar CSV (resumen por día, turnos y cargas)") }

        Spacer(Modifier.height(8.dp))
        Text(
            "Driver Profit ${BuildConfig.VERSION_NAME} · ${data.shifts.size} turnos · ${data.fuelLoads.size} cargas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
