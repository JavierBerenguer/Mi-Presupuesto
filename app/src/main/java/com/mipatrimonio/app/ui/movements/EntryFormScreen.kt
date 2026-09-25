package com.mipatrimonio.app.ui.movements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.SegmentedControl
import com.mipatrimonio.app.ui.theme.extras
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun EntryFormScreen(
    entryId: String?,
    onDone: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: EntryFormViewModel = appViewModel { container -> EntryFormViewModel(container.ledger, entryId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (state.isDirty) confirmDiscard = true else onDone()
    }

    LaunchedEffect(viewModel, onDone) {
        viewModel.saved.collect { event ->
            if (event == EntrySavedEvent.CLOSE) onDone()
        }
    }
    BackHandler(onBack = if (showCategoryPicker) {
        { showCategoryPicker = false }
    } else {
        requestBack
    })

    if (showCategoryPicker) {
        CategoryPickerScreen(
            categories = state.availableCategories,
            frequentCategories = state.frequentCategories,
            selectedId = state.values.categoryId,
            onSelected = {
                viewModel.setCategory(it)
                showCategoryPicker = false
            },
            onBack = { showCategoryPicker = false },
        )
    } else {
        EntryFormScaffold(
            state = state,
            viewModel = viewModel,
            onBack = requestBack,
            onOpenAccounts = onOpenAccounts,
            onChooseCategory = { showCategoryPicker = true },
            onRequestClear = {
                if (state.hasData) confirmClear = true else viewModel.clear()
            },
        )
    }

    if (confirmDiscard) {
        ConfirmationDialog(
            title = stringResource(R.string.mov_discard_title),
            message = stringResource(R.string.mov_discard_message),
            confirm = stringResource(R.string.mov_discard_confirm),
            onConfirm = {
                confirmDiscard = false
                onDone()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmClear) {
        ConfirmationDialog(
            title = stringResource(R.string.mov_clear_form_title),
            message = stringResource(R.string.mov_clear_form_message),
            confirm = stringResource(R.string.mov_clear_form),
            onConfirm = {
                confirmClear = false
                viewModel.clear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryFormScaffold(
    state: EntryFormUiState,
    viewModel: EntryFormViewModel,
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onChooseCategory: () -> Unit,
    onRequestClear: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (!state.isEditing && !state.isLoading && !state.notFound) {
                        IconButton(onClick = { viewModel.save(addAnother = true) }, enabled = !state.isSaving) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.mov_save_and_add),
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && !state.isLoading && !state.notFound,
                    ) {
                        Icon(Icons.Outlined.Done, contentDescription = stringResource(R.string.common_save))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.notFound -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                message = stringResource(R.string.mov_entry_not_found),
                actionLabel = stringResource(R.string.common_back),
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            state.activeAccounts.isEmpty() -> EmptyState(
                icon = Icons.Outlined.AccountBalanceWallet,
                message = stringResource(R.string.mov_no_active_accounts),
                actionLabel = stringResource(R.string.mov_open_accounts),
                onAction = onOpenAccounts,
                modifier = Modifier.padding(padding),
            )
            else -> EntryFormContent(
                state = state,
                viewModel = viewModel,
                onChooseCategory = onChooseCategory,
                onRequestClear = onRequestClear,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun EntryFormContent(
    state: EntryFormUiState,
    viewModel: EntryFormViewModel,
    onChooseCategory: () -> Unit,
    onRequestClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = state.values
    val kinds = EntryKind.entries
    var moreDetails by rememberSaveable { mutableStateOf(values.merchant.isNotBlank()) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.common_kind_gasto),
                stringResource(R.string.common_kind_ingreso),
                stringResource(R.string.mov_transfer),
            ),
            selectedIndex = kinds.indexOf(values.kind),
            onSelected = { if (!state.isEditing) viewModel.setKind(kinds[it]) },
            selectedContainerColor = when (values.kind) {
                EntryKind.EXPENSE -> MaterialTheme.extras.expense.copy(alpha = 0.24f)
                EntryKind.INCOME -> MaterialTheme.colorScheme.primaryContainer
                EntryKind.TRANSFER -> MaterialTheme.colorScheme.surfaceVariant
            },
            selectedContentColor = when (values.kind) {
                EntryKind.EXPENSE -> MaterialTheme.extras.expense
                EntryKind.INCOME -> MaterialTheme.colorScheme.onPrimaryContainer
                EntryKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (state.isEditing) {
            Text(
                stringResource(R.string.mov_type_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard {
            LabeledTextField(
                label = stringResource(R.string.mov_title),
                value = values.title,
                onValueChange = viewModel::setTitle,
            )
            if (values.kind != EntryKind.TRANSFER) {
                LabeledTextField(
                    label = stringResource(R.string.mov_comment),
                    value = values.comment,
                    onValueChange = viewModel::setComment,
                )
            }
            DateRow(values.date, viewModel::setDate)
            LabeledAmountField(
                label = if (values.kind == EntryKind.TRANSFER) {
                    stringResource(R.string.mov_from_amount)
                } else {
                    stringResource(R.string.mov_amount)
                },
                value = values.amount,
                currency = state.selectedAccount?.currency,
                isError = state.error == EntryFormError.INVALID_AMOUNT,
                onValueChange = viewModel::setAmount,
            )
            if (values.kind != EntryKind.TRANSFER) {
                CategoryRow(state, onChooseCategory, onClear = { viewModel.setCategory(null) })
                AccountDropdown(
                    label = stringResource(R.string.mov_account),
                    accounts = state.activeAccounts,
                    selected = state.selectedAccount,
                    onSelected = { viewModel.setAccount(it) },
                )
            } else {
                AccountDropdown(
                    label = stringResource(R.string.mov_from_account),
                    accounts = state.activeAccounts,
                    selected = state.selectedAccount,
                    onSelected = { viewModel.setAccount(it) },
                )
                OutlinedButton(
                    onClick = viewModel::swapAccounts,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null)
                    Text(stringResource(R.string.mov_swap_accounts), Modifier.padding(start = 8.dp))
                }
                AccountDropdown(
                    label = stringResource(R.string.mov_to_account),
                    accounts = state.destinationAccounts,
                    selected = state.selectedDestinationAccount,
                    onSelected = { viewModel.setDestinationAccount(it) },
                )
                LabeledAmountField(
                    label = stringResource(R.string.mov_to_amount),
                    value = values.destinationAmount,
                    currency = state.selectedDestinationAccount?.currency,
                    enabled = state.isCrossCurrency,
                    isError = state.error == EntryFormError.DESTINATION_AMOUNT_REQUIRED,
                    onValueChange = viewModel::setDestinationAmount,
                )
                state.exchangeRate?.let { rate ->
                    Text(
                        stringResource(R.string.mov_exchange_rate, rate),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (values.kind != EntryKind.TRANSFER) {
            SectionCard {
                TextButton(
                    onClick = { moreDetails = !moreDetails },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.mov_more_details), Modifier.weight(1f))
                    Icon(
                        if (moreDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                }
                if (moreDetails) {
                    LabeledTextField(
                        label = stringResource(R.string.mov_merchant),
                        value = values.merchant,
                        onValueChange = viewModel::setMerchant,
                    )
                }
            }
        }
        state.selectedCategory?.takeIf { it.archived }?.let {
            Text(
                stringResource(R.string.mov_archived_category_warning),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.error?.let { error ->
            Text(stringResource(error.messageResource()), color = MaterialTheme.colorScheme.error)
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.mov_save_error), color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(
            onClick = onRequestClear,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Clear, contentDescription = null)
            Text(stringResource(R.string.mov_clear_form), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(104.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LabeledAmountField(
    label: String,
    value: String,
    currency: String?,
    enabled: Boolean = true,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(104.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            isError = isError,
            singleLine = true,
            suffix = currency?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<com.mipatrimonio.app.domain.model.Account>,
    selected: com.mipatrimonio.app.domain.model.Account?,
    onSelected: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(104.dp), style = MaterialTheme.typography.labelLarge)
        DropdownField(
            label = "",
            options = accounts,
            selected = selected,
            optionLabel = { it.name },
            onSelected = { onSelected(it?.id) },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: java.time.LocalDate, onChange: (java.time.LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.mov_date), Modifier.width(104.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(formatDate(date))
        }
    }
    if (open) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.common_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun CategoryRow(state: EntryFormUiState, onChoose: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.mov_category), Modifier.width(104.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onChoose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(
                state.selectedCategory?.let { categoryPath(it, state.categories) }
                    ?: stringResource(R.string.mov_category_unassigned),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.values.categoryId != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.mov_clear_category))
            }
        }
    }
}

@Composable
private fun entryTitle(state: EntryFormUiState): String = stringResource(
    when (state.values.kind) {
        EntryKind.EXPENSE -> if (state.isEditing) R.string.mov_edit_expense else R.string.mov_new_expense
        EntryKind.INCOME -> if (state.isEditing) R.string.mov_edit_income else R.string.mov_new_income
        EntryKind.TRANSFER -> if (state.isEditing) R.string.mov_edit_transfer_title else R.string.mov_new_transfer_title
    },
)

private fun EntryFormError.messageResource(): Int = when (this) {
    EntryFormError.INVALID_AMOUNT -> R.string.mov_invalid_amount
    EntryFormError.ACCOUNT_REQUIRED -> R.string.mov_account_required
    EntryFormError.ACCOUNTS_MUST_DIFFER -> R.string.mov_accounts_required
    EntryFormError.DESTINATION_AMOUNT_REQUIRED -> R.string.mov_destination_amount_required
    EntryFormError.AMOUNTS_MUST_MATCH -> R.string.mov_transfer_amounts_must_match
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
