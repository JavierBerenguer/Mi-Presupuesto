package com.mipatrimonio.app.ui.common.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    formatValue: (Long) -> String,
    description: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val positiveSlices = remember(slices) { slices.filter { it.valueMinor > 0L } }
    val semanticModifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = description
    }
    if (positiveSlices.isEmpty()) {
        Box(
            modifier = semanticModifier.fillMaxWidth().size(DONUT_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sweeps = remember(positiveSlices) { donutSweeps(positiveSlices.map(DonutSlice::valueMinor)) }
    val percentages = remember(positiveSlices) { percentages(positiveSlices.map(DonutSlice::valueMinor)) }
    val percentageFormat = remember {
        NumberFormat.getPercentInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }
    var selectedIndex by remember(positiveSlices) { mutableStateOf<Int?>(null) }

    Column(
        modifier = semanticModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(DONUT_SIZE), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(positiveSlices) {
                        detectTapGestures { position ->
                            donutIndexAt(position, size.width.toFloat(), sweeps)?.let { selectedIndex = it }
                        }
                    },
            ) {
                val strokeWidth = DONUT_STROKE.toPx()
                val selectedExtra = SELECTED_EXTRA_STROKE.toPx()
                val diameter = size.minDimension - strokeWidth - selectedExtra
                val arcTopLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                var startAngle = START_ANGLE
                positiveSlices.forEachIndexed { index, slice ->
                    val selected = selectedIndex == index
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweeps[index],
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth + if (selected) selectedExtra else 0f),
                    )
                    startAngle += sweeps[index]
                }
            }
            Text(centerLabel, style = MaterialTheme.typography.titleMedium)
        }

        selectedIndex?.let { index ->
            DonutSelection(
                slice = positiveSlices[index.coerceIn(positiveSlices.indices)],
                percentage = formatPercentage(percentages[index], percentageFormat),
                formatValue = formatValue,
            )
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            positiveSlices.forEachIndexed { index, slice ->
                DonutLegendItem(
                    slice = slice,
                    value = formatValue(slice.valueMinor),
                    percentage = formatPercentage(percentages[index], percentageFormat),
                )
            }
        }
    }
}

@Composable
private fun DonutSelection(slice: DonutSlice, percentage: String, formatValue: (Long) -> String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(slice.color, CircleShape))
            Text(slice.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Column(horizontalAlignment = Alignment.End) {
                Text(formatValue(slice.valueMinor), style = MaterialTheme.typography.labelLarge)
                Text(percentage, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DonutLegendItem(slice: DonutSlice, value: String, percentage: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(slice.color, CircleShape))
        Text(slice.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Text(percentage, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun donutIndexAt(position: Offset, canvasWidth: Float, sweeps: List<Float>): Int? {
    if (canvasWidth <= 0f || sweeps.isEmpty()) return null
    val center = canvasWidth / 2f
    val deltaX = position.x - center
    val deltaY = position.y - center
    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
    val outerRadius = canvasWidth / 2f
    val innerRadius = outerRadius * DONUT_INNER_RADIUS_FRACTION
    if (distance !in innerRadius..outerRadius) return null

    val angle = ((Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat() - START_ANGLE) + FULL_CIRCLE) % FULL_CIRCLE
    var accumulated = 0f
    sweeps.forEachIndexed { index, sweep ->
        accumulated += sweep
        if (angle < accumulated || index == sweeps.lastIndex) return index
    }
    return null
}

private fun formatPercentage(percentage: Int, formatter: NumberFormat): String =
    formatter.format(BigDecimal.valueOf(percentage.toLong()).movePointLeft(2))

private val DONUT_SIZE = 200.dp
private val DONUT_STROKE = 32.dp
private val SELECTED_EXTRA_STROKE = 8.dp
private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f
private const val DONUT_INNER_RADIUS_FRACTION = 0.45f
