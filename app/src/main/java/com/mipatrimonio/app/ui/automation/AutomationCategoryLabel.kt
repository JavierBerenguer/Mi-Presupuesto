package com.mipatrimonio.app.ui.automation

import com.mipatrimonio.app.domain.model.Category

internal fun automationCategoryLabel(
    category: Category?,
    categories: List<Category>,
    unassignedLabel: String,
): String {
    if (category == null) return unassignedLabel
    val parent = category.parentId?.let { parentId -> categories.find { it.id == parentId } }
    return if (parent == null) category.name else "${parent.name} › ${category.name}"
}

internal fun selectableAutomationCategories(categories: List<Category>): List<Category> =
    categories.filterNot(Category::archived)
