package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.List
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
import com.mipatrimonio.app.domain.model.OperationType
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
    onOpenAssets: () -> Unit = {},
    onOpenPortfolios: () -> Unit = {},
    viewModel: InvestmentsViewModel = appViewModel { c -> InvestmentsViewModel(c.ledger, c.investments, c.settings, quotes = c.quotes) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var operationType by remember { mutableStateOf<OperationType?>(null) }
    var operationPosition by remember { mutableStateOf<PositionRow?>(null) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    var transferDialog by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(2) }
    var refreshSummary by remember { mutableStateOf<com.mipatrimonio.app.data.quotes.QuoteRefreshSummary?>(null) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }

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
                    actionLabel = stringResource(R.string.inv_manage_portfolios),
                    onAction = onOpenPortfolios,
                )
                TextButton(onClick = onOpenAccounts, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.inv_new_investment_account))
                }
            }
            else -> PortfolioContent(
                state, section, range, { section = it }, { range = it }, viewModel::selectPortfolio,
                { priceDialog = true }, onOpenAssets, { onOpenAssetDetail(it.portfolio.id, it.asset.id) },
                { row, type -> operationPosition = row; operationType = type },
            )
        }
        if (!state.isLoading) Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton({ menu = true }, Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.inv_add_action))
            }
            DropdownMenu(menu, { menu = false }) {
                investmentMenuActions.forEach { action ->
                    val label = when (action) {
                        InvestmentMenuAction.REFRESH_PRICES -> R.string.inv_refresh_prices
                        InvestmentMenuAction.BUY -> R.string.inv_buy
                        InvestmentMenuAction.SELL -> R.string.inv_sell
                        InvestmentMenuAction.DIVIDEND -> R.string.inv_dividend
                        InvestmentMenuAction.FEE -> R.string.inv_fee
                        InvestmentMenuAction.TRANSFER -> R.string.inv_transfer
                    }
                    DropdownMenuItem({ Text(stringResource(label)) }, {
                        menu = false
                        when (action) {
                            InvestmentMenuAction.REFRESH_PRICES -> {
                                if (state.selectedPositions.none { it.asset.quoteProvider != null }) {
                                    refreshStatus = "missing"
                                } else viewModel.refreshPrices { summary ->
                                    refreshSummary = summary
                                    refreshStatus = if (summary == null) "error" else null
                                }
                            }
                            InvestmentMenuAction.BUY -> {
                                operationPosition = null
                                operationType = OperationType.COMPRA
                            }
                            InvestmentMenuAction.SELL -> {
                                operationPosition = null
                                operationType = OperationType.VENTA
                            }
                            InvestmentMenuAction.DIVIDEND -> operationType = OperationType.DIVIDENDO
                            InvestmentMenuAction.FEE -> operationType = OperationType.COMISION
                            InvestmentMenuAction.TRANSFER -> transferDialog = true
                        }
                    })
                }
            }
        }
        refreshStatus?.let { message ->
            val text = when (message) {
                "missing" -> stringResource(R.string.inv_refresh_no_providers)
                "error" -> stringResource(R.string.inv_refresh_error)
                else -> stringResource(R.string.inv_refresh_error)
            }
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(text) }
        }
    }
    refreshSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { refreshSummary = null },
            title = { Text(stringResource(R.string.inv_refresh_summary_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.providers.forEach { (provider, result) ->
                        val providerName = when (provider) {
                            com.mipatrimonio.app.domain.model.QuoteProvider.TWELVE_DATA -> stringResource(R.string.inv_provider_twelve_data)
                            com.mipatrimonio.app.domain.model.QuoteProvider.COINGECKO -> stringResource(R.string.inv_provider_coingecko)
                            com.mipatrimonio.app.domain.model.QuoteProvider.EODHD -> stringResource(R.string.inv_provider_eodhd)
                        }
                        Text(stringResource(R.string.inv_refresh_provider_result, providerName, result.updated, result.failed))
                        result.failures.forEach { (reason, count) ->
                            Text(stringResource(R.string.inv_refresh_failure_reason, count, quoteFailureLabel(reason)))
                        }
                    }
                    if (summary.skippedWithoutProvider > 0) {
                        Text(stringResource(R.string.inv_refresh_without_provider, summary.skippedWithoutProvider))
                    }
                }
            },
            confirmButton = {
                if (summary.skippedWithoutProvider > 0) TextButton({ refreshSummary = null; onOpenAssets() }) {
                    Text(stringResource(R.string.inv_configure_quotes))
                } else TextButton({ refreshSummary = null }) { Text(stringResource(R.string.common_accept)) }
            },
            dismissButton = if (summary.skippedWithoutProvider > 0) ({
                TextButton({ refreshSummary = null }) { Text(stringResource(R.string.common_close)) }
            }) else null,
        )
    }
    if (operationType != null) OperationDialog(
        operationPortfolios(state.portfolios), state.assets.filterNot { it.archived }, state.accounts,
        operationPosition?.portfolio?.id ?: state.selectedPortfolioId, operationPosition?.asset?.id,
        { operationType = null; operationPosition = null },
        { operationType = null; onOpenPortfolios() },
        { operationType = null; onOpenAssets() },
        { portfolio, asset, type, date, quantity, price, fees, account, note, result ->
            viewModel.addOperation(portfolio, asset, type, date, quantity, price, fees, account, note, result)
        },
        initialType = operationType ?: OperationType.COMPRA,
        availableQuantity = operationPosition?.valuation?.position?.quantity,
    )
    if (priceDialog) ManualPriceDialog(
        state.assets, state.selectedPositions.singleOrNull()?.asset?.id, { priceDialog = false },
        { priceDialog = false; onOpenAssets() },
        { asset, price, result -> viewModel.setManualPrice(asset, price, result) },
    )
    if (transferDialog) CryptoTransferDialog(
        state.portfolios, state.assets, state.operationsByPosition, state.selectedPortfolioId, null,
        { transferDialog = false },
        { source, destination, asset, quantity, fee, dateTime, result ->
            viewModel.saveTransfer(source, destination, asset, quantity, fee, dateTime, onResult = result)
        },
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
    onAssets: () -> Unit,
    onPosition: (PositionRow) -> Unit,
    onTrade: (PositionRow, OperationType) -> Unit,
) {
    val snapshot = state.snapshot ?: return
    val value = state.totalValueMinor
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
                IconButton(onAssets, Modifier.size(48.dp)) { Icon(Icons.Default.List, stringResource(R.string.inv_manage_assets)) }
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
            SectionCard(title = stringResource(R.string.inv_profitability_title)) {
                DataRow(stringResource(R.string.inv_total_cost_label), money(state.totalCostMinor, snapshot.baseCurrency, state.hideAmounts))
                DataRow(stringResource(R.string.inv_current_value_label), money(state.totalValueMinor, snapshot.baseCurrency, state.hideAmounts))
                PerformanceRow(
                    stringResource(R.string.inv_unrealized_gain),
                    signedMoney(state.totalUnrealizedMinor, snapshot.baseCurrency, state.hideAmounts),
                    state.totalUnrealizedPct,
                )
                PerformanceRow(
                    stringResource(R.string.inv_realized_gain),
                    signedMoney(state.totalRealizedMinor, snapshot.baseCurrency, state.hideAmounts),
                    state.totalRealizedPct,
                )
                DataRow(stringResource(R.string.inv_dividends_collected), money(state.totalDividendsNetMinor, snapshot.baseCurrency, state.hideAmounts))
                DataRow(stringResource(R.string.inv_total_fees), money(state.totalFeesMinor, snapshot.baseCurrency, state.hideAmounts))
            }
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
                items(state.selectedPositions, key = { "${it.portfolio.id}:${it.asset.id}" }) {
                    PositionCard(it, state.latestPrices[it.asset.id], state.hideAmounts, onPosition, onTrade)
                }
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
            operationPortfolios(state.portfolios).forEach { portfolio ->
                DropdownMenuItem({ Text(portfolio.name) }, { open = false; onSelect(portfolio.id) })
            }
        }
    }
}

@Composable
private fun PositionCard(
    row: PositionRow,
    price: com.mipatrimonio.app.domain.model.AssetPrice?,
    hidden: Boolean,
    onClick: (PositionRow) -> Unit,
    onTrade: (PositionRow, OperationType) -> Unit,
) {
    SectionCard(Modifier.clickable { onClick(row) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(row.asset.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.inv_shares, MoneyMath.formatQuantity(row.valuation.position.quantity)))
                price?.let {
                    val age = priceAge(it.asOfEpochMillis, System.currentTimeMillis())
                    val ageText = when (age.unit) {
                        PriceAgeUnit.MINUTES -> stringResource(R.string.inv_age_minutes, age.amount)
                        PriceAgeUnit.HOURS -> stringResource(R.string.inv_age_hours, age.amount)
                        PriceAgeUnit.DAYS -> stringResource(R.string.inv_age_days, age.amount)
                    }
                    val source = if (it.source == com.mipatrimonio.app.domain.model.PriceSource.MANUAL) {
                        stringResource(R.string.inv_price_manual)
                    } else when (row.asset.quoteProvider) {
                        com.mipatrimonio.app.domain.model.QuoteProvider.TWELVE_DATA -> stringResource(R.string.inv_provider_twelve_data)
                        com.mipatrimonio.app.domain.model.QuoteProvider.COINGECKO -> stringResource(R.string.inv_provider_coingecko)
                        com.mipatrimonio.app.domain.model.QuoteProvider.EODHD -> stringResource(R.string.inv_provider_eodhd)
                        null -> stringResource(R.string.inv_price_provider)
                    }
                    val quality = when (it.quality) {
                        com.mipatrimonio.app.domain.model.PriceQuality.RETRASADO -> stringResource(R.string.inv_price_delayed)
                        com.mipatrimonio.app.domain.model.PriceQuality.CIERRE -> stringResource(R.string.inv_price_close)
                        null -> stringResource(R.string.inv_price_without_quality)
                    }
                    Text(stringResource(R.string.inv_position_price_info, source, quality, positionPriceDateTime(it), ageText))
                    if (age.old) Text(stringResource(R.string.inv_price_old), color = MaterialTheme.colorScheme.error)
                }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton({ onTrade(row, OperationType.COMPRA) }) { Text(stringResource(R.string.inv_buy)) }
            TextButton({ onTrade(row, OperationType.VENTA) }) { Text(stringResource(R.string.inv_sell)) }
        }
    }
}

@Composable
private fun quoteFailureLabel(reason: com.mipatrimonio.app.data.quotes.QuoteFailure): String = stringResource(when (reason) {
    com.mipatrimonio.app.data.quotes.QuoteFailure.SIN_CLAVE -> R.string.inv_failure_no_key
    com.mipatrimonio.app.data.quotes.QuoteFailure.CLAVE_INVALIDA -> R.string.inv_failure_invalid_key
    com.mipatrimonio.app.data.quotes.QuoteFailure.LIMITE_ALCANZADO -> R.string.inv_failure_limit
    com.mipatrimonio.app.data.quotes.QuoteFailure.NO_ENCONTRADO -> R.string.inv_failure_not_found
    com.mipatrimonio.app.data.quotes.QuoteFailure.SIN_CONEXION -> R.string.inv_failure_offline
    com.mipatrimonio.app.data.quotes.QuoteFailure.RESPUESTA_INVALIDA -> R.string.inv_failure_invalid_response
    com.mipatrimonio.app.data.quotes.QuoteFailure.DIVISA_DISTINTA -> R.string.inv_failure_currency
})

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
@Composable private fun DataRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) { Text(label); Text(value) }
@Composable private fun PerformanceRow(label: String, value: String, percentage: BigDecimal?) = DataRow(
    label,
    stringResource(
        R.string.inv_amount_percentage,
        value,
        percentage?.let(::signedPercentage) ?: stringResource(R.string.inv_percentage_unavailable),
    ),
)
@Composable private fun Warning(text: String) = Text(text, color = MoneyColors.warning, style = MaterialTheme.typography.bodySmall)
private fun money(value: Long, currency: String, hidden: Boolean) = if (hidden) "••••" else MoneyMath.format(value, currency)
private fun signedMoney(value: Long, currency: String, hidden: Boolean) = if (hidden) "••••" else (if (value > 0) "+" else "") + MoneyMath.format(value, currency)
private fun signedPercentage(value: BigDecimal): String {
    val formatter = NumberFormat.getNumberInstance(Locale("es", "ES")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
    return (if (value.signum() > 0) "+" else "") + formatter.format(value) + " %"
}
private fun positionPriceDateTime(price: com.mipatrimonio.app.domain.model.AssetPrice) =
    Instant.ofEpochMilli(price.asOfEpochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "ES")))
@Composable private fun signColor(sign: Int): Color = when { sign > 0 -> MoneyColors.positive; sign < 0 -> MoneyColors.negative; else -> MaterialTheme.colorScheme.onSurfaceVariant }
