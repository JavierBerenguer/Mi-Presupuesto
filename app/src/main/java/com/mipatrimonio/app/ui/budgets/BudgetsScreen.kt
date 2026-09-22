package com.mipatrimonio.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.calc.BudgetLevel
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.domain.model.Currencies

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel = appViewModel { c -> BudgetsViewModel(c.ledger, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Budget?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Budget?>(null) }

    if (state.loading) {
        LoadingBox()
        return
    }
    val globalName = stringResource(R.string.pres_global)
    val pathFormat = stringResource(R.string.pres_category_path)
    fun nameOf(id: String?): String {
        if (id == null) return globalName
        val category = state.categories.find { it.id == id } ?: return globalName
        val parent = category.parentId?.let { pid -> state.categories.find { it.id == pid } }
        return if (parent != null) pathFormat.format(parent.name, category.name) else category.name
    }
    val sorted = sortStatuses(state.statuses) { nameOf(it) }

    Box(Modifier.fillMaxSize()) {
        if (sorted.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.PieChart,
                message = stringResource(R.string.pres_empty),
                actionLabel = stringResource(R.string.pres_new),
                onAction = { creating = true },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.budget.id }) { status ->
                    BudgetCard(
                        status = status,
                        title = nameOf(status.budget.categoryId),
                        onEdit = { editing = status.budget },
                        onDelete = { deleting = status.budget },
                    )
                }
            }
            FloatingActionButton(
                onClick = { creating = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pres_new)) }
        }
    }

    if (creating || editing != null) {
        BudgetDialog(
            existing = editing,
            state = state,
            categoryLabel = { nameOf(it.id) },
            viewModel = viewModel,
            onDismiss = { creating = false; editing = null },
        )
    }
    deleting?.let { budget ->
        ConfirmDialog(
            title = stringResource(R.string.pres_delete_title),
            text = stringResource(R.string.pres_delete_text, nameOf(budget.categoryId)),
            onConfirm = { viewModel.delete(budget); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun BudgetCard(status: BudgetStatus, title: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val budget = status.budget
    val (levelColor, levelIcon, levelText) = when (status.level) {
        BudgetLevel.NORMAL -> Triple(MaterialTheme.colorScheme.primary, Icons.Filled.CheckCircle, R.string.pres_level_ok)
        BudgetLevel.AVISO -> Triple(MoneyColors.warning, Icons.Filled.Warning, R.string.pres_level_warning)
        BudgetLevel.SUPERADO -> Triple(MoneyColors.negative, Icons.Filled.Error, R.string.pres_level_over)
    }
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.pres_period_range,
                            budget.period.label(),
                            formatDate(status.range.start),
                            formatDate(status.range.endInclusive),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_edit)) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { status.consumedRatio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = levelColor,
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(levelIcon, contentDescription = null, tint = levelColor)
                Text(
                    stringResource(levelText) + " · " + stringResource(R.string.pres_percent, consumedPercent(status.consumedRatio)),
                    color = levelColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                stringResource(
                    R.string.pres_spent_of,
                    MoneyMath.format(status.spentMinor, budget.currency),
                    MoneyMath.format(budget.limitMinor, budget.currency),
                ),
            )
            if (status.remainingMinor >= 0) {
                Text(stringResource(R.string.pres_available, MoneyMath.format(status.remainingMinor, budget.currency)))
            } else {
                Text(
                    stringResource(R.string.pres_over_by, MoneyMath.format(-status.remainingMinor, budget.currency)),
                    color = MoneyColors.negative,
                )
            }
            if (status.excludedCount > 0) {
                Text(
                    stringResource(R.string.pres_excluded, status.excludedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetDialog(
    existing: Budget?,
    state: BudgetsUiState,
    categoryLabel: @Composable (Category) -> String,
    viewModel: BudgetsViewModel,
    onDismiss: () -> Unit,
) {
    var period by remember { mutableStateOf(existing?.period ?: BudgetPeriod.MENSUAL) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var limit by remember {
        mutableStateOf(existing?.let { MoneyMath.toDecimal(it.limitMinor, it.currency).stripTrailingZeros().toPlainString() }.orEmpty())
    }
    var error by remember { mutableStateOf<BudgetSaveResult?>(null) }
    val currency = existing?.currency ?: state.baseCurrency
    val selectedCategory = state.expenseCategories.find { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.pres_new else R.string.pres_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    label = stringResource(R.string.pres_period),
                    options = BudgetPeriod.entries,
                    selected = period,
                    optionLabel = { it.label() },
                    onSelected = { it?.let { p -> period = p; error = null } },
                )
                DropdownField(
                    label = stringResource(R.string.pres_category),
                    options = state.expenseCategories,
                    selected = selectedCategory,
                    optionLabel = { categoryLabel(it) },
                    onSelected = { categoryId = it?.id; error = null },
                    noneLabel = stringResource(R.string.pres_global_option),
                )
                AmountField(
                    label = stringResource(R.string.pres_limit),
                    value = limit,
                    onChange = { limit = it; error = null },
                    suffix = Currencies.symbol(currency),
                    isError = error == BudgetSaveResult.InvalidLimit,
                )
                val message = when (val e = error) {
                    BudgetSaveResult.InvalidLimit -> stringResource(R.string.pres_error_limit)
                    BudgetSaveResult.Duplicate -> stringResource(R.string.pres_error_duplicate)
                    is BudgetSaveResult.Failure -> e.message
                    else -> null
                }
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.save(existing, categoryId, period, limit) { result ->
                    if (result == BudgetSaveResult.Success) onDismiss() else error = result
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
