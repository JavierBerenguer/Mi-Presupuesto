package com.mipatrimonio.app.ui.investments

import com.mipatrimonio.app.domain.calc.Position
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvestmentSummariesTest {
    @Test
    fun `calcula valor coste plusvalia y porcentaje conocidos`() {
        val row = row(
            quantity = "10",
            cost = "1000",
            price = "150",
            realized = "25",
            dividends = "10",
            otherFees = "5",
        )

        val result = summarize(listOf(row), EUR).single()

        assertEquals(150_000L, result.valueMinor)
        assertEquals(100_000L, result.costMinor)
        assertEquals(50_000L, result.unrealizedMinor)
        assertEquals(BigDecimal("50.00"), result.unrealizedPct)
        assertEquals(2_000L, result.realizedMinor)
        assertEquals(1_000L, result.dividendsNetMinor)
    }

    @Test
    fun `activo sin cotizacion no aporta valor ni plusvalia y queda listado`() {
        val result = summarize(listOf(row(quantity = "2", cost = "120", price = null)), EUR).single()

        assertEquals(0L, result.valueMinor)
        assertEquals(12_000L, result.costMinor)
        assertEquals(0L, result.unrealizedMinor)
        assertNull(result.unrealizedPct)
        assertEquals(listOf("Activo"), result.unpricedAssets)
    }

    @Test
    fun `divisa distinta se excluye sin convertir`() {
        val result = summarize(
            listOf(row(quantity = "1", cost = "100", price = "125", currency = "USD")),
            EUR,
        ).single()

        assertEquals(0L, result.valueMinor)
        assertEquals(0L, result.costMinor)
        assertEquals(0L, result.unrealizedMinor)
        assertEquals(setOf("USD"), result.excludedCurrencies)
    }

    @Test
    fun `posicion cerrada no tiene coste ni valor pero conserva realizado y dividendos`() {
        val result = summarize(
            listOf(row(quantity = "0", cost = "0", price = "90", realized = "123.45", dividends = "10")),
            EUR,
        ).single()

        assertEquals(0L, result.valueMinor)
        assertEquals(0L, result.costMinor)
        assertEquals(0L, result.unrealizedMinor)
        assertEquals(12_345L, result.realizedMinor)
        assertEquals(1_000L, result.dividendsNetMinor)
    }

    @Test
    fun `coste cero produce porcentaje nulo`() {
        val result = summarize(listOf(row(quantity = "2", cost = "0", price = "12.50")), EUR).single()

        assertEquals(2_500L, result.valueMinor)
        assertEquals(2_500L, result.unrealizedMinor)
        assertNull(result.unrealizedPct)
    }

    @Test
    fun `lista vacia produce resumen vacio`() {
        assertEquals(emptyList<PortfolioSummary>(), summarize(emptyList(), EUR))
    }

    @Test
    fun `antiguedad de hoy es cero`() {
        assertEquals(0L, priceAgeDays(asOfEpochMillis = 1_000L, nowMillis = 80_000_000L))
    }

    @Test
    fun `precio futuro tiene antiguedad cero`() {
        assertEquals(0L, priceAgeDays(asOfEpochMillis = 2_000L, nowMillis = 1_000L))
    }

    private fun row(
        quantity: String,
        cost: String,
        price: String?,
        currency: String = EUR,
        realized: String = "0",
        dividends: String = "0",
        otherFees: String = "0",
    ): PositionRow {
        val asset = Asset("asset", "Activo", "ACT", "", AssetType.ACCION, "", currency)
        val portfolio = Portfolio("portfolio", "Cartera", 1L)
        val position = Position(
            quantity = BigDecimal(quantity),
            costBasis = BigDecimal(cost),
            realizedPnl = BigDecimal(realized),
            dividendsNet = BigDecimal(dividends),
            otherFees = BigDecimal(otherFees),
        )
        val decimalPrice = price?.let(::BigDecimal)
        val assetPrice = decimalPrice?.let { AssetPrice(asset.id, it, currency, 1L, PriceSource.MANUAL) }
        return PositionRow(portfolio, asset, PositionCalculator.value(position, decimalPrice), assetPrice)
    }

    private companion object {
        const val EUR = "EUR"
    }
}
