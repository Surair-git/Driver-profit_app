package com.surair.driverprofit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.surair.driverprofit.App
import com.surair.driverprofit.core.AppData
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.core.Reports
import com.surair.driverprofit.core.Shift
import com.surair.driverprofit.core.ShiftStatus
import com.surair.driverprofit.core.Summary
import com.surair.driverprofit.tracking.Live
import com.surair.driverprofit.tracking.TrackingService

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun requiredPermissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
    return list.toTypedArray()
}

@Composable
fun HomeScreen(data: AppData, onOpenShift: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = App.repo
    val live by Live.tracking.collectAsState()
    val active = data.activeShift
    val now = rememberNow(active != null)
    val settings = data.settings

    var showTripDialog by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Al terminar un turno (desde la app o desde la notificación) se abre sola la pantalla de cierre.
    var lastActiveId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active?.id) {
        val prev = lastActiveId
        if (active == null && prev != null && data.shifts.any { it.id == prev && it.status == ShiftStatus.PENDING_CLOSE }) {
            onOpenShift(prev)
        }
        lastActiveId = active?.id
    }

    fun launchShift() {
        repo.startShift()
        TrackingService.start(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) launchShift() else permissionDenied = true
    }
    fun startWithPermission() {
        if (hasLocationPermission(context)) launchShift() else permissionLauncher.launch(requiredPermissions())
    }
    fun requestStart() {
        if (settings.lastPricePerLiter <= 0) showPriceDialog = true else startWithPermission()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${settings.carBrand} ${settings.carModel} ${settings.carEngine}".trim(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (permissionDenied) {
            InfoCard(tone = CardTone.Error) {
                Text("Sin permiso de ubicación no se puede medir el recorrido.", fontWeight = FontWeight.SemiBold)
                Text("Activalo en Ajustes del teléfono › Aplicaciones › Driver Profit › Permisos.", style = MaterialTheme.typography.bodySmall)
            }
        }

        val pending = data.pendingShifts
        if (pending.isNotEmpty()) {
            InfoCard(tone = CardTone.Warning) {
                Text(
                    if (pending.size == 1) "Tenés un turno sin cerrar" else "Tenés ${pending.size} turnos sin cerrar",
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Cargá lo que te mostró DiDi para calcular la ganancia neta.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onOpenShift(pending.first().id) }) { Text("Cargar datos de DiDi") }
            }
        }

        if (active != null) {
            ActiveShiftCard(
                shift = active,
                liveDistance = if (live?.shiftId == active.id) live?.distanceMeters else null,
                gpsLine = gpsStatus(live, active, now),
                now = now,
                onPause = { TrackingService.pause(context) },
                onResume = { TrackingService.resume(context) },
                onStop = { showStopConfirm = true },
                onAddTrip = { showTripDialog = true },
                onRemoveTrip = { repo.removeTrip(active.id, it) },
            )
        } else {
            Button(
                onClick = { requestStart() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(8.dp))
                Text("Iniciar turno", style = MaterialTheme.typography.titleLarge)
            }
            CostPerKmCard(settings.consumptionL100, settings.lastPricePerLiter)
        }

        TankCard(data = data, now = now)

        val today = Reports.today(data.shifts, now = now)
        val week = Reports.thisWeek(data.shifts, now = now)
        SummaryCard("Hoy", today)
        SummaryCard("Esta semana", week)

        Spacer(Modifier.height(24.dp))
    }

    if (showTripDialog && active != null) {
        AmountDialog(
            title = "Ganancia del viaje",
            label = "Importe que te mostró DiDi",
            onDismiss = { showTripDialog = false },
            onConfirm = { amount ->
                repo.addTrip(active.id, amount)
                showTripDialog = false
            },
        )
    }

    if (showPriceDialog) {
        AmountDialog(
            title = "Precio del litro",
            label = "¿A cuánto está el litro de ${settings.fuelType}?",
            onDismiss = { showPriceDialog = false },
            onConfirm = { price ->
                repo.updateSettings { it.copy(lastPricePerLiter = price) }
                showPriceDialog = false
                startWithPermission()
            },
        )
    }

    if (showStopConfirm && active != null) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("¿Terminar el turno?") },
            text = { Text("Se deja de medir el recorrido. Después cargás lo que te mostró DiDi para ver la ganancia neta.") },
            confirmButton = {
                Button(onClick = {
                    showStopConfirm = false
                    TrackingService.stop(context)
                }) { Text("Terminar") }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("Seguir") } },
        )
    }
}

private fun gpsStatus(live: com.surair.driverprofit.tracking.LiveTracking?, shift: Shift, now: Long): String {
    if (shift.status == ShiftStatus.PAUSED) return if (live?.autoPaused == true) "En pausa (se pausó solo por falta de movimiento)" else "En pausa · GPS apagado"
    if (live == null || live.shiftId != shift.id) return "Iniciando el servicio de GPS…"
    val fixAt = live.lastFixAt ?: return "Buscando señal de GPS…"
    if (now - fixAt > 20_000L) return "Sin señal de GPS hace ${(now - fixAt) / 1000} s"
    val acc = live.accuracyMeters?.let { "±${it.toInt()} m" } ?: ""
    val speed = live.speedKmh?.let { "${it.toInt()} km/h" } ?: ""
    return listOf("GPS", acc, speed).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun ActiveShiftCard(
    shift: Shift,
    liveDistance: Double?,
    gpsLine: String,
    now: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onAddTrip: () -> Unit,
    onRemoveTrip: (String) -> Unit,
) {
    val meters = maxOf(liveDistance ?: 0.0, shift.distanceMeters)
    val km = meters / 1000.0
    val cost = Calc.fuelCost(km, shift.consumptionL100, shift.pricePerLiter)
    val gross = Calc.tripsTotal(shift)
    val net = gross - cost
    val pct = Calc.netPercent(gross, net)
    val paused = shift.status == ShiftStatus.PAUSED

    InfoCard(tone = if (paused) CardTone.Warning else CardTone.Primary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = {}, label = { Text(if (paused) "En pausa" else "En marcha") })
            Text(Fmt.clock(Calc.activeMs(shift, now)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(gpsLine, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Metric("recorridos", Fmt.km(km), modifier = Modifier.weight(1f), big = true)
            Metric("en nafta", Fmt.money(cost), modifier = Modifier.weight(1f), big = true)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Metric("bruto (${shift.trips.size} viajes)", Fmt.money(gross), modifier = Modifier.weight(1f))
            Metric(
                if (pct != null) "neto · ${Fmt.pct(pct)}" else "neto",
                Fmt.money(net),
                modifier = Modifier.weight(1f),
                valueColor = netColor(net),
            )
        }
        Text(
            "Costo por km: ${Fmt.money(Calc.costPerKm(shift.consumptionL100, shift.pricePerLiter))} · ${Fmt.l100(shift.consumptionL100)} · ${Fmt.money(shift.pricePerLiter)}/L",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (paused) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Reanudar")
                }
            } else {
                FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Pausar")
                }
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Terminar")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddTrip, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Viaje terminado: cargar ganancia")
        }
        if (shift.trips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            shift.trips.sortedByDescending { it.at }.take(8).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${Fmt.time(t.at)} · ${Fmt.money(t.amount)}", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onRemoveTrip(t.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar viaje") }
                }
            }
        }
    }
}

@Composable
private fun CostPerKmCard(consumption: Double, price: Double) {
    InfoCard {
        if (price <= 0) {
            Text("Falta el precio del litro", fontWeight = FontWeight.SemiBold)
            Text("Se te va a pedir al iniciar el turno, o cargalo registrando una carga de nafta.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric("por km en nafta", Fmt.money(Calc.costPerKm(consumption, price)), modifier = Modifier.weight(1f))
                Metric("consumo", Fmt.l100(consumption), modifier = Modifier.weight(1f))
            }
            Text("Litro a ${Fmt.money(price)}. Se ajusta en Nafta o Ajustes.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun TankCard(data: AppData, now: Long) {
    val s = data.settings
    val liters = Calc.tankBalanceLiters(data, now)
    InfoCard {
        SectionTitle("Nafta en el tanque (estimado)")
        if (liters == null) {
            Text("Registrá una carga de nafta en la pestaña Nafta para ver cuánto te queda y cuántos km rinde.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val km = Calc.kmForLiters(liters, s.consumptionL100)
            val money = liters * s.lastPricePerLiter
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric("litros", Fmt.liters(liters), modifier = Modifier.weight(1f), valueColor = if (liters < 3) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
                Metric("km que rinden", Fmt.km(km, 0), modifier = Modifier.weight(1f))
                Metric("en plata", Fmt.money(money), modifier = Modifier.weight(1f))
            }
            if (liters < 0) {
                Text("Quedó negativo: hiciste km que no se cubren con las cargas registradas. Ajustá el saldo desde Nafta.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, s: Summary) {
    InfoCard {
        SectionTitle(title)
        if (s.shiftCount == 0) {
            Text("Sin turnos todavía.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric("km", Fmt.dec(s.km, 1), modifier = Modifier.weight(1f))
                Metric("bruto", Fmt.money(s.gross), modifier = Modifier.weight(1f))
                Metric("nafta", Fmt.money(s.fuelCost), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric(
                    s.netPercent?.let { "neto · ${Fmt.pct(it)}" } ?: "neto",
                    Fmt.money(s.net),
                    modifier = Modifier.weight(1f),
                    valueColor = netColor(s.net),
                )
                Metric("por hora neto", s.netPerHour?.let { Fmt.money(it) } ?: "—", modifier = Modifier.weight(1f))
                Metric("turnos", "${s.shiftCount} · ${Fmt.duration(s.activeMs)}", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AmountDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    initial: String = "",
    decimal: Boolean = false,
    prefix: String = "$",
) {
    var text by remember { mutableStateOf(initial) }
    val value = Fmt.parseNumber(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NumberField(label = label, value = text, onValueChange = { text = it }, decimal = decimal, prefix = prefix)
        },
        confirmButton = {
            Button(enabled = value != null && value > 0, onClick = { onConfirm(value ?: 0.0) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
