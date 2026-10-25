package com.surair.driverprofit.core

import java.time.ZoneId

/**
 * Exportación pensada para leerse, no solo para importarse: cada turno y cada carga
 * van en un bloque vertical "Campo;Valor", con una línea en blanco entre bloques.
 * Separador ";" y coma decimal, que es lo que abre Excel y Sheets en español sin configurar nada.
 */
object Csv {
    private const val SEP = ";"

    private fun esc(v: String): String =
        if (v.contains(SEP) || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun num(v: Double, decimals: Int = 0): String {
        if (decimals <= 0) return Math.round(v).toString()
        val s = String.format(java.util.Locale.US, "%.${decimals}f", v)
        return s.replace('.', ',')
    }

    private fun StringBuilder.row(label: String, value: String) {
        append(esc(label)).append(SEP).append(esc(value)).append('\n')
    }

    private fun statusLabel(s: ShiftStatus): String = when (s) {
        ShiftStatus.RUNNING -> "En marcha"
        ShiftStatus.PAUSED -> "En pausa"
        ShiftStatus.PENDING_CLOSE -> "Sin cerrar"
        ShiftStatus.CLOSED -> "Cerrado"
    }

    /** Un bloque vertical por turno. */
    fun shifts(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.row("Campo", "Valor")
        sb.append('\n')
        for (s in shifts.sortedByDescending { it.startedAt }) {
            val gross = Calc.gross(s)
            val cost = Calc.fuelCost(s)
            val net = gross - cost
            val pct = Calc.netPercent(gross, net)
            val trips = if (s.tripCount > 0) s.tripCount else s.trips.size
            val activeMs = Calc.activeMs(s, s.endedAt ?: s.startedAt)
            val emptyKm = Calc.emptyKm(s)

            sb.row("Turno", Fmt.dateTime(s.startedAt, zone))
            sb.row("Fin", s.endedAt?.let { Fmt.time(it, zone) } ?: "")
            sb.row("Estado", statusLabel(s.status))
            sb.row("Tiempo activo", Fmt.duration(activeMs))
            sb.row("Km recorridos", num(s.km, 1))
            sb.row("Km con pasajero", if (s.passengerKm > 0) num(s.passengerKm, 1) else "")
            sb.row("Km en vacío", emptyKm?.let { num(it, 1) } ?: "")
            sb.row("Viajes", if (trips > 0) trips.toString() else "")
            sb.row("Tarifa del viaje", num(s.fareTotal))
            sb.row("Premio", num(s.bonusTotal))
            sb.row("Propina", num(s.tipTotal))
            sb.row("Reembolso y otros", num(s.otherTotal))
            sb.row("Bruto", num(gross))
            sb.row("Consumo (L/100 km)", num(s.consumptionL100, 1))
            sb.row("Precio del litro", num(s.pricePerLiter))
            sb.row("Litros usados", num(Calc.fuelLiters(s), 2))
            sb.row("Costo nafta", num(cost))
            sb.row("Neto", num(net))
            sb.row("Neto sobre bruto (%)", pct?.let { num(it, 1) } ?: "")
            sb.row("Neto por km", if (s.km > 0) num(net / s.km) else "")
            sb.row("Neto por hora activa", if (activeMs > 0) num(net / (activeMs / 3_600_000.0)) else "")
            sb.row("Ganancias por km (DiDi)", if (s.didiPerKm > 0) num(s.didiPerKm) else "")
            sb.row("Notas", s.notes)
            sb.row("Id", s.id)
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Un bloque vertical por carga de nafta. */
    fun fuelLoads(loads: List<FuelLoad>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.row("Campo", "Valor")
        sb.append('\n')
        for (l in loads.sortedByDescending { it.at }) {
            sb.row("Carga", Fmt.dateTime(l.at, zone))
            sb.row("Importe", num(l.amount))
            sb.row("Precio del litro", num(l.pricePerLiter))
            sb.row("Litros", num(l.liters, 2))
            sb.row("Tipo", l.fuelType)
            sb.row("Estación", l.station)
            sb.row("Id", l.id)
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Tabla compacta de un renglón por día, para ver la evolución de un vistazo. */
    fun dailySummary(shifts: List<Shift>, monthlyFixedCosts: Double, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.append(listOf("Día", "Turnos", "Horas activas", "Km", "Km en vacío", "Bruto", "Nafta", "Neto", "Neto sobre bruto (%)", "Neto por hora").joinToString(SEP)).append('\n')
        for (row in Reports.byPeriod(shifts, Period.DAY, monthlyFixedCosts, zone)) {
            val s = row.summary
            sb.append(
                listOf(
                    Fmt.date(row.key.atStartOfDay(zone).toInstant().toEpochMilli(), zone),
                    s.shiftCount.toString(),
                    num(s.activeMs / 3_600_000.0, 1),
                    num(s.km, 1),
                    s.emptyKm?.let { num(it, 1) } ?: "",
                    num(s.gross),
                    num(s.fuelCost),
                    num(s.net),
                    s.netPercent?.let { num(it, 1) } ?: "",
                    s.netPerHour?.let { num(it) } ?: "",
                ).joinToString(SEP) { esc(it) },
            ).append('\n')
        }
        return sb.toString()
    }
}
