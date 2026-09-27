package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.op
import com.mipatrimonio.app.domain.calc.InvalidOperationException
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.OperationType.COMISION
import com.mipatrimonio.app.domain.model.OperationType.COMPRA
import com.mipatrimonio.app.domain.model.OperationType.DIVIDENDO
import com.mipatrimonio.app.domain.model.OperationType.VENTA
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PositionTest {
    private fun assertNum(expected: String, actual: BigDecimal?) {
        assertNotNull("esperado $expected, real null", actual)
        assertEquals("esperado $expected, real $actual", 0L, BigDecimal(expected).compareTo(actual).toLong())
    }

    @Test
    fun `una compra actualiza cantidad y coste con comisiones capitalizadas`() {
        val p = PositionCalculator.compute(listOf(op(COMPRA, "10", "50", fees = 1_00)))
        assertNum("10", p.quantity)
        assertNum("501", p.costBasis)
        assertNum("50.1", p.averagePrice)
    }

    @Test
    fun `dos compras dan precio medio ponderado`() {
        val p = PositionCalculator.compute(listOf(op(COMPRA, "10", "10"), op(COMPRA, "10", "20")))
        assertNum("20", p.quantity)
        assertNum("15", p.averagePrice)
    }

    @Test
    fun `una venta reduce la posicion y realiza plusvalia sobre coste medio`() {
        val p = PositionCalculator.compute(
            listOf(op(COMPRA, "10", "10"), op(COMPRA, "10", "20"), op(VENTA, "5", "30", fees = 50)),
        )
        assertNum("15", p.quantity)
        assertNum("225", p.costBasis) // 300 - 5*15
        assertNum("74.5", p.realizedPnl) // 150 - 0.5 - 75
    }

    @Test
    fun `vender todo cierra la posicion`() {
        val p = PositionCalculator.compute(listOf(op(COMPRA, "3", "10"), op(VENTA, "3", "12")))
        assertNum("0", p.quantity)
        assertNum("0", p.costBasis)
        assertNum("6", p.realizedPnl)
        assertNull(p.averagePrice)
    }

    @Test
    fun `no se puede vender mas de lo que se posee`() {
        assertThrows(InvalidOperationException::class.java) {
            PositionCalculator.compute(listOf(op(COMPRA, "1", "10"), op(VENTA, "2", "10")))
        }
    }

    @Test
    fun `el orden se determina por fecha y no por orden de insercion`() {
        val venta = op(VENTA, "1", "10", date = LocalDate.of(2026, 2, 1))
        val compra = op(COMPRA, "1", "10", date = LocalDate.of(2026, 1, 1))
        assertNum("0", PositionCalculator.compute(listOf(venta, compra)).quantity)
    }

    @Test
    fun `la hora real ordena compras y venta del mismo dia`() {
        val operations = listOf(
            op(COMPRA, "10", "30", createdAt = 1, time = LocalTime.of(11, 0)),
            op(VENTA, "5", "20", createdAt = 2, time = LocalTime.of(10, 0)),
            op(COMPRA, "10", "10", createdAt = 3, time = LocalTime.of(9, 0)),
        )

        val position = PositionCalculator.compute(operations)

        assertNum("350", position.costBasis)
        assertNum("50", position.realizedPnl)
    }

    @Test
    fun `editar la hora cambia el coste medio posterior a una venta`() {
        val firstBuy = op(COMPRA, "10", "10", createdAt = 3, time = LocalTime.of(9, 0))
        val sale = op(VENTA, "5", "20", createdAt = 2, time = LocalTime.of(10, 0))
        val secondBuy = op(COMPRA, "10", "30", createdAt = 1, time = LocalTime.of(11, 0))

        val beforeSale = PositionCalculator.compute(listOf(firstBuy, sale, secondBuy.copy(time = LocalTime.of(9, 30))))
        val afterSale = PositionCalculator.compute(listOf(firstBuy, sale, secondBuy))

        assertNum("300", beforeSale.costBasis)
        assertNum("350", afterSale.costBasis)
    }

    @Test
    fun `fecha prevalece entre 2359 y 0001 del dia siguiente`() {
        val lateBuy = op(
            COMPRA, "1", "10", date = LocalDate.of(2026, 1, 1),
            createdAt = 2, time = LocalTime.of(23, 59),
        )
        val earlySale = op(
            VENTA, "1", "12", date = LocalDate.of(2026, 1, 2),
            createdAt = 1, time = LocalTime.of(0, 1),
        )

        assertNum("2", PositionCalculator.compute(listOf(earlySale, lateBuy)).realizedPnl)
    }

    @Test
    fun `createdAt desempata operaciones con fecha y hora iguales`() {
        val time = LocalTime.of(12, 0)
        val sale = op(VENTA, "1", "12", createdAt = 2, time = time)
        val buy = op(COMPRA, "1", "10", createdAt = 1, time = time)

        assertNum("2", PositionCalculator.compute(listOf(sale, buy)).realizedPnl)
    }

    @Test
    fun `dividendos se acumulan netos una sola vez y no alteran la posicion`() {
        val ops = listOf(op(COMPRA, "10", "10"), op(DIVIDENDO, "1", "5", fees = 95), op(COMISION, "1", "2"))
        val p = PositionCalculator.compute(ops)
        assertNum("4.05", p.dividendsNet)
        assertNum("10", p.quantity)
        assertNum("100", p.costBasis)
        assertNum("2", p.otherFees)
        assertNum("4.05", PositionCalculator.compute(ops).dividendsNet) // recalcular no duplica
    }

    @Test
    fun `valoracion con y sin cotizacion`() {
        val p = PositionCalculator.compute(listOf(op(COMPRA, "10", "10")))
        val v = PositionCalculator.value(p, BigDecimal("12.5"))
        assertNum("125", v.marketValue)
        assertNum("25", v.unrealizedPnl)
        assertNum("25", v.unrealizedReturnPct)
        val sin = PositionCalculator.value(p, null)
        assertNull(sin.marketValue)
        assertNull(sin.unrealizedReturnPct)
    }

    @Test
    fun `rentabilidad con coste cero no divide por cero`() {
        val p = PositionCalculator.compute(listOf(op(COMPRA, "1", "0")))
        assertNull(PositionCalculator.value(p, BigDecimal("5")).unrealizedReturnPct)
    }

    @Test
    fun `operaciones invalidas se rechazan`() {
        assertThrows(InvalidOperationException::class.java) { PositionCalculator.compute(listOf(op(COMPRA, "-1", "10"))) }
        assertThrows(InvalidOperationException::class.java) { PositionCalculator.compute(listOf(op(COMPRA, "1", "-10"))) }
        assertThrows(InvalidOperationException::class.java) { PositionCalculator.compute(listOf(op(COMPRA, "0", "10"))) }
    }

    @Test
    fun `sin operaciones la posicion es vacia`() {
        assertNum("0", PositionCalculator.compute(emptyList()).quantity)
    }
}
