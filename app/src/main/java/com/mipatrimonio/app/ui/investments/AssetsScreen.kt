package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.SegmentedControl

@Composable
fun AssetsScreen(viewModel: AssetsViewModel = appViewModel { c -> AssetsViewModel(c.investments, c.settings) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AssetFilter.CON_POSICION) }
    var editing by remember { mutableStateOf<Asset?>(null) }
    var deleting by remember { mutableStateOf<Asset?>(null) }
    if (state.isLoading) return LoadingBox()
    val visible = filterAssets(state.assets, query, filter)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            query, { query = it },
            label = { Text(stringResource(R.string.inv_assets_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        SegmentedControl(
            listOf(stringResource(R.string.inv_assets_with_position), stringResource(R.string.inv_assets_without_position), stringResource(R.string.inv_assets_archived)),
            filter.ordinal, { filter = AssetFilter.entries[it] }, Modifier.padding(horizontal = 16.dp),
        )
        if (visible.isEmpty()) {
            EmptyState(Icons.Default.Search, stringResource(if (filter == AssetFilter.ARCHIVADOS) R.string.inv_assets_archive_pending else R.string.inv_assets_empty))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.asset.id }) { item ->
                    AssetRow(item, state.hideAmounts) {
                        viewModel.clearError()
                        viewModel.inspect(item.asset.id)
                        editing = item.asset
                    }
                }
            }
        }
    }
    editing?.let { asset ->
        val deps = state.dependencies[asset.id]
        AssetDialog(
            assets = state.assets.map { it.asset },
            onDismiss = { editing = null },
            onSave = { name, ticker, isin, type, market, currency, result ->
                viewModel.save(asset, name, ticker, isin, type, market, currency) { result(null); editing = null }
            },
            existingAsset = asset,
            hasOperations = deps?.operations?.let { it > 0 } ?: true,
            externalError = state.error,
            onDelete = { deleting = asset },
        )
    }
    deleting?.let { asset ->
        val deps = state.dependencies[asset.id]
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.inv_delete_asset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        deps == null -> Text(stringResource(R.string.inv_assets_checking))
                        deps.operations > 0 -> {
                            Text(stringResource(R.string.inv_delete_asset_blocked, deps.operations))
                            Text(stringResource(R.string.inv_assets_archive_pending))
                        }
                        else -> {
                            Text(stringResource(R.string.inv_delete_asset_message, asset.name))
                            if (deps.manualPrices > 0) Text(stringResource(R.string.inv_delete_asset_prices, deps.manualPrices))
                        }
                    }
                }
            },
            confirmButton = {
                if (deps?.canDelete == true) TextButton({ viewModel.delete(asset) { deleting = null; editing = null } }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun AssetRow(item: ManagedAsset, hidden: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.asset.name, style = MaterialTheme.typography.titleMedium)
                val unavailable = stringResource(R.string.inv_not_available)
                Text(stringResource(R.string.inv_assets_identity, item.asset.ticker, item.asset.isin.ifBlank { unavailable }))
                Text(stringResource(R.string.inv_assets_market, item.asset.type.label(), item.asset.market.ifBlank { unavailable }, item.asset.currency))
                Text(stringResource(R.string.inv_quantity_value, MoneyMath.formatQuantity(item.quantity)))
                item.valueMinor?.let { Text(stringResource(R.string.inv_assets_value, if (hidden) stringResource(R.string.common_hidden_amount) else MoneyMath.format(it, item.asset.currency))) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.inv_assets_edit_description, item.asset.name))
        }
    }
}
