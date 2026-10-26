package com.surair.driverprofit.tracking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad mínimo: solo mira qué app pasa al frente para detectar DiDi.
 * No lee el contenido de las pantallas (canRetrieveWindowContent = false).
 */
class AppWatchService : AccessibilityService() {
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastPackage = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Teclado, barra del sistema y nuestra propia app no cuentan como "salir de DiDi".
        if (pkg == packageName || pkg.startsWith("com.android.systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")) return
        val isDidi = AutoStart.isDidiPackage(pkg)
        val wasDidi = lastPackage?.let { AutoStart.isDidiPackage(it) } ?: false
        lastPackage = pkg
        if (isDidi && !wasDidi) AutoStart.onDidiOpened(this)
    }

    override fun onInterrupt() {}
}
