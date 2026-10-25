package com.surair.driverprofit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.surair.driverprofit.core.Fmt
import kotlinx.coroutines.delay

/** Reloj que avanza cada segundo mientras `active` sea true. */
@Composable
fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        now = System.currentTimeMillis()
        while (active) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun parseOrZero(s: String): Double = Fmt.parseNumber(s) ?: 0.0

fun Double.toInput(decimals: Int = 0): String =
    if (this == 0.0) "" else if (decimals == 0) Fmt.grouped(Math.round(this)) else Fmt.dec(this, decimals)

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    prefix: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, big: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Normal,
    content: @Composable () -> Unit,
) {
    val colors = when (tone) {
        CardTone.Normal -> CardDefaults.cardColors()
        CardTone.Primary -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        CardTone.Warning -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        CardTone.Error -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

enum class CardTone { Normal, Primary, Warning, Error }

@Composable
fun netColor(net: Double): Color =
    if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
