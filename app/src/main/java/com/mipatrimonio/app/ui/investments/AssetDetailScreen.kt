package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.*
import com.mipatrimonio.app.ui.common.*
import com.mipatrimonio.app.ui.common.charts.ChartPoint
import com.mipatrimonio.app.ui.common.charts.LineChart
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.PillTabs
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AssetDetailScreen(
    portfolioId: String,
    assetId: String,
    viewModel: AssetDetailViewModel = appViewModel { c ->
        AssetDetailViewModel(portfolioId, assetId, c.investments, c.ledger, c.settings)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var operationDialog by rememberSaveable { mutableStateOf(false) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf<InvestmentOperation?>(null) }
    var editing by remember { mutableStateOf<InvestmentOperation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    when {
        state.isLoading -> LoadingBox()
        state.asset == null || state.portfolio == null -> EmptyState(Icons.Default.PriceChange, stringResource(R.string.inv_asset_not_found))
        else -> AssetDetailContent(
            state,
            { editing = null; operationDialog = true },
            { priceDialog = true },
            { editing = it; operationDialog = true },
            { delete = it },
            error,
        )
    }
    val asset = state.asset
    val portfolio = state.portfolio
    if (operationDialog && asset != null && portfolio != null) OperationDialog(
        listOf(portfolio), listOf(asset), state.accountsById.values.toList(), portfolio.id, asset.id,
        { operationDialog = false }, {}, {},
        { p, a, type, date, quantity, price, fees, account, note, result ->
            val existing = editing
            if (existing == null) viewModel.addOperation(p, a, type, date, quantity, price, fees, account, note, result)
            else viewModel.updateOperation(existing, p, a, type, date, quantity, price, fees, account, note, result)
        },
        existingOperation = editing,
    )
    if (priceDialog && asset != null) ManualPriceDialog(
        listOf(asset), asset.id, { priceDialog = false }, {},
        { selected, price, result -> viewModel.setManualPrice(selected, price, result) },
    )
    delete?.let { operation ->
        ConfirmDialog(
            stringResource(R.string.inv_delete_operation_title),
            stringResource(R.string.inv_delete_operation_message),
            { viewModel.deleteOperation(operation) { error = it; delete = null } },
            { delete = null },
        )
    }
}

@Composable
private fun AssetDetailContent(
    state: AssetDetailUiState,
    onNew: () -> Unit,
    onPrice: () -> Unit,
    onEdit: (InvestmentOperation) -> Unit,
    onDelete: (InvestmentOperation) -> Unit,
    error: String?,
) {
    val asset = state.asset ?: return
    val row = state.row
    val position = row?.valuation?.position
    val price = state.latestPrice
    var range by rememberSaveable { mutableIntStateOf(0) }
    val points = price?.let {
        listOf(ChartPoint(priceDate(it), MoneyMath.toMinor(it.price, asset.currency)))
    }.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(asset.name, style = MaterialTheme.typography.headlineLarge)
            Text(listOf(asset.isin, asset.market, asset.currency).filter { it.isNotBlank() }.joinToString(" · "))
            Spacer(Modifier.height(12.dp))
            if (price == null) {
                Text(stringResource(R.string.inv_without_price), style = MaterialTheme.typography.titleLarge)
                TextButton(onPrice, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.inv_enter_manual_price)) }
            } else {
                Text(detailMoney(MoneyMath.toMinor(price.price, asset.currency), asset.currency, state.hideAmounts), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.inv_manual_price_date, priceDateTime(price)))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            PillTabs(
                listOf(
                    stringResource(R.string.inv_range_month),
                    stringResource(R.string.inv_range_six_months),
                    stringResource(R.string.inv_range_year),
                    stringResource(R.string.inv_range_max),
                ),
                range,
                { range = it },
            )
            LineChart(
                if (points.size >= 2) points else emptyList(),
                { detailMoney(it, asset.currency, state.hideAmounts) },
                stringResource(R.string.inv_asset_chart_description),
                stringResource(R.string.inv_chart_not_enough_data),
            )
        }
        item {
            SectionCard(title = stringResource(R.string.inv_position_data)) {
                DataRow(stringResource(R.string.inv_quantity), position?.quantity?.let(MoneyMath::formatQuantity) ?: "—")
                DataRow(stringResource(R.string.inv_average_price_label), position?.averagePrice?.let { decimalMoney(it, asset.currency, state.hideAmounts) } ?: "—")
                DataRow(stringResource(R.string.inv_total_cost_label), position?.costBasis?.let { decimalMoney(it, asset.currency, state.hideAmounts) } ?: "—")
                DataRow(stringResource(R.string.inv_portfolio_value), row?.valueMinor?.let { detailMoney(it, asset.currency, state.hideAmounts) } ?: "—")
                DataRow(stringResource(R.string.inv_unrealized_short), row?.valuation?.unrealizedPnl?.let { decimalMoney(it, asset.currency, state.hideAmounts) } ?: "—")
                DataRow(stringResource(R.string.inv_simple_return_short), row?.valuation?.unrealizedReturnPct?.let { "$it %" } ?: "—")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inv_operations), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onNew, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.inv_new_operation)) }
            }
        }
        if (state.operations.isEmpty()) item { Text(stringResource(R.string.inv_empty_history)) }
        items(state.operations, key = { it.id }) { operation ->
            OperationRow(
                operation,
                operation.accountId?.let { state.accountsById[it]?.name },
                state.hideAmounts,
                onEdit,
                onDelete,
            )
        }
    }
}

@Composable
private fun OperationRow(
    operation: InvestmentOperation,
    account: String?,
    hidden: Boolean,
    onEdit: (InvestmentOperation) -> Unit,
    onDelete: (InvestmentOperation) -> Unit,
) {
    val gross = MoneyMath.toMinor(operation.quantity.multiply(operation.unitPrice), operation.currency)
    val signed = when (operation.type) {
        OperationType.COMPRA -> -Math.addExact(gross, operation.feesMinor)
        OperationType.VENTA, OperationType.DIVIDENDO -> Math.subtractExact(gross, operation.feesMinor)
        OperationType.COMISION -> -gross
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(operation.type.label(), style = MaterialTheme.typography.titleMedium)
                Text(formatDate(operation.date))
                Text(account ?: stringResource(R.string.inv_no_account), style = MaterialTheme.typography.bodySmall)
                Text((if (signed > 0) "+" else "") + detailMoney(signed, operation.currency, hidden))
            }
            IconButton({ onEdit(operation) }, Modifier.size(48.dp)) {
                Icon(Icons.Default.Edit, stringResource(R.string.inv_edit_operation))
            }
            IconButton({ onDelete(operation) }, Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, stringResource(R.string.inv_delete_operation))
            }
        }
    }
}

@Composable private fun DataRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
private fun detailMoney(value: Long, currency: String, hidden: Boolean) = if (hidden) "••••" else MoneyMath.format(value, currency)
private fun decimalMoney(value: BigDecimal, currency: String, hidden: Boolean) = if (hidden) "••••" else MoneyMath.format(MoneyMath.toMinor(value, currency), currency)
private fun priceDate(price: AssetPrice) = Instant.ofEpochMilli(price.asOfEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
private fun priceDateTime(price: AssetPrice) = Instant.ofEpochMilli(price.asOfEpochMillis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "ES")))
