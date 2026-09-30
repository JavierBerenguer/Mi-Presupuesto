package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.SaleAmountKind
import com.mipatrimonio.app.domain.calc.TradeSizingError
import com.mipatrimonio.app.domain.calc.TradeSizingResult
import com.mipatrimonio.app.domain.calc.calculateTradeSizing
import com.mipatrimonio.app.domain.model.OperationType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeSizingTest {
    @Test fun `compra descuenta la comision del importe total`() {
        val sizing = success(OperationType.COMPRA, "1000", "10", "5")
        assertDecimal("99.50000000", sizing.quantity)
        assertDecimal("995", sizing.tradedAmount)
        assertDecimal("1000.00000000", sizing.reproducedInputAmount)
    }

    @Test fun `compra sin comision`() {
        assertDecimal("4.00000000", success(OperationType.COMPRA, "10", "2.5", "0").quantity)
    }

    @Test fun `precio con seis decimales y redondeo half even`() {
        val sizing = success(OperationType.COMPRA, "1", "0.123456", "0")
        assertDecimal("8.10005184", sizing.quantity)
        assertTrue(sizing.roundingDifference.abs() < BigDecimal("0.00000001"))
    }

    @Test fun `redondea empates con half even a escala configurable`() {
        val sizing = success(OperationType.COMPRA, "1", "8", "0", scale = 2)
        assertDecimal("0.12", sizing.quantity)
        assertDecimal("0.04", sizing.roundingDifference)
    }

    @Test fun `venta neta suma la comision para obtener el bruto`() {
        val sizing = success(OperationType.VENTA, "995", "10", "5", SaleAmountKind.NETO)
        assertDecimal("100.00000000", sizing.quantity)
        assertDecimal("1000", sizing.tradedAmount)
        assertDecimal("995.00000000", sizing.reproducedInputAmount)
    }

    @Test fun `venta bruta no suma la comision`() {
        val sizing = success(OperationType.VENTA, "1000", "10", "5", SaleAmountKind.BRUTO)
        assertDecimal("100.00000000", sizing.quantity)
        assertDecimal("1000.00000000", sizing.reproducedInputAmount)
    }

    @Test fun `importe igual o menor que comision devuelve error tipado`() {
        listOf("5", "4").forEach { amount ->
            val result = calculateTradeSizing(
                OperationType.COMPRA, BigDecimal(amount), BigDecimal.TEN, BigDecimal("5"),
            )
            assertEquals(TradeSizingResult.Error(TradeSizingError.AmountNotGreaterThanFees), result)
        }
    }

    @Test fun `precio cero devuelve error tipado`() {
        val result = calculateTradeSizing(
            OperationType.COMPRA, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
        )
        assertEquals(TradeSizingResult.Error(TradeSizingError.NonPositivePrice), result)
    }

    @Test fun `admite importes enormes sin desbordamiento`() {
        val sizing = success(
            OperationType.COMPRA,
            "999999999999999999999999999999.99",
            "0.000001",
            "0.01",
        )
        assertDecimal("999999999999999999999999999999980000.00000000", sizing.quantity)
    }

    private fun success(
        type: OperationType,
        amount: String,
        price: String,
        fees: String,
        kind: SaleAmountKind = SaleAmountKind.NETO,
        scale: Int = 8,
    ) = (calculateTradeSizing(
        type,
        BigDecimal(amount),
        BigDecimal(price),
        BigDecimal(fees),
        kind,
        scale,
    ) as TradeSizingResult.Success).sizing

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
