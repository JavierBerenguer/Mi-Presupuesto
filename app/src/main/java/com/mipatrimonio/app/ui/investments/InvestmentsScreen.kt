package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.PositionRow
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import com.mipatrimonio.app.ui.common.label
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvestmentsScreen(
    viewModel: InvestmentsViewModel = appViewModel { c ->
        InvestmentsViewModel(c.ledger, c.investments, c.settings)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fabExpanded by remember { mutableStateOf(false) }
    var showPortfolioDialog by remember { mutableStateOf(false) }
    var showAssetDialog by remember { mutableStateOf(false) }
    var operationDialogOpen by remember { mutableStateOf(false) }
    var operationPortfolioId by remember { mutableStateOf<String?>(null) }
    var operationAssetId by remember { mutableStateOf<String?>(null) }
    var priceDialogOpen by remember { mutableStateOf(false) }
    var priceAssetId by remember { mutableStateOf<String?>(null) }
    var selectedPosition by remember { mutableStateOf<PositionRow?>(null) }
    var deleteCandidate by remember { mutableStateOf<InvestmentOperation?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingBox()
            state.portfolios.isEmpty() -> EmptyState(
                icon = Icons.Filled.ShowChart,
                message = stringResource(R.string.inv_empty_portfolios),
                actionLabel = stringResource(R.string.inv_new_portfolio),
                onAction = { showPortfolioDialog = true },
            )
            else -> InvestmentsContent(
                state = state,
                onPositionClick = {
                    detailError = null
                    selectedPosition = it
                },
            )
        }

        if (!state.isLoading) {
            InvestmentFabMenu(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onNewPortfolio = { showPortfolioDialog = true },
                onNewAsset = { showAssetDialog = true },
                onNewOperation = {
                    operationPortfolioId = null
                    operationAssetId = null
                    operationDialogOpen = true
                },
                onUpdatePrice = {
                    priceAssetId = null
                    priceDialogOpen = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (showPortfolioDialog) {
        PortfolioDialog(
            onDismiss = { showPortfolioDialog = false },
            onSave = { name, onResult -> viewModel.savePortfolio(name, onResult) },
        )
    }
    if (showAssetDialog) {
        AssetDialog(
            assets = state.assets,
            onDismiss = { showAssetDialog = false },
            onSave = { name, ticker, isin, type, market, currency, onResult ->
                viewModel.saveAsset(name, ticker, isin, type, market, currency, onResult)
            },
        )
    }
    if (operationDialogOpen) {
        key(operationPortfolioId, operationAssetId) {
            OperationDialog(
                portfolios = state.portfolios,
                assets = state.assets,
                initialPortfolioId = operationPortfolioId,
                initialAssetId = operationAssetId,
                onDismiss = { operationDialogOpen = false },
                onCreatePortfolio = {
                    operationDialogOpen = false
                    showPortfolioDialog = true
                },
                onCreateAsset = {
                    operationDialogOpen = false
                    showAssetDialog = true
                },
                onSave = { portfolio, asset, type, date, quantity, price, fees, note, onResult ->
                    viewModel.addOperation(
                        portfolio,
                        asset,
                        type,
                        date,
                        quantity,
                        price,
                        fees,
                        note,
                        onResult,
                    )
                },
            )
        }
    }
    if (priceDialogOpen) {
        key(priceAssetId) {
            ManualPriceDialog(
                assets = state.assets,
                initialAssetId = priceAssetId,
                onDismiss = { priceDialogOpen = false },
                onCreateAsset = {
                    priceDialogOpen = false
                    showAssetDialog = true
                },
                onSave = { asset, price, onResult -> viewModel.setManualPrice(asset, price, onResult) },
            )
        }
    }

    selectedPosition?.let { row ->
        PositionDetailDialog(
            row = row,
            operations = state.operationsByPosition[row.portfolio.id to row.asset.id].orEmpty(),
            error = detailError,
            onDismiss = { selectedPosition = null },
            onDelete = { deleteCandidate = it },
            onNewOperation = {
                selectedPosition = null
                operationPortfolioId = row.portfolio.id
                operationAssetId = row.asset.id
                operationDialogOpen = true
            },
            onUpdatePrice = {
                selectedPosition = null
                priceAssetId = row.asset.id
                priceDialogOpen = true
            },
        )
    }
    deleteCandidate?.let { operation ->
        ConfirmDialog(
            title = stringResource(R.string.inv_delete_operation_title),
            text = stringResource(R.string.inv_delete_operation_message),
            onConfirm = {
                viewModel.deleteOperation(operation) { error ->
                    if (error == null) {
                        deleteCandidate = null
                        detailError = null
                    } else {
                        deleteCandidate = null
                        detailError = error
                    }
                }
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun InvestmentsContent(state: InvestmentsUiState, onPositionClick: (PositionRow) -> Unit) {
    val snapshot = state.snapshot ?: return
    val summaryByPortfolio = state.summaries.associateBy { it.portfolio.id }
    val positionsByPortfolio = snapshot.openPositions.groupBy { it.portfolio.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InvestmentHeader(state)
        }
        items(state.portfolios, key = { it.id }) { portfolio ->
            PortfolioCard(
                portfolio = portfolio,
                summary = summaryByPortfolio[portfolio.id],
                positions = positionsByPortfolio[portfolio.id].orEmpty(),
                baseCurrency = snapshot.baseCurrency,
                onPositionClick = onPositionClick,
            )
        }
    }
}

@Composable
private fun InvestmentHeader(state: InvestmentsUiState) {
    val snapshot = state.snapshot ?: return
    SectionCard(title = stringResource(R.string.inv_summary_title)) {
        SummaryMoneyRow(
            label = stringResource(R.string.inv_total_value),
            minor = snapshot.netWorth.investmentsMinor,
            currency = snapshot.baseCurrency,
            emphasized = true,
        )
        SummaryMoneyRow(stringResource(R.string.inv_total_cost), state.totalCostMinor, snapshot.baseCurrency)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.inv_unrealized_gain))
            Column(horizontalAlignment = Alignment.End) {
                SignedMoneyText(state.totalUnrealizedMinor, snapshot.baseCurrency)
                Text(
                    state.totalUnrealizedPct?.let { signedPercentage(it) }
                        ?: stringResource(R.string.inv_percentage_unavailable),
                    color = amountColor(state.totalUnrealizedMinor),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            stringResource(R.string.inv_simple_return_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.unpricedAssets.isNotEmpty()) {
            Text(
                stringResource(R.string.inv_unpriced_warning, state.unpricedAssets.joinToString()),
                color = MoneyColors.warning,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.excludedCurrencies.isNotEmpty()) {
            Text(
                stringResource(R.string.inv_excluded_warning, state.excludedCurrencies.joinToString()),
                color = MoneyColors.warning,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryMoneyRow(label: String, minor: Long, currency: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
        MoneyText(
            minor = minor,
            currency = currency,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PortfolioCard(
    portfolio: Portfolio,
    summary: PortfolioSummary?,
    positions: List<PositionRow>,
    baseCurrency: String,
    onPositionClick: (PositionRow) -> Unit,
) {
    SectionCard(title = portfolio.name) {
        SummaryMoneyRow(stringResource(R.string.inv_portfolio_value), summary?.valueMinor ?: 0L, baseCurrency)
        SummaryMoneyRow(stringResource(R.string.inv_realized_gain), summary?.realizedMinor ?: 0L, baseCurrency)
        SummaryMoneyRow(stringResource(R.string.inv_net_dividends), summary?.dividendsNetMinor ?: 0L, baseCurrency)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.inv_open_positions), style = MaterialTheme.typography.labelLarge)
        if (positions.isEmpty()) {
            Text(
                stringResource(R.string.inv_empty_positions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            positions.forEachIndexed { index, position ->
                if (index > 0) HorizontalDivider()
                PositionItem(position, onClick = { onPositionClick(position) })
            }
        }
    }
}

@Composable
private fun PositionItem(row: PositionRow, onClick: () -> Unit) {
    val position = row.valuation.position
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.inv_asset_name_ticker, row.asset.name, row.asset.ticker),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.inv_quantity_value, MoneyMath.formatQuantity(position.quantity)),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(
                R.string.inv_average_price_value,
                position.averagePrice?.let { formatDecimalMoney(it, row.asset.currency) }
                    ?: stringResource(R.string.inv_not_available),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        val priceText = row.price?.let { price ->
            val age = priceAgeDays(price.asOfEpochMillis, System.currentTimeMillis())
            val ageText = if (age == 0L) {
                stringResource(R.string.inv_updated_today)
            } else {
                pluralStringResource(R.plurals.inv_updated_days, age.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), age)
            }
            stringResource(
                R.string.inv_current_price_value,
                formatDecimalMoney(price.price, row.asset.currency),
                ageText,
            )
        } ?: stringResource(R.string.inv_without_price)
        Text(priceText, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.inv_position_value))
            row.valueMinor?.let { MoneyText(it, row.asset.currency) }
                ?: Text(stringResource(R.string.inv_not_available))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.inv_unrealized_short))
            val unrealizedMinor = row.valuation.unrealizedPnl?.let { MoneyMath.toMinor(it, row.asset.currency) }
            if (unrealizedMinor == null) {
                Text(stringResource(R.string.inv_not_available))
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    SignedMoneyText(unrealizedMinor, row.asset.currency)
                    Text(
                        row.valuation.unrealizedReturnPct?.let { signedPercentage(it) }
                            ?: stringResource(R.string.inv_percentage_unavailable),
                        color = amountColor(unrealizedMinor),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionDetailDialog(
    row: PositionRow,
    operations: List<InvestmentOperation>,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: (InvestmentOperation) -> Unit,
    onNewOperation: () -> Unit,
    onUpdatePrice: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_history_title, row.asset.name, row.portfolio.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (operations.isEmpty()) {
                    Text(stringResource(R.string.inv_empty_history))
                } else {
                    LazyColumn(Modifier.height(300.dp)) {
                        items(operations, key = { it.id }) { operation ->
                            OperationHistoryItem(operation, onDelete)
                            HorizontalDivider()
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onNewOperation) { Text(stringResource(R.string.inv_new_operation)) }
                    TextButton(onClick = onUpdatePrice) { Text(stringResource(R.string.inv_update_price)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun OperationHistoryItem(operation: InvestmentOperation, onDelete: (InvestmentOperation) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(operation.type.label(), fontWeight = FontWeight.SemiBold)
            Text(formatDate(operation.date), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    R.string.inv_operation_amount,
                    MoneyMath.formatQuantity(operation.quantity),
                    formatDecimalMoney(operation.unitPrice, operation.currency),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (operation.feesMinor != 0L) {
                Text(
                    stringResource(R.string.inv_operation_fees, MoneyMath.format(operation.feesMinor, operation.currency)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (operation.note.isNotBlank()) Text(operation.note, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onDelete(operation) }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.inv_delete_operation))
        }
    }
}

@Composable
private fun InvestmentFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewPortfolio: () -> Unit,
    onNewAsset: () -> Unit,
    onNewOperation: () -> Unit,
    onUpdatePrice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            InvestmentMenuItem(Icons.Filled.AddBusiness, R.string.inv_new_portfolio) {
                onExpandedChange(false)
                onNewPortfolio()
            }
            InvestmentMenuItem(Icons.Filled.Paid, R.string.inv_new_asset) {
                onExpandedChange(false)
                onNewAsset()
            }
            InvestmentMenuItem(Icons.Filled.Edit, R.string.inv_new_operation) {
                onExpandedChange(false)
                onNewOperation()
            }
            InvestmentMenuItem(Icons.Filled.AttachMoney, R.string.inv_update_price) {
                onExpandedChange(false)
                onUpdatePrice()
            }
        }
        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(
                if (expanded) Icons.Filled.MoreVert else Icons.Filled.Add,
                contentDescription = stringResource(R.string.inv_actions),
            )
        }
    }
}

@Composable
private fun InvestmentMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun SignedMoneyText(minor: Long, currency: String) {
    Text(
        text = if (minor > 0) {
            stringResource(R.string.inv_positive_amount, MoneyMath.format(minor, currency))
        } else {
            MoneyMath.format(minor, currency)
        },
        color = amountColor(minor),
    )
}

private fun amountColor(minor: Long): Color = when {
    minor > 0 -> MoneyColors.positive
    minor < 0 -> MoneyColors.negative
    else -> Color.Unspecified
}

private fun formatDecimalMoney(value: BigDecimal, currency: String): String =
    MoneyMath.format(MoneyMath.toMinor(value, currency), currency)

private fun signedPercentage(value: BigDecimal): String {
    val formatter = (NumberFormat.getPercentInstance(Locale.forLanguageTag("es-ES")) as DecimalFormat).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
        positivePrefix = "+"
    }
    return formatter.format(value.movePointLeft(2))
}
