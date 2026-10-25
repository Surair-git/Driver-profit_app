package com.surair.driverprofit.core

import java.time.ZoneId

object Csv {
    private fun esc(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun num(v: Double, decimals: Int = 2): String = String.format(java.util.Locale.US, "%.${decimals}f", v)

    fun shifts(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.append(
            "id,inicio,fin,estado,minutos_activos,km_total,km_pasajero,km_vacio,viajes,tarifa,premio,propina,otros," +
                "bruto,consumo_l100,precio_litro,litros,costo_nafta,neto,pct_neto,ganancia_por_km_didi,notas\n",
        )
        for (s in shifts.sortedBy { it.startedAt }) {
            val gross = Calc.gross(s)
            val cost = Calc.fuelCost(s)
            val net = gross - cost
            val pct = Calc.netPercent(gross, net)
            val trips = if (s.tripCount > 0) s.tripCount else s.trips.size
            sb.append(
                listOf(
                    s.id,
                    Fmt.dateTime(s.startedAt, zone),
                    s.endedAt?.let { Fmt.dateTime(it, zone) } ?: "",
                    s.status.name,
                    (Calc.activeMs(s, s.endedAt ?: s.startedAt) / 60_000).toString(),
                    num(s.km),
                    num(s.passengerKm),
                    Calc.emptyKm(s)?.let { num(it) } ?: "",
                    trips.toString(),
                    num(s.fareTotal, 0),
                    num(s.bonusTotal, 0),
                    num(s.tipTotal, 0),
                    num(s.otherTotal, 0),
                    num(gross, 0),
                    num(s.consumptionL100, 1),
                    num(s.pricePerLiter, 0),
                    num(Calc.fuelLiters(s)),
                    num(cost, 0),
                    num(net, 0),
                    pct?.let { num(it, 1) } ?: "",
                    num(s.didiPerKm, 0),
                    s.notes,
                ).joinToString(",") { esc(it) },
            ).append('\n')
        }
        return sb.toString()
    }

    fun fuelLoads(loads: List<FuelLoad>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder("id,fecha,importe,precio_litro,litros,tipo,estacion\n")
        for (l in loads.sortedBy { it.at }) {
            sb.append(
                listOf(
                    l.id, Fmt.dateTime(l.at, zone), num(l.amount, 0), num(l.pricePerLiter, 0),
                    num(l.liters), l.fuelType, l.station,
                ).joinToString(",") { esc(it) },
            ).append('\n')
        }
        return sb.toString()
    }
}
