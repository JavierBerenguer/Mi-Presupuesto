package com.mipatrimonio.app.ui.movements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import com.mipatrimonio.app.ui.components.SecondaryTopBar
import com.mipatrimonio.app.ui.components.SegmentedControl
import com.mipatrimonio.app.ui.theme.Fraunces
import com.mipatrimonio.app.ui.theme.extras
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun EntryFormScreen(
    entryId: String?,
    onDone: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
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
            onNewCategory = onOpenCategories,
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
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SecondaryTopBar(
                title = entryTitle(state),
                onBack = onBack,
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
        bottomBar = {
            if (!state.isLoading && !state.notFound && state.activeAccounts.isNotEmpty()) {
                EntryActionBar(
                    onClear = onRequestClear,
                    onSave = { viewModel.save() },
                    saveEnabled = !state.isSaving,
                )
            }
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
        FormCard {
            LabeledTextField(
                label = stringResource(R.string.mov_title),
                value = values.title,
                onValueChange = viewModel::setTitle,
            )
            if (values.kind != EntryKind.TRANSFER) {
                FormDivider()
                LabeledTextField(
                    label = stringResource(R.string.mov_comment),
                    value = values.comment,
                    onValueChange = viewModel::setComment,
                )
            }
            FormDivider()
            DateRow(values.date, viewModel::setDate)
            FormDivider()
            LabeledAmountField(
                label = if (values.kind == EntryKind.TRANSFER) {
                    stringResource(R.string.mov_from_amount)
                } else {
                    stringResource(R.string.mov_amount)
                },
                value = values.amount,
                currency = state.selectedAccount?.currency,
                isError = state.error == EntryFormError.INVALID_AMOUNT,
                kind = values.kind,
                sign = if (values.kind == EntryKind.INCOME) "+" else "−",
                onValueChange = viewModel::setAmount,
            )
            if (values.kind != EntryKind.TRANSFER) {
                FormDivider()
                CategoryRow(state, onChooseCategory, onClear = { viewModel.setCategory(null) })
                FormDivider()
                AccountDropdown(
                    label = stringResource(R.string.mov_account),
                    accounts = state.activeAccounts,
                    selected = state.selectedAccount,
                    onSelected = { viewModel.setAccount(it) },
                )
            } else {
                FormDivider()
                AccountDropdown(
                    label = stringResource(R.string.mov_origin),
                    accounts = state.activeAccounts,
                    selected = state.selectedAccount,
                    onSelected = { viewModel.setAccount(it) },
                )
                FormDivider()
                OutlinedButton(
                    onClick = viewModel::swapAccounts,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null)
                    Text(stringResource(R.string.mov_swap_accounts), Modifier.padding(start = 8.dp))
                }
                FormDivider()
                AccountDropdown(
                    label = stringResource(R.string.mov_destination),
                    accounts = state.destinationAccounts,
                    selected = state.selectedDestinationAccount,
                    onSelected = { viewModel.setDestinationAccount(it) },
                )
                FormDivider()
                LabeledAmountField(
                    label = stringResource(R.string.mov_to_amount),
                    value = values.destinationAmount,
                    currency = state.selectedDestinationAccount?.currency,
                    enabled = state.isCrossCurrency,
                    isError = state.error == EntryFormError.DESTINATION_AMOUNT_REQUIRED,
                    kind = values.kind,
                    sign = "+",
                    onValueChange = viewModel::setDestinationAmount,
                )
                state.exchangeRate?.let { rate ->
                    FormDivider()
                    Text(
                        stringResource(R.string.mov_exchange_rate, rate),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (values.kind != EntryKind.TRANSFER) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { moreDetails = !moreDetails }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.mov_more_details), Modifier.weight(1f))
                        Icon(
                            if (moreDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    if (moreDetails) {
                        FormDivider()
                        LabeledTextField(
                            label = stringResource(R.string.mov_merchant),
                            value = values.merchant,
                            onValueChange = viewModel::setMerchant,
                        )
                    }
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
    }
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    FormRow(label = label) {
        InlineTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            accessibilityLabel = label,
            modifier = Modifier.fillMaxWidth(),
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
    kind: EntryKind,
    sign: String,
    onValueChange: (String) -> Unit,
) {
    val amountColor = when (kind) {
        EntryKind.EXPENSE -> MaterialTheme.extras.expense
        EntryKind.INCOME -> MaterialTheme.colorScheme.primary
        EntryKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    FormRow(label = label, minHeight = 64.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(sign, style = amountTextStyle(amountColor), color = amountColor)
            InlineTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = stringResource(R.string.mov_amount_placeholder),
                accessibilityLabel = label,
                textStyle = amountTextStyle(if (isError) MaterialTheme.colorScheme.error else amountColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f, fill = false),
            )
            currency?.let {
                Text(
                    it,
                    modifier = Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<com.mipatrimonio.app.domain.model.Account>,
    selected: com.mipatrimonio.app.domain.model.Account?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FormRow(label = label) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .semantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selected?.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            onSelected(account.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: java.time.LocalDate, onChange: (java.time.LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.mov_date)
    FormRow(label = label) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = true }
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatDate(date), Modifier.weight(1f), textAlign = TextAlign.End)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
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
    val label = stringResource(R.string.mov_category)
    FormRow(label = label) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onChoose)
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.selectedCategory?.let { categoryPath(it, state.categories) }
                    ?: stringResource(R.string.mov_category_unassigned),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (state.selectedCategory == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (state.values.categoryId != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.mov_clear_category))
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun FormRow(
    label: String,
    minHeight: androidx.compose.ui.unit.Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.width(96.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
        content()
    }
}

@Composable
private fun FormDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
    ),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = accessibilityLabel },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(textStyle.color),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = textStyle,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

private fun amountTextStyle(color: androidx.compose.ui.graphics.Color) = TextStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    color = color,
    textAlign = TextAlign.End,
    fontFeatureSettings = "tnum",
)

@Composable
private fun EntryActionBar(onClear: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.mov_clear_form), maxLines = 2, textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onSave,
                    enabled = saveEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.common_save),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
