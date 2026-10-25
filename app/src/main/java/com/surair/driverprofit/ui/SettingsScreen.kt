package com.surair.driverprofit.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
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

        SectionTitle("Turno")
        NumberField("Pausar solo si no hay movimiento", autoPause, { autoPause = it }, suffix = "min",
            supporting = "Vacío o 0 = apagado. Útil si te olvidás de pausar cuando apagás el motor.")

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
