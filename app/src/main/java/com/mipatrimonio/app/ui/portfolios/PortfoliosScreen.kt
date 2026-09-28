package com.mipatrimonio.app.ui.portfolios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.SegmentedControl

@Composable
fun PortfoliosScreen(
    initialNewPortfolio: Boolean = false,
    viewModel: PortfoliosViewModel = appViewModel { c -> PortfoliosViewModel(c.ledger, c.investments, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(PortfolioFilter.ACTIVAS) }
    var editing by remember { mutableStateOf<Portfolio?>(null) }
    var creating by remember(initialNewPortfolio) { mutableStateOf(initialNewPortfolio) }
    var archiving by remember { mutableStateOf<ManagedPortfolio?>(null) }
    var deleting by remember { mutableStateOf<Portfolio?>(null) }
    if (state.isLoading) return LoadingBox()
    val visible = filterPortfolios(state.portfolios, filter)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SegmentedControl(
                listOf(
                    stringResource(R.string.portfolios_filter_active),
                    stringResource(R.string.portfolios_filter_archived),
                    stringResource(R.string.portfolios_filter_all),
                ),
                filter.ordinal,
                { filter = PortfolioFilter.entries[it] },
                Modifier.padding(16.dp),
            )
            if (visible.isEmpty()) EmptyState(
                icon = Icons.Default.ShowChart,
                message = stringResource(R.string.portfolios_empty),
                actionLabel = stringResource(R.string.portfolios_new),
                onAction = { creating = true; viewModel.clearError() },
            ) else LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.portfolio.id }) { item ->
                    PortfolioRow(item, state) {
                        viewModel.clearError(); viewModel.inspect(item.portfolio.id); editing = item.portfolio
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true; viewModel.clearError() },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.portfolios_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (creating) PortfolioDialog(
        portfolio = null,
        accounts = state.accounts,
        error = state.error,
        onDismiss = { creating = false },
        onSave = { name, account -> viewModel.save(null, name, account?.id) { creating = false } },
    )
    editing?.let { portfolio ->
        PortfolioDialog(
            portfolio, state.accounts, state.error, { editing = null },
            { name, account -> viewModel.save(portfolio, name, account?.id) { editing = null } },
            { editing = null; archiving = state.portfolios.firstOrNull { it.portfolio.id == portfolio.id } },
            { deleting = portfolio },
        )
    }
    archiving?.let { item ->
        val archive = !item.portfolio.archived
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text(stringResource(if (archive) R.string.portfolios_archive_title else R.string.portfolios_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (archive) R.string.portfolios_archive_message else R.string.portfolios_restore_message, item.portfolio.name))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton({ viewModel.setArchived(item.portfolio, archive) { archiving = null } }) { Text(stringResource(if (archive) R.string.common_archive else R.string.common_restore)) } },
            dismissButton = { TextButton({ archiving = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    deleting?.let { portfolio ->
        val dependencies = state.dependencies[portfolio.id]
        val canDelete = dependencies?.canDelete == true
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.portfolios_delete_title)) },
            text = {
                Text(
                    when {
                        dependencies == null -> stringResource(R.string.portfolios_checking)
                        canDelete -> stringResource(R.string.portfolios_delete_message, portfolio.name)
                        else -> stringResource(R.string.portfolios_delete_blocked, portfolio.name, dependencies.operations)
                    },
                )
            },
            confirmButton = {
                if (canDelete) TextButton({ viewModel.delete(portfolio) { deleting = null; editing = null } }) { Text(stringResource(R.string.common_delete)) }
                else if (dependencies != null && !portfolio.archived) TextButton({ deleting = null; editing = null; archiving = state.portfolios.firstOrNull { it.portfolio.id == portfolio.id } }) { Text(stringResource(R.string.common_archive)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun PortfolioRow(item: ManagedPortfolio, state: PortfoliosUiState, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.portfolio.name, style = MaterialTheme.typography.titleMedium)
                Text(item.defaultAccount?.name ?: stringResource(R.string.portfolios_no_default_account))
                Text(stringResource(R.string.portfolios_open_assets, item.openAssets))
                Text(if (state.hideAmounts) stringResource(R.string.common_hidden_amount) else MoneyMath.format(item.valueMinor, state.baseCurrency))
                Text(stringResource(if (item.portfolio.archived) R.string.portfolios_status_archived else R.string.portfolios_status_active), style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.portfolios_edit_description, item.portfolio.name))
        }
    }
}

@Composable
private fun PortfolioDialog(
    portfolio: Portfolio?,
    accounts: List<Account>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, Account?) -> Unit,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(portfolio?.id) { mutableStateOf(portfolio?.name.orEmpty()) }
    var account by remember(portfolio?.id, accounts) { mutableStateOf(accounts.firstOrNull { it.id == portfolio?.defaultAccountId }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (portfolio == null) R.string.portfolios_new else R.string.portfolios_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.portfolios_name)) }, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    stringResource(R.string.portfolios_default_account), accounts, account, { it.name }, { account = it },
                    noneLabel = stringResource(R.string.portfolios_no_default_account),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (portfolio != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onArchive ?: {}) { Text(stringResource(if (portfolio.archived) R.string.common_restore else R.string.common_archive)) }
                    TextButton(onClick = onDelete ?: {}) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton({ onSave(name, account) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
