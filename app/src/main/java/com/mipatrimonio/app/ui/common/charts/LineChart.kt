package com.mipatrimonio.app.ui.common.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatValue: (Long) -> String,
    description: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val semanticModifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = description
    }
    if (points.isEmpty()) {
        Box(
            modifier = semanticModifier.fillMaxWidth().height(LINE_CHART_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedIndex by remember(points) { mutableIntStateOf(points.lastIndex) }
    val selectedPoint = points[selectedIndex.coerceIn(points.indices)]
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerOuterColor = MaterialTheme.colorScheme.surface

    Box(modifier = semanticModifier.fillMaxWidth().height(LINE_CHART_HEIGHT)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = LINE_TOP_PADDING, bottom = AXIS_LABEL_HEIGHT)
                .pointerInput(points) {
                    detectTapGestures { position ->
                        selectedIndex = nearestIndex(position.x, points.size, size.width.toFloat())
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            selectedIndex = nearestIndex(position.x, points.size, size.width.toFloat())
                        },
                        onHorizontalDrag = { change, _ ->
                            selectedIndex = nearestIndex(change.position.x, points.size, size.width.toFloat())
                            change.consume()
                        },
                    )
                },
        ) {
            val mappedPoints = linePoints(points.map(ChartPoint::value), size.width, size.height)
            repeat(GUIDE_LINE_COUNT) { index ->
                val y = size.height * (index + 1) / (GUIDE_LINE_COUNT + 1)
                drawLine(guideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = GUIDE_STROKE.toPx())
            }

            val zeroY = zeroAxisY(points.map(ChartPoint::value), size.height)
            drawLine(zeroColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = ZERO_STROKE.toPx())

            val linePath = smoothPath(mappedPoints)
            val areaPath = smoothPath(mappedPoints).apply {
                lineTo(mappedPoints.last().x, zeroY)
                lineTo(mappedPoints.first().x, zeroY)
                close()
            }
            drawPath(areaPath, lineColor.copy(alpha = AREA_ALPHA))
            drawPath(linePath, lineColor, style = Stroke(width = LINE_STROKE.toPx()))

            val selected = mappedPoints[selectedIndex.coerceIn(mappedPoints.indices)]
            drawCircle(markerOuterColor, radius = MARKER_OUTER_RADIUS.toPx(), center = Offset(selected.x, selected.y))
            drawCircle(lineColor, radius = MARKER_INNER_RADIUS.toPx(), center = Offset(selected.x, selected.y))
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TOOLTIP_ALPHA),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(selectedPoint.label, style = MaterialTheme.typography.labelMedium)
                Text(formatValue(selectedPoint.value), style = MaterialTheme.typography.labelLarge)
            }
        }

        LineAxisLabels(points = points, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LineAxisLabels(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    val indices = when (points.size) {
        1 -> listOf(0)
        2 -> listOf(0, 1)
        else -> listOf(0, points.lastIndex / 2, points.lastIndex)
    }
    Row(
        modifier = modifier.fillMaxWidth().height(AXIS_LABEL_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        indices.forEach { index ->
            Text(
                text = points[index].label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = when {
                    indices.size == 1 -> TextAlign.Center
                    index == indices.first() -> TextAlign.Start
                    index == indices.last() -> TextAlign.End
                    else -> TextAlign.Center
                },
            )
        }
    }
}

private fun smoothPath(points: List<ChartOffset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    points.zipWithNext().forEach { (start, end) ->
        val middleX = (start.x + end.x) / 2f
        cubicTo(middleX, start.y, middleX, end.y, end.x, end.y)
    }
}

private val LINE_CHART_HEIGHT = 200.dp
private val LINE_TOP_PADDING = 48.dp
private val AXIS_LABEL_HEIGHT = 24.dp
private val LINE_STROKE = 2.dp
private val ZERO_STROKE = 1.5.dp
private val GUIDE_STROKE = 1.dp
private val MARKER_OUTER_RADIUS = 6.dp
private val MARKER_INNER_RADIUS = 4.dp
private const val GUIDE_LINE_COUNT = 3
private const val AREA_ALPHA = 0.18f
private const val TOOLTIP_ALPHA = 0.94f
