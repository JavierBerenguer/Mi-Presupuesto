package com.mipatrimonio.app.ui.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.sync.PortfolioProduct
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.charts.DonutChart
import com.mipatrimonio.app.ui.common.charts.DonutSlice
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onConnect: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenRumboPanel: () -> Unit,
    viewModel: PortfolioViewModel = appViewModel { c -> PortfolioViewModel(SyncPortfolioGateway(c.sync), c.ledger) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var buying by remember { mutableStateOf(false) }
    if (state.loading) { LoadingBox(); return }

    PullToRefreshBox(isRefreshing = state.syncing, onRefresh = viewModel::refresh) {
        when (state.status) {
            PortfolioStatus.NO_SESSION -> EmptyState(
                Icons.Filled.ShowChart, stringResource(R.string.cartera_no_session),
                actionLabel = stringResource(R.string.cartera_connect), onAction = onConnect,
            )
            PortfolioStatus.NO_SUMMARY -> EmptyState(
                Icons.Filled.ShowChart, stringResource(R.string.cartera_no_summary),
                actionLabel = stringResource(R.string.rumbo_panel_open), onAction = onOpenRumboPanel,
            )
            PortfolioStatus.ERROR -> EmptyState(
                Icons.Filled.ShowChart, state.error ?: stringResource(R.string.cartera_summary_error),
                actionLabel = stringResource(R.string.rumbo_panel_open), onAction = onOpenRumboPanel,
            )
            PortfolioStatus.READY -> state.summary?.let { summary ->
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.cartera_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.cartera_sync_now)) }
                            }
                            Text(stringResource(R.string.cartera_updated, summary.generated), style = MaterialTheme.typography.bodySmall)
                        }
                        item { PortfolioHeader(summary) }
                        item {
                            Button(onClick = onOpenRumboPanel, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.rumbo_panel_open))
                            }
                        }
                        item {
                            SectionCard(title = stringResource(R.string.cartera_distribution)) {
                                DonutChart(
                                    slices = summary.classes.mapIndexed { index, value ->
                                        DonutSlice(value.name, value.valueMinor, PORTFOLIO_COLORS[index % PORTFOLIO_COLORS.size])
                                    },
                                    centerLabel = MoneyMath.format(summary.total.valueMinor, summary.currency),
                                    formatValue = { MoneyMath.format(it, summary.currency) },
                                    description = stringResource(R.string.cartera_distribution_description),
                                    emptyText = stringResource(R.string.cartera_no_products),
                                )
                            }
                        }
                        item { Text(stringResource(R.string.cartera_products), style = MaterialTheme.typography.titleLarge) }
                        items(summary.products, key = PortfolioProduct::id) { product ->
                            ProductRow(product, summary.currency) { onOpenProduct(product.id) }
                        }
                        if (summary.warnings.isNotEmpty()) item {
                            SectionCard(title = stringResource(R.string.cartera_warnings)) {
                                summary.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { buying = true },
                        icon = { Icon(Icons.Filled.Add, stringResource(R.string.cartera_register_purchase)) },
                        text = { Text(stringResource(R.string.cartera_register_purchase)) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                }
            }
        }
    }
    if (buying) PurchaseDialog(state.products, state.accounts, state.error, onDismiss = {
        buying = false; viewModel.clearError()
    }, onSave = { product, date, amount, units, commission, account, note ->
        viewModel.savePurchase(product, date, amount, units, commission, account, note) { buying = false }
    })
}

@Composable
private fun PortfolioHeader(summary: com.mipatrimonio.app.data.sync.PortfolioSummary) {
    SectionCard(title = stringResource(R.string.cartera_value)) {
        MoneyText(summary.total.valueMinor, summary.currency, style = MaterialTheme.typography.headlineLarge)
        MetricRow(stringResource(R.string.cartera_gain), MoneyMath.format(summary.total.gainMinor, summary.currency) + " · " + percent(summary.total.returnRate))
        MetricRow(stringResource(R.string.cartera_irr), summary.total.irr?.let(::percent) ?: stringResource(R.string.cartera_not_available))
        MetricRow(stringResource(R.string.cartera_realized), MoneyMath.format(summary.total.realizedMinor, summary.currency))
    }
}

@Composable private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
}

@Composable
private fun ProductRow(product: PortfolioProduct, currency: String, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(product.name, style = MaterialTheme.typography.titleMedium); Text(product.type, style = MaterialTheme.typography.bodySmall) }
            Column(horizontalAlignment = Alignment.End) { MoneyText(product.valueMinor, currency); Text(percent(product.weight), style = MaterialTheme.typography.bodySmall) }
        }
        Text(stringResource(R.string.cartera_product_gain, MoneyMath.format(product.gainMinor, currency), percent(product.returnRate)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PurchaseDialog(
    products: List<SyncProductoEntity>, accounts: List<Account>, error: String?,
    onDismiss: () -> Unit,
    onSave: (String?, LocalDate, String, String, String, String?, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var product by remember { mutableStateOf<SyncProductoEntity?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("") }
    var commission by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<Account?>(null) }
    var note by remember { mutableStateOf("") }
    val filtered = products.filter { it.nombre.contains(query, ignoreCase = true) || it.corto.orEmpty().contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cartera_purchase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (products.isEmpty()) Text(stringResource(R.string.cartera_no_products_help)) else {
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.cartera_search_product)) }, singleLine = true)
                    DropdownField(stringResource(R.string.cartera_product), filtered, product, { it.nombre }, { product = it })
                }
                DateField(stringResource(R.string.cartera_date), date, { date = it })
                AmountField(stringResource(R.string.cartera_amount), amount, { amount = it }, suffix = "EUR")
                AmountField(stringResource(R.string.cartera_units_optional), units, { units = it })
                AmountField(stringResource(R.string.cartera_commission_optional), commission, { commission = it }, suffix = "EUR")
                DropdownField(
                    stringResource(R.string.cartera_liquidity_account), accounts.filter { it.currency == "EUR" && !it.archived && it.useInApp }, account,
                    { it.name }, { account = it }, noneLabel = stringResource(R.string.cartera_no_account),
                )
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.cartera_note)) }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = products.isNotEmpty(), onClick = { onSave(product?.id, date, amount, units, commission, account?.id, note) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun percent(value: BigDecimal): String = value.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " %"
private val PORTFOLIO_COLORS = listOf(Color(0xFF4F86F7), Color(0xFF2EBD85), Color(0xFFE6A23C), Color(0xFF9B72CF), Color(0xFFE5645A))
