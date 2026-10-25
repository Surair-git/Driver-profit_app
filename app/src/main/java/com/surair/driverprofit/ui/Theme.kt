package com.surair.driverprofit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta oscura: "tablero nocturno". Un acento verde, ámbar para la nafta.
private val Dark = darkColorScheme(
    primary = Color(0xFF5EE39A),
    onPrimary = Color(0xFF06160E),
    primaryContainer = Color(0xFF10251B),
    onPrimaryContainer = Color(0xFFB8F5CE),
    secondary = Color(0xFFC9D3CE),
    onSecondary = Color(0xFF0B0F0E),
    secondaryContainer = Color(0xFF22302A),
    onSecondaryContainer = Color(0xFFF2F5F3),
    tertiary = Color(0xFFF5B74C),
    onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = Color(0xFF2E2410),
    onTertiaryContainer = Color(0xFFFFE1A3),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2B0A0A),
    errorContainer = Color(0xFF2A1515),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Color(0xFF0B0F0E),
    onBackground = Color(0xFFF2F5F3),
    surface = Color(0xFF0B0F0E),
    onSurface = Color(0xFFF2F5F3),
    surfaceVariant = Color(0xFF151B19),
    onSurfaceVariant = Color(0xFF9AA8A1),
    surfaceContainer = Color(0xFF151B19),
    surfaceContainerHigh = Color(0xFF1B2320),
    surfaceContainerHighest = Color(0xFF22302A),
    surfaceContainerLow = Color(0xFF101613),
    outline = Color(0xFF2A3631),
    outlineVariant = Color(0xFF1B2320),
)

// Paleta clara: "libreta diurna". Papel cálido, verde azulado y naranja para la nafta.
private val Light = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F3EE),
    onPrimaryContainer = Color(0xFF0A4A3D),
    secondary = Color(0xFF4F4A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE9DF),
    onSecondaryContainer = Color(0xFF1E1B16),
    tertiary = Color(0xFFC8551B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBEFE8),
    onTertiaryContainer = Color(0xFF7A2E0B),
    error = Color(0xFFB3461A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBEFE8),
    onErrorContainer = Color(0xFF7A2E0B),
    background = Color(0xFFF6F3EE),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFF6F3EE),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6F675C),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEFE9DF),
    surfaceContainerLow = Color(0xFFFBF9F6),
    outline = Color(0xFFE6E0D6),
    outlineVariant = Color(0xFFEFE9DF),
)

private const val TNUM = "tnum"

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-2).sp, fontFeatureSettings = TNUM),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TNUM),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, fontFeatureSettings = TNUM),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
        bodyLarge = t.bodyLarge.copy(fontFeatureSettings = TNUM),
        bodyMedium = t.bodyMedium.copy(fontFeatureSettings = TNUM),
        bodySmall = t.bodySmall.copy(fontFeatureSettings = TNUM),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun isDarkTheme(themeMode: String): Boolean = when (themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val scheme = if (isDarkTheme(themeMode)) Dark else Light
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
