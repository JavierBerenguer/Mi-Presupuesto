package com.mipatrimonio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.label

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = appViewModel { c -> AccountsViewModel(c.ledger) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingBox()
        return
    }

    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var accountToArchive by remember { mutableStateOf<Account?>(null) }
    val activeAccounts = state.accounts.filterNot { it.account.archived }
    val archivedAccounts = state.accounts.filter { it.account.archived }

    Box(Modifier.fillMaxSize()) {
        if (state.accounts.isEmpty()) {
            EmptyState(
                icon = Icons.Default.AccountBalanceWallet,
                message = stringResource(R.string.aj_no_accounts),
                actionLabel = stringResource(R.string.aj_new_account),
                onAction = {
                    viewModel.clearFormError()
                    showCreateDialog = true
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeAccounts.isNotEmpty()) {
                    item { AccountSectionTitle(stringResource(R.string.aj_active_accounts)) }
                    items(activeAccounts, key = { it.account.id }) { item ->
                        AccountRow(
                            item = item,
                            onEdit = {
                                viewModel.clearFormError()
                                editingAccount = item.account
                            },
                            onArchive = { accountToArchive = item.account },
                        )
                    }
                }
                if (archivedAccounts.isNotEmpty()) {
                    item { AccountSectionTitle(stringResource(R.string.aj_archived_accounts)) }
                    items(archivedAccounts, key = { it.account.id }) { item ->
                        AccountRow(
                            item = item,
                            onEdit = {
                                viewModel.clearFormError()
                                editingAccount = item.account
                            },
                            onArchive = { accountToArchive = item.account },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                viewModel.clearFormError()
                showCreateDialog = true
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.aj_new_account)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showCreateDialog) {
        AccountFormDialog(
            account = null,
            error = state.formError,
            onDismiss = {
                viewModel.clearFormError()
                showCreateDialog = false
            },
            onSave = { name, type, currency, initialBalance ->
                viewModel.saveAccount(null, name, type, currency, initialBalance) {
                    showCreateDialog = false
                }
            },
        )
    }
    editingAccount?.let { account ->
        AccountFormDialog(
            account = account,
            error = state.formError,
            onDismiss = {
                viewModel.clearFormError()
                editingAccount = null
            },
            onSave = { name, type, currency, initialBalance ->
                viewModel.saveAccount(account, name, type, currency, initialBalance) {
                    editingAccount = null
                }
            },
        )
    }
    accountToArchive?.let { account ->
        val willArchive = !account.archived
        ConfirmDialog(
            title = stringResource(
                if (willArchive) R.string.aj_archive_account_title else R.string.aj_restore_account_title,
            ),
            text = stringResource(
                if (willArchive) R.string.aj_archive_account_message else R.string.aj_restore_account_message,
                account.name,
            ),
            confirmLabel = stringResource(if (willArchive) R.string.common_archive else R.string.common_restore),
            onConfirm = {
                viewModel.setArchived(account, willArchive) { accountToArchive = null }
            },
            onDismiss = { accountToArchive = null },
        )
    }
}

@Composable
private fun AccountSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val account = item.account
    Card(Modifier.fillMaxWidth().alpha(if (account.archived) 0.6f else 1f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.aj_account_details, account.type.label(), account.currency),
                    style = MaterialTheme.typography.bodyMedium,
                )
                MoneyText(
                    minor = item.balanceMinor,
                    currency = account.currency,
                    colored = true,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.aj_edit_account, account.name),
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    if (account.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = stringResource(
                        if (account.archived) R.string.aj_restore_account else R.string.aj_archive_account,
                        account.name,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AccountFormDialog(
    account: Account?,
    error: AccountFormError?,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, String, String) -> Unit,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var type by remember(account?.id) { mutableStateOf(account?.type ?: AccountType.CORRIENTE) }
    var currency by remember(account?.id) { mutableStateOf(account?.currency ?: Currencies.EUR) }
    var initialBalance by remember(account?.id) {
        mutableStateOf(
            account?.let {
                MoneyMath.toDecimal(it.initialBalanceMinor, it.currency).stripTrailingZeros().toPlainString()
            }.orEmpty(),
        )
    }
    val errorText = when (error) {
        AccountFormError.BlankName -> stringResource(R.string.aj_error_blank_account_name)
        AccountFormError.InvalidInitialBalance -> stringResource(R.string.aj_error_invalid_initial_balance)
        is AccountFormError.Repository -> error.message.ifBlank { stringResource(R.string.aj_error_saving) }
        null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (account == null) R.string.aj_create_account else R.string.aj_edit_account_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.aj_name)) },
                    singleLine = true,
                    isError = error == AccountFormError.BlankName,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = stringResource(R.string.aj_account_type),
                    options = AccountType.entries,
                    selected = type,
                    optionLabel = { it.label() },
                    onSelected = { selected -> selected?.let { type = it } },
                )
                DropdownField(
                    label = stringResource(R.string.aj_currency),
                    options = Currencies.comunes,
                    selected = currency,
                    optionLabel = { it },
                    onSelected = { selected -> selected?.let { currency = it } },
                    enabled = account == null,
                )
                if (account != null) {
                    Text(
                        stringResource(R.string.aj_currency_cannot_change),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AmountField(
                    label = stringResource(R.string.aj_initial_balance),
                    value = initialBalance,
                    onChange = { initialBalance = it },
                    suffix = currency,
                    isError = error == AccountFormError.InvalidInitialBalance,
                )
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, type, currency, initialBalance) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
