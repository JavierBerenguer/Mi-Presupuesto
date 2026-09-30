package com.mipatrimonio.app.ui.budgets

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.CategoryIconBadge
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import com.mipatrimonio.app.ui.components.ProgressBar
import com.mipatrimonio.app.ui.components.SectionCard
import java.time.YearMonth

private sealed interface BudgetPage {
    data object List : BudgetPage
    data class Form(val budget: Budget?, val draft: BudgetDraft? = null) : BudgetPage
    data class Categories(val draft: BudgetDraft) : BudgetPage
    data class Detail(val id: String) : BudgetPage
}

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel = appViewModel { c -> BudgetsViewModel(c.ledger, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<BudgetPage>(BudgetPage.List) }
    if (state.loading) return LoadingBox()
    when (val current = page) {
        BudgetPage.List -> BudgetList(
            state = state,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onNew = { page = BudgetPage.Form(null) },
            onOpen = { page = BudgetPage.Detail(it.id) },
        )
        is BudgetPage.Form -> BudgetForm(
            existing = current.budget,
            initialDraft = current.draft,
            baseCurrency = state.baseCurrency,
            onBack = { page = BudgetPage.List },
            onCategories = { page = BudgetPage.Categories(it) },
            onSave = { draft, result -> viewModel.save(draft, result) },
        )
        is BudgetPage.Categories -> CategorySelector(
            categories = state.expenseCategories,
            initial = current.draft,
            onBack = { page = BudgetPage.Form(state.budgets.find { it.id == current.draft.id }, current.draft) },
            onDone = { page = BudgetPage.Form(state.budgets.find { budget -> budget.id == it.id }, it) },
        )
        is BudgetPage.Detail -> state.budgets.find { it.id == current.id }?.let { budget ->
            BudgetDetail(
                budget = budget,
                state = state,
                onBack = { page = BudgetPage.List },
                onEdit = { page = BudgetPage.Form(budget) },
                onDuplicate = { viewModel.duplicate(budget) {} },
                onArchive = { viewModel.setArchived(budget, true); page = BudgetPage.List },
                onDelete = { viewModel.delete(budget); page = BudgetPage.List },
            )
        } ?: run { page = BudgetPage.List }
    }
}

@Composable
private fun BudgetList(
    state: BudgetsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Budget) -> Unit,
) {
    val globals = state.statuses.filter { it.budget.categoryRules.isEmpty() }
    val categorized = sortStatuses(state.statuses.filter { it.budget.categoryRules.isNotEmpty() }) { "" }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.pres_title), style = MaterialTheme.typography.headlineLarge)
                MonthPicker(state.selectedMonth.toString(), onPrevious, onNext)
            }
            if (globals.isNotEmpty()) {
                item { Text(stringResource(R.string.pres_global_summary), style = MaterialTheme.typography.titleMedium) }
                items(globals, key = { it.budget.id }) { BudgetCard(it, state, onOpen) }
            }
            if (categorized.isNotEmpty()) {
                item { Text(stringResource(R.string.pres_by_category), style = MaterialTheme.typography.titleMedium) }
                items(categorized, key = { it.budget.id }) { BudgetCard(it, state, onOpen) }
            }
            if (state.statuses.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.PieChart,
                        stringResource(R.string.pres_empty_detailed),
                        actionLabel = stringResource(R.string.pres_create),
                        onAction = onNew,
                    )
                }
            }
        }
        FloatingActionButton(onNew, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, stringResource(R.string.pres_create))
        }
    }
}

@Composable
private fun MonthPicker(label: String, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(previous) { Text(stringResource(R.string.pres_previous_month)) }
        Text(formatDate(YearMonth.parse(label).atDay(1)), style = MaterialTheme.typography.titleMedium)
        TextButton(next) { Text(stringResource(R.string.pres_next_month)) }
    }
}

@Composable
private fun BudgetCard(status: BudgetStatus, state: BudgetsUiState, onOpen: (Budget) -> Unit) {
    val budget = status.budget
    val warning = usesExpenseWarning(status)
    val amount = if (state.hideAmounts) stringResource(R.string.common_hidden_amount)
    else MoneyMath.format(kotlin.math.abs(status.remainingMinor), budget.currency)
    SectionCard(Modifier.clickable { onOpen(budget) }) {
        Text(budget.name, style = MaterialTheme.typography.titleLarge)
        Text(periodLabel(budget.period))
        Text(stringResource(R.string.pres_date_range, formatDate(budget.startDate), budget.endDate?.let(::formatDate) ?: stringResource(R.string.pres_no_end)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            budget.categoryRules.take(4).mapNotNull { rule -> state.categories.find { it.id == rule.categoryId } }.forEach {
                CategoryIconBadge(it, state.categories, size = 28.dp)
            }
            Text(categorySummary(budget, state.categories), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ProgressBar((status.percentage.coerceAtMost(100) / 100f), progressColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (status.remainingMinor < 0) R.string.pres_over_by else R.string.pres_left, amount), color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.pres_percent_short, status.percentage))
        }
        if (warning) Text(stringResource(R.string.pres_threshold_reached, budget.alertThresholdPct), color = MaterialTheme.colorScheme.error)
        if (status.excludedCount > 0) Text(pluralStringResource(R.plurals.pres_excluded, status.excludedCount, status.excludedCount))
    }
}

@Composable
private fun BudgetForm(
    existing: Budget?,
    initialDraft: BudgetDraft?,
    baseCurrency: String,
    onBack: () -> Unit,
    onCategories: (BudgetDraft) -> Unit,
    onSave: (BudgetDraft, (BudgetSaveResult) -> Unit) -> Unit,
) {
    var draft by remember(existing, initialDraft) { mutableStateOf(initialDraft ?: existing.toDraft(baseCurrency)) }
    var error by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader(stringResource(if (existing == null) R.string.pres_new else R.string.pres_edit), onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text(stringResource(R.string.pres_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { AmountField(stringResource(R.string.pres_amount), draft.amount, { draft = draft.copy(amount = it) }, suffix = draft.currency) }
            item { DropdownField(stringResource(R.string.pres_periodicity), BudgetPeriod.entries, draft.period, { periodLabel(it) }, { it?.let { p -> draft = draft.copy(period = p) } }) }
            item { DateField(stringResource(R.string.pres_from), draft.startDate, { draft = draft.copy(startDate = it) }) }
            item {
                Column {
                    draft.endDate?.let { date -> DateField(stringResource(R.string.pres_until), date, { draft = draft.copy(endDate = it) }) }
                    TextButton({ draft = draft.copy(endDate = draft.endDate?.let { null } ?: draft.startDate) }) {
                        Text(stringResource(if (draft.endDate == null) R.string.pres_add_end else R.string.pres_remove_end))
                    }
                }
            }
            item {
                SectionCard(Modifier.clickable { onCategories(draft) }) {
                    Text(stringResource(R.string.pres_categories), style = MaterialTheme.typography.labelLarge)
                    Text(if (draft.categoryRules.isEmpty()) stringResource(R.string.pres_all_categories) else pluralStringResource(R.plurals.pres_selected_categories, draft.categoryRules.size, draft.categoryRules.size))
                }
            }
            item {
                OutlinedTextField(
                    draft.alertThresholdPct.toString(),
                    { value -> value.toIntOrNull()?.let { draft = draft.copy(alertThresholdPct = it) } },
                    label = { Text(stringResource(R.string.pres_alert_at)) }, suffix = { Text(stringResource(R.string.pres_percent_symbol)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
            error?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } }
        }
        Button(
            onClick = {
                onSave(draft) { result ->
                    error = result.errorResource()
                    if (result == BudgetSaveResult.Success) onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.common_save)) }
    }
}

@Composable
private fun CategorySelector(
    categories: List<Category>,
    initial: BudgetDraft,
    onBack: () -> Unit,
    onDone: (BudgetDraft) -> Unit,
) {
    var rules by remember { mutableStateOf(initial.categoryRules) }
    var query by remember { mutableStateOf("") }
    val roots = categories.filter { it.parentId == null && (query.isBlank() || it.name.contains(query, true) || categories.any { child -> child.parentId == it.id && child.name.contains(query, true) }) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader(stringResource(R.string.pres_select_categories), onBack)
        OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text(stringResource(R.string.pres_search_categories)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ rules = roots.map { BudgetCategoryRule(it.id, true) } }) { Text(stringResource(R.string.pres_select_all)) }
            TextButton({ rules = emptyList() }) { Text(stringResource(R.string.pres_none)) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(roots, key = Category::id) { root ->
                val rootRule = rules.find { it.categoryId == root.id }
                CategoryRuleRow(root, categories, rootRule != null, rootRule?.includeSubcategories == true, true) { selected, all ->
                    rules = rules.filterNot { it.categoryId == root.id || (all && categories.find { category -> category.id == it.categoryId }?.parentId == root.id) } +
                        if (selected) listOf(BudgetCategoryRule(root.id, all)) else emptyList()
                }
                if (rootRule?.includeSubcategories != true) {
                    categories.filter { it.parentId == root.id && (query.isBlank() || it.name.contains(query, true)) }.forEach { child ->
                        val selected = rules.any { it.categoryId == child.id }
                        CategoryRuleRow(child, categories, selected, false, false) { checked, _ ->
                            rules = rules.filterNot { it.categoryId == child.id } + if (checked) listOf(BudgetCategoryRule(child.id, false)) else emptyList()
                        }
                    }
                }
            }
        }
        Button({ onDone(initial.copy(categoryRules = rules)) }, Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.common_accept)) }
    }
}

@Composable
private fun CategoryRuleRow(category: Category, categories: List<Category>, selected: Boolean, includeAll: Boolean, parent: Boolean, onChange: (Boolean, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = if (parent) 0.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, { onChange(it, includeAll) })
        CategoryIconBadge(category, categories, size = 32.dp)
        Text(category.name, Modifier.weight(1f).padding(start = 8.dp))
        if (parent && selected) {
            Text(stringResource(R.string.pres_include_subcategories), style = MaterialTheme.typography.bodySmall)
            Switch(includeAll, { onChange(true, it) })
        }
    }
}

@Composable
private fun BudgetDetail(
    budget: Budget,
    state: BudgetsUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = BudgetCalculator.status(
        budget, state.transactions, state.categories, state.selectedMonth.atDay(1), state.today,
    )
    val history = BudgetCalculator.recentWindows(budget, current.range.endInclusive, 6).map { range ->
        BudgetCalculator.statusForRange(budget, state.transactions, state.categories, range, state.today)
    }
    val matching = if (current.applies) {
        BudgetCalculator.matchingTransactions(
            budget, state.transactions, state.categories, current.range, state.today,
        )
    } else emptyList()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader(budget.name, onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BudgetCard(current, state) {} }
            item { Text(stringResource(R.string.pres_history), style = MaterialTheme.typography.titleMedium) }
            items(history) { status ->
                SectionCard {
                    Text(stringResource(R.string.pres_window, formatDate(status.range.start), formatDate(status.range.endInclusive)))
                    ProgressBar(status.percentage.coerceAtMost(100) / 100f, progressColor = if (usesExpenseWarning(status)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.pres_amount_of, displayAmount(status.spentMinor, budget.currency, state.hideAmounts), displayAmount(budget.limitMinor, budget.currency, state.hideAmounts)))
                }
            }
            item { Text(stringResource(R.string.pres_computed_transactions), style = MaterialTheme.typography.titleMedium) }
            if (matching.isEmpty()) item { Text(stringResource(R.string.pres_no_transactions)) }
            items(matching, key = { it.id }) { transaction ->
                SectionCard {
                    Text(transaction.description.ifBlank { transaction.merchant.ifBlank { stringResource(R.string.pres_expense) } })
                    Text(displayAmount(transaction.amountMinor, transaction.currency, state.hideAmounts))
                    Text(formatDate(transaction.date))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onEdit) { Text(stringResource(R.string.common_edit)) }
                    TextButton(onDuplicate) { Text(stringResource(R.string.pres_duplicate)) }
                    TextButton({ confirmArchive = true }) { Text(stringResource(R.string.pres_archive)) }
                    TextButton({ confirmDelete = true }) { Text(stringResource(R.string.common_delete)) }
                }
            }
        }
    }
    if (confirmArchive) ConfirmDialog(
        stringResource(R.string.pres_archive_title), stringResource(R.string.pres_archive_text, budget.name),
        onArchive, { confirmArchive = false }, stringResource(R.string.pres_archive),
    )
    if (confirmDelete) ConfirmDialog(
        stringResource(R.string.pres_delete_title), stringResource(R.string.pres_delete_text, budget.name),
        onDelete, { confirmDelete = false },
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun periodLabel(period: BudgetPeriod): String = stringResource(
    when (period) {
        BudgetPeriod.SEMANAL -> R.string.pres_period_weekly
        BudgetPeriod.MENSUAL -> R.string.pres_period_monthly
        BudgetPeriod.TRIMESTRAL -> R.string.pres_period_quarterly
        BudgetPeriod.SEMESTRAL -> R.string.pres_period_half_yearly
        BudgetPeriod.ANUAL -> R.string.pres_period_yearly
        BudgetPeriod.UNICO -> R.string.pres_period_once
    },
)

@Composable
private fun categorySummary(budget: Budget, categories: List<Category>): String {
    if (budget.categoryRules.isEmpty()) return stringResource(R.string.pres_all_categories)
    val byId = categories.associateBy(Category::id)
    val archivedLabel = stringResource(R.string.pres_archived_category)
    val allSuffix = stringResource(R.string.pres_all_suffix)
    return budget.categoryRules.joinToString { rule ->
        val name = byId[rule.categoryId]?.name ?: archivedLabel
        if (rule.includeSubcategories) "$name $allSuffix" else name
    }
}

private fun Budget?.toDraft(baseCurrency: String): BudgetDraft = if (this == null) BudgetDraft(currency = baseCurrency) else BudgetDraft(
    id, name, MoneyMath.toDecimal(limitMinor, currency).toPlainString(), currency, period, startDate, endDate,
    alertThresholdPct, categoryRules,
)

@Composable
private fun displayAmount(minor: Long, currency: String, hidden: Boolean): String =
    if (hidden) stringResource(R.string.common_hidden_amount) else MoneyMath.format(minor, currency)

private fun BudgetSaveResult.errorResource(): Int? = when (this) {
    BudgetSaveResult.Success -> null
    BudgetSaveResult.InvalidName -> R.string.pres_error_name
    BudgetSaveResult.InvalidLimit -> R.string.pres_error_limit
    BudgetSaveResult.InvalidDates -> R.string.pres_error_dates
    BudgetSaveResult.MissingUniqueEnd -> R.string.pres_error_unique_end
    BudgetSaveResult.InvalidThreshold -> R.string.pres_error_threshold
    BudgetSaveResult.Duplicate -> R.string.pres_error_duplicate
    is BudgetSaveResult.Failure -> R.string.pres_error_save
}
