package com.mipatrimonio.app.ui.investments

import com.mipatrimonio.app.domain.calc.Position
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBreakdownsTest {
    @Test
    fun `dividendo sin cuenta aparece con bruto retencion y neto`() {
        val operation = InvestmentOperation(
            id = "import:tr:div:div",
            portfolioId = "portfolio",
            assetId = "asset",
            type = OperationType.DIVIDENDO,
            date = LocalDate.of(2026, 9, 29),
            quantity = BigDecimal.ONE,
            unitPrice = BigDecimal("20.00"),
            feesMinor = 400L,
            currency = "EUR",
            note = "Dividendo",
            createdAt = 1L,
            accountId = null,
        )

        val item = dividendItems(listOf(operation), mapOf("asset" to "Empresa"), "EUR").single()

        assertEquals("Empresa", item.assetName)
        assertEquals(2_000L, item.grossMinor)
        assertEquals(400L, item.withholdingMinor)
        assertEquals(1_600L, item.netMinor)
    }

    @Test
    fun `resultado total conserva formula y no resta dos veces comisiones capitalizadas`() {
        val position = Position(
            quantity = BigDecimal.ONE,
            costBasis = BigDecimal("101"),
            realizedPnl = BigDecimal("20"),
            dividendsNet = BigDecimal("5"),
            otherFees = BigDecimal("2"),
            realizedCostBasis = BigDecimal("40"),
            capitalizedFees = BigDecimal("3"),
        )
        val row = PositionRow(
            Portfolio("portfolio", "Cartera", 1L),
            Asset("asset", "Activo", "ACT", "", AssetType.ACCION, "", "EUR"),
            PositionCalculator.value(position, BigDecimal("111")),
            null,
        )

        assertEquals(3_300L, totalReturnMinor(listOf(row), "EUR"))
    }

    @Test
    fun `prestamo p2p aparece como categoria propia en la distribucion por tipo`() {
        val position = Position(
            quantity = BigDecimal.TEN,
            costBasis = BigDecimal("1000"),
            realizedPnl = BigDecimal.ZERO,
            dividendsNet = BigDecimal.ZERO,
            otherFees = BigDecimal.ZERO,
            realizedCostBasis = BigDecimal.ZERO,
            capitalizedFees = BigDecimal.ZERO,
        )
        val row = PositionRow(
            Portfolio("portfolio", "Cartera", 1L),
            Asset("p2p", "Préstamo", "P2P", "", AssetType.PRESTAMO_P2P, "", "EUR"),
            PositionCalculator.value(position, BigDecimal("110")),
            null,
        )

        assertEquals(
            listOf(AllocationItem(AssetType.PRESTAMO_P2P.name, 110_000L)),
            allocationByType(listOf(row), "EUR"),
        )
    }
}
