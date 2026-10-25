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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
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
import com.surair.driverprofit.tracking.LiveTracking
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
            ActiveDashboard(
                data = data,
                shift = active,
                live = if (live?.shiftId == active.id) live else null,
                now = now,
                onPause = { TrackingService.pause(context) },
                onResume = { TrackingService.resume(context) },
                onStop = { showStopConfirm = true },
                onAddTrip = { showTripDialog = true },
                onRemoveTrip = { repo.removeTrip(active.id, it) },
            )
        } else {
            IdleHeader(data = data, now = now, onStart = { requestStart() })
        }

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

private fun gpsStatus(live: LiveTracking?, shift: Shift, now: Long): String {
    if (shift.status == ShiftStatus.PAUSED) return if (live?.autoPaused == true) "Pausado solo por falta de movimiento" else "En pausa · GPS apagado"
    if (live == null) return "Iniciando el servicio de GPS…"
    val fixAt = live.lastFixAt ?: return "Buscando señal de GPS…"
    if (now - fixAt > 20_000L) return "Sin señal de GPS hace ${(now - fixAt) / 1000} s"
    val acc = live.accuracyMeters?.let { "±${it.toInt()} m" } ?: ""
    val speed = live.speedKmh?.let { "${it.toInt()} km/h" } ?: ""
    return listOf("GPS", acc, speed).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun CarLine(data: AppData, detail: String) {
    val s = data.settings
    Column {
        Text(
            "${s.carBrand} ${s.carModel} ${s.carEngine}".trim().uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tablero del turno en marcha: el neto manda, el resto acompaña. */
@Composable
private fun ActiveDashboard(
    data: AppData,
    shift: Shift,
    live: LiveTracking?,
    now: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onAddTrip: () -> Unit,
    onRemoveTrip: (String) -> Unit,
) {
    val meters = maxOf(live?.distanceMeters ?: 0.0, shift.distanceMeters)
    val km = meters / 1000.0
    val cost = Calc.fuelCost(km, shift.consumptionL100, shift.pricePerLiter)
    val gross = Calc.tripsTotal(shift)
    val net = gross - cost
    val pct = Calc.netPercent(gross, net)
    val paused = shift.status == ShiftStatus.PAUSED
    val activeMs = Calc.activeMs(shift, now)
    val perHour = if (activeMs > 10 * 60_000L && gross > 0) net / (activeMs / 3_600_000.0) else null
    val cs = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CarLine(data, gpsStatus(live, shift, now))
            StatusPill(
                text = if (paused) "En pausa" else "En marcha",
                detail = Fmt.clock(activeMs),
                color = if (paused) cs.tertiary else cs.primary,
            )
        }

        Column(modifier = Modifier.padding(top = 6.dp)) {
            Text("Neto hasta ahora", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text(Fmt.money(net), style = MaterialTheme.typography.displayLarge, color = netColor(net), maxLines = 1)
            Text(
                buildString {
                    if (pct != null) append(Fmt.pct(pct)).append(" del bruto") else append("Anotá los viajes para ver el neto en vivo")
                    if (perHour != null) append(" · ").append(Fmt.money(perHour)).append(" por hora")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.secondary,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("km", Fmt.dec(km, 1), modifier = Modifier.weight(1f))
            Tile("nafta", Fmt.money(cost), modifier = Modifier.weight(1f), valueColor = fuelColor())
            Tile(if (shift.trips.isEmpty()) "bruto" else "bruto · ${shift.trips.size} viajes", Fmt.money(gross), modifier = Modifier.weight(1f))
        }

        TankCard(data = data, now = now, compact = true)

        Button(
            onClick = onAddTrip,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Viaje terminado", style = MaterialTheme.typography.titleLarge)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (paused) {
                FilledTonalButton(onClick = onResume, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reanudar", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pausar", style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.errorContainer, contentColor = cs.error),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Terminar", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (shift.trips.isNotEmpty()) {
            InfoCard {
                SectionTitle("Viajes de este turno")
                shift.trips.sortedByDescending { it.at }.take(8).forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(Fmt.time(t.at), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Text("Viaje ${shift.trips.indexOf(t) + 1}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.money(t.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onRemoveTrip(t.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar viaje", tint = cs.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleHeader(data: AppData, now: Long, onStart: () -> Unit) {
    val s = data.settings
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CarLine(data, "Sin turno en curso")
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("Iniciar turno", style = MaterialTheme.typography.headlineSmall)
        }
        if (s.lastPricePerLiter <= 0) {
            InfoCard(tone = CardTone.Warning) {
                Text("Falta el precio del litro", fontWeight = FontWeight.SemiBold)
                Text("Se te va a pedir al iniciar el turno, o cargalo registrando una carga de nafta.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("por km en nafta", Fmt.money(Calc.costPerKm(s.consumptionL100, s.lastPricePerLiter)), modifier = Modifier.weight(1f), valueColor = fuelColor())
                Tile("consumo", Fmt.l100(s.consumptionL100), modifier = Modifier.weight(1f))
                Tile("litro", Fmt.money(s.lastPricePerLiter), modifier = Modifier.weight(1f))
            }
        }
        TankCard(data = data, now = now, compact = false)
    }
}

/** Lo que queda de la última carga: anillo + km + plata. */
@Composable
fun TankCard(data: AppData, now: Long, compact: Boolean = false) {
    val s = data.settings
    val liters = Calc.tankBalanceLiters(data, now)
    val lastLoad = data.fuelLoads.maxByOrNull { it.at }
    val cs = MaterialTheme.colorScheme
    InfoCard {
        if (liters == null || lastLoad == null) {
            SectionTitle("Nafta en el tanque")
            Text("Registrá una carga de nafta en la pestaña Nafta para ver cuánto te queda y cuántos km rinde.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val km = Calc.kmForLiters(liters, s.consumptionL100)
            val money = liters * s.lastPricePerLiter
            val fraction = if (lastLoad.liters > 0) (liters / lastLoad.liters).toFloat() else 0f
            val recovered = Reports.byFuelLoad(data, now).firstOrNull()?.recoveredPercent
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Ring(fraction = fraction, size = if (compact) 52.dp else 64.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Carga de ${Fmt.money(lastLoad.amount)}", style = MaterialTheme.typography.titleMedium)
                        Text(Fmt.liters(liters), style = MaterialTheme.typography.bodyMedium, color = if (liters < 3) cs.error else cs.onSurfaceVariant)
                    }
                    Text(
                        if (liters < 0) "Hiciste más km de los que cubren las cargas. Ajustá el saldo desde Nafta."
                        else "Quedan unos ${Fmt.km(km, 0)} · ${Fmt.money(money)} en nafta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (liters < 0) cs.error else cs.onSurface,
                    )
                    if (recovered != null) {
                        Text(
                            if (recovered >= 100) "Ya recuperaste el ${Fmt.pct(recovered)} de esta carga" else "Recuperaste el ${Fmt.pct(recovered)} de esta carga",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, s: Summary) {
    val cs = MaterialTheme.colorScheme
    InfoCard {
        SectionTitle(title)
        if (s.shiftCount == 0) {
            Text("Sin turnos todavía.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Fmt.money(s.net), style = MaterialTheme.typography.displaySmall, color = netColor(s.net))
                Text(
                    s.netPercent?.let { "neto · ${Fmt.pct(it)}" } ?: "neto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            SplitBar(gross = s.gross, fuelCost = s.fuelCost)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("bruto", Fmt.money(s.gross), modifier = Modifier.weight(1f))
                Metric("nafta", Fmt.money(s.fuelCost), modifier = Modifier.weight(1f), valueColor = fuelColor())
                Metric("km", Fmt.dec(s.km, 0), modifier = Modifier.weight(1f))
            }
            Text(
                buildList {
                    add("${s.shiftCount} turno${if (s.shiftCount == 1) "" else "s"} · ${Fmt.duration(s.activeMs)}")
                    s.netPerHour?.let { add("${Fmt.money(it)} por hora") }
                    s.emptyKm?.let { add("${Fmt.km(it, 0)} en vacío") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
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
