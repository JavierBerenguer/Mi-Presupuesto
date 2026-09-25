package com.mipatrimonio.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.charts.DonutChart
import com.mipatrimonio.app.ui.common.charts.DonutSlice
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.components.ProgressBar
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.components.SegmentedControl
import com.mipatrimonio.app.ui.theme.Fraunces
import com.mipatrimonio.app.ui.theme.extras
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel = appViewModel { c -> BudgetsViewModel(c.ledger, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var selectedStatistics by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.loading) {
        LoadingBox()
        return
    }
    val budgetById = state.budgets.associateBy(Budget::id)
    val editingBudget = editing?.let(budgetById::get)
    val deletingBudget = deleting?.let(budgetById::get)
    val noCategory = stringResource(R.string.pres_no_category)
    val categoryPath = stringResource(R.string.pres_category_path)
    val categoryName: (String?) -> String = { id ->
        categoryName(id, state.categories, noCategory) { parent, child -> categoryPath.format(parent, child) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.pres_title),
                    fontFamily = Fraunces,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item { MonthSelector(state.selectedMonth, viewModel::previousMonth, viewModel::nextMonth) }
            item {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.pres_segment_budgets),
                        stringResource(R.string.pres_segment_statistics),
                    ),
                    selectedIndex = selectedSection,
                    onSelected = { selectedSection = it },
                )
            }
            if (selectedSection == 0) {
                budgetItems(
                    state = state,
                    categoryName = categoryName,
                    onCreate = { creating = true },
                    onEdit = { editing = it.id },
                    onDelete = { deleting = it.id },
                )
            } else {
                item {
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.pres_stats_expenses),
                            stringResource(R.string.pres_stats_income),
                        ),
                        selectedIndex = selectedStatistics,
                        onSelected = { selectedStatistics = it },
                    )
                }
                item {
                    StatisticsContent(
                        statistics = if (selectedStatistics == 0) state.expenseStatistics else state.incomeStatistics,
                        kind = if (selectedStatistics == 0) StatisticsKind.GASTOS else StatisticsKind.INGRESOS,
                        state = state,
                        categoryName = categoryName,
                    )
                }
            }
        }
        if (selectedSection == 0 && state.budgets.isNotEmpty()) {
            FloatingActionButton(
                onClick = { creating = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp),
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pres_create)) }
        }
    }

    if (creating || editingBudget != null) {
        BudgetDialog(
            existing = editingBudget,
            state = state,
            categoryLabel = { categoryName(it.id) },
            viewModel = viewModel,
            onDismiss = { creating = false; editing = null },
        )
    }
    deletingBudget?.let { budget ->
        ConfirmDialog(
            title = stringResource(R.string.pres_delete_title),
            text = stringResource(
                R.string.pres_delete_text,
                if (budget.categoryId == null) stringResource(R.string.pres_global) else categoryName(budget.categoryId),
            ),
            onConfirm = { viewModel.delete(budget); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.budgetItems(
    state: BudgetsUiState,
    categoryName: (String?) -> String,
    onCreate: () -> Unit,
    onEdit: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
) {
    val global = state.statuses.firstOrNull {
        it.budget.categoryId == null && it.budget.period == BudgetPeriod.MENSUAL
    }
    val annualGlobals = state.statuses.filter {
        it.budget.categoryId == null && it.budget.period == BudgetPeriod.ANUAL
    }
    val categoryStatuses = sortStatuses(state.statuses.filter { it.budget.categoryId != null }, categoryName)
    if (global != null) {
        item { GlobalBudgetCard(global, state, onEdit, onDelete) }
    } else {
        item {
            EmptyState(
                icon = Icons.Outlined.PieChart,
                message = stringResource(R.string.pres_no_global),
                modifier = Modifier.height(240.dp),
                actionLabel = stringResource(R.string.pres_create),
                onAction = onCreate,
            )
        }
    }
    if (categoryStatuses.isNotEmpty()) {
        item { Text(stringResource(R.string.pres_by_category), style = MaterialTheme.typography.titleLarge) }
        items(categoryStatuses, key = { it.budget.id }) { status ->
            CategoryBudgetCard(status, categoryName(status.budget.categoryId), state.hideAmounts, onEdit, onDelete)
        }
    } else if (global != null) {
        item {
            Text(
                stringResource(R.string.pres_no_categories),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (annualGlobals.isNotEmpty()) {
        item { Text(stringResource(R.string.pres_other_budgets), style = MaterialTheme.typography.titleLarge) }
        items(annualGlobals, key = { it.budget.id }) { status ->
            CategoryBudgetCard(
                status,
                stringResource(R.string.pres_global_annual),
                state.hideAmounts,
                onEdit,
                onDelete,
            )
        }
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.pres_previous_month))
        }
        Text(
            month.format(formatter).replaceFirstChar(Char::titlecase),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.pres_next_month))
        }
    }
}

@Composable
private fun GlobalBudgetCard(
    status: BudgetStatus,
    state: BudgetsUiState,
    onEdit: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
) {
    val hidden = stringResource(R.string.common_hidden_amount)
    val remaining = if (state.hideAmounts) hidden else MoneyMath.format(status.remainingMinor.coerceAtLeast(0), status.budget.currency)
    val limit = if (state.hideAmounts) hidden else MoneyMath.format(status.budget.limitMinor, status.budget.currency)
    val spent = if (state.hideAmounts) hidden else MoneyMath.format(status.spentMinor, status.budget.currency)
    val days = daysRemaining(state.selectedMonth, LocalDate.now())
    val over = status.remainingMinor < 0
    val progressColor = if (usesExpenseWarning(status)) MaterialTheme.extras.expense else MaterialTheme.colorScheme.primary
    var menuOpen by rememberSaveable(status.budget.id) { mutableStateOf(false) }
    SectionCard(Modifier.clickable { onEdit(status.budget) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.pres_remaining),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.common_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        onClick = { menuOpen = false; onDelete(status.budget) },
                    )
                }
            }
        }
        Text(
            if (over) stringResource(
                R.string.pres_over_by,
                if (state.hideAmounts) hidden else MoneyMath.format(-status.remainingMinor, status.budget.currency),
            ) else remaining,
            fontFamily = Fraunces,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (over) MaterialTheme.extras.expense else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
        Text(
            pluralStringResource(R.plurals.pres_limit_days, days, limit, days),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProgressBar(status.consumedRatio.toFloat(), progressColor = progressColor)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.pres_spent, spent), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pres_percent_short, consumedPercent(status.consumedRatio)), style = MaterialTheme.typography.bodyMedium)
        }
        ExcludedNotice(status.excludedCount)
    }
}

@Composable
private fun CategoryBudgetCard(
    status: BudgetStatus,
    name: String,
    hideAmounts: Boolean,
    onEdit: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
) {
    val hidden = stringResource(R.string.common_hidden_amount)
    val isOver = status.remainingMinor < 0
    val isWarning = usesExpenseWarning(status)
    val accent = if (isOver || isWarning) MaterialTheme.extras.expense else MaterialTheme.colorScheme.primary
    val statusText = when {
        isOver -> stringResource(
            R.string.pres_over_by,
            if (hideAmounts) hidden else MoneyMath.format(-status.remainingMinor, status.budget.currency),
        )
        status.remainingMinor == 0L -> stringResource(R.string.pres_covered)
        else -> stringResource(
            R.string.pres_left,
            if (hideAmounts) hidden else MoneyMath.format(status.remainingMinor, status.budget.currency),
        )
    }
    var menuOpen by rememberSaveable(status.budget.id) { mutableStateOf(false) }
    SectionCard(Modifier.clickable { onEdit(status.budget) }) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                statusText,
                color = if (isWarning || isOver) MaterialTheme.extras.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge,
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.common_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        onClick = { menuOpen = false; onDelete(status.budget) },
                    )
                }
            }
        }
        ProgressBar(status.consumedRatio.toFloat(), progressColor = accent)
        Text(
            stringResource(
                R.string.pres_amount_of,
                if (hideAmounts) hidden else MoneyMath.format(status.spentMinor, status.budget.currency),
                if (hideAmounts) hidden else MoneyMath.format(status.budget.limitMinor, status.budget.currency),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            status.budget.period.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExcludedNotice(status.excludedCount)
    }
}

@Composable
private fun StatisticsContent(
    statistics: MonthlyStatistics,
    kind: StatisticsKind,
    state: BudgetsUiState,
    categoryName: (String?) -> String,
) {
    if (statistics.categories.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyState(
                icon = Icons.Outlined.PieChart,
                message = stringResource(R.string.pres_stats_empty),
                modifier = Modifier.height(240.dp),
            )
            Text(
                stringResource(R.string.pres_transfer_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (statistics.excludedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.pres_stats_excluded, statistics.excludedCount, statistics.excludedCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.extras.expense,
        Color(0xFFD9A441),
        Color(0xFFA9CFC1),
        Color(0xFF5A6B64),
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val otherLabel = stringResource(R.string.pres_other)
    val names = statistics.categories.map { if (it.isOther) otherLabel else categoryName(it.categoryId) }
    val hidden = stringResource(R.string.common_hidden_amount)
    val formatAmount: (Long) -> String = {
        if (state.hideAmounts) hidden else MoneyMath.format(it, state.baseCurrency)
    }
    val summaryItemFormat = stringResource(R.string.pres_stats_summary_item)
    val summary = statistics.categories.indices.joinToString(", ") { index ->
        summaryItemFormat.format(names[index], statistics.categories[index].percentage)
    }
    val description = stringResource(
        if (kind == StatisticsKind.GASTOS) R.string.pres_stats_expenses_description
        else R.string.pres_stats_income_description,
        summary,
    )
    SectionCard {
        DonutChart(
            slices = statistics.categories.mapIndexed { index, item ->
                DonutSlice(names[index], item.amountMinor, colors[index])
            },
            centerLabel = formatAmount(statistics.totalMinor),
            formatValue = formatAmount,
            description = description,
            emptyText = stringResource(R.string.pres_stats_empty),
            showLegend = false,
        )
        statistics.categories.forEachIndexed { index, item ->
            StatisticsRow(names[index], item, colors[index], formatAmount(item.amountMinor))
        }
        Text(
            stringResource(R.string.pres_transfer_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (statistics.excludedCount > 0) {
            Text(
                pluralStringResource(R.plurals.pres_stats_excluded, statistics.excludedCount, statistics.excludedCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatisticsRow(name: String, item: StatisticsCategory, color: Color, formattedAmount: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(name, Modifier.padding(start = 8.dp).weight(1f))
            Text(stringResource(R.string.pres_percent_short, item.percentage), Modifier.padding(start = 8.dp))
        }
        Text(
            formattedAmount,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
        ProgressBar(item.percentage / 100f, Modifier.height(4.dp), progressColor = color)
    }
}

@Composable
private fun ExcludedNotice(count: Int) {
    if (count > 0) {
        Text(
            pluralStringResource(R.plurals.pres_excluded, count, count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun categoryName(
    id: String?,
    categories: List<Category>,
    fallback: String,
    path: (String, String) -> String,
): String {
    if (id == null) return fallback
    val category = categories.find { it.id == id } ?: return fallback
    val parent = category.parentId?.let { parentId -> categories.find { it.id == parentId } }
    return if (parent == null) category.name else path(parent.name, category.name)
}

@Composable
private fun BudgetDialog(
    existing: Budget?,
    state: BudgetsUiState,
    categoryLabel: @Composable (Category) -> String,
    viewModel: BudgetsViewModel,
    onDismiss: () -> Unit,
) {
    var period by rememberSaveable(existing?.id) { mutableStateOf(existing?.period ?: BudgetPeriod.MENSUAL) }
    var categoryId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var limit by rememberSaveable(existing?.id) {
        mutableStateOf(
            existing?.let { MoneyMath.toDecimal(it.limitMinor, it.currency).stripTrailingZeros().toPlainString() }.orEmpty(),
        )
    }
    var error by remember(existing?.id) { mutableStateOf<BudgetSaveResult?>(null) }
    val currency = existing?.currency ?: state.baseCurrency
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
                    onSelected = { it?.let { selected -> period = selected; error = null } },
                )
                DropdownField(
                    label = stringResource(R.string.pres_category),
                    options = state.expenseCategories,
                    selected = state.expenseCategories.find { it.id == categoryId },
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
                val message = when (val current = error) {
                    BudgetSaveResult.InvalidLimit -> stringResource(R.string.pres_error_limit)
                    BudgetSaveResult.Duplicate -> stringResource(R.string.pres_error_duplicate)
                    is BudgetSaveResult.Failure -> current.message
                    else -> null
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
