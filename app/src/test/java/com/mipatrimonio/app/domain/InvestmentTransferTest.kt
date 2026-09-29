package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.op
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.InvalidOperationException
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.OperationType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentTransferTest {
    @Test fun `salida reduce cantidad y coste sin plusvalia y entrada conserva coste`() {
        val source = PositionCalculator.compute(
            listOf(
                op(OperationType.COMPRA, "10", "20"),
                op(OperationType.TRASPASO_SALIDA, "4", "0"),
            ),
        )
        val destination = PositionCalculator.compute(
            listOf(op(OperationType.TRASPASO_ENTRADA, "4", "20")),
        )

        assertDecimal("6", source.quantity)
        assertDecimal("120", source.costBasis)
        assertDecimal("0", source.realizedPnl)
        assertDecimal("80", destination.costBasis)
        assertDecimal("200", source.costBasis.add(destination.costBasis))
    }

    @Test fun `comision de red encarece el coste medio de destino`() {
        val destination = PositionCalculator.compute(
            listOf(op(OperationType.TRASPASO_ENTRADA, "3.9", "20.51282051282051282051282051282051")),
        )
        assertTrue(BigDecimal("80").subtract(destination.costBasis).abs() < BigDecimal("1E-20"))
        assertEquals(0, BigDecimal("20.51282051282051282051282051282051").compareTo(destination.averagePrice))
    }

    @Test fun `traspaso total cierra origen sin realizar plusvalia`() {
        val source = PositionCalculator.compute(
            listOf(op(OperationType.COMPRA, "2", "25"), op(OperationType.TRASPASO_SALIDA, "2", "0")),
        )
        assertDecimal("0", source.quantity)
        assertDecimal("0", source.costBasis)
        assertDecimal("0", source.realizedPnl)
    }

    @Test fun `no permite sacar mas de lo disponible e informa la cantidad`() {
        val error = assertThrows(InvalidOperationException::class.java) {
            PositionCalculator.compute(
                listOf(op(OperationType.COMPRA, "2.5", "10"), op(OperationType.TRASPASO_SALIDA, "3", "0")),
            )
        }
        assert(error.message.orEmpty().contains("2,5") || error.message.orEmpty().contains("2.5"))
    }

    @Test fun `traspasos no tienen efecto de caja`() {
        assertEquals(0L, BalanceCalculator.investmentEffectMinor(op(OperationType.TRASPASO_SALIDA, "2", "10")))
        assertEquals(0L, BalanceCalculator.investmentEffectMinor(op(OperationType.TRASPASO_ENTRADA, "2", "10")))
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))
}
