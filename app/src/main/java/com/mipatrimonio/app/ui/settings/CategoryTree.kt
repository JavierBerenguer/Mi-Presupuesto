package com.mipatrimonio.app.ui.settings

import com.mipatrimonio.app.domain.model.Category

data class CategoryNode(
    val category: Category,
    val children: List<Category>,
)

data class CategoryDisplayNode(
    val category: Category,
    val children: List<Category>,
    val childCount: Int,
    val isExpanded: Boolean,
)

data class CategoryDeleteTarget(
    val category: Category,
    val label: String,
)

fun buildTree(categories: List<Category>): List<CategoryNode> {
    val byId = categories.associateBy(Category::id)
    val roots = categories.filter { category ->
        val parent = category.parentId?.let(byId::get)
        parent == null || parent.parentId != null
    }.sortedBy(Category::archived)
    val rootIds = roots.mapTo(mutableSetOf(), Category::id)

    return roots.map { root ->
        CategoryNode(
            category = root,
            children = categories
                .filter { it.id !in rootIds && it.parentId == root.id }
                .sortedBy(Category::archived),
        )
    }
}

fun buildCategoryDisplayTree(
    categories: List<Category>,
    expandedCategoryIds: Set<String>,
    query: String = "",
): List<CategoryDisplayNode> {
    val normalizedQuery = query.trim()
    return buildTree(categories).mapNotNull { node ->
        val matchingChildren = if (normalizedQuery.isBlank() || node.category.name.contains(normalizedQuery, true)) {
            node.children
        } else {
            node.children.filter { it.name.contains(normalizedQuery, true) }
        }
        val matches = normalizedQuery.isBlank() ||
            node.category.name.contains(normalizedQuery, true) ||
            matchingChildren.isNotEmpty()
        if (!matches) return@mapNotNull null

        val isExpanded = node.children.isNotEmpty() &&
            (normalizedQuery.isNotBlank() || node.category.id in expandedCategoryIds)
        CategoryDisplayNode(
            category = node.category,
            children = if (isExpanded) matchingChildren else emptyList(),
            childCount = node.children.size,
            isExpanded = isExpanded,
        )
    }
}

fun canSetParent(
    categoryId: String?,
    parentId: String?,
    categories: List<Category>,
): Boolean {
    if (parentId == null) return true
    if (categoryId == parentId) return false

    val parent = categories.firstOrNull { it.id == parentId } ?: return false
    if (parent.archived || parent.parentId != null) return false

    val category = categoryId?.let { id -> categories.firstOrNull { it.id == id } } ?: return true
    return categories.none { it.parentId == category.id }
}

fun categoryDeleteTargets(
    categoryId: String,
    categories: List<Category>,
): List<CategoryDeleteTarget> {
    val excludedIds = categories
        .filter { it.id == categoryId || it.parentId == categoryId }
        .mapTo(mutableSetOf()) { it.id }
    val active = categories.filter { !it.archived && it.id !in excludedIds }
    val byId = active.associateBy { it.id }
    return buildTree(active).flatMap { node ->
        listOf(CategoryDeleteTarget(node.category, node.category.name)) + node.children.map { child ->
            CategoryDeleteTarget(child, "${node.category.name} · ${child.name}")
        }
    }.filter { target ->
        val parentId = target.category.parentId
        parentId == null || parentId in byId
    }
}
