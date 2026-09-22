package com.mipatrimonio.app.ui.home

import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.CategorySpend
import com.mipatrimonio.app.domain.calc.MonthTotals
import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.calc.PeriodTotals
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.usecase.AssetShare
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.NetWorthPoint
import com.mipatrimonio.app.domain.usecase.Period
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import com.mipatrimonio.app.domain.usecase.assetDistribution
import java.time.LocalDate
import java.time.YearMonth

data class BudgetRemaining(val remainingMinor: Long, val currency: String)

data class HomeState(
    val hasAccounts: Boolean,
    val baseCurrency: String,
    val netWorth: NetWorth,
    val monthTotals: PeriodTotals,
    /** Null si no hay presupuestos mensuales en divisa base. */
    val budgetRemaining: BudgetRemaining?,
    val period: Period,
    val netWorthSeries: List<NetWorthPoint>,
    val monthlySeries: List<MonthTotals>,
    val expenseByCategory: List<CategorySpend>,
    val assetShares: List<AssetShare>,
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
    val statuses = monthly.map { BudgetCalculator.status(it, transactions, categories, today) }
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
    portfolios: List<Portfolio>,
    assets: List<Asset>,
    operations: List<InvestmentOperation>,
    prices: Map<String, AssetPrice>,
    today: LocalDate,
    period: Period,
): HomeState {
    val snapshot = SnapshotBuilder.build(
        baseCurrency, accounts, transactions, transfers, portfolios, assets, operations, prices,
    )
    val firstActivity = (transactions.map { it.date } + transfers.map { it.date } + operations.map { it.date }).minOrNull()
    val thisMonth = YearMonth.from(today)
    return HomeState(
        hasAccounts = accounts.any { !it.archived },
        baseCurrency = baseCurrency,
        netWorth = snapshot.netWorth,
        monthTotals = StatsCalculator.totals(transactions, baseCurrency, StatsCalculator.monthRange(thisMonth)),
        budgetRemaining = budgetRemaining(budgets, transactions, categories, baseCurrency, today),
        period = period,
        netWorthSeries = HistoryCalculator.netWorthSeries(
            baseCurrency, accounts, transactions, transfers, assets, operations,
            HistoryCalculator.sampleDates(period, today, firstActivity),
        ),
        monthlySeries = StatsCalculator.monthlySeries(
            transactions, baseCurrency, thisMonth, period.barMonths(today, firstActivity),
        ),
        expenseByCategory = StatsCalculator.expenseByCategory(
            transactions, categories, baseCurrency, period.range(today, firstActivity),
        ),
        assetShares = assetDistribution(snapshot.netWorth),
    )
}
