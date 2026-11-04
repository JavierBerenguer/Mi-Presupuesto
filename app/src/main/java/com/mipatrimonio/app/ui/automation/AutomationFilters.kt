package com.mipatrimonio.app.ui.automation

import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.notifications.NotificationRule
import java.text.Normalizer
import java.util.Locale

internal fun normalizeAutomationSearch(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .trim()

internal fun filterStructures(
    groups: List<StructureGroup>,
    query: String,
    applicationNames: Map<String, String>,
): List<StructureGroup> {
    val needle = normalizeAutomationSearch(query)
    if (needle.isBlank()) return groups
    return groups.mapNotNull { group ->
        val appName = applicationNames[group.packageName].orEmpty()
        val structures = group.structures.filter { managed ->
            listOf(managed.structure.name, appName, group.packageName)
                .plus(managed.rules.map(NotificationRule::variableDisplay))
                .any { normalizeAutomationSearch(it).contains(needle) }
        }
        StructureGroup(group.packageName, structures).takeIf { structures.isNotEmpty() }
    }
}

internal fun filterRules(
    rules: List<NotificationRule>,
    query: String,
    categories: List<Category>,
    products: List<SyncProductoEntity>,
): List<NotificationRule> {
    val needle = normalizeAutomationSearch(query)
    if (needle.isBlank()) return rules
    val categoryNames = categories.associate { it.id to it.name }
    val productNames = products.associate { it.id to it.nombre }
    return rules.filter { rule ->
        listOfNotNull(
            rule.variableDisplay,
            rule.title,
            rule.detail,
            rule.categoryId?.let(categoryNames::get),
            rule.productId?.let(productNames::get),
        ).any { normalizeAutomationSearch(it).contains(needle) }
    }
}

internal fun filterProducts(
    products: List<SyncProductoEntity>,
    query: String,
): List<SyncProductoEntity> {
    val needle = normalizeAutomationSearch(query)
    val available = products.filter { it.deletedAt == null }
    if (needle.isBlank()) return available
    return available.filter { product ->
        listOfNotNull(product.nombre, product.corto, product.identificador)
            .any { normalizeAutomationSearch(it).contains(needle) }
    }
}

internal val productTypeOrder = listOf(
    "fondo", "etf", "accion", "cripto", "commodity", "bono", "pension", "efectivo",
    "inmueble", "p2p", "otro",
)

internal fun productsByType(products: List<SyncProductoEntity>): Map<String, List<SyncProductoEntity>> =
    products.filter { it.deletedAt == null }
        .groupBy { it.tipo.lowercase(Locale.ROOT).let { type -> if (type in productTypeOrder) type else "otro" } }
        .toSortedMap(compareBy<String> { productTypeOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } })
        .mapValues { (_, values) -> values.sortedBy { normalizeAutomationSearch(it.nombre) } }

internal fun frequentProducts(
    products: List<SyncProductoEntity>,
    productUsages: Iterable<String?>,
): List<SyncProductoEntity> {
    val counts = productUsages.filterNotNull().groupingBy { it }.eachCount()
    return products.filter { it.deletedAt == null && counts.containsKey(it.id) }
        .sortedWith(compareByDescending<SyncProductoEntity> { counts.getValue(it.id) }
            .thenBy { normalizeAutomationSearch(it.nombre) })
}
