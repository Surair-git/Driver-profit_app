package com.surair.driverprofit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.surair.driverprofit.App
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.core.Settings
import com.surair.driverprofit.core.Shift
import com.surair.driverprofit.core.ShiftStatus
import com.surair.driverprofit.tracking.OcrInbox

/** Cierre o edición de un turno: se cargan los datos del resumen de DiDi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseShiftScreen(shift: Shift, settings: Settings, onDone: () -> Unit) {
    val repo = App.repo
    BackHandler { onDone() }

    val liveTotal = Calc.liveGross(shift)
    val fromDidi = shift.didiGross > 0
    val prefill = remember(shift.id) { OcrInbox.take() }
    val prefillTrips = prefill?.let { b ->
        val g = (b.fare ?: 0.0) + (b.bonus ?: 0.0) + (b.tip ?: 0.0)
        val pt = b.perTrip ?: 0.0
        if (g > 0 && pt > 0) Math.round(g / pt).toInt() else null
    }
    var fare by remember(shift.id) { mutableStateOf((prefill?.fare ?: if (shift.fareTotal > 0) shift.fareTotal else liveTotal).toInput()) }
    var bonus by remember(shift.id) { mutableStateOf((prefill?.bonus ?: shift.bonusTotal).toInput()) }
    var tip by remember(shift.id) { mutableStateOf((prefill?.tip ?: shift.tipTotal).toInput()) }
    var other by remember(shift.id) { mutableStateOf((prefill?.let { (it.refund ?: 0.0) + (it.other ?: 0.0) }?.takeIf { it > 0 } ?: shift.otherTotal).toInput()) }
    var tripCount by remember(shift.id) { mutableStateOf(prefillTrips?.toString() ?: if (shift.tripCount > 0) shift.tripCount.toString() else if (shift.trips.isNotEmpty()) shift.trips.size.toString() else "") }
    var didiPerKm by remember(shift.id) { mutableStateOf((prefill?.perKm ?: shift.didiPerKm).toInput()) }
    var passengerKm by remember(shift.id) { mutableStateOf(shift.passengerKm.toInput(1)) }
    var price by remember(shift.id) { mutableStateOf(shift.pricePerLiter.toInput()) }
    var notes by remember(shift.id) { mutableStateOf(shift.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    val gross = parseOrZero(fare) + parseOrZero(bonus) + parseOrZero(tip) + parseOrZero(other)
    val priceValue = parseOrZero(price)
    val cost = Calc.fuelCost(shift.km, shift.consumptionL100, priceValue)
    val net = gross - cost
    val pct = Calc.netPercent(gross, net)
    val perKmValue = parseOrZero(didiPerKm)
    val typedPassengerKm = Fmt.parseNumber(passengerKm)
    val derivedPassengerKm = if (typedPassengerKm != null && typedPassengerKm > 0) typedPassengerKm else Calc.passengerKmFromDidi(gross, perKmValue)
    val emptyKm = if (derivedPassengerKm > 0) (shift.km - derivedPassengerKm).coerceAtLeast(0.0) else null
    val activeMs = Calc.activeMs(shift, shift.endedAt ?: System.currentTimeMillis())

    fun save() {
        repo.updateShift(shift.id) {
            it.copy(
                fareTotal = parseOrZero(fare),
                bonusTotal = parseOrZero(bonus),
                tipTotal = parseOrZero(tip),
                otherTotal = parseOrZero(other),
                tripCount = tripCount.trim().toIntOrNull() ?: 0,
                didiPerKm = perKmValue,
                passengerKm = derivedPassengerKm,
                pricePerLiter = if (priceValue > 0) priceValue else it.pricePerLiter,
                notes = notes.trim(),
                status = if (it.isActive) it.status else ShiftStatus.CLOSED,
            )
        }
        if (priceValue > 0 && priceValue != settings.lastPricePerLiter) {
            repo.updateSettings { it.copy(lastPricePerLiter = priceValue) }
        }
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (shift.status == ShiftStatus.CLOSED) "Editar turno" else "Cerrar turno") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar turno") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoCard {
                Text(Fmt.dateTime(shift.startedAt) + (shift.endedAt?.let { " → " + Fmt.time(it) } ?: ""), fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Metric("recorridos", Fmt.km(shift.km), modifier = Modifier.weight(1f))
                    Metric("activo", Fmt.duration(activeMs), modifier = Modifier.weight(1f))
                    Metric("nafta", Fmt.liters(Calc.fuelLiters(shift)), modifier = Modifier.weight(1f))
                }
            }

            if (prefill != null) {
                InfoCard(tone = CardTone.Primary) {
                    Text("Datos leídos de la captura de DiDi", fontWeight = FontWeight.SemiBold)
                    Text("Revisalos antes de guardar: el reconocimiento de texto puede confundir alguna cifra.", style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionTitle("Lo que muestra DiDi en “Desglose de ganancias”")
            NumberField("Tarifa del viaje", fare, { fare = it }, prefix = "$",
                supporting = when {
                    prefill?.fare != null -> "Leído de la captura"
                    liveTotal > 0 && shift.fareTotal == 0.0 && fromDidi -> "Precargado con lo que subió el recuadro de DiDi durante el turno (${Fmt.money(liveTotal)}). Ese número ya incluye premios y propinas: si los cargás abajo, restalos de acá."
                    liveTotal > 0 && shift.fareTotal == 0.0 -> "Precargado con los ${shift.trips.size} viajes que anotaste en vivo (${Fmt.money(liveTotal)})"
                    else -> null
                })
            NumberField("Premio", bonus, { bonus = it }, prefix = "$")
            NumberField("Propina", tip, { tip = it }, prefix = "$")
            NumberField("Reembolso de DiDi + Otro", other, { other = it }, prefix = "$")

            SectionTitle("Del “Tablero de ganancias”")
            NumberField("Cantidad de viajes", tripCount, { tripCount = it })
            NumberField(
                "Ganancias por km (DiDi)", didiPerKm, { didiPerKm = it }, prefix = "$",
                supporting = if (perKmValue > 0 && gross > 0 && (typedPassengerKm == null || typedPassengerKm <= 0))
                    "Con eso DiDi te cuenta ${Fmt.km(Calc.passengerKmFromDidi(gross, perKmValue))} con pasajero"
                else "Sirve para deducir los km con pasajero",
            )
            NumberField("Km con pasajero (si lo sabés)", passengerKm, { passengerKm = it }, decimal = true, suffix = "km",
                supporting = "Si lo dejás vacío se calcula con “Ganancias por km”")

            SectionTitle("Nafta")
            NumberField("Precio del litro", price, { price = it }, prefix = "$",
                supporting = "Consumo usado: ${Fmt.l100(shift.consumptionL100)}")

            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notas") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )

            InfoCard {
                Text("Te quedan", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.money(net), style = MaterialTheme.typography.displayMedium, color = netColor(net), maxLines = 1)
                Spacer(Modifier.height(10.dp))
                SplitBar(gross = gross, fuelCost = cost)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendDot(MaterialTheme.colorScheme.onSurface)
                        Text("Bruto de DiDi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(Fmt.money(gross), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendDot(fuelColor())
                        Text("Nafta · ${Fmt.km(shift.km)} · ${Fmt.liters(Calc.fuelLiters(shift))}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("−" + Fmt.money(cost), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = fuelColor())
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Neto sobre bruto", pct?.let { Fmt.pct(it) } ?: "—", emphasize = true, valueColor = netColor(net))
                if (shift.km > 0) {
                    StatRow("Neto por km recorrido", Fmt.money(net / shift.km))
                }
                if (activeMs > 0) {
                    StatRow("Neto por hora activa", Fmt.money(net / (activeMs / 3_600_000.0)))
                }
                if (emptyKm != null) {
                    StatRow("Km con pasajero", Fmt.km(derivedPassengerKm))
                    StatRow("Km en vacío", Fmt.km(emptyKm) + " (${Fmt.pct(if (shift.km > 0) emptyKm / shift.km * 100 else 0.0)})")
                }
            }

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (shift.status == ShiftStatus.CLOSED) "Guardar cambios" else "Guardar y cerrar turno", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Volver sin guardar") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar este turno?") },
            text = { Text("Se borran los km medidos y los datos cargados. No se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    repo.deleteShift(shift.id)
                    onDone()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
