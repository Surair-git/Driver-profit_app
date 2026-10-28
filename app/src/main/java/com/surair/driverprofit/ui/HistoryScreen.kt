package com.surair.driverprofit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.surair.driverprofit.core.AppData
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.Fmt
import com.surair.driverprofit.core.Period
import com.surair.driverprofit.core.PeriodRow
import com.surair.driverprofit.core.Reports
import com.surair.driverprofit.core.Shift
import com.surair.driverprofit.core.ShiftStatus
import com.surair.driverprofit.export.CsvExport
import java.time.ZoneId
import java.time.format.TextStyle

@Composable
fun HistoryScreen(data: AppData, onOpenShift: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(1) }
    val period = when (tabIndex) { 0 -> Period.DAY; 1 -> Period.WEEK; else -> Period.MONTH }
    val rows = remember(data, period) { Reports.byPeriod(data.shifts, period, data.settings.monthlyFixedCosts) }
    val cs = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
            Text("Historial", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { CsvExport.share(context, data) }) { Icon(Icons.Filled.Share, contentDescription = "Exportar CSV") }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Día", "Semana", "Mes").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tabIndex == i,
                    onClick = { tabIndex = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                ) { Text(label) }
            }
        }
        if (rows.isEmpty()) {
            Text("Todavía no hay turnos terminados.", modifier = Modifier.padding(16.dp), color = cs.onSurfaceVariant)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.key.toString() }) { row ->
                PeriodCard(row, period, data, onOpenShift)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PeriodCard(row: PeriodRow, period: Period, data: AppData, onOpenShift: (String) -> Unit) {
    val s = row.summary
    val cs = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    InfoCard {
        Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Fmt.money(s.net), style = MaterialTheme.typography.displayMedium, color = netColor(s.net), maxLines = 1)
            Text("neto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            buildList {
                s.netPercent?.let { add("${Fmt.pct(it)} de ${Fmt.money(s.gross)} brutos") } ?: add("bruto ${Fmt.money(s.gross)}")
                add("${s.shiftCount} turno${if (s.shiftCount == 1) "" else "s"}")
                add("${Fmt.duration(s.activeMs)} al volante")
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        when (period) {
            Period.WEEK -> {
                val values = Reports.dailyNet(row.shifts, row.key, 7, zone)
                val labels = (0 until 7).map { row.key.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Fmt.locale).uppercase() }
                BarChart(values, labels)
            }
            Period.MONTH -> {
                val days = row.key.lengthOfMonth()
                val values = Reports.dailyNet(row.shifts, row.key, days, zone)
                val labels = (1..days).map { if (it == 1 || it % 5 == 0) it.toString() else "" }
                BarChart(values, labels, height = 72.dp)
            }
            Period.DAY -> {
                SplitBar(gross = s.gross, fuelCost = s.fuelCost)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(s.emptyKm?.let { "${Fmt.km(it, 0)} en vacío" } ?: "recorridos", Fmt.km(s.km, 0), modifier = Modifier.weight(1f))
            Tile("nafta · ${Fmt.liters(s.fuelLiters, 0)}", Fmt.money(s.fuelCost), modifier = Modifier.weight(1f), valueColor = fuelColor())
            Tile("neto por hora", s.netPerHour?.let { Fmt.money(it) } ?: "—", modifier = Modifier.weight(1f))
        }
        if (s.personalKm > 0) {
            Text(
                "Uso personal: ${Fmt.km(s.personalKm, 0)} · ${Fmt.money(s.personalFuelCost)} de nafta, fuera de la ganancia",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (row.fixedCosts > 0) {
            Text(
                "Con gastos fijos prorrateados (${Fmt.money(row.fixedCosts)}): neto ${Fmt.money(row.netAfterFixed)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("Recorridos")
        row.shifts.forEachIndexed { i, shift ->
            if (i > 0) HorizontalDivider(color = cs.outlineVariant)
            ShiftLine(shift, showDay = period != Period.DAY, onClick = { onOpenShift(shift.id) })
        }
    }
}

@Composable
private fun ShiftLine(shift: Shift, showDay: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gross = Calc.gross(shift)
    val net = Calc.net(shift)
    val pct = Calc.netPercent(shift)
    val pending = shift.status == ShiftStatus.PENDING_CLOSE
    val personal = !shift.isWork
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDay) {
            val date = java.time.Instant.ofEpochMilli(shift.startedAt).atZone(ZoneId.systemDefault())
            Column(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerHighest),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Fmt.locale).uppercase().take(3), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                Fmt.time(shift.startedAt) + (shift.endedAt?.let { "–" + Fmt.time(it) } ?: "") + " · " + Fmt.km(shift.km),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    personal && shift.manual -> "Personal · km cargados a mano"
                    personal -> "Personal · fuera de la ganancia"
                    pending -> "Sin cerrar: faltan los datos de DiDi"
                    else -> "bruto ${Fmt.money(gross)} · nafta ${Fmt.money(Calc.fuelCost(shift))}" + (if (shift.tripCount > 0) " · ${shift.tripCount} viajes" else "")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (pending) cs.tertiary else cs.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (pending || personal) {
                Text("−" + Fmt.money(Calc.fuelCost(shift)), style = MaterialTheme.typography.titleMedium, color = cs.tertiary)
                Text("nafta", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                Text(Fmt.money(net), style = MaterialTheme.typography.titleMedium, color = netColor(net))
                Text(pct?.let { Fmt.pct(it) } ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}
