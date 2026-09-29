package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
fun AssetsScreen(
    initialNewAsset: Boolean = false,
    viewModel: AssetsViewModel = appViewModel { c ->
        AssetsViewModel(c.investments, c.settings, c.eodhdSearch, c.coinGeckoSearch, c.quoteSecrets)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AssetFilter.CON_POSICION) }
    var editing by remember { mutableStateOf<Asset?>(null) }
    var deleting by remember { mutableStateOf<Asset?>(null) }
    var creating by remember(initialNewAsset) { mutableStateOf(initialNewAsset) }
    if (state.isLoading) return LoadingBox()
    val visible = filterAssets(state.assets, query, filter)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.assets.any { it.asset.supportsAutomaticQuoteConfiguration() }) {
            Button(
                onClick = viewModel::openQuoteConfiguration,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.inv_configure_quotes)) }
        }
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
            EmptyState(Icons.Default.Search, stringResource(R.string.inv_assets_empty))
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
    if (state.quoteConfiguration.visible) QuoteConfigurationDialog(
        state = state.quoteConfiguration,
        onStart = viewModel::findQuoteConfigurations,
        onAccepted = viewModel::setProposalAccepted,
        onSave = { viewModel.saveQuoteConfigurations {} },
        onDismiss = viewModel::closeQuoteConfiguration,
    )
    editing?.let { asset ->
        val deps = state.dependencies[asset.id]
        AssetDialog(
            assets = state.assets.map { it.asset },
            onDismiss = { editing = null },
            onSaved = { editing = null },
            existingAsset = asset,
            hasOperations = deps?.operations?.let { it > 0 } ?: true,
            externalError = state.error,
            onDelete = { deleting = asset },
        )
    }
    if (creating) AssetDialog(
        assets = state.assets.map { it.asset },
        onDismiss = { creating = false },
        onSaved = { creating = false },
    )
    deleting?.let { asset ->
        val deps = state.dependencies[asset.id]
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(if (asset.archived) R.string.inv_reactivate_asset_title else R.string.inv_manage_asset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        deps == null -> Text(stringResource(R.string.inv_assets_checking))
                        asset.archived -> Text(stringResource(R.string.inv_reactivate_asset_message, asset.name))
                        deps.operations > 0 -> {
                            Text(stringResource(R.string.inv_archive_asset_message, asset.name))
                            Text(stringResource(R.string.inv_archive_preserves_history, deps.operations))
                        }
                        else -> {
                            Text(stringResource(R.string.inv_delete_asset_message, asset.name))
                            if (deps.manualPrices > 0) Text(stringResource(R.string.inv_delete_asset_prices, deps.manualPrices))
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (asset.archived) {
                        TextButton({ viewModel.setArchived(asset, false) { deleting = null; editing = null } }) {
                            Text(stringResource(R.string.inv_reactivate_asset))
                        }
                    } else if (deps != null) {
                        TextButton({ viewModel.setArchived(asset, true) { deleting = null; editing = null } }) {
                            Text(stringResource(R.string.inv_archive_asset))
                        }
                        if (deps.canDelete) {
                            TextButton({ viewModel.delete(asset) { deleting = null; editing = null } }) {
                                Text(stringResource(R.string.common_delete))
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun QuoteConfigurationDialog(
    state: QuoteConfigurationState,
    onStart: () -> Unit,
    onAccepted: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_configure_quotes)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.started) {
                    Text(stringResource(R.string.inv_configure_quotes_calls, state.eodhdCalls))
                    Text(stringResource(R.string.inv_configure_quotes_daily_limit))
                } else if (state.loading) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.inv_configure_quotes_searching))
                } else if (state.proposals.isEmpty()) {
                    Text(stringResource(R.string.inv_configure_quotes_empty))
                } else {
                    state.proposals.forEach { proposal ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = proposal.accepted && proposal.configurable,
                                onCheckedChange = { onAccepted(proposal.asset.id, it) },
                                enabled = proposal.configurable,
                            )
                            Column {
                                Text(proposal.asset.name, style = MaterialTheme.typography.titleSmall)
                                if (proposal.configurable) {
                                    val detail = if (proposal.provider == com.mipatrimonio.app.domain.model.QuoteProvider.COINGECKO) {
                                        proposal.marketCapRank?.let {
                                            stringResource(R.string.inv_configure_quotes_crypto_rank, proposal.candidateName.orEmpty(), proposal.candidateCode.orEmpty(), it)
                                        } ?: stringResource(R.string.inv_configure_quotes_crypto, proposal.candidateName.orEmpty(), proposal.candidateCode.orEmpty())
                                    } else stringResource(
                                        R.string.inv_configure_quotes_listing,
                                        proposal.candidateCode.orEmpty(), proposal.market.orEmpty(), proposal.candidateCurrency.orEmpty(),
                                    )
                                    Text(stringResource(R.string.inv_configure_quotes_proposal, detail))
                                } else {
                                    Text(
                                        stringResource(proposal.failure.configurationMessage()),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!state.started) TextButton(onStart) { Text(stringResource(R.string.inv_configure_quotes_search)) }
            else if (!state.loading && state.proposals.any { it.accepted && it.configurable }) {
                TextButton(onSave) { Text(stringResource(R.string.common_save)) }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun com.mipatrimonio.app.data.quotes.LookupFailure?.configurationMessage(): Int = when (this) {
    com.mipatrimonio.app.data.quotes.LookupFailure.SIN_CONEXION -> R.string.inv_lookup_offline
    com.mipatrimonio.app.data.quotes.LookupFailure.LIMITE_ALCANZADO -> R.string.inv_lookup_limit
    com.mipatrimonio.app.data.quotes.LookupFailure.RESPUESTA_INVALIDA -> R.string.inv_lookup_invalid_response
    com.mipatrimonio.app.data.quotes.LookupFailure.CLAVE_INVALIDA -> R.string.inv_lookup_invalid_key
    else -> R.string.inv_configure_quotes_not_found
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
