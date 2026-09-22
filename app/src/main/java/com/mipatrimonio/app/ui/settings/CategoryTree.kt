package com.mipatrimonio.app.ui.settings

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind

data class CategoryNode(
    val category: Category,
    val children: List<Category>,
)

fun buildTree(categories: List<Category>, kind: CategoryKind): List<CategoryNode> {
    val filtered = categories.filter { it.kind == kind }
    val byId = filtered.associateBy(Category::id)
    val roots = filtered.filter { category ->
        val parent = category.parentId?.let(byId::get)
        parent == null || parent.parentId != null
    }.sortedBy(Category::archived)
    val rootIds = roots.mapTo(mutableSetOf(), Category::id)

    return roots.map { root ->
        CategoryNode(
            category = root,
            children = filtered
                .filter { it.id !in rootIds && it.parentId == root.id }
                .sortedBy(Category::archived),
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
    if (category.kind != parent.kind) return false
    return categories.none { it.parentId == category.id }
}
