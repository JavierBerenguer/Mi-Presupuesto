package com.mipatrimonio.app.ui.accounts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.mipatrimonio.app.data.repository.AccountDependencies
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.SegmentedControl

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = appViewModel { c -> AccountsViewModel(c.ledger, c.investments, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(AccountFilter.ACTIVAS) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var archiving by remember { mutableStateOf<ManagedAccount?>(null) }
    var deleting by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (state.isLoading) return LoadingBox()
    val visible = filterAccounts(state.accounts, filter)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SegmentedControl(
                listOf(
                    stringResource(R.string.accounts_filter_active),
                    stringResource(R.string.accounts_filter_archived),
                    stringResource(R.string.accounts_filter_all),
                ),
                filter.ordinal,
                { filter = AccountFilter.entries[it] },
                modifier = Modifier.padding(16.dp),
            )
            if (state.totals.isNotEmpty()) {
                CurrencyTotals(
                    totals = state.totals,
                    hideAmounts = state.hideAmounts,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.AccountBalanceWallet,
                    message = stringResource(R.string.accounts_empty),
                    actionLabel = stringResource(R.string.accounts_new),
                    onAction = { creating = true; viewModel.clearError() },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.account.id }) { item ->
                        AccountRow(item, state.hideAmounts) {
                            viewModel.clearError()
                            viewModel.inspect(item.account.id)
                            editing = item.account
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true; viewModel.clearError() },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.accounts_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) AccountFormDialog(
        account = null,
        dependencies = null,
        error = state.error,
        onDismiss = { creating = false },
        onSave = { n, t, c, b, linked -> viewModel.save(null, n, t, c, b, linked) { creating = false } },
    )
    editing?.let { account ->
        AccountFormDialog(
            account,
            state.dependencies[account.id],
            state.error,
            { editing = null },
            { n, t, c, b, _ -> viewModel.save(account, n, t, c, b) { editing = null } },
            { editing = null; archiving = state.accounts.firstOrNull { it.account.id == account.id } },
            { deleting = account },
        )
    }
    archiving?.let { item ->
        val archive = !item.account.archived
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text(stringResource(if (archive) R.string.accounts_archive_title else R.string.accounts_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (archive) R.string.accounts_archive_message else R.string.accounts_restore_message, item.account.name))
                    if (archive && item.balanceMinor != 0L) Text(
                        stringResource(R.string.accounts_archived_balance_warning),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            },
            confirmButton = { TextButton({ viewModel.setArchived(item.account, archive) { archiving = null } }) { Text(stringResource(if (archive) R.string.common_archive else R.string.common_restore)) } },
            dismissButton = { TextButton({ archiving = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    deleting?.let { account ->
        DeleteAccountDialog(
            account,
            state.dependencies[account.id],
            { deleting = null },
            { viewModel.delete(account) { deleting = null; editing = null } },
            { deleting = null; editing = null; archiving = state.accounts.firstOrNull { it.account.id == account.id } },
        )
    }
}

@Composable
private fun CurrencyTotals(
    totals: List<CurrencyTotal>,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.accounts_active_totals), style = MaterialTheme.typography.titleMedium)
        totals.forEach { total ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(total.currency)
                Text(
                    if (hideAmounts) stringResource(R.string.common_hidden_amount)
                    else MoneyMath.format(total.amountMinor, total.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(item: ManagedAccount, hidden: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.account.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.accounts_details, item.account.type.label(), item.account.currency))
                Text(if (hidden) stringResource(R.string.common_hidden_amount) else MoneyMath.format(item.balanceMinor, item.account.currency))
                Text(stringResource(if (item.account.archived) R.string.accounts_status_archived else R.string.accounts_status_active), style = MaterialTheme.typography.labelMedium)
                if (item.account.archived && item.balanceMinor != 0L) Text(stringResource(R.string.accounts_archived_balance_warning), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.accounts_edit_description, item.account.name))
        }
    }
}

@Composable
private fun AccountFormDialog(
    account: Account?,
    dependencies: AccountDependencies?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, String, String, Boolean) -> Unit,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var type by remember(account?.id) { mutableStateOf(account?.type ?: AccountType.CORRIENTE) }
    var currency by remember(account?.id) { mutableStateOf(account?.currency ?: Currencies.EUR) }
    var balance by remember(account?.id) { mutableStateOf(account?.let { MoneyMath.toDecimal(it.initialBalanceMinor, it.currency).toPlainString() }.orEmpty()) }
    var createLinkedPortfolio by remember(account?.id) { mutableStateOf(false) }
    val currencyEnabled = account == null || dependencies?.hasHistory == false
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (account == null) R.string.accounts_new else R.string.accounts_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.accounts_name)) }, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    stringResource(R.string.accounts_type),
                    AccountType.entries,
                    type,
                    { it.label() },
                    { selected ->
                        selected?.let {
                            type = it
                            if (it != AccountType.INVERSION) createLinkedPortfolio = false
                        }
                    },
                )
                DropdownField(stringResource(R.string.accounts_currency), Currencies.comunes, currency, { it }, { it?.let { selected -> currency = selected } }, enabled = currencyEnabled)
                if (account != null && !currencyEnabled) Text(stringResource(R.string.accounts_currency_locked), style = MaterialTheme.typography.bodySmall)
                AmountField(stringResource(R.string.accounts_initial_balance), balance, { balance = it }, suffix = currency)
                if (account == null && type == AccountType.INVERSION) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = createLinkedPortfolio,
                            onCheckedChange = { createLinkedPortfolio = it },
                        )
                        Text(
                            stringResource(R.string.accounts_create_linked_portfolio),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (account != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onArchive ?: {}) { Text(stringResource(if (account.archived) R.string.common_restore else R.string.common_archive)) }
                    TextButton(onClick = onDelete ?: {}) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(name, type, currency, balance, createLinkedPortfolio) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun DeleteAccountDialog(
    account: Account,
    dependencies: AccountDependencies?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
) {
    val canDelete = dependencies?.canDelete == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (canDelete) R.string.accounts_delete_message else R.string.accounts_delete_blocked, account.name))
                dependencies?.let {
                    if (!it.canDelete) {
                        Text(stringResource(R.string.accounts_dependency_transactions, it.transactions))
                        Text(stringResource(R.string.accounts_dependency_transfers, it.transfers))
                        Text(stringResource(R.string.accounts_dependency_operations, it.investmentOperations))
                        Text(stringResource(R.string.accounts_dependency_portfolios, it.portfolios))
                        Text(stringResource(R.string.accounts_dependency_apps, it.notificationApps))
                    }
                } ?: Text(stringResource(R.string.accounts_loading_dependencies))
            }
        },
        confirmButton = {
            if (canDelete) TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
            else if (dependencies != null && !account.archived) TextButton(onClick = onArchive) { Text(stringResource(R.string.common_archive)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
