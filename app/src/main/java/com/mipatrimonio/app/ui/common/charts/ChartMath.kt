package com.mipatrimonio.app.ui.common.charts

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.roundToInt

internal data class ChartOffset(val x: Float, val y: Float)

internal fun linePoints(values: List<Long>, width: Float, height: Float): List<ChartOffset> {
    if (values.isEmpty()) return emptyList()

    val safeWidth = width.finiteNonNegative()
    val safeHeight = height.finiteNonNegative()
    val range = lineRange(values)
    return values.mapIndexed { index, value ->
        val x = if (values.size == 1) {
            safeWidth / 2f
        } else {
            safeWidth * index / (values.size - 1)
        }
        val y = if (range.span == BigInteger.ZERO) {
            safeHeight / 2f
        } else {
            safeHeight * (1f - ratio(BigInteger.valueOf(value).subtract(range.minimum), range.span))
        }
        ChartOffset(x = x, y = y.coerceIn(0f, safeHeight))
    }
}

internal fun zeroAxisY(values: List<Long>, height: Float): Float {
    val safeHeight = height.finiteNonNegative()
    if (values.isEmpty()) return safeHeight

    val range = lineRange(values)
    if (range.span == BigInteger.ZERO) return safeHeight / 2f
    val zeroRatio = ratio(range.minimum.negate(), range.span)
    return (safeHeight * (1f - zeroRatio)).coerceIn(0f, safeHeight)
}

internal fun nearestIndex(x: Float, count: Int, width: Float): Int {
    if (count <= 0) return -1
    if (count == 1 || !width.isFinite() || width <= 0f) return 0
    val safeX = if (x.isFinite()) x.coerceIn(0f, width) else 0f
    return ((safeX / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

internal fun donutSweeps(values: List<Long>): List<Float> {
    val positiveValues = values.filter { it > 0L }.map(BigInteger::valueOf)
    if (positiveValues.isEmpty()) return emptyList()

    val total = positiveValues.fold(BigInteger.ZERO, BigInteger::add)
    var assigned = 0f
    return positiveValues.mapIndexed { index, value ->
        if (index == positiveValues.lastIndex) {
            (FULL_CIRCLE_DEGREES - assigned).coerceAtLeast(0f)
        } else {
            (FULL_CIRCLE_DEGREES * ratio(value, total)).coerceAtLeast(0f).also { assigned += it }
        }
    }
}

internal fun percentages(values: List<Long>): List<Int> {
    val positiveValues = values.filter { it > 0L }.map(BigInteger::valueOf)
    if (positiveValues.isEmpty()) return emptyList()

    val total = positiveValues.fold(BigInteger.ZERO, BigInteger::add)
    val divisions = positiveValues.map { it.multiply(ONE_HUNDRED).divideAndRemainder(total) }
    val result = divisions.map { it[0].toInt() }.toMutableList()
    var remaining = 100 - result.sum()
    val remainderOrder = divisions.indices.sortedWith(
        compareByDescending<Int> { divisions[it][1] }.thenBy { it },
    )
    for (index in remainderOrder) {
        if (remaining == 0) break
        result[index] += 1
        remaining -= 1
    }
    return result
}

internal fun magnitudeRatio(value: Long, maximumMagnitude: BigInteger): Float {
    if (maximumMagnitude <= BigInteger.ZERO) return 0f
    return ratio(BigInteger.valueOf(value).abs(), maximumMagnitude).coerceIn(0f, 1f)
}

internal fun maximumMagnitude(values: List<Long>): BigInteger =
    values.maxOfOrNull { BigInteger.valueOf(it).abs() } ?: BigInteger.ZERO

private data class LineRange(val minimum: BigInteger, val maximum: BigInteger) {
    val span: BigInteger = maximum.subtract(minimum)
}

private fun lineRange(values: List<Long>): LineRange {
    val minimumValue = values.minOrNull() ?: 0L
    val maximumValue = values.maxOrNull() ?: 0L
    return LineRange(
        minimum = BigInteger.valueOf(minOf(minimumValue, 0L)),
        maximum = BigInteger.valueOf(maxOf(maximumValue, 0L)),
    )
}

private fun ratio(numerator: BigInteger, denominator: BigInteger): Float {
    if (denominator == BigInteger.ZERO) return 0f
    return BigDecimal(numerator)
        .divide(BigDecimal(denominator), RATIO_SCALE, RoundingMode.HALF_EVEN)
        .toFloat()
}

private fun Float.finiteNonNegative(): Float = if (isFinite() && this > 0f) this else 0f

private const val FULL_CIRCLE_DEGREES = 360f
private const val RATIO_SCALE = 12
private val ONE_HUNDRED = BigInteger.valueOf(100L)
