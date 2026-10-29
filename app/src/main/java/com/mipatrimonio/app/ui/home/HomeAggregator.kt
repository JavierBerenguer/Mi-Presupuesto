package com.mipatrimonio.app.ui.home

import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.CategorySpend
import com.mipatrimonio.app.domain.calc.MonthTotals
import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.LiquidityMovement
import com.mipatrimonio.app.domain.calc.LiquidityBalanceCalculator
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.calc.PeriodTotals
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.movementStatus
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.NetWorthPoint
import com.mipatrimonio.app.domain.usecase.Period
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import java.time.LocalDate
import java.time.YearMonth

data class BudgetRemaining(val remainingMinor: Long, val currency: String)

data class HomeState(
    val hasAccounts: Boolean,
    val hasFinancialData: Boolean,
    val baseCurrency: String,
    val netWorth: NetWorth,
    val monthTotals: PeriodTotals,
    /** Null si no hay presupuestos mensuales en divisa base. */
    val budgetRemaining: BudgetRemaining?,
    val period: Period,
    val netWorthSeries: List<NetWorthPoint>,
    val monthlySeries: List<MonthTotals>,
    val expenseByCategory: List<CategorySpend>,
    val hideAmounts: Boolean,
    val automaticTransactionsCount: Int,
    val spentIncomePercent: Int?,
    val displayedNetWorthMinor: Long,
    val displayedVariationMinor: Long,
    val portfolioValueMinor: Long = 0L,
    val portfolioGenerated: String? = null,
)

/**
 * Presupuesto restante del mes actual: si existe un presupuesto global mensual, su restante;
 * si no, la suma de los restantes de los presupuestos mensuales por categoría. Nunca se suman ambos.
 */
fun budgetRemaining(
    budgets: List<Budget>,
    transactions: List<Transaction>,
    categories: List<Category>,
    baseCurrency: String,
    today: LocalDate,
): BudgetRemaining? {
    val monthly = budgets.filter { !it.archived && it.period == BudgetPeriod.MENSUAL && it.currency == baseCurrency }
    if (monthly.isEmpty()) return null
    val statuses = monthly.map { BudgetCalculator.status(it, transactions, categories, today, today) }
    val global = statuses.firstOrNull { it.budget.categoryId == null }
    val remaining = global?.remainingMinor ?: statuses.sumOf { it.remainingMinor }
    return BudgetRemaining(remaining, baseCurrency)
}

@Suppress("LongParameterList")
fun buildHomeState(
    baseCurrency: String,
    accounts: List<Account>,
    transactions: List<Transaction>,
    transfers: List<Transfer>,
    categories: List<Category>,
    budgets: List<Budget>,
    today: LocalDate,
    period: Period,
    hideAmounts: Boolean = false,
    liquidityMovements: List<LiquidityMovement> = emptyList(),
    portfolioValueMinor: Long = 0L,
    portfolioGenerated: String? = null,
    accountBalanceRecords: List<AccountBalanceRecord> = emptyList(),
): HomeState {
    val balances = accounts.map { AccountBalance(it, BalanceCalculator.balance(it, transactions, transfers, today, liquidityMovements, accountBalanceRecords)) }
    val netWorth = NetWorthCalculator.compute(baseCurrency, balances)
    val firstActivity = (transactions.map { it.date } + transfers.map { it.date } + liquidityMovements.map { it.date } + accountBalanceRecords.map { it.date }).minOrNull()
    val thisMonth = YearMonth.from(today)
    val includedAccountIds = accounts.filter { !it.archived && it.useInApp && it.currency == baseCurrency }.mapTo(hashSetOf(), Account::id)
    val filteredSeries = HistoryCalculator.netWorthSeries(
        baseCurrency, accounts, transactions, transfers,
        HistoryCalculator.sampleDates(period, today, firstActivity), today, accountBalanceRecords,
    ).map { point -> point.copy(totalMinor = Math.addExact(point.totalMinor, LiquidityBalanceCalculator.total(liquidityMovements, includedAccountIds, point.date))) }
    val displayedNetWorth = Math.addExact(netWorth.cashMinor, portfolioValueMinor)
    val spentPercent = StatsCalculator.totals(
        transactions, baseCurrency, StatsCalculator.monthRange(thisMonth), today,
    ).let { totals ->
        if (totals.incomeMinor <= 0L) null else
            java.math.BigDecimal(totals.expenseMinor)
                .multiply(java.math.BigDecimal(100))
                .divide(java.math.BigDecimal(totals.incomeMinor), 0, java.math.RoundingMode.HALF_UP)
                .toInt()
    }
    return HomeState(
        hasAccounts = accounts.any { !it.archived },
        hasFinancialData = accounts.any { !it.archived } || portfolioValueMinor != 0L,
        baseCurrency = baseCurrency,
        netWorth = netWorth,
        monthTotals = StatsCalculator.totals(
            transactions, baseCurrency, StatsCalculator.monthRange(thisMonth), today,
        ),
        budgetRemaining = budgetRemaining(budgets, transactions, categories, baseCurrency, today),
        period = period,
        netWorthSeries = filteredSeries,
        monthlySeries = StatsCalculator.monthlySeries(
            transactions, baseCurrency, thisMonth, period.barMonths(today, firstActivity), today,
        ),
        expenseByCategory = StatsCalculator.expenseByCategory(
            transactions, categories, baseCurrency, period.range(today, firstActivity), today,
        ),
        hideAmounts = hideAmounts,
        automaticTransactionsCount = transactions.count {
            it.source == TransactionSource.NOTIFICACION &&
                !it.date.isBefore(today.minusDays(6)) &&
                movementStatus(it.date, today) == MovementStatus.EJECUTADO
        },
        spentIncomePercent = spentPercent,
        displayedNetWorthMinor = displayedNetWorth,
        displayedVariationMinor = if (filteredSeries.size < 2) 0L else
            Math.subtractExact(filteredSeries.last().totalMinor, filteredSeries.first().totalMinor),
        portfolioValueMinor = portfolioValueMinor,
        portfolioGenerated = portfolioGenerated,
    )
}
