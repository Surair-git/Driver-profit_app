package com.surair.driverprofit.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.surair.driverprofit.core.AppData
import com.surair.driverprofit.core.Csv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExport {
    fun share(context: Context, data: AppData) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        // Sin BOM: algunas apps lo muestran como caracteres raros al principio del archivo.
        val daily = File(dir, "resumen-por-dia-$stamp.csv").apply {
            writeText(Csv.dailySummary(data.shifts, data.settings.monthlyFixedCosts))
        }
        val shifts = File(dir, "turnos-$stamp.csv").apply { writeText(Csv.shifts(data.shifts)) }
        val loads = File(dir, "cargas-nafta-$stamp.csv").apply { writeText(Csv.fuelLoads(data.fuelLoads)) }
        val authority = context.packageName + ".fileprovider"
        val uris = arrayListOf<Uri>(
            FileProvider.getUriForFile(context, authority, daily),
            FileProvider.getUriForFile(context, authority, shifts),
            FileProvider.getUriForFile(context, authority, loads),
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Driver Profit - exportación $stamp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar CSV"))
    }
}
