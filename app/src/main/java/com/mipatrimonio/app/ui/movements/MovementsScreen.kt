package com.mipatrimonio.app.ui.movements

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import java.time.LocalDate

@Composable
fun MovementsScreen(
    onNewTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onNewTransfer: () -> Unit,
    onEditTransfer: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: MovementsViewModel = appViewModel { c -> MovementsViewModel(c.ledger, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MovementItem?>(null) }

    if (state.isLoading) {
        LoadingBox()
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
        Column(Modifier.fillMaxSize()) {
            SearchAndFilters(
                state = state,
                onQueryChange = viewModel::setQuery,
                onKindChange = viewModel::setKind,
                onOpenFilters = { showFilters = true },
                onClearFilters = viewModel::clearFilters,
                onClearError = viewModel::clearError,
            )
            Box(Modifier.weight(1f)) {
                when {
                    state.allItems.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        message = stringResource(R.string.mov_no_movements),
                    )
                    state.visibleItems.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.Search,
                        message = stringResource(R.string.mov_no_results),
                        actionLabel = stringResource(R.string.mov_clear_filters),
                        onAction = viewModel::clearFilters,
                    )
                    else -> MovementList(
                        items = state.visibleItems,
                        accounts = state.accounts,
                        categories = state.categories,
                        onEditTransaction = onEditTransaction,
                        onEditTransfer = onEditTransfer,
                        onDuplicate = viewModel::duplicate,
                        onDelete = { pendingDelete = it },
                    )
                }
            }
        }
        NewMovementButton(
            transferEnabled = state.activeAccounts.size >= 2,
            onNewTransaction = onNewTransaction,
            onNewTransfer = onNewTransfer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showFilters) {
        MovementFiltersDialog(
            filters = state.filters,
            accounts = state.accounts,
            categories = state.categories,
            baseCurrency = state.baseCurrency,
            onApply = {
                viewModel.setFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.mov_delete_title),
            text = stringResource(R.string.mov_delete_message),
            onConfirm = {
                viewModel.delete(item)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SearchAndFilters(
    state: MovementsUiState,
    onQueryChange: (String) -> Unit,
    onKindChange: (KindFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.mov_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(KindFilter.entries) { kind ->
                FilterChip(
                    selected = state.filters.kind == kind,
                    onClick = { onKindChange(kind) },
                    label = { Text(kind.label()) },
                )
            }
            item {
                OutlinedButton(onClick = onOpenFilters) {
                    Icon(Icons.Outlined.FilterList, contentDescription = null)
                    Text(stringResource(R.string.mov_filters), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (state.hasActiveFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.mov_filters_active), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onClearFilters) { Text(stringResource(R.string.mov_clear_filters)) }
            }
        }
        state.error?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                IconButton(onClick = onClearError) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.mov_dismiss_error),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementList(
    items: List<MovementItem>,
    accounts: List<Account>,
    categories: List<Category>,
    onEditTransaction: (String) -> Unit,
    onEditTransfer: (String) -> Unit,
    onDuplicate: (com.mipatrimonio.app.domain.model.Transaction) -> Unit,
    onDelete: (MovementItem) -> Unit,
) {
    val accountsById = remember(accounts) { accounts.associateBy(Account::id) }
    val categoriesById = remember(categories) { categories.associateBy(Category::id) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { item -> item.key() }) { item ->
            MovementRow(
                item = item,
                accountsById = accountsById,
                categoriesById = categoriesById,
                onEdit = {
                    when (item) {
                        is MovementItem.Tx -> onEditTransaction(item.transaction.id)
                        is MovementItem.Move -> onEditTransfer(item.transfer.id)
                    }
                },
                onDuplicate = { (item as? MovementItem.Tx)?.transaction?.let(onDuplicate) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun MovementRow(
    item: MovementItem,
    accountsById: Map<String, Account>,
    categoriesById: Map<String, Category>,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val presentation = movementPresentation(item, accountsById, categoriesById)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = presentation.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = presentation.amount,
                color = presentation.color,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.common_more_options),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    if (item is MovementItem.Tx) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_duplicate)) },
                            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private data class MovementPresentation(
    val title: String,
    val subtitle: String,
    val amount: String,
    val color: Color,
)

@Composable
private fun movementPresentation(
    item: MovementItem,
    accountsById: Map<String, Account>,
    categoriesById: Map<String, Category>,
): MovementPresentation = when (item) {
    is MovementItem.Tx -> {
        val transaction = item.transaction
        val categoryName = transaction.categoryId?.let(categoriesById::get)?.name
        val accountName = accountsById[transaction.accountId]?.name
            ?: stringResource(R.string.mov_unknown_account)
        val title = transaction.description.ifBlank {
            transaction.merchant.ifBlank { categoryName ?: stringResource(R.string.mov_no_description) }
        }
        val formattedAmount = MoneyMath.format(transaction.amountMinor, transaction.currency)
        MovementPresentation(
            title = title,
            subtitle = stringResource(
                R.string.mov_transaction_subtitle,
                categoryName ?: stringResource(R.string.mov_no_category),
                accountName,
                formatDate(transaction.date),
            ),
            amount = stringResource(
                if (transaction.type == TransactionType.GASTO) {
                    R.string.mov_negative_amount
                } else {
                    R.string.mov_positive_amount
                },
                formattedAmount,
            ),
            color = if (transaction.type == TransactionType.GASTO) MoneyColors.negative else MoneyColors.positive,
        )
    }
    is MovementItem.Move -> {
        val transfer = item.transfer
        val from = accountsById[transfer.fromAccountId]
        val to = accountsById[transfer.toAccountId]
        val fromName = from?.name ?: stringResource(R.string.mov_unknown_account)
        val toName = to?.name ?: stringResource(R.string.mov_unknown_account)
        val fromAmount = MoneyMath.format(transfer.fromAmountMinor, from?.currency.orEmpty())
        val toAmount = MoneyMath.format(transfer.toAmountMinor, to?.currency.orEmpty())
        MovementPresentation(
            title = transfer.description.ifBlank { stringResource(R.string.mov_transfer) },
            subtitle = stringResource(R.string.mov_transfer_subtitle, fromName, toName, formatDate(transfer.date)),
            amount = if (from?.currency == to?.currency) {
                fromAmount
            } else {
                stringResource(R.string.mov_transfer_amounts, fromAmount, toAmount)
            },
            color = Color.Unspecified,
        )
    }
}

@Composable
private fun NewMovementButton(
    transferEnabled: Boolean,
    onNewTransaction: () -> Unit,
    onNewTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.mov_new))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mov_new_transaction)) },
                onClick = {
                    expanded = false
                    onNewTransaction()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.mov_new_transfer))
                        if (!transferEnabled) {
                            Text(
                                stringResource(R.string.mov_transfer_disabled),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                enabled = transferEnabled,
                onClick = {
                    expanded = false
                    onNewTransfer()
                },
            )
        }
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
    var minAmount by remember(filters, baseCurrency) {
        mutableStateOf(filters.minAmountMinor.toInputAmount(baseCurrency))
    }
    var maxAmount by remember(filters, baseCurrency) {
        mutableStateOf(filters.maxAmountMinor.toInputAmount(baseCurrency))
    }
    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mov_filters)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DropdownField(
                    label = stringResource(R.string.mov_filter_account),
                    options = accounts,
                    selected = accounts.find { it.id == draft.accountId },
                    optionLabel = { it.name },
                    onSelected = { draft = draft.copy(accountId = it?.id) },
                    noneLabel = stringResource(R.string.mov_filter_all_accounts),
                )
                DropdownField(
                    label = stringResource(R.string.mov_filter_category),
                    options = categories,
                    selected = categories.find { it.id == draft.categoryId },
                    optionLabel = { category -> categoryPathLabel(category, categories) },
                    onSelected = { draft = draft.copy(categoryId = it?.id) },
                    noneLabel = stringResource(R.string.mov_filter_all_categories),
                )
                OptionalDateField(
                    label = stringResource(R.string.mov_filter_from),
                    addLabel = stringResource(R.string.mov_filter_add_from),
                    date = draft.from,
                    onChange = { draft = draft.copy(from = it) },
                )
                OptionalDateField(
                    label = stringResource(R.string.mov_filter_to),
                    addLabel = stringResource(R.string.mov_filter_add_to),
                    date = draft.to,
                    onChange = { draft = draft.copy(to = it) },
                )
                AmountField(
                    label = stringResource(R.string.mov_filter_min_amount),
                    value = minAmount,
                    onChange = {
                        minAmount = it
                        amountError = false
                    },
                    suffix = baseCurrency,
                    isError = amountError,
                )
                AmountField(
                    label = stringResource(R.string.mov_filter_max_amount),
                    value = maxAmount,
                    onChange = {
                        maxAmount = it
                        amountError = false
                    },
                    suffix = baseCurrency,
                    isError = amountError,
                )
                DropdownField(
                    label = stringResource(R.string.mov_filter_sort),
                    options = MovementSort.entries,
                    selected = draft.sort,
                    optionLabel = { it.label() },
                    onSelected = { selected -> selected?.let { draft = draft.copy(sort = it) } },
                )
                if (amountError) {
                    Text(
                        stringResource(R.string.mov_filter_invalid_amount),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedMin = minAmount.parseFilterAmount(baseCurrency)
                val parsedMax = maxAmount.parseFilterAmount(baseCurrency)
                if ((minAmount.isNotBlank() && parsedMin == null) ||
                    (maxAmount.isNotBlank() && parsedMax == null)
                ) {
                    amountError = true
                } else {
                    onApply(draft.copy(minAmountMinor = parsedMin, maxAmountMinor = parsedMax))
                }
            }) { Text(stringResource(R.string.mov_apply_filters)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun OptionalDateField(
    label: String,
    addLabel: String,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
) {
    if (date == null) {
        OutlinedButton(onClick = { onChange(LocalDate.now()) }, modifier = Modifier.fillMaxWidth()) {
            Text(addLabel)
        }
    } else {
        Column {
            DateField(label = label, date = date, onChange = { onChange(it) })
            TextButton(onClick = { onChange(null) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.mov_filter_remove_date))
            }
        }
    }
}

@Composable
private fun KindFilter.label(): String = stringResource(
    when (this) {
        KindFilter.TODOS -> R.string.mov_kind_all
        KindFilter.INGRESOS -> R.string.mov_kind_income
        KindFilter.GASTOS -> R.string.mov_kind_expenses
        KindFilter.TRANSFERENCIAS -> R.string.mov_kind_transfers
    },
)

@Composable
private fun MovementSort.label(): String = stringResource(
    when (this) {
        MovementSort.FECHA_DESC -> R.string.mov_sort_date_desc
        MovementSort.FECHA_ASC -> R.string.mov_sort_date_asc
        MovementSort.IMPORTE_DESC -> R.string.mov_sort_amount_desc
        MovementSort.IMPORTE_ASC -> R.string.mov_sort_amount_asc
    },
)

@Composable
private fun categoryPathLabel(category: Category, categories: List<Category>): String {
    val parent = category.parentId?.let { parentId -> categories.find { it.id == parentId } }
    return if (parent == null) {
        category.name
    } else {
        stringResource(R.string.mov_category_path, categoryPathLabel(parent, categories), category.name)
    }
}

private fun MovementItem.key(): String = when (this) {
    is MovementItem.Tx -> "tx:${transaction.id}"
    is MovementItem.Move -> "transfer:${transfer.id}"
}

private fun Long?.toInputAmount(currency: String): String = this?.let {
    MoneyMath.toDecimal(it, currency).stripTrailingZeros().toPlainString()
}.orEmpty()

private fun String.parseFilterAmount(currency: String): Long? {
    if (isBlank()) return null
    return runCatching { MoneyMath.parse(this)?.let { value -> MoneyMath.toMinor(value, currency) } }.getOrNull()
}
