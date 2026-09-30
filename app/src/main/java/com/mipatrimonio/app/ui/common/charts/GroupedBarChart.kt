package com.mipatrimonio.app.ui.common.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.ui.common.MoneyColors

@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    incomeLabel: String,
    expenseLabel: String,
    formatValue: (Long) -> String,
    description: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val semanticModifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = description
    }
    if (groups.isEmpty()) {
        Box(
            modifier = semanticModifier.fillMaxWidth().height(BAR_CHART_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedIndex by remember(groups) { mutableStateOf<Int?>(null) }
    Column(modifier = semanticModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(color = MoneyColors.positive, label = incomeLabel)
            LegendItem(
                color = MoneyColors.negative,
                label = expenseLabel,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        selectedIndex?.let { index ->
            BarSelection(
                group = groups[index.coerceIn(groups.indices)],
                incomeLabel = incomeLabel,
                expenseLabel = expenseLabel,
                formatValue = formatValue,
            )
        }

        BarPlot(
            groups = groups,
            selectedIndex = selectedIndex,
            onSelected = { selectedIndex = it },
        )
    }
}

@Composable
private fun BarPlot(groups: List<BarGroup>, selectedIndex: Int?, onSelected: (Int) -> Unit) {
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.primaryContainer
    val maximum = remember(groups) {
        maximumMagnitude(groups.flatMap { listOf(it.incomeMinor, it.expenseMinor) })
    }

    Column(Modifier.fillMaxWidth().height(BAR_CHART_HEIGHT)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(groups) {
                    detectTapGestures { position ->
                        onSelected(nearestIndex(position.x, groups.size, size.width.toFloat()))
                    }
                },
        ) {
            repeat(GUIDE_LINE_COUNT) { index ->
                val y = size.height * (index + 1) / (GUIDE_LINE_COUNT + 1)
                drawLine(guideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = GUIDE_STROKE.toPx())
            }
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = AXIS_STROKE.toPx())

            val slotWidth = size.width / groups.size
            val barWidth = (slotWidth * BAR_WIDTH_FRACTION).coerceAtMost(MAX_BAR_WIDTH.toPx())
            groups.forEachIndexed { index, group ->
                val slotLeft = index * slotWidth
                if (selectedIndex == index) {
                    drawRect(
                        color = selectionColor.copy(alpha = SELECTION_ALPHA),
                        topLeft = Offset(slotLeft, 0f),
                        size = Size(slotWidth, size.height),
                    )
                }
                val centerX = slotLeft + slotWidth / 2f
                drawBar(
                    color = MoneyColors.positive,
                    left = centerX - barWidth - BAR_GAP.toPx() / 2f,
                    height = size.height * magnitudeRatio(group.incomeMinor, maximum),
                    width = barWidth,
                )
                drawBar(
                    color = MoneyColors.negative,
                    left = centerX + BAR_GAP.toPx() / 2f,
                    height = size.height * magnitudeRatio(group.expenseMinor, maximum),
                    width = barWidth,
                )
            }
        }
        BarAxisLabels(groups)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    color: Color,
    left: Float,
    height: Float,
    width: Float,
) {
    drawRect(
        color = color,
        topLeft = Offset(left, size.height - height),
        size = Size(width, height),
    )
}

@Composable
private fun BarAxisLabels(groups: List<BarGroup>) {
    val shownIndices = remember(groups.size) { labelIndices(groups.size, MAX_VISIBLE_LABELS) }
    Row(Modifier.fillMaxWidth().height(AXIS_LABEL_HEIGHT), verticalAlignment = Alignment.Bottom) {
        groups.forEachIndexed { index, group ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                if (index in shownIndices) {
                    Text(
                        text = group.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun labelIndices(count: Int, maximumLabels: Int): Set<Int> {
    if (count <= maximumLabels) return (0 until count).toSet()
    if (maximumLabels <= 1) return setOf(0)
    return (0 until maximumLabels)
        .map { position -> position * (count - 1) / (maximumLabels - 1) }
        .toSet()
}

@Composable
private fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BarSelection(
    group: BarGroup,
    incomeLabel: String,
    expenseLabel: String,
    formatValue: (Long) -> String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(group.label, style = MaterialTheme.typography.labelLarge)
            SelectionValue(incomeLabel, formatValue(group.incomeMinor), MoneyColors.positive)
            SelectionValue(expenseLabel, formatValue(group.expenseMinor), MoneyColors.negative)
        }
    }
}

@Composable
private fun SelectionValue(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

private val BAR_CHART_HEIGHT = 200.dp
private val AXIS_LABEL_HEIGHT = 24.dp
private val GUIDE_STROKE = 1.dp
private val AXIS_STROKE = 1.5.dp
private val MAX_BAR_WIDTH = 28.dp
private val BAR_GAP = 3.dp
private const val BAR_WIDTH_FRACTION = 0.28f
private const val GUIDE_LINE_COUNT = 3
private const val MAX_VISIBLE_LABELS = 4
private const val SELECTION_ALPHA = 0.35f
