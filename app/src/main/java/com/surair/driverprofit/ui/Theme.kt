package com.surair.driverprofit.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFF7CE0A6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0F5132),
    onPrimaryContainer = Color(0xFFB8F5CE),
    secondary = Color(0xFFF5C26B),
    onSecondary = Color(0xFF3F2A00),
    secondaryContainer = Color(0xFF5A4100),
    onSecondaryContainer = Color(0xFFFFE1A3),
    tertiary = Color(0xFF8FD3FF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0E1412),
    surface = Color(0xFF121A17),
    surfaceVariant = Color(0xFF1E2A25),
    onSurfaceVariant = Color(0xFFB9CBC2),
)

private val Light = lightColorScheme(
    primary = Color(0xFF136B41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2C3),
    secondary = Color(0xFF7A5A00),
    secondaryContainer = Color(0xFFFFDF9B),
    tertiary = Color(0xFF00658E),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
