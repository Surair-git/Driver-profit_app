package com.surair.driverprofit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun HistoryScreen(data: AppData, onOpenShift: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val period = when (tabIndex) { 0 -> Period.DAY; 1 -> Period.WEEK; else -> Period.MONTH }
    val rows = remember(data, period) { Reports.byPeriod(data.shifts, period, data.settings.monthlyFixedCosts) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)) {
            Text("Historial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { CsvExport.share(context, data) }) { Icon(Icons.Filled.Share, contentDescription = "Exportar CSV") }
        }
        TabRow(selectedTabIndex = tabIndex) {
            listOf("Diario", "Semanal", "Mensual").forEachIndexed { i, label ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(label) })
            }
        }
        if (rows.isEmpty()) {
            Text(
                "Todavía no hay turnos terminados.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.key.toString() }) { row ->
                PeriodCard(row, onOpenShift)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PeriodCard(row: PeriodRow, onOpenShift: (String) -> Unit) {
    val s = row.summary
    InfoCard {
        Text(row.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Metric("km", Fmt.dec(s.km, 0), modifier = Modifier.weight(1f))
            Metric("bruto", Fmt.money(s.gross), modifier = Modifier.weight(1f))
            Metric("nafta", Fmt.money(s.fuelCost), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Metric(s.netPercent?.let { "neto · ${Fmt.pct(it)}" } ?: "neto", Fmt.money(s.net), modifier = Modifier.weight(1f), valueColor = netColor(s.net))
            Metric("por km", s.netPerKm?.let { Fmt.money(it) } ?: "—", modifier = Modifier.weight(1f))
            Metric("por hora", s.netPerHour?.let { Fmt.money(it) } ?: "—", modifier = Modifier.weight(1f))
        }
        val extra = buildList {
            add("${s.shiftCount} turno${if (s.shiftCount == 1) "" else "s"} · ${Fmt.duration(s.activeMs)}")
            if (s.tripCount > 0) add("${s.tripCount} viajes")
            s.emptyKm?.let { add("${Fmt.km(it, 0)} en vacío") }
            if (row.fixedCosts > 0) add("neto con gastos fijos ${Fmt.money(row.netAfterFixed)}")
        }
        Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        row.shifts.forEach { shift -> ShiftLine(shift, onClick = { onOpenShift(shift.id) }) }
    }
}

@Composable
private fun ShiftLine(shift: Shift, onClick: () -> Unit) {
    val gross = Calc.gross(shift)
    val net = Calc.net(shift)
    val pct = Calc.netPercent(shift)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                Fmt.time(shift.startedAt) + (shift.endedAt?.let { "–" + Fmt.time(it) } ?: "") + " · " + Fmt.km(shift.km),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (shift.status == ShiftStatus.PENDING_CLOSE) "Sin cerrar: tocá para cargar los datos de DiDi"
                else "bruto ${Fmt.money(gross)} · nafta ${Fmt.money(Calc.fuelCost(shift))}",
                style = MaterialTheme.typography.bodySmall,
                color = if (shift.status == ShiftStatus.PENDING_CLOSE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(net), fontWeight = FontWeight.Bold, color = netColor(net))
            Text(pct?.let { Fmt.pct(it) } ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}
