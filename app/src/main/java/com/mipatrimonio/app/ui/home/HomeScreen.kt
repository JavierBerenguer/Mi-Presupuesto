package com.mipatrimonio.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.mipatrimonio.app.ui.common.assetColor
import com.mipatrimonio.app.ui.common.charts.BarGroup
import com.mipatrimonio.app.ui.common.charts.DonutChart
import com.mipatrimonio.app.ui.common.charts.DonutSlice
import com.mipatrimonio.app.ui.common.charts.GroupedBarChart
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.common.monthLabel
import com.mipatrimonio.app.ui.components.PillTabs
import com.mipatrimonio.app.ui.components.ProgressBar
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.Sparkline
import com.mipatrimonio.app.ui.theme.LargeAmountStyle

private val NO_CATEGORY_COLOR = Color(0xFF8D99AE)
@Composable
fun HomeScreen(
    onOpenAccounts: () -> Unit,
    onOpenAutomaticMovements: () -> Unit = {},
    viewModel: HomeViewModel = appViewModel { c -> HomeViewModel(c.ledger, c.investments, c.settings) },
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
        Text(stringResource(R.string.ini_add_to_networth), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetWorthFilterChip(
                selected = s.includeAccounts,
                label = stringResource(R.string.ini_accounts),
                amount = format(s.netWorth.cashMinor),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setNetWorthIncludes(!s.includeAccounts, s.includeInvestments) },
            )
            NetWorthFilterChip(
                selected = s.includeInvestments,
                label = stringResource(R.string.ini_investments),
                amount = format(s.netWorth.investmentsMinor),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setNetWorthIncludes(s.includeAccounts, !s.includeInvestments) },
            )
        }
        NetWorthNotices(s.netWorth)
        PillTabs(
            options = listOf(
                stringResource(R.string.ini_period_1m),
                stringResource(R.string.ini_period_3m),
                stringResource(R.string.ini_period_6m),
                stringResource(R.string.ini_period_1y),
                stringResource(R.string.ini_period_max),
            ),
            selectedIndex = Period.entries.indexOf(s.period),
            onSelected = { viewModel.selectPeriod(Period.entries[it]) },
        )
        SectionCard(title = stringResource(R.string.ini_chart_networth)) {
            Sparkline(
                values = s.netWorthSeries.map { it.totalMinor.toFloat() },
                description = stringResource(R.string.ini_chart_networth_desc),
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            if (s.netWorthSeries.isEmpty()) Text(stringResource(R.string.ini_no_data_period))
            Text(
                stringResource(R.string.ini_chart_networth_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.automaticTransactionsCount > 0) {
            AutomaticMovementsCard(s.automaticTransactionsCount, onOpenAutomaticMovements)
        }
        MonthCard(s, format, hiddenAmount)
        SectionCard(title = stringResource(R.string.ini_chart_expense_distribution)) {
            val noCategory = stringResource(R.string.ini_no_category)
            DonutChart(
                slices = s.expenseByCategory.map { spend ->
                    val category = categories.find { it.id == spend.categoryId }
                    DonutSlice(category?.name ?: noCategory, spend.amountMinor, category.colorOrDefault())
                },
                centerLabel = format(s.expenseByCategory.sumOf { it.amountMinor }),
                formatValue = format,
                description = stringResource(R.string.ini_chart_expense_distribution_desc),
                emptyText = stringResource(R.string.ini_no_data_period),
            )
        }
        SectionCard(title = stringResource(R.string.ini_chart_asset_distribution)) {
            DonutChart(
                slices = s.assetShares.map { share ->
                    DonutSlice(
                        share.type?.label() ?: stringResource(R.string.ini_investments_label),
                        share.valueMinor,
                        assetColor(share.type),
                    )
                },
                centerLabel = format(s.assetShares.sumOf { it.valueMinor }),
                formatValue = format,
                description = stringResource(R.string.ini_chart_asset_distribution_desc),
                emptyText = stringResource(R.string.ini_no_data_period),
            )
        }
        SectionCard(title = stringResource(R.string.ini_chart_income_vs_expenses)) {
            GroupedBarChart(
                groups = s.monthlySeries.map { BarGroup(monthLabel(it.month), it.incomeMinor, it.expenseMinor) },
                incomeLabel = stringResource(R.string.ini_income),
                expenseLabel = stringResource(R.string.ini_expenses),
                formatValue = format,
                description = stringResource(R.string.ini_chart_income_vs_expenses_desc),
                emptyText = stringResource(R.string.ini_no_data_period),
            )
        }
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
                else MaterialTheme.colorScheme.errorContainer,
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
private fun NetWorthFilterChip(
    selected: Boolean,
    label: String,
    amount: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null,
        label = {
            Column {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(amount, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun AutomaticMovementsCard(count: Int, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ini_automatic_count, count), style = MaterialTheme.typography.titleMedium)
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
    SectionCard(title = stringResource(R.string.ini_month)) {
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
    return sign + MoneyMath.format(amount, currency).removePrefix("-")
}
