package com.mipatrimonio.app.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.ui.components.SecondaryTopBar

private enum class CategoryTab { FREQUENT, TREE, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerScreen(
    categories: List<Category>,
    frequentCategories: List<Category>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    onBack: () -> Unit,
    onNewCategory: () -> Unit,
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
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SecondaryTopBar(title = stringResource(R.string.mov_choose_category), onBack = onBack)
        },
        bottomBar = {
            CategoryActionBar(
                onUnassigned = { onSelected(null) },
                onNewCategory = onNewCategory,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
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
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            UnderlineTabs(
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Box(Modifier.weight(1f)) {
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
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedTab == CategoryTab.FREQUENT -> CategoryList(
                        categories = frequentCategories,
                        allCategories = categories,
                        selectedId = selectedId,
                        onSelected = onSelected,
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedTab == CategoryTab.ALPHABETICAL -> CategoryList(
                        categories = categories.sortedBy { categoryPath(it, categories).lowercase() },
                        allCategories = categories,
                        selectedId = selectedId,
                        onSelected = onSelected,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> CategoryTree(
                        categories = categories,
                        selectedId = selectedId,
                        expanded = expanded,
                        onSelected = onSelected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
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
    modifier: Modifier = Modifier,
) {
    val roots = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
    val children = categories.filter { it.parentId != null }.groupBy(Category::parentId)
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
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
                        modifier = Modifier.padding(start = 28.dp),
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
    val isSelected = selected
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { this.selected = isSelected }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (isSelected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            trailing?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun UnderlineTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Tab) { onSelected(index) }
                    .semantics { selected = isSelected },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        option,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(2.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun CategoryActionBar(onUnassigned: () -> Unit, onNewCategory: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onUnassigned,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.mov_category_unassigned), maxLines = 2, textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onNewCategory,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.aj_new_category),
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
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
