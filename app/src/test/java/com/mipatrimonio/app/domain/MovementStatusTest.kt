package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.transfer
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import com.mipatrimonio.app.domain.model.movementStatus
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementStatusTest {
    private val today = LocalDate.of(2026, 9, 28)
    private val tomorrow = today.plusDays(1)

    @Test
    fun `estado se deriva solo de la fecha y del dia inyectado`() {
        assertEquals(MovementStatus.EJECUTADO, movementStatus(today.minusDays(1), today))
        assertEquals(MovementStatus.EJECUTADO, movementStatus(today, today))
        assertEquals(MovementStatus.PREVISTO, movementStatus(tomorrow, today))
        assertEquals(MovementStatus.PREVISTO, movementStatus(today.plusYears(5), today))
        assertEquals(MovementStatus.EJECUTADO, movementStatus(tomorrow, tomorrow))
    }

    @Test
    fun `saldo y transferencia prevista cambian solo al llegar su fecha`() {
        val source = account("source", initial = 1_000)
        val destination = account("destination", initial = 500)
        val transaction = tx(GASTO, 100, account = source.id, date = tomorrow)
        val plannedTransfer = transfer(source.id, destination.id, 200, date = tomorrow)

        assertEquals(1_000L, BalanceCalculator.balance(source, listOf(transaction), listOf(plannedTransfer), today = today))
        assertEquals(500L, BalanceCalculator.balance(destination, emptyList(), listOf(plannedTransfer), today = today))
        assertEquals(700L, BalanceCalculator.balance(source, listOf(transaction), listOf(plannedTransfer), today = tomorrow))
        assertEquals(700L, BalanceCalculator.balance(destination, emptyList(), listOf(plannedTransfer), today = tomorrow))
    }

    @Test
    fun `cuenta archivada con saldo solo previsto conserva saldo actual cero`() {
        val archived = account("archived", initial = 0, archived = true)
        val plannedIncome = tx(INGRESO, 400, account = archived.id, date = tomorrow)

        assertEquals(0L, BalanceCalculator.balance(archived, listOf(plannedIncome), emptyList(), today = today))
        assertEquals(400L, BalanceCalculator.balance(archived, listOf(plannedIncome), emptyList(), today = tomorrow))
    }

    @Test
    fun `patrimonio presupuesto y estadisticas ignoran previsto hasta su fecha`() {
        val account = account("account", initial = 1_000)
        val plannedExpense = tx(GASTO, 250, account = account.id, date = tomorrow)
        val budget = Budget("budget", null, BudgetPeriod.MENSUAL, 1_000, "EUR", false)
        val range = today.withDayOfMonth(1)..today.withDayOfMonth(today.lengthOfMonth())

        val currentSnapshot = SnapshotBuilder.build(
            "EUR", listOf(account), listOf(plannedExpense), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyMap(), today,
        )
        val laterSnapshot = SnapshotBuilder.build(
            "EUR", listOf(account), listOf(plannedExpense), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyMap(), tomorrow,
        )
        assertEquals(1_000L, currentSnapshot.netWorth.totalMinor)
        assertEquals(750L, laterSnapshot.netWorth.totalMinor)
        assertEquals(0L, BudgetCalculator.status(budget, listOf(plannedExpense), emptyList(), today, today).spentMinor)
        assertEquals(250L, BudgetCalculator.status(budget, listOf(plannedExpense), emptyList(), today, tomorrow).spentMinor)
        assertEquals(0L, StatsCalculator.totals(listOf(plannedExpense), "EUR", range, today).expenseMinor)
        assertEquals(250L, StatsCalculator.totals(listOf(plannedExpense), "EUR", range, tomorrow).expenseMinor)
        assertEquals(0, StatsCalculator.expenseByCategory(listOf(plannedExpense), emptyList(), "EUR", range, today).size)
        assertEquals(250L, StatsCalculator.expenseByCategory(listOf(plannedExpense), emptyList(), "EUR", range, tomorrow).single().amountMinor)
    }

    @Test
    fun `historia no crea puntos futuros y aplica el movimiento al cambiar de dia`() {
        val account = account("account", initial = 1_000)
        val plannedIncome = tx(INGRESO, 300, account = account.id, date = tomorrow)
        val dates = listOf(today, tomorrow)

        val current = HistoryCalculator.netWorthSeries(
            "EUR", listOf(account), listOf(plannedIncome), emptyList(), emptyList(), emptyList(), dates, today,
        )
        val later = HistoryCalculator.netWorthSeries(
            "EUR", listOf(account), listOf(plannedIncome), emptyList(), emptyList(), emptyList(), dates, tomorrow,
        )

        assertEquals(listOf(today), current.map { it.date })
        assertEquals(listOf(1_000L, 1_300L), later.map { it.totalMinor })
    }
}
