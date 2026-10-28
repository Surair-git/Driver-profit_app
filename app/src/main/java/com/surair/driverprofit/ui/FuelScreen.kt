package com.surair.driverprofit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.surair.driverprofit.core.AppData
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.core.FuelLoadRow
import com.surair.driverprofit.core.Reports

@Composable
fun FuelScreen(data: AppData, modifier: Modifier = Modifier) {
    val repo = App.repo
    val s = data.settings
    val now = rememberNow(data.activeShift != null)
    var showAdd by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showManualKm by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var calcAmount by remember { mutableStateOf("15.000") }

    val rows = remember(data, now / 60_000) { Reports.byFuelLoad(data, now) }
    val observed = Calc.observedConsumption(data)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    TankCard(data = data, now = now)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { showManualKm = true }) { Text("Hice km sin la app") }
                        TextButton(onClick = { showAdjust = true }) { Text("Ajustar litros") }
                    }
                }
            }
            item {
                InfoCard {
                    SectionTitle("Consumo")
                    StatRow("Configurado", Fmt.l100(s.consumptionL100))
                    StatRow("Observado con tus cargas y km", observed?.let { Fmt.l100(it) } ?: "todavía sin datos")
                    Text(
                        if (observed == null) "Aparece después de 2 cargas y más de 150 km medidos entre ellas."
                        else "Es aproximado: supone que el tanque estaba parecido en la primera y en la última carga. Si se mantiene, actualizá el consumo en Ajustes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (observed != null) {
                        TextButton(onClick = { repo.updateSettings { it.copy(consumptionL100 = Math.round(observed * 10) / 10.0) } }) {
                            Text("Usar ${Fmt.l100(observed)} como consumo")
                        }
                    }
                }
            }
            item {
                InfoCard {
                    SectionTitle("¿Cuánto rinde una carga?")
                    NumberField("Importe", calcAmount, { calcAmount = it }, prefix = "$")
                    val amount = parseOrZero(calcAmount)
                    if (s.lastPricePerLiter <= 0) {
                        Text("Registrá una carga para conocer el precio del litro.", style = MaterialTheme.typography.bodySmall)
                    } else if (amount > 0) {
                        val liters = Calc.litersFromAmount(amount, s.lastPricePerLiter)
                        val km = Calc.kmForLiters(liters, s.consumptionL100)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Metric("litros", Fmt.liters(liters), modifier = Modifier.weight(1f))
                            Metric("km aprox.", Fmt.km(km, 0), modifier = Modifier.weight(1f))
                            Metric("por km", Fmt.money(Calc.costPerKm(s.consumptionL100, s.lastPricePerLiter)), modifier = Modifier.weight(1f))
                        }
                        Text(
                            "Para que te quede esa plata libre, tenés que facturar ${Fmt.money(amount)} de más. A ${Fmt.money(s.lastPricePerLiter)} el litro.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item { SectionTitle("Cargas registradas") }
            if (rows.isEmpty()) {
                item {
                    Text("Todavía no registraste ninguna carga. Tocá “Cargué nafta” después de cargar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(rows, key = { it.load.id }) { row ->
                FuelLoadCard(row, isLatest = row == rows.firstOrNull(), onDelete = { deleteId = row.load.id })
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Cargué nafta") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showAdd) {
        AddFuelDialog(
            defaultPrice = s.lastPricePerLiter,
            defaultType = s.fuelType,
            onDismiss = { showAdd = false },
            onConfirm = { amount, price, type, station ->
                repo.addFuelLoad(amount, price, type, station)
                showAdd = false
            },
        )
    }

    if (showManualKm) {
        AmountDialog(
            title = "Km hechos sin la app",
            label = "Kilómetros",
            decimal = true,
            prefix = "",
            onDismiss = { showManualKm = false },
            onConfirm = { km ->
                repo.addManualKm(km)
                showManualKm = false
            },
        )
    }

    if (showAdjust) {
        AmountDialog(
            title = "Litros en el tanque ahora",
            label = "Litros (estimado)",
            decimal = true,
            prefix = "",
            onDismiss = { showAdjust = false },
            onConfirm = { liters ->
                repo.updateSettings { it.copy(tankBaselineLiters = liters, tankBaselineAt = System.currentTimeMillis()) }
                showAdjust = false
            },
        )
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("¿Borrar esta carga?") },
            confirmButton = { Button(onClick = { repo.deleteFuelLoad(id); deleteId = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FuelLoadCard(row: FuelLoadRow, isLatest: Boolean, onDelete: () -> Unit) {
    val l = row.load
    val sum = row.summary
    InfoCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(Fmt.money(l.amount) + " · " + Fmt.liters(l.liters), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(Fmt.dateTime(l.at), l.fuelType, l.station).filter { it.isNotBlank() }.joinToString(" · ") + " · ${Fmt.money(l.pricePerLiter)}/L",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Borrar carga") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(if (isLatest) "Desde esta carga" else "Hasta la carga siguiente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sum.shiftCount == 0 && sum.personalCount == 0) {
            Text("Sin recorridos registrados en este período.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Metric("km de trabajo", Fmt.dec(sum.km, 0), modifier = Modifier.weight(1f))
                Metric("bruto", Fmt.money(sum.gross), modifier = Modifier.weight(1f))
                Metric("recuperado", row.recoveredPercent?.let { Fmt.pct(it) } ?: "—", modifier = Modifier.weight(1f))
            }
            Text(
                "Usaste ${Fmt.liters(sum.totalLiters)} de ${Fmt.liters(l.liters)} (${Fmt.money(sum.fuelCost + sum.personalFuelCost)}) · neto ${Fmt.money(sum.net)}" +
                    (if (sum.personalKm > 0) " · ${Fmt.km(sum.personalKm, 0)} personales" else ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AddFuelDialog(
    defaultPrice: Double,
    defaultType: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, price: Double, type: String, station: String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(defaultPrice.toInput()) }
    var type by remember { mutableStateOf(defaultType) }
    var station by remember { mutableStateOf("") }
    val a = parseOrZero(amount)
    val p = parseOrZero(price)
    val liters = Calc.litersFromAmount(a, p)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carga de nafta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Importe cargado", amount, { amount = it }, prefix = "$")
                NumberField("Precio del litro", price, { price = it }, prefix = "$")
                Text(
                    if (liters > 0) "Son ${Fmt.liters(liters, 2)}" else "Ingresá importe y precio para calcular los litros",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Tipo (Súper, Infinia…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text("Estación (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = a > 0 && p > 0, onClick = { onConfirm(a, p, type.trim().ifEmpty { "Súper" }, station.trim()) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
