package com.mipatrimonio.app.ui.common.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    @Test
    fun `linea positiva usa cero como base`() {
        val points = linePoints(listOf(0L, 50L, 100L), width = 200f, height = 100f)

        assertOffset(points[0], x = 0f, y = 100f)
        assertOffset(points[1], x = 100f, y = 50f)
        assertOffset(points[2], x = 200f, y = 0f)
        assertEquals(100f, zeroAxisY(listOf(0L, 50L, 100L), 100f), TOLERANCE)
    }

    @Test
    fun `linea con negativos coloca el eje cero dentro del grafico`() {
        val values = listOf(-100L, 0L, 100L)
        val points = linePoints(values, width = 200f, height = 100f)

        assertOffset(points[0], x = 0f, y = 100f)
        assertOffset(points[1], x = 100f, y = 50f)
        assertOffset(points[2], x = 200f, y = 0f)
        assertEquals(50f, zeroAxisY(values, 100f), TOLERANCE)
    }

    @Test
    fun `linea con valores iguales no divide por cero`() {
        val nonZero = linePoints(listOf(5L, 5L, 5L), width = 100f, height = 80f)
        val zero = linePoints(listOf(0L, 0L), width = 100f, height = 80f)

        nonZero.forEach { assertEquals(0f, it.y, TOLERANCE) }
        zero.forEach { assertEquals(40f, it.y, TOLERANCE) }
        assertEquals(40f, zeroAxisY(listOf(0L, 0L), 80f), TOLERANCE)
    }

    @Test
    fun `linea de un punto lo centra horizontalmente`() {
        val point = linePoints(listOf(25L), width = 120f, height = 80f).single()

        assertOffset(point, x = 60f, y = 0f)
    }

    @Test
    fun `indice mas cercano respeta extremos y caso unitario`() {
        assertEquals(0, nearestIndex(-20f, count = 5, width = 100f))
        assertEquals(2, nearestIndex(50f, count = 5, width = 100f))
        assertEquals(4, nearestIndex(120f, count = 5, width = 100f))
        assertEquals(0, nearestIndex(999f, count = 1, width = 0f))
        assertEquals(-1, nearestIndex(10f, count = 0, width = 100f))
    }

    @Test
    fun `sectores suman una vuelta completa`() {
        val sweeps = donutSweeps(listOf(1L, 2L, 3L))

        assertEquals(3, sweeps.size)
        assertEquals(360f, sweeps.sum(), TOLERANCE)
        assertEquals(60f, sweeps[0], TOLERANCE)
        assertEquals(120f, sweeps[1], TOLERANCE)
        assertEquals(180f, sweeps[2], TOLERANCE)
    }

    @Test
    fun `sectores ignoran valores no positivos`() {
        val sweeps = donutSweeps(listOf(-4L, 0L, 7L))

        assertEquals(listOf(360f), sweeps)
        assertTrue(donutSweeps(listOf(-1L, 0L)).isEmpty())
        assertTrue(donutSweeps(emptyList()).isEmpty())
    }

    @Test
    fun `porcentajes reparten el redondeo y suman cien`() {
        val result = percentages(listOf(1L, 1L, 1L))

        assertEquals(listOf(34, 33, 33), result)
        assertEquals(100, result.sum())
    }

    @Test
    fun `porcentajes evitan division por cero e ignoran no positivos`() {
        assertEquals(listOf(25, 75), percentages(listOf(-5L, 0L, 1L, 3L)))
        assertTrue(percentages(listOf(0L, 0L)).isEmpty())
        assertTrue(percentages(emptyList()).isEmpty())
    }

    @Test
    fun `calculos proporcionales soportan extremos de long`() {
        val points = linePoints(listOf(Long.MIN_VALUE, Long.MAX_VALUE), width = 100f, height = 100f)
        val sweeps = donutSweeps(listOf(Long.MAX_VALUE, Long.MAX_VALUE))

        points.forEach { point ->
            assertTrue(point.x.isFinite())
            assertTrue(point.y.isFinite())
        }
        assertEquals(360f, sweeps.sum(), TOLERANCE)
        assertEquals(listOf(50, 50), percentages(listOf(Long.MAX_VALUE, Long.MAX_VALUE)))
    }

    private fun assertOffset(point: ChartOffset, x: Float, y: Float) {
        assertEquals(x, point.x, TOLERANCE)
        assertEquals(y, point.y, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
