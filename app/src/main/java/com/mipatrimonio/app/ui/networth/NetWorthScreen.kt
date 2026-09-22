package com.mipatrimonio.app.ui.networth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.usecase.assetDistribution
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.NetWorthNotices
import com.mipatrimonio.app.ui.common.PeriodSelector
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.assetColor
import com.mipatrimonio.app.ui.common.charts.ChartPoint
import com.mipatrimonio.app.ui.common.charts.DonutChart
import com.mipatrimonio.app.ui.common.charts.DonutSlice
import com.mipatrimonio.app.ui.common.charts.LineChart
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.common.pointLabel

@Composable
fun NetWorthScreen(
    viewModel: NetWorthViewModel = appViewModel { c -> NetWorthViewModel(c.ledger, c.investments, c.settings) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state
    if (s == null) {
        LoadingBox()
        return
    }
    val snapshot = s.snapshot
    val netWorth = snapshot.netWorth
    val currency = snapshot.baseCurrency
    if (snapshot.balances.none { !it.account.archived } && snapshot.positions.isEmpty()) {
        EmptyState(Icons.Outlined.AccountBalance, stringResource(R.string.pat_empty))
        return
    }
    val format: (Long) -> String = { MoneyMath.format(it, currency) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.pat_current)) {
            Text(format(netWorth.totalMinor), style = MaterialTheme.typography.headlineLarge)
            NetWorthNotices(netWorth)
        }
        SectionCard(title = stringResource(R.string.pat_breakdown)) {
            AmountRow(stringResource(R.string.pat_cash), format(netWorth.cashMinor))
            AmountRow(stringResource(R.string.pat_investments), format(netWorth.investmentsMinor))
            AmountRow(stringResource(R.string.pat_liabilities), stringResource(R.string.pat_liabilities_none))
            Text(
                stringResource(R.string.pat_liabilities_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PeriodSelector(s.period, viewModel::selectPeriod)
        SectionCard(title = stringResource(R.string.pat_evolution)) {
            LineChart(
                points = s.series.map { ChartPoint(pointLabel(it.date, s.period), it.totalMinor) },
                formatValue = format,
                description = stringResource(R.string.pat_evolution_desc),
                emptyText = stringResource(R.string.pat_no_data),
            )
            Text(
                stringResource(R.string.pat_evolution_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.pat_by_type)) {
            val shares = assetDistribution(netWorth)
            DonutChart(
                slices = shares.map { share ->
                    DonutSlice(
                        share.type?.label() ?: stringResource(R.string.pat_investments_label),
                        share.valueMinor,
                        assetColor(share.type),
                    )
                },
                centerLabel = format(shares.sumOf { it.valueMinor }),
                formatValue = format,
                description = stringResource(R.string.pat_by_type_desc),
                emptyText = stringResource(R.string.pat_no_data),
            )
        }

        SectionCard(title = stringResource(R.string.pat_by_currency)) {
            byCurrency(snapshot.balances, snapshot.positions, currency).forEach { total ->
                Column {
                    AmountRow(total.currency, MoneyMath.format(total.minor, total.currency))
                    if (total.currency != currency) {
                        Text(
                            stringResource(R.string.pat_not_included),
                            style = MaterialTheme.typography.bodySmall,
                            color = MoneyColors.warning,
                        )
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.pat_by_account)) {
            val shares = accountShares(snapshot.balances, currency)
            if (shares.isEmpty()) Text(stringResource(R.string.pat_no_accounts))
            shares.forEach { share ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(share.account.name)
                        Text(
                            share.account.type.label(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        MoneyText(share.balanceMinor, share.account.currency, colored = true)
                        share.percent?.let {
                            Text(stringResource(R.string.pat_percent, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.pat_by_portfolio)) {
            val values = portfolioValues(snapshot.positions, currency)
            if (values.isEmpty()) Text(stringResource(R.string.pat_no_portfolios))
            values.forEach { value ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(value.portfolio.name)
                        if (value.unpricedCount > 0) {
                            Text(
                                stringResource(R.string.pat_unpriced_count, value.unpricedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MoneyColors.warning,
                            )
                        }
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(format(value.valueMinor))
                        value.percent?.let {
                            Text(stringResource(R.string.pat_percent, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
