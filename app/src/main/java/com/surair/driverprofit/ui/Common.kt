package com.surair.driverprofit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, big: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            color = valueColor,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tile compacto del tablero: número arriba, etiqueta abajo, fondo propio. */
@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    val cs = MaterialTheme.colorScheme
    val colors = when (tone) {
        CardTone.Normal -> CardDefaults.cardColors(containerColor = cs.surfaceContainer, contentColor = cs.onSurface)
        CardTone.Primary -> CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)
        CardTone.Warning -> CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer)
        CardTone.Error -> CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        shape = RoundedCornerShape(20.dp),
        border = if (tone == CardTone.Normal) androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

enum class CardTone { Normal, Primary, Warning, Error }

@Composable
fun netColor(net: Double): Color =
    if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

@Composable
fun fuelColor(): Color = MaterialTheme.colorScheme.tertiary

/** Barra que muestra qué parte del bruto se fue en nafta y qué parte quedó. */
@Composable
fun SplitBar(gross: Double, fuelCost: Double, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 12.dp) {
    val fuelFraction = when {
        gross <= 0 -> 1f
        fuelCost >= gross -> 1f
        else -> (fuelCost / gross).toFloat().coerceIn(0.02f, 0.98f)
    }
    Row(modifier = modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .weight(fuelFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(fuelColor()),
        )
        if (fuelFraction < 1f) {
            Box(
                modifier = Modifier
                    .weight(1f - fuelFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

/** Anillo de progreso (lo que queda de la carga de nafta). */
@Composable
fun Ring(fraction: Float, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp, stroke: androidx.compose.ui.unit.Dp = 7.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = fuelColor()
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(size)) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
        if (f > 0f) {
            drawArc(fill, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
    }
}

/** Barras verticales simples (neto por día). Valores negativos se dibujan como barra mínima en color de error. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 88.dp) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Row(modifier = modifier.fillMaxWidth().height(height + 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { i, v ->
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val frac = if (v > 0) (v / max).toFloat().coerceIn(0.06f, 1f) else 0.06f
                    val color = when {
                        v > 0 -> MaterialTheme.colorScheme.primary
                        v < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(frac)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(color),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatusPill(text: String, detail: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.8.sp)
        Text(detail, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(0.dp))
    }
}
