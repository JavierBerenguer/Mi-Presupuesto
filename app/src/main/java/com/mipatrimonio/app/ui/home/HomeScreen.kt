package com.mipatrimonio.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.usecase.Period
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.NetWorthNotices
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.charts.BarGroup
import com.mipatrimonio.app.ui.common.charts.ChartPoint
import com.mipatrimonio.app.ui.common.charts.LineChart
import com.mipatrimonio.app.ui.common.pointLabel
import com.mipatrimonio.app.ui.common.charts.DonutChart
import com.mipatrimonio.app.ui.common.charts.DonutSlice
import com.mipatrimonio.app.ui.common.charts.GroupedBarChart
import com.mipatrimonio.app.ui.common.monthLabel
import com.mipatrimonio.app.ui.common.resolvedIconKey
import com.mipatrimonio.app.ui.components.PillTabs
import com.mipatrimonio.app.ui.components.ProgressBar
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.theme.LargeAmountStyle
import com.mipatrimonio.app.ui.theme.extras
import java.math.BigDecimal
import java.math.RoundingMode

private val NO_CATEGORY_COLOR = Color(0xFF8D99AE)

@Composable
fun HomeScreen(
    onOpenAccounts: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAutomaticMovements: () -> Unit = {},
    viewModel: HomeViewModel = appViewModel { c -> HomeViewModel(c.ledger, c.settings, c.sync) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val s = state ?: run { LoadingBox(); return }
    if (!s.hasFinancialData) {
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            message = stringResource(R.string.ini_welcome_empty),
            actionLabel = stringResource(R.string.ini_create_account),
            onAction = onOpenAccounts,
        )
        return
    }
    val hiddenAmount = stringResource(R.string.common_hidden_amount)
    val format: (Long) -> String = { amount ->
        if (s.hideAmounts) hiddenAmount else MoneyMath.format(amount, s.baseCurrency)
    }

    Column(
        Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NetWorthHeader(s, format, viewModel::setHideAmounts)
        s.allocation?.let { AllocationBar(it) }
        SummaryCards(s, format, onOpenAccounts, onOpenPortfolio, onOpenSync)
        PeriodTabs(s.period, viewModel::selectPeriod)
        EvolutionCard(s, format, viewModel::selectEvolutionSeries)
        if (s.netWorth.excludedCurrencies.isNotEmpty()) NetWorthNotices(s.netWorth)
        if (s.automaticTransactionsCount > 0) {
            AutomaticMovementsCard(s.automaticTransactionsCount, onOpenAutomaticMovements)
        }
        MonthCard(s, format, hiddenAmount)
        s.liquidityCushion?.let { LiquidityCushionCard(it, format) }
        s.investments?.let { InvestmentsCard(it, format, onOpenPortfolio) }
        Text(stringResource(R.string.ini_analysis), style = MaterialTheme.typography.titleLarge)
        ExpenseDistributionCard(s, categories, format)
        IncomeExpensesCard(s, format)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NetWorthHeader(state: HomeState, format: (Long) -> String, onHideChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ini_networth), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(format(state.displayedNetWorthMinor), style = LargeAmountStyle)
            Surface(
                color = if (state.displayedVariationMinor >= 0) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.extras.expense.copy(alpha = 0.20f),
                contentColor = if (state.displayedVariationMinor >= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.extras.expense,
                shape = MaterialTheme.shapes.large,
            ) {
                val sign = if (state.displayedVariationMinor >= 0) "+" else "−"
                val value = if (state.hideAmounts) stringResource(R.string.common_hidden_amount) else
                    MoneyMath.format(state.displayedVariationMinor, state.baseCurrency).removePrefix("-")
                Text(
                    stringResource(R.string.ini_period_variation, sign, value),
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        IconButton(onClick = { onHideChange(!state.hideAmounts) }, modifier = Modifier.size(48.dp)) {
            Icon(
                if (state.hideAmounts) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = stringResource(
                    if (state.hideAmounts) R.string.ini_show_amounts else R.string.ini_hide_amounts,
                ),
            )
        }
    }
}

@Composable
private fun AllocationBar(allocation: AssetAllocation) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ini_allocation_cash, allocation.cashPercent), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.ini_allocation_investments, allocation.investmentsPercent),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
            if (allocation.cashPercent > 0) {
                Box(
                    Modifier.weight(allocation.cashPercent.toFloat()).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            if (allocation.investmentsPercent > 0) {
                Box(
                    Modifier.weight(allocation.investmentsPercent.toFloat()).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
        }
    }
}

@Composable
private fun SummaryCards(
    state: HomeState,
    format: (Long) -> String,
    onOpenAccounts: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenSync: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard(Modifier.weight(1f).clickable(onClick = onOpenAccounts)) {
            Text(stringResource(R.string.ini_cash), style = MaterialTheme.typography.titleMedium)
            Text(format(state.netWorth.cashMinor), style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(R.plurals.ini_account_count, state.accountCount, state.accountCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val investments = state.investments
        SectionCard(Modifier.weight(1f).clickable(onClick = if (investments == null) onOpenSync else onOpenPortfolio)) {
            Text(stringResource(R.string.ini_investments), style = MaterialTheme.typography.titleMedium)
            if (investments == null) {
                Text(stringResource(R.string.ini_no_rumbo_data), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.ini_open_sync),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(format(investments.valueMinor), style = MaterialTheme.typography.titleLarge)
                InvestmentGain(investments, format)
            }
        }
    }
}

@Composable
private fun InvestmentGain(summary: InvestmentSummary, format: (Long) -> String) {
    val rate = summary.returnRate?.let { stringResource(R.string.ini_percent_value, percentageNumber(it)) }
    Text(
        if (rate == null) stringResource(R.string.ini_gain_only, format(summary.gainMinor))
        else stringResource(R.string.ini_gain_and_return, format(summary.gainMinor), rate),
        style = MaterialTheme.typography.bodySmall,
        color = if (summary.gainMinor >= 0L) MoneyColors.positive else MoneyColors.negative,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PeriodTabs(period: Period, onSelect: (Period) -> Unit) {
    PillTabs(
        options = listOf(
            stringResource(R.string.ini_period_1m),
            stringResource(R.string.ini_period_3m),
            stringResource(R.string.ini_period_6m),
            stringResource(R.string.ini_period_1y),
            stringResource(R.string.ini_period_max),
        ),
        selectedIndex = Period.entries.indexOf(period),
        onSelected = { onSelect(Period.entries[it]) },
    )
}

@Composable
private fun EvolutionCard(state: HomeState, format: (Long) -> String, onSelectSeries: (EvolutionSeries) -> Unit) {
    SectionCard {
        val seriesOptions = if (state.hasInvestmentHistory) EvolutionSeries.entries else
            listOf(EvolutionSeries.LIQUIDITY, EvolutionSeries.TOTAL)
        Text(stringResource(R.string.ini_chart_networth), style = MaterialTheme.typography.titleMedium)
        // Selector en su propia fila: junto al título no cabían las tres opciones.
        Row(Modifier.fillMaxWidth()) {
            PillTabs(
                options = seriesOptions.map { series ->
                    stringResource(
                        when (series) {
                            EvolutionSeries.LIQUIDITY -> R.string.ini_evolution_liquidity
                            EvolutionSeries.INVESTMENT -> R.string.ini_evolution_investment
                            EvolutionSeries.TOTAL -> R.string.ini_evolution_total
                        },
                    )
                },
                selectedIndex = seriesOptions.indexOf(state.selectedEvolutionSeries).coerceAtLeast(0),
                onSelected = { onSelectSeries(seriesOptions[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Con ejes, cuadrícula e importes (el mismo gráfico que tenía la antigua pantalla Patrimonio).
        LineChart(
            points = state.netWorthSeries.map { ChartPoint(pointLabel(it.date, state.period), it.totalMinor) },
            formatValue = format,
            description = stringResource(R.string.ini_chart_networth_desc),
            emptyText = stringResource(R.string.ini_no_data_period),
        )
        if (state.hasInvestmentHistory) {
            Text(
                stringResource(R.string.ini_chart_networth_note, state.investmentHistoryUpdated.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (state.investments != null) Text(
            stringResource(R.string.cartera_history_partial),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutomaticMovementsCard(count: Int, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.ini_automatic_count, count, count), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ini_automatic_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.ini_open_automatic))
        }
    }
}

@Composable
private fun MonthCard(state: HomeState, format: (Long) -> String, hiddenAmount: String) {
    SectionCard(title = stringResource(R.string.ini_month, monthLabel(state.month))) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledAmount(
                stringResource(R.string.ini_income),
                signedAmount(state.monthTotals.incomeMinor, state.baseCurrency, state.hideAmounts, hiddenAmount, "+"),
                MoneyColors.positive,
            )
            LabeledAmount(
                stringResource(R.string.ini_expenses),
                signedAmount(state.monthTotals.expenseMinor, state.baseCurrency, state.hideAmounts, hiddenAmount, "−"),
                MoneyColors.negative,
            )
            LabeledAmount(
                stringResource(R.string.ini_saving),
                signedAmount(
                    state.monthTotals.balanceMinor,
                    state.baseCurrency,
                    state.hideAmounts,
                    hiddenAmount,
                    if (state.monthTotals.balanceMinor >= 0) "+" else "−",
                ),
                if (state.monthTotals.balanceMinor >= 0) MoneyColors.positive else MoneyColors.negative,
            )
        }
        state.savingRatePercent?.let {
            Text(stringResource(R.string.ini_saving_rate, decimal(it)), style = MaterialTheme.typography.bodyMedium)
        }
        state.spentIncomePercent?.let { percent ->
            ProgressBar(percent.coerceAtMost(100) / 100f)
            Text(stringResource(R.string.ini_spent_percent, percent), style = MaterialTheme.typography.bodySmall)
        }
        if (state.monthTotals.excludedCount > 0) {
            Text(
                stringResource(R.string.ini_month_excluded, state.monthTotals.excludedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MoneyColors.warning,
            )
        }
        state.budgetRemaining?.let {
            Text(stringResource(R.string.ini_budget_remaining), style = MaterialTheme.typography.labelMedium)
            Text(format(it.remainingMinor), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun LiquidityCushionCard(cushion: LiquidityCushion, format: (Long) -> String) {
    SectionCard(title = stringResource(R.string.ini_liquidity_cushion)) {
        Text(
            cushion.coveredMonthsContinuingInvesting?.let { continuing ->
                stringResource(
                    R.string.ini_liquidity_months_double,
                    decimal(cushion.coveredMonthsWithoutInvesting),
                    decimal(continuing),
                )
            } ?: stringResource(R.string.ini_liquidity_months, decimal(cushion.coveredMonthsWithoutInvesting)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(
                if (cushion.coveredMonthsContinuingInvesting == null) R.string.ini_average_expense
                else R.string.ini_average_living_expense,
                format(cushion.averageLivingExpenseMinor),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        cushion.coveredMonthsContinuingInvesting?.let {
            Text(
                stringResource(R.string.ini_average_total_expense, format(cushion.averageTotalExpenseMinor)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InvestmentsCard(summary: InvestmentSummary, format: (Long) -> String, onOpenPortfolio: () -> Unit) {
    SectionCard(title = stringResource(R.string.ini_investments_summary)) {
        LabeledAmount(stringResource(R.string.ini_value), format(summary.valueMinor), MaterialTheme.colorScheme.onSurface)
        LabeledAmount(stringResource(R.string.ini_gain), format(summary.gainMinor), gainColor(summary.gainMinor))
        LabeledAmount(
            stringResource(R.string.ini_return),
            summary.returnRate?.let { stringResource(R.string.ini_percent_value, percentageNumber(it)) }
                ?: stringResource(R.string.ini_not_available),
            gainColor(summary.gainMinor),
        )
        LabeledAmount(
            stringResource(R.string.ini_irr),
            summary.irr?.let { stringResource(R.string.ini_percent_value, percentageNumber(it)) }
                ?: stringResource(R.string.ini_not_available),
            MaterialTheme.colorScheme.onSurface,
        )
        summary.classes.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(format(item.valueMinor), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ini_class_weight, item.weightPercent), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            stringResource(R.string.ini_rumbo_updated, summary.generated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenPortfolio).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ini_open_portfolio), color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExpenseDistributionCard(state: HomeState, categories: List<Category>, format: (Long) -> String) {
    SectionCard(title = stringResource(R.string.ini_chart_expense_distribution)) {
        val noCategory = stringResource(R.string.ini_no_category)
        DonutChart(
            slices = state.expenseByCategory.map { spend ->
                val category = categories.find { it.id == spend.categoryId }
                DonutSlice(
                    category?.name ?: noCategory,
                    spend.amountMinor,
                    category.colorOrDefault(),
                    category?.resolvedIconKey(categories) ?: "other",
                )
            },
            centerLabel = format(state.expenseByCategory.fold(0L) { sum, item -> Math.addExact(sum, item.amountMinor) }),
            formatValue = format,
            description = stringResource(R.string.ini_chart_expense_distribution_desc),
            emptyText = stringResource(R.string.ini_no_data_period),
        )
        if (state.investedInPeriodMinor > 0L) Text(
            stringResource(R.string.ini_invested_in_period, format(state.investedInPeriodMinor)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IncomeExpensesCard(state: HomeState, format: (Long) -> String) {
    SectionCard(title = stringResource(R.string.ini_chart_income_vs_expenses)) {
        GroupedBarChart(
            groups = state.monthlySeries.map { BarGroup(monthLabel(it.month), it.incomeMinor, it.expenseMinor) },
            incomeLabel = stringResource(R.string.ini_income),
            expenseLabel = stringResource(R.string.ini_expenses),
            formatValue = format,
            description = stringResource(R.string.ini_chart_income_vs_expenses_desc),
            emptyText = stringResource(R.string.ini_no_data_period),
        )
    }
}

@Composable
private fun LabeledAmount(label: String, amount: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            amount,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

private fun Category?.colorOrDefault(): Color = this?.let { Color(it.colorArgb) } ?: NO_CATEGORY_COLOR

private fun signedAmount(amount: Long, currency: String, hidden: Boolean, hiddenAmount: String, sign: String): String {
    if (hidden) return hiddenAmount
    val displayedSign = if (amount >= 0L) sign else if (sign == "+") "−" else "+"
    return displayedSign + MoneyMath.format(amount, currency).removePrefix("-")
}

private fun percentageNumber(value: BigDecimal): String = decimal(
    value.multiply(BigDecimal.valueOf(100L)).setScale(2, RoundingMode.HALF_UP),
)

private fun decimal(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

private fun gainColor(gainMinor: Long): Color = if (gainMinor >= 0L) MoneyColors.positive else MoneyColors.negative
