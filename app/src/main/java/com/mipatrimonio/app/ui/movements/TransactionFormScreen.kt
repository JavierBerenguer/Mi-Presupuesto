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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.label

@Composable
fun TransactionFormScreen(
    transactionId: String?,
    onDone: () -> Unit,
    viewModel: TransactionFormViewModel = appViewModel { c -> TransactionFormViewModel(c.ledger, transactionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, onDone) {
        viewModel.saved.collect { onDone() }
    }

    when {
        state.isLoading -> LoadingBox()
        state.notFound -> EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            message = stringResource(R.string.mov_transaction_not_found),
            actionLabel = stringResource(R.string.common_back),
            onAction = onDone,
        )
        state.activeAccounts.isEmpty() -> EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            message = stringResource(R.string.mov_no_active_accounts),
            actionLabel = stringResource(R.string.common_back),
            onAction = onDone,
        )
        else -> TransactionFormContent(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionFormContent(state: TransactionFormUiState, viewModel: TransactionFormViewModel) {
    val values = state.values
    val types = TransactionType.entries
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (state.isEditing) R.string.mov_edit_transaction_title else R.string.mov_new_transaction_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.mov_transaction_type), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = values.type == type,
                    onClick = { viewModel.setType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    label = { Text(type.label()) },
                )
            }
        }
        val selectedAccount = state.accounts.find { it.id == values.accountId }
        AmountField(
            label = stringResource(R.string.mov_amount),
            value = values.amount,
            onChange = viewModel::setAmount,
            suffix = selectedAccount?.currency,
            isError = state.error == TransactionFormError.INVALID_AMOUNT,
        )
        DropdownField(
            label = stringResource(R.string.mov_account),
            options = state.activeAccounts,
            selected = selectedAccount,
            optionLabel = { it.name },
            onSelected = { viewModel.setAccount(it?.id) },
        )
        DropdownField(
            label = stringResource(R.string.mov_category_optional),
            options = state.availableCategories,
            selected = state.categories.find { it.id == values.categoryId },
            optionLabel = { category -> categoryPath(category, state.categories) },
            onSelected = { viewModel.setCategory(it?.id) },
            noneLabel = stringResource(R.string.common_none),
        )
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
        OutlinedTextField(
            value = values.merchant,
            onValueChange = viewModel::setMerchant,
            label = { Text(stringResource(R.string.mov_merchant)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = values.notes,
            onValueChange = viewModel::setNotes,
            label = { Text(stringResource(R.string.mov_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        state.error?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        TransactionFormError.INVALID_AMOUNT -> R.string.mov_invalid_amount
                        TransactionFormError.ACCOUNT_REQUIRED -> R.string.mov_account_required
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.repositoryError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = viewModel::save, enabled = !state.isSaving) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

private fun categoryPath(category: Category, categories: List<Category>): String {
    val parent = category.parentId?.let { parentId -> categories.find { it.id == parentId } }
    return if (parent == null) category.name else "${categoryPath(parent, categories)} › ${category.name}"
}
