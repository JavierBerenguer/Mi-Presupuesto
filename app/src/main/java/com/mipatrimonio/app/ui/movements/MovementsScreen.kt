package com.mipatrimonio.app.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.AddFab
import com.mipatrimonio.app.ui.components.AmountKind
import com.mipatrimonio.app.ui.components.AmountText
import com.mipatrimonio.app.ui.components.SegmentedControl
import com.mipatrimonio.app.ui.theme.extras
import java.time.LocalDate
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.movementStatus
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MovementsScreen(
    onNewEntry: () -> Unit,
    onEditEntry: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAssetDetail: (String, String) -> Unit,
    initialSource: SourceFilter? = null,
    viewModel: MovementsViewModel = appViewModel { c ->
        MovementsViewModel(c.ledger, c.investments, c.settings)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showCalculation by remember { mutableStateOf(false) }
    var showAccountSelection by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MovementItem?>(null) }
    LaunchedEffect(initialSource) { initialSource?.let(viewModel::setSource) }

    if (state.isLoading) { LoadingBox(); return }
    if (showAccountSelection) {
        MovementAccountsScreen(onBack = { showAccountSelection = false })
        return
    }
    if (state.accounts.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            message = stringResource(R.string.mov_no_accounts),
            actionLabel = stringResource(R.string.mov_open_accounts),
            onAction = onOpenAccounts,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MovementHeader(
                onSearch = { showSearch = !showSearch },
                onCalculation = { showCalculation = true },
                onFilters = { showFilters = true },
            )
            if (showSearch) {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.mov_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (state.filters.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.mov_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            MonthSelector(
                month = state.selectedMonth,
                previous = viewModel::previousMonth,
                next = viewModel::nextMonth,
                onMonthSelected = viewModel::setMonth,
            )
            BalanceBar(
                balanceMinor = state.balanceMinor,
                baseCurrency = state.baseCurrency,
                hideAmounts = state.hideAmounts,
                calculationMode = state.calculationMode,
                onClick = { showAccountSelection = true },
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.mov_kind_all),
                    stringResource(R.string.mov_kind_expenses),
                    stringResource(R.string.mov_kind_income),
                    stringResource(R.string.mov_kind_transfers_short),
                ),
                selectedIndex = kindOrder.indexOf(state.filters.kind),
                onSelected = { viewModel.setKind(kindOrder[it]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.error?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearError) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.mov_dismiss_error))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.allItems.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        message = stringResource(R.string.mov_no_movements),
                    )
                    state.dayGroups.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.Search,
                        message = stringResource(
                            if (state.hasActiveFilters) R.string.mov_no_results else R.string.mov_no_movements_month,
                        ),
                        actionLabel = if (state.hasActiveFilters) stringResource(R.string.mov_clear_filters) else null,
                        onAction = if (state.hasActiveFilters) viewModel::clearFilters else null,
                    )
                    else -> MovementList(
                        groups = state.dayGroups,
                        accounts = state.accounts,
                        categories = state.categories,
                        baseCurrency = state.baseCurrency,
                        hideAmounts = state.hideAmounts,
                        grayFuture = state.dailyBalance,
                        onEditEntry = onEditEntry,
                        onOpenAssetDetail = onOpenAssetDetail,
                        onDuplicate = viewModel::duplicate,
                        onDelete = { pendingDelete = it },
                    )
                }
            }
        }
        AddFab(
            stringResource(R.string.mov_new),
            onNewEntry,
            bottomPadding = 16.dp,
        )
    }

    if (showFilters) {
        MovementFiltersDialog(
            filters = state.filters,
            accounts = state.accounts,
            categories = state.categories,
            baseCurrency = state.baseCurrency,
            onApply = { viewModel.setFilters(it); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
    if (showCalculation) {
        CalculationModeDialog(
            mode = state.calculationMode,
            dailyBalance = state.dailyBalance,
            hideFuture = state.hideFuture,
            ignoreTransfers = state.ignoreTransfers,
            onModeChange = viewModel::setCalculationMode,
            onDailyBalanceChange = viewModel::setDailyBalance,
            onHideFutureChange = viewModel::setHideFuture,
            onIgnoreTransfersChange = viewModel::setIgnoreTransfers,
            onDismiss = { showCalculation = false },
        )
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.mov_delete_title),
            text = stringResource(R.string.mov_delete_message),
            onConfirm = { viewModel.delete(item); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

private val kindOrder = listOf(KindFilter.TODOS, KindFilter.GASTOS, KindFilter.INGRESOS, KindFilter.TRANSFERENCIAS)

@Composable
private fun MovementHeader(onSearch: () -> Unit, onCalculation: () -> Unit, onFilters: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.nav_movimientos), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Search, stringResource(R.string.mov_search))
        }
        IconButton(onClick = onCalculation, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Calculate, stringResource(R.string.mov_calculation_options))
        }
        IconButton(onClick = onFilters, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.FilterList, stringResource(R.string.mov_filters))
        }
    }
}

@Composable
private fun BalanceBar(
    balanceMinor: Long,
    baseCurrency: String,
    hideAmounts: Boolean,
    calculationMode: MovementsCalculationMode,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(calculationModeLabel(calculationMode), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.mov_balance_accounts_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (hideAmounts) {
                Text(stringResource(R.string.common_hidden_amount), style = MaterialTheme.typography.titleLarge)
            } else {
                AmountText(balanceMinor, baseCurrency, AmountKind.NEUTRAL, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun calculationModeLabel(mode: MovementsCalculationMode): String = stringResource(
    when (mode) {
        MovementsCalculationMode.SALDO_ACTUAL -> R.string.mov_mode_current_balance
        MovementsCalculationMode.SALDO_MENSUAL -> R.string.mov_mode_monthly_balance
        MovementsCalculationMode.GASTOS_MENSUALES -> R.string.mov_mode_monthly_expenses
        MovementsCalculationMode.INGRESOS_MENSUALES -> R.string.mov_mode_monthly_income
    },
)

@Composable
private fun CalculationModeDialog(
    mode: MovementsCalculationMode,
    dailyBalance: Boolean,
    hideFuture: Boolean,
    ignoreTransfers: Boolean,
    onModeChange: (MovementsCalculationMode) -> Unit,
    onDailyBalanceChange: (Boolean) -> Unit,
    onHideFutureChange: (Boolean) -> Unit,
    onIgnoreTransfersChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mov_calculation_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MovementsCalculationMode.entries.forEach { option ->
                    ChoiceRow(
                        label = calculationModeLabel(option),
                        selected = option == mode,
                        radio = true,
                        onClick = { onModeChange(option) },
                    )
                }
                ChoiceRow(
                    label = stringResource(R.string.mov_daily_balance),
                    selected = dailyBalance,
                    onClick = { onDailyBalanceChange(!dailyBalance) },
                )
                ChoiceRow(
                    label = stringResource(R.string.mov_hide_future),
                    selected = hideFuture,
                    onClick = { onHideFutureChange(!hideFuture) },
                )
                ChoiceRow(
                    label = stringResource(R.string.mov_ignore_transfers),
                    selected = ignoreTransfers,
                    onClick = { onIgnoreTransfersChange(!ignoreTransfers) },
                )
                Text(
                    stringResource(R.string.mov_ignore_transfers_current_note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mov_close)) } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, radio: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) RadioButton(selected = selected, onClick = onClick)
        else Checkbox(checked = selected, onCheckedChange = { onClick() })
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    previous: () -> Unit,
    next: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
) {
    var showMonthMenu by remember { mutableStateOf(false) }
    var showYearMenu by remember { mutableStateOf(false) }
    val locale = remember { Locale("es", "ES") }
    val currentYear = remember { YearMonth.now().year }
    val selectableYears = remember(currentYear) { (currentYear - 10)..(currentYear + 2) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(previous, Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.mov_previous_month))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            Box {
                TextButton(
                    onClick = { showMonthMenu = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                DropdownMenu(
                    expanded = showMonthMenu,
                    onDismissRequest = { showMonthMenu = false },
                ) {
                    Month.entries.forEach { selectableMonth ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    selectableMonth.getDisplayName(TextStyle.FULL, locale)
                                        .replaceFirstChar(Char::titlecase),
                                )
                            },
                            onClick = {
                                showMonthMenu = false
                                onMonthSelected(YearMonth.of(month.year, selectableMonth))
                            },
                        )
                    }
                }
            }
            Box {
                TextButton(
                    onClick = { showYearMenu = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(month.year.toString(), style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(
                    expanded = showYearMenu,
                    onDismissRequest = { showYearMenu = false },
                ) {
                    selectableYears.reversed().forEach { selectableYear ->
                        DropdownMenuItem(
                            text = { Text(selectableYear.toString()) },
                            onClick = {
                                showYearMenu = false
                                onMonthSelected(YearMonth.of(selectableYear, month.month))
                            },
                        )
                    }
                }
            }
        }
        IconButton(next, Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.mov_next_month))
        }
    }
}

@Composable
private fun MovementList(
    groups: List<MovementDayGroup>,
    accounts: List<Account>,
    categories: List<Category>,
    baseCurrency: String,
    hideAmounts: Boolean,
    grayFuture: Boolean,
    onEditEntry: (String) -> Unit,
    onOpenAssetDetail: (String, String) -> Unit,
    onDuplicate: (Transaction) -> Unit,
    onDelete: (MovementItem) -> Unit,
) {
    val accountsById = remember(accounts) { accounts.associateBy(Account::id) }
    val categoriesById = remember(categories) { categories.associateBy(Category::id) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(groups, key = { it.date.toEpochDay() }) { group ->
            DayGroup(
                group, accountsById, categoriesById, baseCurrency, hideAmounts, grayFuture,
                onEditEntry, onOpenAssetDetail, onDuplicate, onDelete,
            )
        }
    }
}

@Composable
private fun DayGroup(
    group: MovementDayGroup,
    accountsById: Map<String, Account>,
    categoriesById: Map<String, Category>,
    baseCurrency: String,
    hideAmounts: Boolean,
    grayFuture: Boolean,
    onEditEntry: (String) -> Unit,
    onOpenAssetDetail: (String, String) -> Unit,
    onDuplicate: (Transaction) -> Unit,
    onDelete: (MovementItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    group.date.dayOfMonth.toString(),
                    Modifier.size(40.dp).padding(top = 9.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    group.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES")).replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (group.excludedCount > 0) {
                    Text(stringResource(R.string.mov_day_excluded, group.excludedCount), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.mov_day_balance), style = MaterialTheme.typography.labelSmall)
                if (hideAmounts) Text(stringResource(R.string.common_hidden_amount), style = MaterialTheme.typography.titleSmall)
                else AmountText(group.balanceMinor, baseCurrency, AmountKind.NEUTRAL, style = MaterialTheme.typography.titleSmall)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            group.items.forEach { item ->
                MovementRow(
                    item, accountsById, categoriesById, hideAmounts, grayFuture,
                    onOpen = {
                        when (item) {
                            is MovementItem.Investment -> onOpenAssetDetail(
                                item.operation.portfolioId, item.operation.assetId,
                            )
                            else -> onEditEntry(item.id())
                        }
                    },
                    onDuplicate = { (item as? MovementItem.Tx)?.transaction?.let(onDuplicate) },
                    onDelete = { onDelete(item) },
                )
            }
        }
    }
}

@Composable
private fun MovementRow(
    item: MovementItem,
    accountsById: Map<String, Account>,
    categoriesById: Map<String, Category>,
    hideAmounts: Boolean,
    grayFuture: Boolean,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val p = movementPresentation(item, accountsById, categoriesById)
    val isFuture = movementStatus(item.date, LocalDate.now()) == MovementStatus.PREVISTO
    val isRestrictedFuture = isFuture && grayFuture
    val primaryTextColor = if (isRestrictedFuture) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onOpen).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.extras.chipBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (item is MovementItem.Investment) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = stringResource(R.string.mov_investment_icon),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(p.initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.automatic) {
                    Surface(
                        Modifier.padding(start = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Notifications, null, Modifier.size(12.dp))
                            Text(stringResource(R.string.mov_auto), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(p.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (isFuture) {
                Text(
                    stringResource(R.string.mov_future),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hideAmounts) {
            Text(
                stringResource(R.string.common_hidden_amount),
                style = MaterialTheme.typography.titleSmall,
                color = primaryTextColor,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        else if (item is MovementItem.Move) {
            Text(
                p.neutralAmount.orEmpty(),
                modifier = Modifier.widthIn(max = 120.dp).horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.titleSmall,
                color = primaryTextColor,
                maxLines = 1,
            )
        } else {
            AmountText(
                item.amountMinor,
                p.currency,
                p.kind,
                modifier = Modifier.widthIn(max = 120.dp).horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.titleSmall,
                incomeColor = if (isRestrictedFuture) primaryTextColor else MaterialTheme.colorScheme.primary,
                expenseColor = if (isRestrictedFuture) primaryTextColor else MaterialTheme.extras.expense,
                neutralColor = primaryTextColor,
            )
        }
        if (item !is MovementItem.Investment) Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more_options)) }
            DropdownMenu(showMenu, { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_edit)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { showMenu = false; onOpen() },
                )
                if (item is MovementItem.Tx) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_duplicate)) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        onClick = { showMenu = false; onDuplicate() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { showMenu = false; onDelete() },
                )
            }
        }
    }
}

private data class MovementPresentation(
    val title: String,
    val subtitle: String,
    val initial: String,
    val currency: String,
    val kind: AmountKind,
    val automatic: Boolean,
    val neutralAmount: String? = null,
)

@Composable
private fun movementPresentation(
    item: MovementItem,
    accounts: Map<String, Account>,
    categories: Map<String, Category>,
): MovementPresentation = when (item) {
    is MovementItem.Tx -> {
        val tx = item.transaction
        val category = tx.categoryId?.let(categories::get)
        val parent = category?.parentId?.let(categories::get)
        val categoryLabel = when {
            category == null -> stringResource(R.string.mov_no_category)
            parent == null -> category.name
            else -> stringResource(R.string.mov_subcategory_label, category.name, parent.name)
        }
        val account = accounts[tx.accountId]?.name ?: stringResource(R.string.mov_unknown_account)
        val title = tx.description.ifBlank { tx.merchant.ifBlank { categoryLabel } }
        MovementPresentation(
            title, stringResource(R.string.mov_row_metadata, categoryLabel, account),
            category?.name?.firstOrNull()?.uppercase() ?: stringResource(R.string.common_generic_category_mark), tx.currency,
            if (tx.type == TransactionType.GASTO) AmountKind.EXPENSE else AmountKind.INCOME,
            item.isAutomatic,
        )
    }
    is MovementItem.Move -> {
        val transfer = item.transfer
        val from = accounts[transfer.fromAccountId]
        val to = accounts[transfer.toAccountId]
        val fromName = from?.name ?: stringResource(R.string.mov_unknown_account)
        val toName = to?.name ?: stringResource(R.string.mov_unknown_account)
        val fromAmount = MoneyMath.format(transfer.fromAmountMinor, from?.currency.orEmpty())
        val toAmount = MoneyMath.format(transfer.toAmountMinor, to?.currency.orEmpty())
        MovementPresentation(
            stringResource(R.string.mov_transfer_accounts, fromName, toName),
            transfer.description.ifBlank { stringResource(R.string.mov_transfer) },
            stringResource(R.string.common_transfer_mark), from?.currency.orEmpty(), AmountKind.NEUTRAL, false,
            if (from?.currency == to?.currency) fromAmount else stringResource(R.string.mov_transfer_amounts, fromAmount, toAmount),
        )
    }
    is MovementItem.Investment -> {
        val operation = item.operation
        val assetName = item.assetName.ifBlank { stringResource(R.string.mov_unknown_asset) }
        val portfolioName = item.portfolioName.ifBlank { stringResource(R.string.mov_unknown_portfolio) }
        val accountName = accounts[item.accountId]?.name ?: stringResource(R.string.mov_unknown_account)
        val titleResource = when (operation.type) {
            OperationType.COMPRA -> R.string.mov_investment_buy
            OperationType.VENTA -> R.string.mov_investment_sell
            OperationType.DIVIDENDO -> R.string.mov_investment_dividend
            OperationType.COMISION -> R.string.mov_investment_fee
            OperationType.TRASPASO_SALIDA, OperationType.TRASPASO_ENTRADA -> R.string.mov_investment_transfer
        }
        MovementPresentation(
            title = stringResource(titleResource, assetName),
            subtitle = stringResource(R.string.mov_investment_metadata, accountName, portfolioName),
            initial = "",
            currency = operation.currency,
            kind = AmountKind.NEUTRAL,
            automatic = false,
        )
    }
}

@Composable
private fun MovementFiltersDialog(
    filters: MovementFilters,
    accounts: List<Account>,
    categories: List<Category>,
    baseCurrency: String,
    onApply: (MovementFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    var minAmount by remember(filters, baseCurrency) { mutableStateOf(filters.minAmountMinor.toInputAmount(baseCurrency)) }
    var maxAmount by remember(filters, baseCurrency) { mutableStateOf(filters.maxAmountMinor.toInputAmount(baseCurrency)) }
    var amountError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mov_filters)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    label = stringResource(R.string.mov_filter_kind), options = KindFilter.entries,
                    selected = draft.kind, optionLabel = { it.label() },
                    onSelected = { it?.let { kind -> draft = draft.copy(kind = kind) } },
                )
                DropdownField(
                    label = stringResource(R.string.mov_filter_source), options = SourceFilter.entries,
                    selected = draft.source, optionLabel = { it.label() },
                    onSelected = { it?.let { source -> draft = draft.copy(source = source) } },
                )
                DropdownField(
                    label = stringResource(R.string.mov_filter_account), options = accounts,
                    selected = accounts.find { it.id == draft.accountId }, optionLabel = { it.name },
                    onSelected = { draft = draft.copy(accountId = it?.id) },
                    noneLabel = stringResource(R.string.mov_filter_all_accounts),
                )
                DropdownField(
                    label = stringResource(R.string.mov_filter_category), options = categories,
                    selected = categories.find { it.id == draft.categoryId }, optionLabel = { it.name },
                    onSelected = { draft = draft.copy(categoryId = it?.id) },
                    noneLabel = stringResource(R.string.mov_filter_all_categories),
                )
                OptionalDateField(stringResource(R.string.mov_filter_from), stringResource(R.string.mov_filter_add_from), draft.from) {
                    draft = draft.copy(from = it)
                }
                OptionalDateField(stringResource(R.string.mov_filter_to), stringResource(R.string.mov_filter_add_to), draft.to) {
                    draft = draft.copy(to = it)
                }
                AmountField(stringResource(R.string.mov_filter_min_amount), minAmount, { minAmount = it; amountError = false }, suffix = baseCurrency, isError = amountError)
                AmountField(stringResource(R.string.mov_filter_max_amount), maxAmount, { maxAmount = it; amountError = false }, suffix = baseCurrency, isError = amountError)
                DropdownField(
                    label = stringResource(R.string.mov_filter_sort), options = MovementSort.entries,
                    selected = draft.sort, optionLabel = { it.label() },
                    onSelected = { it?.let { sort -> draft = draft.copy(sort = sort) } },
                )
                if (amountError) Text(stringResource(R.string.mov_filter_invalid_amount), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val min = minAmount.parseFilterAmount(baseCurrency)
                val max = maxAmount.parseFilterAmount(baseCurrency)
                if ((minAmount.isNotBlank() && min == null) || (maxAmount.isNotBlank() && max == null)) amountError = true
                else onApply(draft.copy(minAmountMinor = min, maxAmountMinor = max))
            }) { Text(stringResource(R.string.mov_apply_filters)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun OptionalDateField(label: String, addLabel: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    if (date == null) {
        OutlinedButton(onClick = { onChange(LocalDate.now()) }, modifier = Modifier.fillMaxWidth()) { Text(addLabel) }
    } else {
        Column {
            DateField(label, date, onChange = onChange)
            TextButton(onClick = { onChange(null) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.mov_filter_remove_date))
            }
        }
    }
}

@Composable
private fun SourceFilter.label() = stringResource(when (this) {
    SourceFilter.TODOS -> R.string.mov_source_all
    SourceFilter.MANUAL -> R.string.mov_source_manual
    SourceFilter.AUTOMATICOS -> R.string.mov_source_automatic
    SourceFilter.IMPORTADOS -> R.string.mov_source_imported
    SourceFilter.RECURRENTES -> R.string.mov_source_recurring
})

@Composable
private fun KindFilter.label() = stringResource(when (this) {
    KindFilter.TODOS -> R.string.mov_kind_all
    KindFilter.INGRESOS -> R.string.mov_kind_income
    KindFilter.GASTOS -> R.string.mov_kind_expenses
    KindFilter.TRANSFERENCIAS -> R.string.mov_kind_transfers
    KindFilter.INVERSIONES -> R.string.mov_kind_investments
})

@Composable
private fun MovementSort.label() = stringResource(when (this) {
    MovementSort.FECHA_DESC -> R.string.mov_sort_date_desc
    MovementSort.FECHA_ASC -> R.string.mov_sort_date_asc
    MovementSort.IMPORTE_DESC -> R.string.mov_sort_amount_desc
    MovementSort.IMPORTE_ASC -> R.string.mov_sort_amount_asc
})

private fun MovementItem.id() = when (this) {
    is MovementItem.Tx -> transaction.id
    is MovementItem.Move -> transfer.id
    is MovementItem.Investment -> operation.id
}

private fun Long?.toInputAmount(currency: String) = this?.let {
    MoneyMath.toDecimal(it, currency).stripTrailingZeros().toPlainString()
}.orEmpty()

private fun String.parseFilterAmount(currency: String): Long? {
    if (isBlank()) return null
    return runCatching { MoneyMath.parse(this)?.let { MoneyMath.toMinor(it, currency) } }.getOrNull()
}
