package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.category
import com.mipatrimonio.app.domain.TestData.transfer
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.BudgetLevel
import com.mipatrimonio.app.domain.calc.InvestmentValue
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private val a1 = account("a1", initial = 100_00)
    private val a2 = account("a2", initial = 50_00)

    @Test
    fun `un ingreso aumenta el saldo y un gasto lo disminuye`() {
        val txs = listOf(tx(INGRESO, 200_00), tx(GASTO, 30_00))
        assertEquals(270_00L, BalanceCalculator.balance(a1, txs, emptyList()))
    }

    @Test
    fun `base de datos vacia el saldo es el inicial`() {
        assertEquals(100_00L, BalanceCalculator.balance(a1, emptyList(), emptyList()))
    }

    @Test
    fun `movimientos de otra cuenta no afectan al saldo`() {
        assertEquals(100_00L, BalanceCalculator.balance(a1, listOf(tx(GASTO, 10_00, account = "a2")), emptyList()))
    }

    @Test
    fun `transferencia conserva el patrimonio global y no cuenta como gasto`() {
        val transfers = listOf(transfer("a1", "a2", 40_00))
        val b1 = BalanceCalculator.balance(a1, emptyList(), transfers)
        val b2 = BalanceCalculator.balance(a2, emptyList(), transfers)
        assertEquals(60_00L, b1)
        assertEquals(90_00L, b2)
        assertEquals(150_00L, b1 + b2)
        val totals = StatsCalculator.totals(emptyList(), "EUR", LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31))
        assertEquals(0L, totals.expenseMinor)
        assertEquals(0L, totals.incomeMinor)
    }

    @Test
    fun `transferencia entre divisas conserva ambos importes`() {
        val usd = account("a3", initial = 0, currency = "USD")
        val tr = transfer("a1", "a3", 100_00, 109_00)
        assertNull(BalanceCalculator.validateTransfer(a1, usd, 100_00, 109_00))
        assertEquals(0L, BalanceCalculator.balance(a1, emptyList(), listOf(tr)))
        assertEquals(109_00L, BalanceCalculator.balance(usd, emptyList(), listOf(tr)))
    }

    @Test
    fun `validacion de transferencias`() {
        assertNotNull(BalanceCalculator.validateTransfer(a1, a1, 1, 1))
        assertNotNull(BalanceCalculator.validateTransfer(a1, a2, 0, 0))
        assertNotNull(BalanceCalculator.validateTransfer(a1, a2, -5, -5))
        assertNotNull(BalanceCalculator.validateTransfer(a1, a2, 10, 11))
        assertNull(BalanceCalculator.validateTransfer(a1, a2, 10, 10))
    }

    @Test
    fun `presupuesto calcula consumo solo con gastos de su periodo y categoria`() {
        val cats = listOf(category("comida"), category("super", parentId = "comida"), category("ocio"))
        val budget = Budget("b1", "comida", BudgetPeriod.MENSUAL, 200_00, "EUR", false)
        val txs = listOf(
            tx(GASTO, 50_00, category = "comida"),
            tx(GASTO, 70_00, category = "super"), // subcategoría cuenta
            tx(GASTO, 999_00, category = "ocio"), // otra categoría
            tx(INGRESO, 500_00, category = "comida"), // ingreso no cuenta
            tx(GASTO, 999_00, category = "comida", date = LocalDate.of(2026, 2, 28)), // otro mes
        )
        val s = BudgetCalculator.status(budget, txs, cats, LocalDate.of(2026, 3, 15))
        assertEquals(120_00L, s.spentMinor)
        assertEquals(80_00L, s.remainingMinor)
        assertEquals(BudgetLevel.NORMAL, s.level)
    }

    @Test
    fun `presupuesto avisa al 80 por ciento y marca superado`() {
        val cats = emptyList<com.mipatrimonio.app.domain.model.Category>()
        val budget = Budget("b1", null, BudgetPeriod.MENSUAL, 100_00, "EUR", false)
        val ref = LocalDate.of(2026, 3, 15)
        assertEquals(BudgetLevel.AVISO, BudgetCalculator.status(budget, listOf(tx(GASTO, 80_00)), cats, ref).level)
        assertEquals(BudgetLevel.AVISO, BudgetCalculator.status(budget, listOf(tx(GASTO, 100_00)), cats, ref).level)
        val over = BudgetCalculator.status(budget, listOf(tx(GASTO, 100_01)), cats, ref)
        assertEquals(BudgetLevel.SUPERADO, over.level)
        assertEquals(-1L, over.remainingMinor)
    }

    @Test
    fun `presupuesto con limite cero no divide por cero`() {
        val budget = Budget("b1", null, BudgetPeriod.ANUAL, 0, "EUR", false)
        val s = BudgetCalculator.status(budget, listOf(tx(GASTO, 5_00)), emptyList(), LocalDate.of(2026, 3, 15))
        assertEquals(0.0, s.consumedRatio, 0.0)
        assertEquals(BudgetLevel.SUPERADO, s.level)
    }

    @Test
    fun `presupuesto excluye gastos en otra divisa y lo informa`() {
        val budget = Budget("b1", null, BudgetPeriod.MENSUAL, 100_00, "EUR", false)
        val s = BudgetCalculator.status(
            budget, listOf(tx(GASTO, 10_00), tx(GASTO, 500_00, currency = "USD")), emptyList(), LocalDate.of(2026, 3, 15),
        )
        assertEquals(10_00L, s.spentMinor)
        assertEquals(1, s.excludedCount)
    }

    @Test
    fun `presupuesto anual abarca todo el año`() {
        val budget = Budget("b1", null, BudgetPeriod.ANUAL, 1000_00, "EUR", false)
        val txs = listOf(tx(GASTO, 10_00, date = LocalDate.of(2026, 1, 1)), tx(GASTO, 20_00, date = LocalDate.of(2026, 12, 31)))
        assertEquals(30_00L, BudgetCalculator.status(budget, txs, emptyList(), LocalDate.of(2026, 6, 1)).spentMinor)
    }

    @Test
    fun `estadisticas mensuales e ingresos frente a gastos`() {
        val txs = listOf(
            tx(INGRESO, 1000_00, date = LocalDate.of(2026, 3, 1)),
            tx(GASTO, 300_00, date = LocalDate.of(2026, 3, 5)),
            tx(GASTO, 100_00, date = LocalDate.of(2026, 2, 5)),
        )
        val series = StatsCalculator.monthlySeries(txs, "EUR", YearMonth.of(2026, 3), 3)
        assertEquals(3, series.size)
        assertEquals(YearMonth.of(2026, 1), series[0].month)
        assertEquals(100_00L, series[1].expenseMinor)
        assertEquals(1000_00L, series[2].incomeMinor)
    }

    @Test
    fun `gasto por categoria agrupa subcategorias en su padre`() {
        val cats = listOf(category("comida"), category("super", parentId = "comida"), category("ocio"))
        val txs = listOf(tx(GASTO, 10_00, category = "comida"), tx(GASTO, 20_00, category = "super"), tx(GASTO, 5_00, category = "ocio"), tx(GASTO, 1_00))
        val r = StatsCalculator.expenseByCategory(txs, cats, "EUR", LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31))
        assertEquals("comida", r[0].categoryId)
        assertEquals(30_00L, r[0].amountMinor)
        assertEquals(3, r.size) // comida, ocio, sin categoría
    }

    @Test
    fun `patrimonio suma efectivo e inversiones sin doble contabilizar y marca parcial`() {
        val balances = listOf(
            AccountBalance(account("a1", type = AccountType.CORRIENTE), 1000_00),
            AccountBalance(account("a2", type = AccountType.INVERSION), 200_00), // efectivo del broker
            AccountBalance(account("a3", currency = "USD"), 999_00),
            AccountBalance(account("a4", archived = true), 777_00),
        )
        val nw = NetWorthCalculator.compute(
            "EUR", balances,
            listOf(InvestmentValue("ETF", "EUR", 3000_00), InvestmentValue("Sin precio", "EUR", null)),
        )
        assertEquals(1200_00L, nw.cashMinor)
        assertEquals(3000_00L, nw.investmentsMinor)
        assertEquals(4200_00L, nw.totalMinor)
        assertEquals(setOf("USD"), nw.excludedCurrencies)
        assertEquals(listOf("Sin precio"), nw.unpricedAssets)
        assertTrue(nw.isPartial)
        assertTrue(nw.hasExclusions)
    }

    @Test
    fun `patrimonio vacio es cero y sin exclusiones`() {
        val nw = NetWorthCalculator.compute("EUR", emptyList(), emptyList())
        assertEquals(0L, nw.totalMinor)
        assertFalse(nw.hasExclusions)
    }
}
