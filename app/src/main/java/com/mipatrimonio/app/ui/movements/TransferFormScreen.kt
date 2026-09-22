package com.mipatrimonio.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel

@Composable
fun TransferFormScreen(
    transferId: String?,
    onDone: () -> Unit,
    viewModel: TransferFormViewModel = appViewModel { c -> TransferFormViewModel(c.ledger, transferId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, onDone) {
        viewModel.saved.collect { onDone() }
    }

    when {
        state.isLoading -> LoadingBox()
        state.notFound -> EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            message = stringResource(R.string.mov_transfer_not_found),
            actionLabel = stringResource(R.string.common_back),
            onAction = onDone,
        )
        state.activeAccounts.size < 2 -> EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            message = stringResource(R.string.mov_not_enough_accounts),
            actionLabel = stringResource(R.string.common_back),
            onAction = onDone,
        )
        else -> TransferFormContent(state = state, viewModel = viewModel)
    }
}

@Composable
private fun TransferFormContent(state: TransferFormUiState, viewModel: TransferFormViewModel) {
    val values = state.values
    val fromAccount = state.accounts.find { it.id == values.fromAccountId }
    val toAccount = state.accounts.find { it.id == values.toAccountId }
    val sameCurrency = fromAccount?.currency != null && fromAccount.currency == toAccount?.currency

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (state.isEditing) R.string.mov_edit_transfer_title else R.string.mov_new_transfer_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        DropdownField(
            label = stringResource(R.string.mov_from_account),
            options = state.activeAccounts,
            selected = fromAccount,
            optionLabel = { it.name },
            onSelected = { viewModel.setFromAccount(it?.id) },
        )
        DropdownField(
            label = stringResource(R.string.mov_to_account),
            options = state.destinationAccounts,
            selected = toAccount,
            optionLabel = { it.name },
            onSelected = { viewModel.setToAccount(it?.id) },
        )
        AmountField(
            label = stringResource(R.string.mov_from_amount),
            value = values.fromAmount,
            onChange = viewModel::setFromAmount,
            suffix = fromAccount?.currency,
            isError = state.error == TransferFormError.INVALID_AMOUNTS,
        )
        AmountField(
            label = stringResource(R.string.mov_to_amount),
            value = values.toAmount,
            onChange = viewModel::setToAmount,
            suffix = toAccount?.currency,
            isError = state.error == TransferFormError.INVALID_AMOUNTS,
            enabled = !sameCurrency,
        )
        state.exchangeRate?.let { rate ->
            Text(
                text = stringResource(R.string.mov_exchange_rate, rate),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DateField(
            label = stringResource(R.string.mov_date),
            date = values.date,
            onChange = viewModel::setDate,
        )
        OutlinedTextField(
            value = values.description,
            onValueChange = viewModel::setDescription,
            label = { Text(stringResource(R.string.mov_description)) },
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        TransferFormError.ACCOUNTS_REQUIRED -> R.string.mov_accounts_required
                        TransferFormError.INVALID_AMOUNTS -> R.string.mov_invalid_transfer_amounts
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.validationMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = viewModel::save, enabled = !state.isSaving) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
