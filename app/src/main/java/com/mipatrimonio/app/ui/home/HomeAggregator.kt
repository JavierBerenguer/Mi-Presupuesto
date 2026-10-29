package com.mipatrimonio.app.ui.home

import com.mipatrimonio.app.data.sync.PortfolioSummary
import com.mipatrimonio.app.data.sync.PortfolioHistoryPoint
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.CategorySpend
import com.mipatrimonio.app.domain.calc.LiquidityBalanceCalculator
import com.mipatrimonio.app.domain.calc.LiquidityMovement
import com.mipatrimonio.app.domain.calc.MonthTotals
import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.calc.PeriodTotals
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.movementStatus
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.NetWorthPoint
import com.mipatrimonio.app.domain.usecase.Period
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

data class BudgetRemaining(val remainingMinor: Long, val currency: String)

data class InvestmentClassSummary(val name: String, val valueMinor: Long, val weightPercent: Int)

data class InvestmentSummary(
    val valueMinor: Long,
    val gainMinor: Long,
    val returnRate: BigDecimal?,
    val irr: BigDecimal?,
    val classes: List<InvestmentClassSummary>,
    val generated: String,
)

data class AssetAllocation(val cashPercent: Int, val investmentsPercent: Int)

enum class EvolutionSeries { LIQUIDITY, INVESTMENT, TOTAL }

data class LiquidityCushion(
    val liquidCashMinor: Long,
    val averageLivingExpenseMinor: Long,
    val averageTotalExpenseMinor: Long,
    val coveredMonthsWithoutInvesting: BigDecimal,
    val coveredMonthsContinuingInvesting: BigDecimal?,
)

data class HomeState(
    val hasAccounts: Boolean,
    val hasFinancialData: Boolean,
    val accountCount: Int,
    val baseCurrency: String,
    val netWorth: NetWorth,
    val month: YearMonth,
    val monthTotals: PeriodTotals,
    val savingRatePercent: BigDecimal?,
    val budgetRemaining: BudgetRemaining?,
    val period: Period,
    val netWorthSeries: List<NetWorthPoint>,
    val selectedEvolutionSeries: EvolutionSeries,
    val hasInvestmentHistory: Boolean,
    val investmentHistoryUpdated: String?,
    val monthlySeries: List<MonthTotals>,
    val expenseByCategory: List<CategorySpend>,
    val hideAmounts: Boolean,
    val automaticTransactionsCount: Int,
    val spentIncomePercent: Int?,
    val displayedNetWorthMinor: Long,
    val displayedVariationMinor: Long,
    val investments: InvestmentSummary?,
    val allocation: AssetAllocation?,
    val liquidityCushion: LiquidityCushion?,
    /** Lo que pasó de las cuentas a inversiones en el periodo (compras de Rumbo menos ventas y dividendos). No es gasto. */
    val investedInPeriodMinor: Long = 0L,
)

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
    val remaining = global?.remainingMinor ?: statuses.fold(0L) { sum, status ->
        Math.addExact(sum, status.remainingMinor)
    }
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
    portfolioSummary: PortfolioSummary? = null,
    accountBalanceRecords: List<AccountBalanceRecord> = emptyList(),
    evolutionSeries: EvolutionSeries = EvolutionSeries.TOTAL,
): HomeState {
    val balances = accounts.map { account ->
        AccountBalance(
            account,
            BalanceCalculator.balance(
                account, transactions, transfers, today, liquidityMovements, accountBalanceRecords,
            ),
        )
    }
    val netWorth = NetWorthCalculator.compute(baseCurrency, balances)
    val firstActivity = (
        transactions.map { it.date } + transfers.map { it.date } + liquidityMovements.map { it.date } +
            accountBalanceRecords.map { it.date }
        ).minOrNull()
    val thisMonth = YearMonth.from(today)
    val includedAccountIds = accounts.filter { !it.archived && it.useInApp && it.currency == baseCurrency }
        .mapTo(hashSetOf(), Account::id)
    val filteredSeries = HistoryCalculator.netWorthSeries(
        baseCurrency,
        accounts,
        transactions,
        transfers,
        HistoryCalculator.sampleDates(period, today, firstActivity),
        today,
        accountBalanceRecords,
    ).map { point ->
        point.copy(
            totalMinor = Math.addExact(
                point.totalMinor,
                LiquidityBalanceCalculator.total(liquidityMovements, includedAccountIds, point.date),
            ),
        )
    }
    val monthTotals = StatsCalculator.totals(
        transactions, baseCurrency, StatsCalculator.monthRange(thisMonth), today, categories,
    )
    val investments = portfolioSummary?.toInvestmentSummary()
    val investmentHistory = portfolioSummary?.history
    val selectedSeries = if (investmentHistory == null && evolutionSeries == EvolutionSeries.INVESTMENT) {
        EvolutionSeries.TOTAL
    } else {
        evolutionSeries
    }
    val displayedSeries = buildEvolutionSeries(filteredSeries, investmentHistory, selectedSeries)
    return HomeState(
        hasAccounts = accounts.any { !it.archived },
        hasFinancialData = accounts.any { !it.archived } || investments != null,
        accountCount = accounts.count { !it.archived },
        baseCurrency = baseCurrency,
        netWorth = netWorth,
        month = thisMonth,
        monthTotals = monthTotals,
        savingRatePercent = savingRate(monthTotals),
        budgetRemaining = budgetRemaining(budgets, transactions, categories, baseCurrency, today),
        period = period,
        netWorthSeries = displayedSeries,
        selectedEvolutionSeries = selectedSeries,
        hasInvestmentHistory = investmentHistory != null,
        investmentHistoryUpdated = portfolioSummary?.generated?.substringBefore('T'),
        monthlySeries = StatsCalculator.monthlySeries(
            transactions, baseCurrency, thisMonth, period.barMonths(today, firstActivity), today, categories,
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
        spentIncomePercent = percentage(monthTotals.expenseMinor, monthTotals.incomeMinor),
        displayedNetWorthMinor = Math.addExact(netWorth.cashMinor, investments?.valueMinor ?: 0L),
        displayedVariationMinor = if (displayedSeries.size < 2) 0L else
            Math.subtractExact(displayedSeries.last().totalMinor, displayedSeries.first().totalMinor),
        investments = investments,
        allocation = assetAllocation(netWorth.cashMinor, investments?.valueMinor),
        liquidityCushion = liquidityCushion(baseCurrency, accounts, balances, transactions, categories, today, liquidityMovements),
        investedInPeriodMinor = period.range(today, firstActivity).let { range ->
            -liquidityMovements.filter { it.accountId in includedAccountIds && it.date in range }
                .fold(0L) { sum, movement -> Math.addExact(sum, movement.effectMinor) }
        },
    )
}

internal fun buildEvolutionSeries(
    liquidity: List<NetWorthPoint>,
    investmentHistory: List<PortfolioHistoryPoint>?,
    selected: EvolutionSeries,
): List<NetWorthPoint> {
    if (selected == EvolutionSeries.LIQUIDITY || investmentHistory == null) return liquidity
    var historyIndex = 0
    var lastInvestment = 0L
    return liquidity.map { point ->
        while (historyIndex < investmentHistory.size && !investmentHistory[historyIndex].date.isAfter(point.date)) {
            lastInvestment = investmentHistory[historyIndex].investmentMinor
            historyIndex++
        }
        NetWorthPoint(
            point.date,
            if (selected == EvolutionSeries.INVESTMENT) lastInvestment else Math.addExact(point.totalMinor, lastInvestment),
        )
    }
}

internal fun PortfolioSummary.toInvestmentSummary(): InvestmentSummary {
    val gain = products.fold(0L) { sum, product -> Math.addExact(sum, product.gainMinor) }
    val contributed = products.fold(0L) { sum, product -> Math.addExact(sum, product.contributedMinor) }
    val includedClasses = classes.filterNot { it.name.normalized() == "efectivo" }
        .sortedByDescending { it.valueMinor }
    val includedValue = includedClasses.fold(0L) { sum, item -> Math.addExact(sum, item.valueMinor) }
    return InvestmentSummary(
        valueMinor = total.valueMinor,
        gainMinor = gain,
        returnRate = if (contributed <= 0L) null else BigDecimal.valueOf(gain)
            .divide(BigDecimal.valueOf(contributed), 10, RoundingMode.HALF_UP),
        irr = total.irr,
        classes = includedClasses.take(4).map { item ->
            InvestmentClassSummary(item.name, item.valueMinor, percentage(item.valueMinor, includedValue) ?: 0)
        },
        generated = generated,
    )
}

internal fun savingRate(totals: PeriodTotals): BigDecimal? = if (totals.incomeMinor <= 0L) null else
    BigDecimal.valueOf(Math.subtractExact(totals.incomeMinor, totals.expenseMinor))
        .multiply(HUNDRED)
        .divide(BigDecimal.valueOf(totals.incomeMinor), 1, RoundingMode.HALF_UP)

internal fun assetAllocation(cashMinor: Long, investmentsMinor: Long?): AssetAllocation? {
    if (investmentsMinor == null || cashMinor <= 0L || investmentsMinor <= 0L) return null
    val total = Math.addExact(cashMinor, investmentsMinor)
    val cashPercent = percentage(cashMinor, total) ?: return null
    return AssetAllocation(cashPercent, 100 - cashPercent)
}

internal fun liquidityCushion(
    baseCurrency: String,
    accounts: List<Account>,
    balances: List<AccountBalance>,
    transactions: List<Transaction>,
    categories: List<Category>,
    today: LocalDate,
    liquidityMovements: List<LiquidityMovement> = emptyList(),
): LiquidityCushion? {
    val previousMonth = YearMonth.from(today).minusMonths(1)
    val firstActivityMonth = transactions.asSequence()
        .filter { !it.date.isAfter(today) }
        .map { YearMonth.from(it.date) }
        .minOrNull()
        ?: return null
    if (firstActivityMonth.isAfter(previousMonth)) return null
    val availableMonths = ChronoUnit.MONTHS.between(firstActivityMonth, previousMonth) + 1L
    val months = minOf(6L, availableMonths).toInt()
    val totalSeries = StatsCalculator.monthlySeries(
        transactions, baseCurrency, previousMonth, months, today, categories,
    )
    val livingSeries = StatsCalculator.monthlySeries(
        transactions, baseCurrency, previousMonth, months, today, categories,
        StatsCalculator.investmentRootIds(categories),
    )
    val averageLivingExpense = averageExpense(livingSeries, months)
    // «Si sigues invirtiendo»: el gasto de vida, la categoría Inversiones y lo que de verdad sale de las
    // cuentas de liquidez hacia Rumbo (compras menos ventas y dividendos), de media en los mismos meses.
    val liquidityIds = accounts.filter { !it.archived && it.liquidity && it.currency == baseCurrency }.mapTo(hashSetOf(), Account::id)
    val window = previousMonth.minusMonths(months.toLong() - 1).atDay(1)..previousMonth.atEndOfMonth()
    val netInvested = -liquidityMovements.filter { it.accountId in liquidityIds && it.date in window }
        .fold(0L) { sum, movement -> Math.addExact(sum, movement.effectMinor) }
    val averageInvested = BigDecimal.valueOf(maxOf(0L, netInvested))
        .divide(BigDecimal.valueOf(months.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
    val averageTotalExpense = Math.addExact(averageExpense(totalSeries, months), averageInvested)
    if (averageLivingExpense <= 0L) return null
    val balancesByAccount = balances.associateBy { it.account.id }
    val liquidCash = accounts.asSequence()
        .filter { !it.archived && it.liquidity && it.currency == baseCurrency }
        .fold(0L) { sum, account ->
            Math.addExact(sum, balancesByAccount.getValue(account.id).balanceMinor)
        }
    val coveredWithoutInvesting = BigDecimal.valueOf(liquidCash)
        .divide(BigDecimal.valueOf(averageLivingExpense), 1, RoundingMode.HALF_UP)
    val coveredContinuing = if (averageTotalExpense <= 0L) null else
        BigDecimal.valueOf(liquidCash).divide(BigDecimal.valueOf(averageTotalExpense), 1, RoundingMode.HALF_UP)
            .takeIf { it.compareTo(coveredWithoutInvesting) != 0 }
    return LiquidityCushion(
        liquidCash, averageLivingExpense, averageTotalExpense, coveredWithoutInvesting, coveredContinuing,
    )
}

private fun averageExpense(series: List<MonthTotals>, months: Int): Long {
    val totalExpense = series.fold(0L) { sum, month -> Math.addExact(sum, month.expenseMinor) }
    return BigDecimal.valueOf(totalExpense)
        .divide(BigDecimal.valueOf(months.toLong()), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

private fun percentage(part: Long, total: Long): Int? = if (total <= 0L) null else
    BigDecimal.valueOf(part).multiply(HUNDRED)
        .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
        .intValueExact()

private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .lowercase(Locale.ROOT)

private val HUNDRED = BigDecimal.valueOf(100L)
