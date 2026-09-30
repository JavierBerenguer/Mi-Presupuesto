package com.mipatrimonio.app.ui.recurring

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.ReminderOption
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate

@Composable
fun RecurringListScreen(
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: RecurringListViewModel = appViewModel { c ->
        RecurringListViewModel(
            c.recurring,
            c.settings,
            c.recurringReminderScheduler::cancel,
            c.recurringReminderScheduler::schedule,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingArchive by remember { mutableStateOf<String?>(null) }
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    when {
        state.isLoading -> LoadingBox()
        state.items.isEmpty() -> Box(Modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Outlined.EventRepeat,
                message = stringResource(R.string.recurring_empty),
                actionLabel = stringResource(R.string.recurring_new),
                onAction = onNew,
            )
        }
        else -> Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.rule.id }) { item ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    item.rule.description.ifBlank { stringResource(R.string.recurring_unnamed) },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (state.hideAmounts) stringResource(R.string.common_hidden_amount)
                                    else MoneyMath.format(item.rule.amountMinor, item.rule.currency),
                                )
                                Text(periodicityLabel(item.rule.periodQuantity, item.rule.periodUnit))
                                Text(
                                    item.nextDate?.let { stringResource(R.string.recurring_next, formatDate(it)) }
                                        ?: stringResource(R.string.recurring_no_next),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (item.rule.archived) stringResource(R.string.recurring_archived)
                                    else stringResource(R.string.recurring_active),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onEdit(item.rule.id) }) {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.recurring_edit))
                            }
                            IconButton(onClick = { pendingDelete = item.rule.id }) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.recurring_active), Modifier.weight(1f))
                            Switch(
                                checked = !item.rule.archived,
                                onCheckedChange = { active ->
                                    if (active) viewModel.setArchived(item.rule.id, archived = false)
                                    else pendingArchive = item.rule.id
                                },
                            )
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Default.Add, stringResource(R.string.recurring_new)) }
        }
    }
    pendingDelete?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.recurring_delete_title),
            text = stringResource(R.string.recurring_delete_message),
            onConfirm = { viewModel.delete(id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
    pendingArchive?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.recurring_archive_title),
            text = stringResource(R.string.recurring_archive_message),
            confirmLabel = stringResource(R.string.common_archive),
            onConfirm = { viewModel.setArchived(id, true); pendingArchive = null },
            onDismiss = { pendingArchive = null },
        )
    }
}

@Composable
fun RecurringFormScreen(
    ruleId: String?,
    onDone: () -> Unit,
    viewModel: RecurringFormViewModel = appViewModel { c ->
        RecurringFormViewModel(c.recurring, c.ledger, ruleId, onSaved = c.recurringReminderScheduler::schedule)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onDone() } }
    when {
        state.isLoading -> LoadingBox()
        state.notFound -> EmptyState(
            Icons.Outlined.EventRepeat,
            stringResource(R.string.recurring_not_found),
            actionLabel = stringResource(R.string.common_back),
            onAction = onDone,
        )
        else -> RecurringFormContent(state, viewModel)
    }
}

@Composable
private fun RecurringFormContent(state: RecurringFormUiState, viewModel: RecurringFormViewModel) {
    val values = state.values
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DropdownField(
                stringResource(R.string.recurring_kind),
                RecurringKind.entries,
                values.kind,
                { kindLabel(it) },
                { it?.let(viewModel::setKind) },
                enabled = !state.isEditing,
            )
        }
        item { AmountField(stringResource(R.string.recurring_amount), values.amount, viewModel::setAmount) }
        item {
            DropdownField(
                stringResource(R.string.recurring_account), state.activeAccounts, state.accounts.find { it.id == values.accountId },
                { it.name }, { viewModel.setAccount(it?.id) },
            )
        }
        if (values.kind == RecurringKind.TRANSFERENCIA) item {
            DropdownField(
                stringResource(R.string.recurring_destination), state.destinationAccounts,
                state.accounts.find { it.id == values.destinationAccountId }, { it.name },
                { viewModel.setDestinationAccount(it?.id) },
            )
        }
        item {
            DropdownField(
                stringResource(R.string.recurring_category), state.categories.filterNot { it.archived },
                state.categories.find { it.id == values.categoryId }, { it.name }, { viewModel.setCategory(it?.id) },
                noneLabel = stringResource(R.string.recurring_no_category),
            )
        }
        item {
            OutlinedTextField(
                values.description, viewModel::setDescription,
                label = { Text(stringResource(R.string.recurring_description)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                values.merchant, viewModel::setMerchant,
                label = { Text(stringResource(R.string.recurring_merchant)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        item { DateField(stringResource(R.string.recurring_start), values.startDate, viewModel::setStartDate) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    values.periodQuantity, viewModel::setPeriodQuantity,
                    label = { Text(stringResource(R.string.recurring_period_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true,
                )
                DropdownField(
                    stringResource(R.string.recurring_period_unit), RecurringPeriodUnit.entries, values.periodUnit,
                    { periodUnitLabel(it) }, { it?.let(viewModel::setPeriodUnit) }, modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(values.hasExpiration, viewModel::setHasExpiration)
                Text(stringResource(R.string.recurring_has_expiration))
            }
        }
        item {
            if (values.hasExpiration) {
                DateField(stringResource(R.string.recurring_end), values.endDate, viewModel::setEndDate)
            } else {
                OutlinedTextField(
                    value = formatDate(values.endDate),
                    onValueChange = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.recurring_end)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            DropdownField(
                stringResource(R.string.recurring_reminder), ReminderOption.entries, values.reminder,
                { reminderLabel(it) }, { it?.let(viewModel::setReminder) },
            )
        }
        if (values.reminder == ReminderOption.PERSONALIZADO) item {
            OutlinedTextField(
                values.reminderCustomDays, viewModel::setReminderCustomDays,
                label = { Text(stringResource(R.string.recurring_custom_days)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        state.error?.let { formError -> item {
            Text(stringResource(formError.messageResource()), color = MaterialTheme.colorScheme.error)
        } }
        item {
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}

@Composable
private fun kindLabel(kind: RecurringKind) = stringResource(when (kind) {
    RecurringKind.GASTO -> R.string.common_kind_gasto
    RecurringKind.INGRESO -> R.string.common_kind_ingreso
    RecurringKind.TRANSFERENCIA -> R.string.mov_transfer
})

@Composable
private fun periodUnitLabel(unit: RecurringPeriodUnit) = stringResource(when (unit) {
    RecurringPeriodUnit.DIA -> R.string.recurring_days
    RecurringPeriodUnit.MES -> R.string.recurring_months
    RecurringPeriodUnit.ANIO -> R.string.recurring_years
})

@Composable
private fun periodicityLabel(quantity: Int, unit: RecurringPeriodUnit) =
    stringResource(R.string.recurring_every, quantity, periodUnitLabel(unit))

@Composable
private fun reminderLabel(option: ReminderOption) = stringResource(when (option) {
    ReminderOption.NO -> R.string.recurring_reminder_no
    ReminderOption.EXACTO -> R.string.recurring_reminder_exact
    ReminderOption.UN_DIA_ANTES -> R.string.recurring_reminder_one_day
    ReminderOption.DOS_DIAS_ANTES -> R.string.recurring_reminder_two_days
    ReminderOption.PERSONALIZADO -> R.string.recurring_reminder_custom
})

private fun RecurringFormError.messageResource() = when (this) {
    RecurringFormError.ACCOUNT -> R.string.recurring_error_account
    RecurringFormError.AMOUNT -> R.string.recurring_error_amount
    RecurringFormError.PERIOD -> R.string.recurring_error_period
    RecurringFormError.CUSTOM_REMINDER -> R.string.recurring_error_custom_reminder
    RecurringFormError.END_DATE -> R.string.recurring_error_end_date
    RecurringFormError.SAVE -> R.string.recurring_error_save
}
