package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizedTransferInvariantTest {
    private val date = LocalDate.of(2026, 9, 27)

    @Test
    fun `transferencia categorizada no afecta presupuesto estadisticas ni patrimonio`() {
        val accounts = listOf(account("from", 10_000), account("to", 20_000))
        val transfer = Transfer(
            id = "transfer",
            fromAccountId = "from",
            toAccountId = "to",
            fromAmountMinor = 2_500,
            toAmountMinor = 2_500,
            date = date,
            description = "Traspaso interno",
            createdAt = 1,
            categoryId = "category",
        )
        val transactions = emptyList<com.mipatrimonio.app.domain.model.Transaction>()
        val budget = Budget(
            id = "budget",
            name = "Mensual",
            limitMinor = 50_000,
            currency = "EUR",
            period = BudgetPeriod.MENSUAL,
            startDate = date.withDayOfMonth(1),
            endDate = null,
            alertThresholdPct = 90,
            categoryRules = emptyList(),
            archived = false,
        )

        assertEquals(0L, BudgetCalculator.status(budget, transactions, emptyList(), date).spentMinor)
        val totals = StatsCalculator.totals(transactions, "EUR", date..date)
        assertEquals(0L, totals.incomeMinor)
        assertEquals(0L, totals.expenseMinor)
        assertEquals(
            30_000L,
            HistoryCalculator.netWorthSeries(
                baseCurrency = "EUR",
                accounts = accounts,
                transactions = transactions,
                transfers = listOf(transfer),
                assets = emptyList(),
                operations = emptyList(),
                dates = listOf(date),
            ).single().totalMinor,
        )
    }

    private fun account(id: String, initialBalanceMinor: Long) = Account(
        id = id,
        name = id,
        type = AccountType.CORRIENTE,
        currency = "EUR",
        initialBalanceMinor = initialBalanceMinor,
        archived = false,
        createdAt = 1,
    )
}
