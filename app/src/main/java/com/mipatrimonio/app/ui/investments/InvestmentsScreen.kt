package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.usecase.PortfolioValueSeries
import com.mipatrimonio.app.domain.usecase.PositionRow
import com.mipatrimonio.app.ui.common.*
import com.mipatrimonio.app.ui.common.charts.ChartPoint
import com.mipatrimonio.app.ui.common.charts.LineChart
import com.mipatrimonio.app.ui.components.PillTabs
import com.mipatrimonio.app.ui.components.ProgressBar
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.SegmentedControl
import com.mipatrimonio.app.ui.theme.LargeAmountStyle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvestmentsScreen(
    onOpenAssetDetail: (String, String) -> Unit = { _, _ -> },
    onOpenAccounts: () -> Unit = {},
    initialNewPortfolio: Boolean = false,
    viewModel: InvestmentsViewModel = appViewModel { c -> InvestmentsViewModel(c.ledger, c.investments, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var portfolioDialog by rememberSaveable { mutableStateOf(initialNewPortfolio) }
    var assetDialog by rememberSaveable { mutableStateOf(false) }
    var operationDialog by rememberSaveable { mutableStateOf(false) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(2) }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            state.isLoading -> LoadingBox()
            state.portfolios.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.ShowChart,
                    message = stringResource(R.string.inv_empty_portfolios_explanation),
                    actionLabel = stringResource(R.string.inv_new_portfolio),
                    onAction = { portfolioDialog = true },
                )
                TextButton(onClick = onOpenAccounts, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.inv_new_investment_account))
                }
            }
            else -> PortfolioContent(
                state, section, range, { section = it }, { range = it }, viewModel::selectPortfolio,
                { priceDialog = true }, { onOpenAssetDetail(it.portfolio.id, it.asset.id) },
            )
        }
        if (!state.isLoading) Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton({ menu = true }, Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.inv_add_action))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.inv_new_operation)) }, { menu = false; operationDialog = true })
                DropdownMenuItem({ Text(stringResource(R.string.inv_new_asset)) }, { menu = false; assetDialog = true })
                DropdownMenuItem({ Text(stringResource(R.string.inv_new_portfolio)) }, { menu = false; portfolioDialog = true })
            }
        }
    }
    if (portfolioDialog) PortfolioDialog(
        state.accounts.filterNot { it.archived }, { portfolioDialog = false },
        { name, account, result -> viewModel.savePortfolio(name, account, result) },
    )
    if (assetDialog) AssetDialog(
        state.assets, { assetDialog = false },
        { name, ticker, isin, type, market, currency, result ->
            viewModel.saveAsset(name, ticker, isin, type, market, currency, result)
        },
    )
    if (operationDialog) OperationDialog(
        state.portfolios, state.assets, state.accounts, state.selectedPortfolioId, null,
        { operationDialog = false },
        { operationDialog = false; portfolioDialog = true },
        { operationDialog = false; assetDialog = true },
        { portfolio, asset, type, date, quantity, price, fees, account, note, result ->
            viewModel.addOperation(portfolio, asset, type, date, quantity, price, fees, account, note, result)
        },
    )
    if (priceDialog) ManualPriceDialog(
        state.assets, state.selectedPositions.singleOrNull()?.asset?.id, { priceDialog = false },
        { priceDialog = false; assetDialog = true },
        { asset, price, result -> viewModel.setManualPrice(asset, price, result) },
    )
}

@Composable
private fun PortfolioContent(
    state: InvestmentsUiState,
    section: Int,
    rangeIndex: Int,
    onSection: (Int) -> Unit,
    onRange: (Int) -> Unit,
    onSelect: (String?) -> Unit,
    onPrice: () -> Unit,
    onPosition: (PositionRow) -> Unit,
) {
    val snapshot = state.snapshot ?: return
    val summary = state.selectedPortfolioId?.let { id -> state.summaries.firstOrNull { it.portfolio.id == id } }
    val value = summary?.valueMinor ?: state.summaries.sumOf { it.valueMinor }
    val cost = state.selectedPositions.filter { it.asset.currency == snapshot.baseCurrency }.fold(0L) { sum, row ->
        Math.addExact(sum, MoneyMath.toMinor(row.valuation.position.costBasis, snapshot.baseCurrency))
    }
    val pct = if (cost == 0L) null else BigDecimal(state.totalReturnMinor).multiply(BigDecimal(100))
        .divide(BigDecimal(cost), 2, RoundingMode.HALF_EVEN)
    val range = PortfolioRange.entries[rangeIndex.coerceIn(PortfolioRange.entries.indices)]
    val chart = chartPoints(state, range)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_cartera), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onPrice, Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, stringResource(R.string.inv_update_price)) }
            }
            PortfolioSelector(state, onSelect)
        }
        item {
            Text(stringResource(R.string.inv_portfolio_value), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(value, snapshot.baseCurrency, state.hideAmounts), style = LargeAmountStyle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(stringResource(R.string.inv_today), stringResource(R.string.inv_not_available))
                Metric(
                    stringResource(R.string.inv_total_return),
                    signedMoney(state.totalReturnMinor, snapshot.baseCurrency, state.hideAmounts),
                    pct?.let(::signedPercentage),
                )
            }
            Text(stringResource(R.string.inv_simple_return_cost), style = MaterialTheme.typography.bodySmall)
            if (state.unpricedAssets.isNotEmpty()) Warning(stringResource(R.string.inv_unpriced_warning, state.unpricedAssets.joinToString()))
            if (state.excludedCurrencies.isNotEmpty()) Warning(stringResource(R.string.inv_excluded_warning, state.excludedCurrencies.joinToString()))
        }
        item {
            PillTabs(
                listOf(
                    stringResource(R.string.inv_range_day),
                    stringResource(R.string.inv_range_week),
                    stringResource(R.string.inv_range_month),
                    stringResource(R.string.inv_range_year),
                    stringResource(R.string.inv_range_max),
                ),
                rangeIndex,
                onRange,
            )
            Spacer(Modifier.height(8.dp))
            LineChart(
                if (chart.size >= 2) chart else emptyList(),
                { money(it, snapshot.baseCurrency, state.hideAmounts) },
                stringResource(R.string.inv_chart_description),
                stringResource(R.string.inv_chart_not_enough_data),
            )
        }
        item {
            SegmentedControl(
                listOf(
                    stringResource(R.string.inv_tab_positions),
                    stringResource(R.string.inv_tab_distribution),
                    stringResource(R.string.inv_tab_dividends),
                ),
                section,
                onSection,
            )
        }
        when (PortfolioSection.entries[section.coerceIn(PortfolioSection.entries.indices)]) {
            PortfolioSection.POSICIONES -> {
                if (state.selectedPositions.isEmpty()) item { Text(stringResource(R.string.inv_empty_positions)) }
                items(state.selectedPositions, key = { "${it.portfolio.id}:${it.asset.id}" }) { PositionCard(it, state.hideAmounts, onPosition) }
            }
            PortfolioSection.DISTRIBUCION -> {
                item { AllocationGroup(stringResource(R.string.inv_distribution_type), state.allocationsByType, state.hideAmounts) }
                item { AllocationGroup(stringResource(R.string.inv_distribution_portfolio), state.allocationsByPortfolio, state.hideAmounts) }
                item { AllocationGroup(stringResource(R.string.inv_distribution_currency), state.allocationsByCurrency, state.hideAmounts) }
            }
            PortfolioSection.DIVIDENDOS -> dividends(state)
        }
    }
}

@Composable
private fun PortfolioSelector(state: InvestmentsUiState, onSelect: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val name = state.portfolios.firstOrNull { it.id == state.selectedPortfolioId }?.name
        ?: stringResource(R.string.inv_aggregate_all)
    Box {
        TextButton({ open = true }, Modifier.heightIn(min = 48.dp)) { Text(name) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.inv_aggregate_all)) }, { open = false; onSelect(null) })
            state.portfolios.forEach { portfolio ->
                DropdownMenuItem({ Text(portfolio.name) }, { open = false; onSelect(portfolio.id) })
            }
        }
    }
}

@Composable
private fun PositionCard(row: PositionRow, hidden: Boolean, onClick: (PositionRow) -> Unit) {
    SectionCard(Modifier.clickable { onClick(row) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(row.asset.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.inv_shares, MoneyMath.formatQuantity(row.valuation.position.quantity)))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.valueMinor?.let { money(it, row.asset.currency, hidden) } ?: stringResource(R.string.inv_without_price))
                val percentage = row.valuation.unrealizedReturnPct
                Text(
                    percentage?.let(::signedPercentage) ?: stringResource(R.string.inv_percentage_unavailable),
                    color = signColor(percentage?.signum() ?: 0),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AllocationGroup(title: String, values: List<AllocationItem>, hidden: Boolean) {
    SectionCard(title = title) {
        if (values.isEmpty()) Text(stringResource(R.string.inv_no_distribution))
        val total = values.sumOf { it.valueMinor }
        values.forEach { item ->
            val assetType = AssetType.entries.firstOrNull { it.name == item.label }
            val label = if (assetType != null) assetType.label() else item.label
            val fraction = if (total == 0L) 0f else BigDecimal(item.valueMinor).divide(BigDecimal(total), 4, RoundingMode.HALF_EVEN).toFloat()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label)
                Text(if (hidden) stringResource(R.string.common_hidden_amount) else "${(fraction * 100).toInt()} %")
            }
            ProgressBar(fraction)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dividends(state: InvestmentsUiState) {
    val currency = state.snapshot?.baseCurrency ?: return
    val year = LocalDate.now().year
    item {
        SectionCard(title = stringResource(R.string.inv_dividends_year, year)) {
            Text(money(state.dividends.filter { it.date.year == year }.sumOf { it.netMinor }, currency, state.hideAmounts))
        }
    }
    if (state.dividends.isEmpty()) item { Text(stringResource(R.string.inv_no_dividends)) }
    items(state.dividends) { dividend ->
        SectionCard(title = dividend.assetName) {
            Text(dividend.date.toString())
            Text(stringResource(R.string.inv_dividend_gross, money(dividend.grossMinor, currency, state.hideAmounts)))
            Text(stringResource(R.string.inv_dividend_withholding, money(dividend.withholdingMinor, currency, state.hideAmounts)))
            Text(stringResource(R.string.inv_dividend_net, money(dividend.netMinor, currency, state.hideAmounts)))
        }
    }
}

private fun chartPoints(state: InvestmentsUiState, range: PortfolioRange): List<ChartPoint> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val start = rangeStart(range, today)
    val ids = state.selectedPositions.mapTo(hashSetOf()) { it.asset.id }
    val prices = state.latestPrices.values.filter { it.assetId in ids }
    val dates = (state.selectedOperations.map { it.date } + prices.map {
        Instant.ofEpochMilli(it.asOfEpochMillis).atZone(zone).toLocalDate()
    } + today).distinct().filter { start == null || it >= start }
    val currency = state.snapshot?.baseCurrency ?: return emptyList()
    return PortfolioValueSeries.calculate(dates, state.selectedOperations, prices, zone).map {
        ChartPoint(it.date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))), MoneyMath.toMinor(it.value, currency))
    }
}

@Composable private fun Metric(label: String, value: String, detail: String? = null) = Column {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.titleMedium)
    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
@Composable private fun Warning(text: String) = Text(text, color = MoneyColors.warning, style = MaterialTheme.typography.bodySmall)
private fun money(value: Long, currency: String, hidden: Boolean) = if (hidden) "••••" else MoneyMath.format(value, currency)
private fun signedMoney(value: Long, currency: String, hidden: Boolean) = if (hidden) "••••" else (if (value > 0) "+" else "") + MoneyMath.format(value, currency)
private fun signedPercentage(value: BigDecimal): String {
    val formatter = NumberFormat.getNumberInstance(Locale("es", "ES")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
    return (if (value.signum() > 0) "+" else "") + formatter.format(value) + " %"
}
@Composable private fun signColor(sign: Int): Color = when { sign > 0 -> MoneyColors.positive; sign < 0 -> MoneyColors.negative; else -> MaterialTheme.colorScheme.onSurfaceVariant }
