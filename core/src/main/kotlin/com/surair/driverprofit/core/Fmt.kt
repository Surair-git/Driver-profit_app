package com.surair.driverprofit.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formato argentino: $15.000, 42,3 km, 9,5 L. */
object Fmt {
    val locale: Locale = Locale("es", "AR")

    fun grouped(n: Long): String {
        val s = abs(n).toString()
        val sb = StringBuilder()
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(s[i])
        }
        return if (n < 0) "-$sb" else sb.toString()
    }

    fun money(v: Double): String {
        val r = v.roundToLong()
        return if (r < 0) "-$" + grouped(-r) else "$" + grouped(r)
    }

    fun dec(v: Double, decimals: Int = 1): String {
        if (decimals <= 0) return grouped(v.roundToLong())
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val scaled = (abs(v) * factor).roundToLong()
        val whole = scaled / factor
        val frac = (scaled % factor).toString().padStart(decimals, '0')
        val sign = if (v < 0 && scaled != 0L) "-" else ""
        return sign + grouped(whole) + "," + frac
    }

    fun km(v: Double, decimals: Int = 1): String = dec(v, decimals) + " km"
    fun liters(v: Double, decimals: Int = 1): String = dec(v, decimals) + " L"
    fun pct(v: Double): String = dec(v, 0) + "%"
    fun l100(v: Double): String = dec(v, 1) + " L/100 km"

    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "$m min"
    }

    fun clock(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%d:%02d:%02d".format(h, m, s)
    }

    private val dateTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale)
    private val date = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val longDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun dateTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(Instant.ofEpochMilli(ms).atZone(zone)).replaceFirstChar { it.uppercase() }

    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        date.format(Instant.ofEpochMilli(ms).atZone(zone)).replaceFirstChar { it.uppercase() }

    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        time.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun longDate(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        longDate.format(Instant.ofEpochMilli(ms).atZone(zone)).replaceFirstChar { it.uppercase() }

    fun month(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        month.format(Instant.ofEpochMilli(ms).atZone(zone)).replaceFirstChar { it.uppercase() }

    /**
     * Acepta "15.000", "15000", "9,5", "9.5", "1.811,50", "$ 15.000".
     * Devuelve null si no es un número.
     */
    fun parseNumber(raw: String): Double? {
        var s = raw.trim().replace("$", "").replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        if (negative) s = s.substring(1)
        val hasComma = s.contains(',')
        val hasDot = s.contains('.')
        val normalized = when {
            hasComma && hasDot -> s.replace(".", "").replace(',', '.')
            hasComma -> s.replace(',', '.')
            hasDot -> if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(s)) s.replace(".", "") else s
            else -> s
        }
        val v = normalized.toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }
}
