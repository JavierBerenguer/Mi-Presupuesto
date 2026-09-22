package com.mipatrimonio.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.NetWorthNotices
import com.mipatrimonio.app.ui.common.PeriodSelector
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.assetColor
import com.mipatrimonio.app.ui.common.charts.BarGroup
import com.mipatrimonio.app.ui.common.charts.ChartPoint
import com.mipatrimonio.app.ui.common.charts.DonutChart
import com.mipatrimonio.app.ui.common.charts.DonutSlice
import com.mipatrimonio.app.ui.common.charts.GroupedBarChart
import com.mipatrimonio.app.ui.common.charts.LineChart
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.common.monthLabel
import com.mipatrimonio.app.ui.common.pointLabel

private val NO_CATEGORY_COLOR = Color(0xFF8D99AE)

@Composable
fun HomeScreen(
    onOpenAccounts: () -> Unit,
    viewModel: HomeViewModel = appViewModel { c -> HomeViewModel(c.ledger, c.investments, c.settings) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val s = state
    if (s == null) {
        LoadingBox()
        return
    }
    if (!s.hasAccounts) {
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            message = stringResource(R.string.ini_welcome_empty),
            actionLabel = stringResource(R.string.ini_create_account),
            onAction = onOpenAccounts,
        )
        return
    }
    val currency = s.baseCurrency
    val format: (Long) -> String = { MoneyMath.format(it, currency) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.ini_networth)) {
            Text(format(s.netWorth.totalMinor), style = MaterialTheme.typography.headlineLarge)
            NetWorthNotices(s.netWorth)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(Modifier.weight(1f), title = stringResource(R.string.ini_available)) {
                MoneyText(s.netWorth.cashMinor, currency, style = MaterialTheme.typography.titleLarge)
            }
            SectionCard(Modifier.weight(1f), title = stringResource(R.string.ini_investments)) {
                MoneyText(s.netWorth.investmentsMinor, currency, style = MaterialTheme.typography.titleLarge)
            }
        }
        SectionCard(title = stringResource(R.string.ini_month)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledAmount(stringResource(R.string.ini_income), s.monthTotals.incomeMinor, currency, MoneyColors.positive)
                LabeledAmount(stringResource(R.string.ini_expenses), s.monthTotals.expenseMinor, currency, MoneyColors.negative)
                LabeledAmount(
                    stringResource(R.string.ini_balance),
                    s.monthTotals.balanceMinor,
                    currency,
                    if (s.monthTotals.balanceMinor >= 0) MoneyColors.positive else MoneyColors.negative,
                )
            }
            if (s.monthTotals.excludedCount > 0) {
                Text(
                    stringResource(R.string.ini_month_excluded, s.monthTotals.excludedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MoneyColors.warning,
                )
            }
        }
        SectionCard(title = stringResource(R.string.ini_budget_remaining)) {
            val remaining = s.budgetRemaining
            if (remaining == null) {
                Text(stringResource(R.string.ini_no_budgets))
            } else {
                MoneyText(remaining.remainingMinor, remaining.currency, colored = true, style = MaterialTheme.typography.titleLarge)
            }
        }

        PeriodSelector(s.period, viewModel::selectPeriod)

        SectionCard(title = stringResource(R.string.ini_chart_networth)) {
            LineChart(
                points = s.netWorthSeries.map { ChartPoint(pointLabel(it.date, s.period), it.totalMinor) },
                formatValue = format,
                description = stringResource(R.string.ini_chart_networth_desc),
                emptyText = stringResource(R.string.ini_no_data_period),
            )
            Text(
                stringResource(R.string.ini_chart_networth_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

private fun Category?.colorOrDefault(): Color = this?.let { Color(it.colorArgb) } ?: NO_CATEGORY_COLOR

@Composable
private fun LabeledAmount(label: String, minor: Long, currency: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(MoneyMath.format(minor, currency), color = color, style = MaterialTheme.typography.titleMedium)
    }
}
