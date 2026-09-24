package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.op
import com.mipatrimonio.app.domain.TestData.transfer
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.Period
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {
    private val asset = Asset("asset1", "ETF Mundo", "IWDA", "IE00B4L5Y983", AssetType.ETF, "XAMS", "EUR")
    private val portfolio = Portfolio("p1", "Principal", 1)

    @Test
    fun `instantanea combina saldos, posiciones valoradas y patrimonio`() {
        val ops = listOf(op(OperationType.COMPRA, "10", "100"))
        val price = AssetPrice("asset1", BigDecimal("120"), "EUR", 0, PriceSource.MANUAL)
        val s = SnapshotBuilder.build(
            "EUR", listOf(account("a1", initial = 500_00)), listOf(tx(INGRESO, 100_00)), emptyList(),
            listOf(portfolio), listOf(asset), ops, mapOf("asset1" to price),
        )
        assertEquals(600_00L, s.netWorth.cashMinor)
        assertEquals(1200_00L, s.netWorth.investmentsMinor)
        assertEquals(1800_00L, s.netWorth.totalMinor)
        assertEquals(1, s.openPositions.size)
        assertEquals(0, BigDecimal("200").compareTo(s.openPositions[0].valuation.unrealizedPnl))
    }

    @Test
    fun `transferencia al broker y compra reducen patrimonio solo por comisiones`() {
        val bank = account("bank", initial = 1_000_00)
        val broker = account("broker", type = com.mipatrimonio.app.domain.model.AccountType.INVERSION)
        val transfer = transfer("bank", "broker", 500_00)
        val purchase = op(OperationType.COMPRA, "4", "100", fees = 10_00, accountId = "broker")
        val price = AssetPrice("asset1", BigDecimal("100"), "EUR", 0, PriceSource.MANUAL)

        val snapshot = SnapshotBuilder.build(
            "EUR", listOf(bank, broker), emptyList(), listOf(transfer), listOf(portfolio), listOf(asset),
            listOf(purchase), mapOf("asset1" to price),
        )

        assertEquals(500_00L, snapshot.balances.first { it.account.id == "bank" }.balanceMinor)
        assertEquals(90_00L, snapshot.balances.first { it.account.id == "broker" }.balanceMinor)
        assertEquals(400_00L, snapshot.netWorth.investmentsMinor)
        assertEquals(990_00L, snapshot.netWorth.totalMinor)
    }

    @Test
    fun `activo sin cotizacion se informa y no se valora`() {
        val s = SnapshotBuilder.build(
            "EUR", emptyList(), emptyList(), emptyList(), listOf(portfolio), listOf(asset),
            listOf(op(OperationType.COMPRA, "1", "10")), emptyMap(),
        )
        assertEquals(0L, s.netWorth.investmentsMinor)
        assertEquals(listOf("ETF Mundo"), s.netWorth.unpricedAssets)
        assertNull(s.openPositions[0].valueMinor)
    }

    @Test
    fun `precio en otra divisa no se aplica al activo`() {
        val price = AssetPrice("asset1", BigDecimal("120"), "USD", 0, PriceSource.MANUAL)
        val s = SnapshotBuilder.build(
            "EUR", emptyList(), emptyList(), emptyList(), listOf(portfolio), listOf(asset),
            listOf(op(OperationType.COMPRA, "1", "10")), mapOf("asset1" to price),
        )
        assertNull(s.openPositions[0].price)
    }

    @Test
    fun `historico usa saldo inicial, movimientos por fecha e inversion a coste`() {
        val accounts = listOf(account("a1", initial = 100_00), account("a2", initial = 0))
        val txs = listOf(
            tx(INGRESO, 50_00, date = LocalDate.of(2026, 1, 15)),
            tx(GASTO, 20_00, date = LocalDate.of(2026, 2, 10)),
        )
        val trs = listOf(transfer("a1", "a2", 10_00)) // 2026-03-11, interna: no cambia el total
        val ops = listOf(op(OperationType.COMPRA, "2", "100", date = LocalDate.of(2026, 2, 1)))
        val pts = HistoryCalculator.netWorthSeries(
            "EUR", accounts, txs, trs, listOf(asset), ops,
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
        )
        assertEquals(150_00L, pts[0].totalMinor)
        assertEquals(150_00L - 20_00L + 200_00L, pts[1].totalMinor)
        assertEquals(pts[1].totalMinor, pts[2].totalMinor)
    }

    @Test
    fun `periodos calculan inicio y meses`() {
        val today = LocalDate.of(2026, 9, 22)
        assertEquals(LocalDate.of(2026, 9, 1), Period.MES.start(today, null))
        assertEquals(LocalDate.of(2026, 7, 1), Period.TRES_MESES.start(today, null))
        assertEquals(LocalDate.of(2025, 10, 1), Period.ANIO.start(today, null))
        assertEquals(LocalDate.of(2026, 5, 3), Period.TODO.start(today, LocalDate.of(2026, 5, 3)))
        assertEquals(5, Period.TODO.barMonths(today, LocalDate.of(2026, 5, 3)))
        assertEquals(1, Period.TODO.barMonths(today, null))
    }

    @Test
    fun `fechas de muestreo terminan hoy`() {
        val today = LocalDate.of(2026, 9, 22)
        val month = HistoryCalculator.sampleDates(Period.MES, today, null)
        assertEquals(22, month.size)
        val year = HistoryCalculator.sampleDates(Period.ANIO, today, null)
        assertEquals(12, year.size)
        assertEquals(today, year.last())
        assertTrue(year[10] == LocalDate.of(2026, 8, 31))
    }
}
