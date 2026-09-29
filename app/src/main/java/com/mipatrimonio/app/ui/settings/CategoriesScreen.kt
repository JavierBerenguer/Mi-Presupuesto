package com.mipatrimonio.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel

private val categoryColorPalette = listOf(
    0xFFEF8354,
    0xFF6C8EAD,
    0xFFE9C46A,
    0xFFB56576,
    0xFF52B788,
    0xFF9D84B7,
    0xFF4EA8DE,
    0xFF8D99AE,
    0xFF3DDC97,
    0xFF80ED99,
    0xFF57CC99,
    0xFF9EE493,
)

private data class DeleteTargetOption(val categoryId: String?, val label: String)

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = appViewModel { c -> CategoriesViewModel(c.ledger) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingBox()
        return
    }

    var selectedKind by remember { mutableStateOf(CategoryKind.GASTO) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var categoryToArchive by remember { mutableStateOf<Category?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val tree = buildCategoryDisplayTree(
        categories = state.categories,
        kind = selectedKind,
        expandedCategoryIds = state.expandedCategoryIds,
        query = query,
    )

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (selectedKind == CategoryKind.GASTO) 0 else 1) {
            Tab(
                selected = selectedKind == CategoryKind.GASTO,
                onClick = { selectedKind = CategoryKind.GASTO },
                text = { Text(stringResource(R.string.aj_expenses)) },
            )
            Tab(
                selected = selectedKind == CategoryKind.INGRESO,
                onClick = { selectedKind = CategoryKind.INGRESO },
                text = { Text(stringResource(R.string.aj_income)) },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.aj_search_categories)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.aj_clear_category_search),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.fillMaxSize()) {
            if (tree.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Category,
                    message = if (query.isNotBlank()) {
                        stringResource(R.string.aj_no_categories_found)
                    } else {
                        stringResource(
                            if (selectedKind == CategoryKind.GASTO) {
                                R.string.aj_no_expense_categories
                            } else {
                                R.string.aj_no_income_categories
                            },
                        )
                    },
                    actionLabel = stringResource(R.string.aj_new_category),
                    onAction = {
                        viewModel.clearFormError()
                        showCreateDialog = true
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tree.forEach { node ->
                        item(key = node.category.id) {
                            CategoryRow(
                                category = node.category,
                                indented = false,
                                childCount = node.childCount,
                                expanded = node.isExpanded,
                                onToggleExpanded = {
                                    viewModel.toggleCategoryExpanded(node.category.id)
                                },
                                onEdit = {
                                    viewModel.clearFormError()
                                    editingCategory = node.category
                                },
                                onArchive = { categoryToArchive = node.category },
                                onDelete = { viewModel.requestDelete(node.category) },
                            )
                        }
                        items(node.children, key = { it.id }) { child ->
                            CategoryRow(
                                category = child,
                                indented = true,
                                onEdit = {
                                    viewModel.clearFormError()
                                    editingCategory = child
                                },
                                onArchive = { categoryToArchive = child },
                                onDelete = { viewModel.requestDelete(child) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.clearFormError()
                    showCreateDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.aj_new_category)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (showCreateDialog) {
        CategoryFormDialog(
            category = null,
            kind = selectedKind,
            categories = state.categories,
            error = state.formError,
            onDismiss = {
                viewModel.clearFormError()
                showCreateDialog = false
            },
            onSave = { name, parentId, color ->
                viewModel.saveCategory(null, selectedKind, name, parentId, color) {
                    showCreateDialog = false
                }
            },
        )
    }
    editingCategory?.let { category ->
        CategoryFormDialog(
            category = category,
            kind = category.kind,
            categories = state.categories,
            error = state.formError,
            onDismiss = {
                viewModel.clearFormError()
                editingCategory = null
            },
            onSave = { name, parentId, color ->
                viewModel.saveCategory(category, category.kind, name, parentId, color) {
                    editingCategory = null
                }
            },
        )
    }
    categoryToArchive?.let { category ->
        val willArchive = !category.archived
        val message = when {
            !willArchive -> R.string.aj_restore_category_message
            category.parentId == null -> R.string.aj_archive_root_category_message
            else -> R.string.aj_archive_category_message
        }
        ConfirmDialog(
            title = stringResource(
                if (willArchive) R.string.aj_archive_category_title else R.string.aj_restore_category_title,
            ),
            text = stringResource(message, category.name),
            confirmLabel = stringResource(if (willArchive) R.string.common_archive else R.string.common_restore),
            onConfirm = {
                viewModel.setArchived(category, willArchive) { categoryToArchive = null }
            },
            onDismiss = { categoryToArchive = null },
        )
    }
    state.deletion?.let { deletion ->
        CategoryDeleteDialog(
            state = deletion,
            onTargetSelected = viewModel::selectDeleteTarget,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    indented: Boolean,
    childCount: Int = 0,
    expanded: Boolean = false,
    onToggleExpanded: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 24.dp else 0.dp),
    ) {
        Card(Modifier.fillMaxWidth().alpha(if (category.archived) 0.5f else 1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(category.colorArgb.toInt())),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                    if (childCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.aj_subcategory_count, childCount, childCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (category.archived) {
                        Text(
                            stringResource(R.string.aj_archived),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (childCount > 0 && onToggleExpanded != null) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.mov_collapse_category else R.string.mov_expand_category,
                            ),
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.aj_edit_category, category.name),
                    )
                }
                IconButton(onClick = onArchive) {
                    Icon(
                        if (category.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = stringResource(
                            if (category.archived) R.string.aj_restore_category else R.string.aj_archive_category,
                            category.name,
                        ),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.aj_delete_category, category.name),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryDeleteDialog(
    state: CategoryDeletionState,
    onTargetSelected: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val withoutCategoryLabel = stringResource(R.string.aj_without_category)
    val targetOptions = buildList {
        if (state.usage.budgets == 0) {
            add(DeleteTargetOption(null, withoutCategoryLabel))
        }
        addAll(state.targets.map { DeleteTargetOption(it.category.id, it.label) })
    }
    val selected = targetOptions.firstOrNull { state.targetSelected && it.categoryId == state.targetId }
    AlertDialog(
        onDismissRequest = { if (!state.isDeleting) onDismiss() },
        title = { Text(stringResource(R.string.aj_delete_category_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(
                        if (state.usage.isUsed) {
                            R.string.aj_delete_category_used_message
                        } else {
                            R.string.aj_delete_category_unused_message
                        },
                        state.category.name,
                    ),
                )
                if (state.usage.subcategories > 0) {
                    Text(stringResource(R.string.aj_delete_category_children, state.usage.subcategories))
                }
                if (state.usage.isUsed) {
                    Text(
                        stringResource(
                            R.string.aj_delete_category_counts,
                            state.usage.transactions,
                            state.usage.transfers,
                            state.usage.recurringRules,
                            state.usage.budgets,
                            state.usage.pendingProposals,
                        ),
                    )
                    DropdownField(
                        label = stringResource(R.string.aj_delete_category_target),
                        options = targetOptions,
                        selected = selected,
                        optionLabel = { it.label },
                        onSelected = { option -> option?.let { onTargetSelected(it.categoryId) } },
                        enabled = !state.isDeleting,
                    )
                    if (state.usage.budgets > 0) {
                        Text(
                            stringResource(R.string.aj_delete_category_budget_restriction),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                state.error?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isDeleting && (!state.usage.isUsed || state.targetSelected),
            ) { Text(stringResource(R.string.common_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isDeleting) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun CategoryFormDialog(
    category: Category?,
    kind: CategoryKind,
    categories: List<Category>,
    error: CategoryFormError?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Long) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var parentId by remember(category?.id) { mutableStateOf(category?.parentId) }
    var colorArgb by remember(category?.id) {
        mutableStateOf(category?.colorArgb ?: categoryColorPalette.first())
    }
    val hasChildren = category != null && categories.any { it.parentId == category.id }
    val parentOptions = categories.filter { candidate ->
        candidate.kind == kind &&
            candidate.parentId == null &&
            !candidate.archived &&
            candidate.id != category?.id &&
            !hasChildren
    }
    val selectedParent = parentOptions.firstOrNull { it.id == parentId }
    val errorText = when (error) {
        CategoryFormError.BlankName -> stringResource(R.string.aj_error_blank_category_name)
        CategoryFormError.InvalidParent -> stringResource(R.string.aj_error_invalid_parent)
        is CategoryFormError.Repository -> error.message.ifBlank { stringResource(R.string.aj_error_saving) }
        null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (category == null) R.string.aj_create_category else R.string.aj_edit_category_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.aj_name)) },
                    singleLine = true,
                    isError = error == CategoryFormError.BlankName,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = stringResource(R.string.aj_parent_category),
                    options = parentOptions,
                    selected = selectedParent,
                    optionLabel = { it.name },
                    onSelected = { parentId = it?.id },
                    noneLabel = stringResource(R.string.common_none),
                )
                if (hasChildren) {
                    Text(
                        stringResource(R.string.aj_category_with_children),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.aj_color), style = MaterialTheme.typography.labelLarge)
                categoryColorPalette.chunked(6).forEachIndexed { rowIndex, colors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        colors.forEachIndexed { columnIndex, color ->
                            val colorNumber = rowIndex * 6 + columnIndex + 1
                            val isSelected = colorArgb == color
                            val description = stringResource(R.string.aj_select_color, colorNumber)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(color.toInt()))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { colorArgb = color }
                                    .semantics {
                                        contentDescription = description
                                        selected = isSelected
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, parentId, colorArgb) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
