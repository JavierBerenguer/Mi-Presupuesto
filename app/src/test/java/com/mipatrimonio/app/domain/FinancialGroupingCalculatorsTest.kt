package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.MovementsBalanceCalculator
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.ui.budgets.StatisticsKind
import com.mipatrimonio.app.ui.budgets.monthlyStatistics
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialGroupingCalculatorsTest {
    private val parentDate = LocalDate.of(2026, 10, 10)
    private val childDate = LocalDate.of(2026, 11, 2)
    private val accounts = listOf(account("a"), account("b"))
    private val categories = listOf(Category("food", "Comida", null, 1, false))
    private val transactions = listOf(
        tx("p", TransactionType.GASTO, 6000, parentDate, "a"),
        tx("c1", TransactionType.INGRESO, 2000, childDate, "b", "p"),
        tx("c2", TransactionType.INGRESO, 2000, parentDate, "a", "p"),
    )

    @Test fun `estadisticas presupuesto y reglas imputan neto al principal`() {
        val range = parentDate.withDayOfMonth(1)..parentDate.withDayOfMonth(31)
        val totals = StatsCalculator.totals(transactions, "EUR", range, parentDate.plusMonths(2))
        assertEquals(2000L, totals.expenseMinor)
        assertEquals(0L, totals.incomeMinor)
        assertEquals(2000L, StatsCalculator.expenseByCategory(transactions, categories, "EUR", range, parentDate.plusMonths(2)).single().amountMinor)
        val budget = Budget("b", "Comida", 10000, "EUR", BudgetPeriod.MENSUAL, range.start, null, 90, emptyList(), false)
        assertEquals(2000L, BudgetCalculator.status(budget, transactions, categories, parentDate, parentDate.plusMonths(2)).spentMinor)
        assertEquals(2000L, monthlyStatistics(transactions, categories, "EUR", YearMonth.from(parentDate), StatisticsKind.GASTOS, parentDate.plusMonths(2)).totalMinor)
        assertEquals(0L, monthlyStatistics(transactions, categories, "EUR", YearMonth.from(childDate), StatisticsKind.INGRESOS, childDate.plusMonths(1)).totalMinor)
    }

    @Test fun `modos mensuales netean pero saldos conservan caja`() {
        fun calculate(mode: MovementsCalculationMode) = MovementsBalanceCalculator.calculate(
            accounts, transactions, emptyList(), YearMonth.from(parentDate), "EUR", mode,
            dailyBalance = false, hideFuture = false, ignoreTransfers = false, today = parentDate.plusMonths(2),
        )
        assertEquals(2000L, calculate(MovementsCalculationMode.GASTOS_MENSUALES))
        assertEquals(-4000L, calculate(MovementsCalculationMode.SALDO_MENSUAL))
        val loose = transactions.map { it.copy(groupParentId = null) }
        accounts.forEach { account ->
            assertEquals(
                BalanceCalculator.balance(account, loose, emptyList(), today = childDate.plusMonths(1)),
                BalanceCalculator.balance(account, transactions, emptyList(), today = childDate.plusMonths(1)),
            )
        }
    }

    private fun account(id: String) = Account(id, id, AccountType.CORRIENTE, "EUR", 0, false, 1)
    private fun tx(id: String, type: TransactionType, amount: Long, date: LocalDate, account: String, group: String? = null) =
        Transaction(id, type, amount, "EUR", date, account, "food", id, "", "", TransactionSource.MANUAL, 1, 1, group)
}
