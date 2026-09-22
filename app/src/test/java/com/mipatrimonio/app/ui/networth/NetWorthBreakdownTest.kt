package com.mipatrimonio.app.ui.networth

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetWorthBreakdownTest {
    private val portfolio = Portfolio("p1", "Principal", 1)
    private val asset = Asset("as1", "ETF", "X", "", AssetType.ETF, "", "EUR")

    private fun row(price: String?, qty: String = "10", asset: Asset = this.asset): PositionRow {
        val op = com.mipatrimonio.app.domain.TestData.op(OperationType.COMPRA, qty, "10", currency = asset.currency)
        val position = PositionCalculator.compute(listOf(op))
        return PositionRow(portfolio, asset, PositionCalculator.value(position, price?.let(::BigDecimal)), null)
    }

    @Test
    fun `agrupa por divisa sin convertir con la base primero`() {
        val balances = listOf(
            AccountBalance(account("a1"), 100_00),
            AccountBalance(account("a2", currency = "USD"), 50_00),
            AccountBalance(account("a3", archived = true), 999_00),
        )
        val totals = byCurrency(balances, listOf(row("12")), "EUR")
        assertEquals(listOf("EUR", "USD"), totals.map { it.currency })
        assertEquals(100_00L + 120_00L, totals[0].minor) // efectivo + posición 10×12 €
        assertEquals(50_00L, totals[1].minor)
    }

    @Test
    fun `porcentajes por cuenta con negativos y sin dividir por cero`() {
        val balances = listOf(
            AccountBalance(account("a1"), 300_00),
            AccountBalance(account("a2"), 100_00),
            AccountBalance(account("a3"), -50_00),
            AccountBalance(account("a4", currency = "USD"), 10_00),
        )
        val shares = accountShares(balances, "EUR").associateBy { it.account.id }
        assertEquals(75, shares["a1"]?.percent)
        assertEquals(25, shares["a2"]?.percent)
        assertEquals(0, shares["a3"]?.percent)
        assertNull(shares["a4"]?.percent)
    }

    @Test
    fun `sin saldo positivo no hay porcentajes`() {
        val shares = accountShares(listOf(AccountBalance(account("a1"), 0), AccountBalance(account("a2"), -5_00)), "EUR")
        assertEquals(listOf<Int?>(null, null), shares.map { it.percent })
    }

    @Test
    fun `valor por cartera con posicion sin cotizacion`() {
        val other = Asset("as2", "Bono", "B", "", AssetType.FONDO_INVERSION, "", "EUR")
        val values = portfolioValues(listOf(row("20"), row(null, asset = other)), "EUR")
        assertEquals(1, values.size)
        assertEquals(200_00L, values[0].valueMinor)
        assertEquals(1, values[0].unpricedCount)
        assertEquals(100, values[0].percent)
    }

    @Test
    fun `sin posiciones no hay carteras`() {
        assertEquals(0, portfolioValues(emptyList(), "EUR").size)
    }
}
