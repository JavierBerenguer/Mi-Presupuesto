package com.mipatrimonio.app.ui.movements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.ui.components.SegmentedControl

private enum class CategoryTab { FREQUENT, TREE, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerScreen(
    categories: List<Category>,
    frequentCategories: List<Category>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val tabs = if (frequentCategories.isEmpty()) {
        listOf(CategoryTab.TREE, CategoryTab.ALPHABETICAL)
    } else {
        listOf(CategoryTab.FREQUENT, CategoryTab.TREE, CategoryTab.ALPHABETICAL)
    }
    var selectedTab by remember(tabs) { mutableStateOf(tabs.first()) }
    val expanded = remember { mutableStateListOf<String>() }
    val matches = remember(categories, query) {
        if (query.isBlank()) categories else categories.filter { category ->
            categoryPath(category, categories).contains(query.trim(), ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mov_choose_category)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onSelected(null) }) {
                        Text(stringResource(R.string.mov_clear_category))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.mov_search_categories)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.mov_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            SegmentedControl(
                options = tabs.map { tab ->
                    stringResource(
                        when (tab) {
                            CategoryTab.FREQUENT -> R.string.mov_categories_frequent
                            CategoryTab.TREE -> R.string.mov_categories_tree
                            CategoryTab.ALPHABETICAL -> R.string.mov_categories_alphabetical
                        },
                    )
                },
                selectedIndex = tabs.indexOf(selectedTab),
                onSelected = { selectedTab = tabs[it] },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            when {
                matches.isEmpty() -> {
                    Text(
                        stringResource(R.string.mov_no_categories_found),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                query.isNotBlank() -> CategoryList(
                    categories = matches.sortedBy { categoryPath(it, categories).lowercase() },
                    allCategories = categories,
                    selectedId = selectedId,
                    onSelected = onSelected,
                )
                selectedTab == CategoryTab.FREQUENT -> CategoryList(
                    categories = frequentCategories,
                    allCategories = categories,
                    selectedId = selectedId,
                    onSelected = onSelected,
                )
                selectedTab == CategoryTab.ALPHABETICAL -> CategoryList(
                    categories = categories.sortedBy { categoryPath(it, categories).lowercase() },
                    allCategories = categories,
                    selectedId = selectedId,
                    onSelected = onSelected,
                )
                else -> CategoryTree(
                    categories = categories,
                    selectedId = selectedId,
                    expanded = expanded,
                    onSelected = onSelected,
                )
            }
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    allCategories: List<Category>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CategoryRow(
                title = stringResource(R.string.mov_category_unassigned),
                selected = selectedId == null,
                onClick = { onSelected(null) },
            )
        }
        items(categories, key = Category::id) { category ->
            CategoryRow(
                title = alphabeticalCategoryName(category, allCategories),
                selected = category.id == selectedId,
                onClick = { onSelected(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryTree(
    categories: List<Category>,
    selectedId: String?,
    expanded: MutableList<String>,
    onSelected: (String?) -> Unit,
) {
    val roots = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
    val children = categories.filter { it.parentId != null }.groupBy(Category::parentId)
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CategoryRow(
                title = stringResource(R.string.mov_category_unassigned),
                selected = selectedId == null,
                onClick = { onSelected(null) },
            )
        }
        roots.forEach { parent ->
            val subcategories = children[parent.id].orEmpty().sortedBy { it.name.lowercase() }
            item(key = parent.id) {
                CategoryRow(
                    title = parent.name,
                    selected = parent.id == selectedId,
                    onClick = { onSelected(parent.id) },
                    trailing = if (subcategories.isEmpty()) null else {
                        {
                            IconButton(onClick = {
                                if (parent.id in expanded) expanded.remove(parent.id) else expanded.add(parent.id)
                            }) {
                                Icon(
                                    if (parent.id in expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = stringResource(
                                        if (parent.id in expanded) R.string.mov_collapse_category else R.string.mov_expand_category,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            if (parent.id in expanded) {
                items(subcategories, key = Category::id) { child ->
                    CategoryRow(
                        title = child.name,
                        selected = child.id == selectedId,
                        onClick = { onSelected(child.id) },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        ListItem(
            headlineContent = {
                Text(
                    title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
            trailingContent = trailing,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

internal fun categoryPath(category: Category, categories: List<Category>): String {
    val parent = category.parentId?.let { id -> categories.find { it.id == id } }
    return if (parent == null) category.name else "${parent.name} › ${category.name}"
}

private fun alphabeticalCategoryName(category: Category, categories: List<Category>): String {
    val parent = category.parentId?.let { id -> categories.find { it.id == id } }
    return if (parent == null) category.name else "${category.name} (${parent.name})"
}
