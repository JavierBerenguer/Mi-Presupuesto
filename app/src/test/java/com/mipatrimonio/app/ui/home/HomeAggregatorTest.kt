package com.mipatrimonio.app.ui.home

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.category
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.TestData.op
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.Period
import java.time.LocalDate
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAggregatorTest {
    private val today = LocalDate.of(2026, 3, 20)
    private val cats = listOf(category("comida"), category("super", parentId = "comida"), category("ocio"))

    private fun state(
        txs: List<com.mipatrimonio.app.domain.model.Transaction> = emptyList(),
        budgets: List<Budget> = emptyList(),
        accounts: List<com.mipatrimonio.app.domain.model.Account> = listOf(account("a1", initial = 100_00)),
        period: Period = Period.MES,
    ) = buildHomeState("EUR", accounts, txs, emptyList(), cats, budgets, emptyList(), emptyList(), emptyList(), emptyMap(), today, period)

    private fun budget(id: String, category: String?, limit: Long, period: BudgetPeriod = BudgetPeriod.MENSUAL, currency: String = "EUR") =
        Budget(id, category, period, limit, currency, false)

    @Test
    fun `sin cuentas es primer uso`() {
        assertFalse(state(accounts = emptyList()).hasAccounts)
    }

    @Test
    fun `totales del mes actual ignoran otros meses y movimientos futuros`() {
        val s = state(
            listOf(
                tx(INGRESO, 1000_00, date = LocalDate.of(2026, 3, 1)),
                tx(GASTO, 300_00, date = LocalDate.of(2026, 3, 10)),
                tx(GASTO, 999_00, date = LocalDate.of(2026, 2, 28)),
                tx(GASTO, 50_00, date = LocalDate.of(2026, 3, 31)), // futuro pero dentro del mes calendario
            ),
        )
        assertEquals(1000_00L, s.monthTotals.incomeMinor)
        assertEquals(350_00L, s.monthTotals.expenseMinor)
        assertEquals(650_00L, s.monthTotals.balanceMinor)
        assertEquals(100_00L + 1000_00 - 999_00 - 300_00 - 50_00, s.netWorth.cashMinor)
    }

    @Test
    fun `presupuesto restante prevalece el global sobre las categorias`() {
        val txs = listOf(tx(GASTO, 40_00, category = "comida"), tx(GASTO, 30_00, category = "ocio"))
        val s = state(txs, listOf(budget("g", null, 500_00), budget("c", "comida", 100_00)))
        assertEquals(430_00L, s.budgetRemaining?.remainingMinor)
    }

    @Test
    fun `sin global suma los restantes de las categorias`() {
        val txs = listOf(tx(GASTO, 40_00, category = "comida"), tx(GASTO, 30_00, category = "ocio"))
        val s = state(txs, listOf(budget("c1", "comida", 100_00), budget("c2", "ocio", 50_00)))
        assertEquals(60_00L + 20_00L, s.budgetRemaining?.remainingMinor)
    }

    @Test
    fun `sin presupuestos mensuales el restante es nulo y se ignoran anuales y otra divisa`() {
        assertNull(state().budgetRemaining)
        val s = state(listOf(tx(GASTO, 10_00)), listOf(budget("a", null, 100_00, BudgetPeriod.ANUAL), budget("u", null, 100_00, currency = "USD")))
        assertNull(s.budgetRemaining)
    }

    @Test
    fun `distribucion de gastos agrupa subcategorias y deja sin categoria`() {
        val s = state(
            listOf(
                tx(GASTO, 10_00, category = "comida"),
                tx(GASTO, 20_00, category = "super"),
                tx(GASTO, 5_00, category = "ocio"),
                tx(GASTO, 1_00),
                tx(INGRESO, 900_00, category = "comida"),
            ),
        )
        val byId = s.expenseByCategory.associate { it.categoryId to it.amountMinor }
        assertEquals(30_00L, byId["comida"])
        assertEquals(5_00L, byId["ocio"])
        assertEquals(1_00L, byId[null])
    }

    @Test
    fun `longitud de la serie de barras segun periodo`() {
        val first = tx(GASTO, 1_00, date = LocalDate.of(2025, 12, 5))
        assertEquals(1, state(listOf(first), period = Period.MES).monthlySeries.size)
        assertEquals(3, state(listOf(first), period = Period.TRES_MESES).monthlySeries.size)
        assertEquals(12, state(listOf(first), period = Period.ANIO).monthlySeries.size)
        assertEquals(4, state(listOf(first), period = Period.TODO).monthlySeries.size) // dic, ene, feb, mar
    }

    @Test
    fun `solo cuentas en otra divisa quedan excluidas y avisadas`() {
        val s = state(accounts = listOf(account("a1", initial = 500_00, currency = "USD")))
        assertEquals(0L, s.netWorth.totalMinor)
        assertEquals(setOf("USD"), s.netWorth.excludedCurrencies)
        assertTrue(s.hasAccounts)
    }

    @Test
    fun `filtros de patrimonio seleccionan cuentas inversiones o ambos`() {
        val asset = Asset("asset1", "Fondo", "F", "", AssetType.FONDO_INDEXADO, "", "EUR")
        val operation = op(OperationType.COMPRA, "2", "50", date = today)
        fun filtered(accounts: Boolean, investments: Boolean) = buildHomeState(
            "EUR", listOf(account(initial = 40_00)), emptyList(), emptyList(), cats, emptyList(),
            listOf(Portfolio("p1", "Principal", 0)), listOf(asset), listOf(operation),
            mapOf("asset1" to AssetPrice("asset1", BigDecimal("50"), "EUR", 0, PriceSource.MANUAL)),
            today, Period.MES, accounts, investments,
        )

        assertEquals(40_00L, filtered(true, false).displayedNetWorthMinor)
        assertEquals(100_00L, filtered(false, true).displayedNetWorthMinor)
        assertEquals(140_00L, filtered(true, true).displayedNetWorthMinor)
        assertTrue(filtered(false, true).netWorthSeries.all { it.totalMinor >= 0L })
        val investmentsOnly = buildHomeState(
            "EUR", emptyList(), emptyList(), emptyList(), cats, emptyList(),
            listOf(Portfolio("p1", "Principal", 0)), listOf(asset), listOf(operation), emptyMap(),
            today, Period.MES,
        )
        assertFalse(investmentsOnly.hasAccounts)
        assertTrue(investmentsOnly.hasFinancialData)
    }

    @Test
    fun `cuenta automaticos de siete dias y calcula porcentaje gastado`() {
        val income = tx(INGRESO, 1_000_00, date = today)
        val expense = tx(GASTO, 255_00, date = today.minusDays(2)).copy(source = TransactionSource.NOTIFICACION)
        val old = tx(GASTO, 1_00, date = today.minusDays(7)).copy(source = TransactionSource.NOTIFICACION)
        val future = tx(GASTO, 1_00, date = today.plusDays(1)).copy(source = TransactionSource.NOTIFICACION)
        val s = state(listOf(income, expense, old, future))

        assertEquals(1, s.automaticTransactionsCount)
        assertEquals(26, s.spentIncomePercent)
    }

    @Test
    fun `sin ingresos no muestra porcentaje gastado`() {
        assertNull(state(listOf(tx(GASTO, 10_00, date = today))).spentIncomePercent)
    }
}
