package com.surair.driverprofit.tracking

import com.surair.driverprofit.core.DidiBreakdown
import kotlinx.coroutines.flow.MutableStateFlow

/** Resultado de una captura del desglose de DiDi compartida con la app, a la espera de que se abra el cierre del turno. */
object OcrInbox {
    val prefill = MutableStateFlow<DidiBreakdown?>(null)
    val openShiftId = MutableStateFlow<String?>(null)

    fun take(): DidiBreakdown? {
        val p = prefill.value
        prefill.value = null
        return p
    }
}
