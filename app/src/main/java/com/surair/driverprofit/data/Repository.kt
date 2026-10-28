package com.surair.driverprofit.data

import android.util.Log
import com.surair.driverprofit.core.AppData
import com.surair.driverprofit.core.Calc
import com.surair.driverprofit.core.FuelLoad
import com.surair.driverprofit.core.Settings
import com.surair.driverprofit.core.Shift
import com.surair.driverprofit.core.ShiftKind
import com.surair.driverprofit.core.ShiftStatus
import com.surair.driverprofit.core.TripIncome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Todos los datos viven en un único JSON local. Es chico (unos KB por mes de uso). */
class Repository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val io = Executors.newSingleThreadExecutor()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            json.decodeFromString(AppData.serializer(), file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer ${file.name}; se guarda copia y se arranca vacío", e)
            runCatching { file.copyTo(File(file.path + ".corrupto-" + System.currentTimeMillis()), overwrite = true) }
            AppData()
        }
    }

    fun update(block: (AppData) -> AppData) {
        val next: AppData
        synchronized(lock) {
            next = block(_data.value)
            _data.value = next
        }
        io.execute { save(next) }
    }

    private fun save(d: AppData) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), d))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar", e)
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---- Turnos ----

    fun startShift(kind: ShiftKind = ShiftKind.WORK, now: Long = System.currentTimeMillis()): Shift {
        var created: Shift? = null
        update { d ->
            val existing = d.activeShift
            if (existing != null) {
                created = existing
                return@update d
            }
            val s = d.settings
            val price = if (s.lastPricePerLiter > 0) s.lastPricePerLiter
            else d.fuelLoads.maxByOrNull { it.at }?.pricePerLiter ?: 0.0
            val shift = Shift(
                id = newId(),
                startedAt = now,
                consumptionL100 = s.consumptionL100,
                pricePerLiter = price,
                status = ShiftStatus.RUNNING,
                kind = kind,
            )
            created = shift
            d.copy(shifts = listOf(shift) + d.shifts)
        }
        return created!!
    }

    /** Km hechos con la app cerrada: un recorrido personal ya cerrado, para que descuente nafta. */
    fun addManualKm(km: Double, at: Long = System.currentTimeMillis()) {
        update { d ->
            val s = d.settings
            val price = if (s.lastPricePerLiter > 0) s.lastPricePerLiter else d.fuelLoads.maxByOrNull { it.at }?.pricePerLiter ?: 0.0
            val shift = Shift(
                id = newId(), startedAt = at, endedAt = at, distanceMeters = km * 1000.0,
                consumptionL100 = s.consumptionL100, pricePerLiter = price,
                status = ShiftStatus.CLOSED, kind = ShiftKind.PERSONAL, manual = true,
                notes = "Km cargados a mano",
            )
            d.copy(shifts = listOf(shift) + d.shifts)
        }
    }

    fun updateShift(id: String, block: (Shift) -> Shift) {
        update { d -> d.copy(shifts = d.shifts.map { if (it.id == id) block(it) else it }) }
    }

    fun deleteShift(id: String) {
        update { d -> d.copy(shifts = d.shifts.filterNot { it.id == id }) }
    }

    fun addTrip(shiftId: String, amount: Double, now: Long = System.currentTimeMillis()) {
        updateShift(shiftId) { it.copy(trips = it.trips + TripIncome(newId(), now, amount)) }
    }

    fun removeTrip(shiftId: String, tripId: String) {
        updateShift(shiftId) { s -> s.copy(trips = s.trips.filterNot { it.id == tripId }) }
    }

    // ---- Nafta ----

    fun addFuelLoad(amount: Double, pricePerLiter: Double, fuelType: String, station: String, at: Long = System.currentTimeMillis()) {
        val load = FuelLoad(
            id = newId(), at = at, amount = amount, pricePerLiter = pricePerLiter,
            liters = Calc.litersFromAmount(amount, pricePerLiter), fuelType = fuelType, station = station,
        )
        update { d ->
            d.copy(
                fuelLoads = (d.fuelLoads + load).sortedByDescending { it.at },
                settings = d.settings.copy(lastPricePerLiter = pricePerLiter, fuelType = fuelType),
            )
        }
    }

    fun deleteFuelLoad(id: String) {
        update { d -> d.copy(fuelLoads = d.fuelLoads.filterNot { it.id == id }) }
    }

    // ---- Ajustes ----

    fun updateSettings(block: (Settings) -> Settings) {
        update { d -> d.copy(settings = block(d.settings)) }
    }

    companion object {
        private const val TAG = "Repository"
    }
}
