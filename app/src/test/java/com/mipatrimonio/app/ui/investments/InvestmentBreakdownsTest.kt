package com.mipatrimonio.app.ui.investments

import com.mipatrimonio.app.domain.calc.Position
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBreakdownsTest {
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
