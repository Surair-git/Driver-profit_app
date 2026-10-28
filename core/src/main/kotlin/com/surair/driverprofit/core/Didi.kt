package com.surair.driverprofit.core

import java.time.Instant
import java.time.ZoneId

/** Lecturas del recuadro "ganancias del día" de DiDi: acumulado que vuelve a $0 a las 00:00. */
object DidiReading {
    /** Más que esto entre dos lecturas no es creíble: casi seguro una cifra mal leída. */
    const val MAX_STEP = 150_000.0

    fun apply(shift: Shift, value: Double, at: Long, zone: ZoneId = ZoneId.systemDefault()): Shift {
        if (value < 0) return shift
        val start = shift.didiDayStart
        val last = shift.didiDayLast
        val lastAt = shift.didiLastReadAt
        if (start == null || last == null || lastAt == null) {
            return shift.copy(didiDayStart = value, didiDayLast = value, didiLastReadAt = at)
        }
        if (value >= last) {
            if (value - last > MAX_STEP) return shift // lectura inverosímil: se ignora
            return shift.copy(didiDayLast = value, didiLastReadAt = at)
        }
        // Bajó. Solo es un reinicio real si cambió el día entre la lectura anterior y esta.
        val prevDay = Instant.ofEpochMilli(lastAt).atZone(zone).toLocalDate()
        val thisDay = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        if (thisDay.isAfter(prevDay)) {
            return shift.copy(
                didiBeforeReset = shift.didiBeforeReset + (last - start),
                didiDayStart = 0.0,
                didiDayLast = value,
                didiLastReadAt = at,
            )
        }
        return shift // misma fecha y bajó: lectura mala, se ignora
    }
}

/** Una línea de texto reconocida en una imagen, con su posición. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val cx: Double get() = (left + right) / 2.0
    val cy: Double get() = (top + bottom) / 2.0
    val height: Int get() = bottom - top
}

data class DidiBreakdown(
    val fare: Double? = null,
    val bonus: Double? = null,
    val tip: Double? = null,
    val refund: Double? = null,
    val other: Double? = null,
    val perKm: Double? = null,
    val perTrip: Double? = null,
    val perHour: Double? = null,
) {
    val isEmpty: Boolean get() = listOf(fare, bonus, tip, refund, other, perKm, perTrip, perHour).all { it == null }
}

/** Saca números de lo que muestra DiDi en pantalla. */
object DidiParser {
    private val amountRegex = Regex("""\$\s?(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?""")

    /** Primer importe con "$" en el texto: "$12.500" → 12500. */
    fun parseAmount(text: String): Double? {
        val m = amountRegex.find(text) ?: return null
        val whole = m.groupValues[1].replace(".", "").toLongOrNull() ?: return null
        val cents = m.groupValues[2]
        val frac = if (cents.isEmpty()) 0.0 else cents.padEnd(2, '0').toDouble() / 100.0
        return whole + frac
    }

    /**
     * El recuadro del día está arriba y centrado. Se toma el primer importe que aparezca
     * en la franja superior y en el tercio central del ancho.
     */
    fun dailyAmount(lines: List<OcrLine>, width: Int, height: Int): Double? {
        val top = height * 0.25
        val xMin = width * 0.2
        val xMax = width * 0.8
        return lines
            .filter { it.cy <= top && it.cx >= xMin && it.cx <= xMax }
            .sortedBy { it.top }
            .firstNotNullOfOrNull { parseAmount(it.text) }
    }

    private fun norm(s: String): String = s.lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')

    private fun amountFor(label: String, lines: List<OcrLine>): Double? {
        val labelLine = lines.firstOrNull { norm(it.text).contains(label) } ?: return null
        parseAmount(labelLine.text)?.let { return it }
        val amounts = lines.filter { it !== labelLine && parseAmount(it.text) != null }
        // A la derecha, en la misma fila (desglose de ganancias).
        val sameRow = amounts.filter { it.left >= labelLine.cx && kotlin.math.abs(it.cy - labelLine.cy) <= labelLine.height * 0.6 }
            .minByOrNull { it.left }
        if (sameRow != null) return parseAmount(sameRow.text)
        // Arriba, en la misma columna (tablero de ganancias: el número va encima del rótulo).
        val above = amounts.filter { it.cx >= labelLine.left - labelLine.height && it.cx <= labelLine.right + labelLine.height && it.bottom <= labelLine.top + labelLine.height / 2 && labelLine.top - it.bottom <= labelLine.height * 3 }
            .maxByOrNull { it.bottom }
        return above?.let { parseAmount(it.text) }
    }

    fun breakdown(lines: List<OcrLine>): DidiBreakdown = DidiBreakdown(
        fare = amountFor("tarifa del viaje", lines),
        bonus = amountFor("premio", lines),
        tip = amountFor("propina", lines),
        refund = amountFor("reembolso", lines),
        other = lines.firstOrNull { norm(it.text).trim().startsWith("otro") }?.let { l -> parseAmount(l.text) ?: amountFor(norm(l.text).trim().take(4), lines) },
        perKm = amountFor("ganancias por km", lines),
        perTrip = amountFor("ganancias por viaje", lines),
        perHour = amountFor("ganancias por hora", lines),
    )
}
