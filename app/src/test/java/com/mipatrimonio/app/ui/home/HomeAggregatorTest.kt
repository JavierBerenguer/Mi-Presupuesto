package com.mipatrimonio.app.ui.home

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.category
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import com.mipatrimonio.app.domain.usecase.Period
import java.time.LocalDate
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
}
