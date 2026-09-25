package com.mipatrimonio.app.ui.movements

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.Transfer
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

sealed interface MovementItem {
    val date: LocalDate
    val createdAt: Long
    val amountMinor: Long

    data class Tx(val transaction: Transaction) : MovementItem {
        override val date: LocalDate = transaction.date
        override val createdAt: Long = transaction.createdAt
        override val amountMinor: Long = transaction.amountMinor
    }

    data class Move(val transfer: Transfer) : MovementItem {
        override val date: LocalDate = transfer.date
        override val createdAt: Long = transfer.createdAt
        override val amountMinor: Long = transfer.fromAmountMinor
    }
}

data class MovementFilters(
    val query: String = "",
    val kind: KindFilter = KindFilter.TODOS,
    val accountId: String? = null,
    val categoryId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val sort: MovementSort = MovementSort.FECHA_DESC,
    val source: SourceFilter = SourceFilter.TODOS,
)

enum class KindFilter { TODOS, INGRESOS, GASTOS, TRANSFERENCIAS }

enum class MovementSort { FECHA_DESC, FECHA_ASC, IMPORTE_DESC, IMPORTE_ASC }

enum class SourceFilter { TODOS, MANUAL, AUTOMATICOS, IMPORTADOS, RECURRENTES }

data class MovementDayGroup(
    val date: LocalDate,
    val items: List<MovementItem>,
    val balanceMinor: Long,
    val excludedCount: Int,
)

fun groupMovementsByDay(items: List<MovementItem>, baseCurrency: String): List<MovementDayGroup> =
    items.groupBy(MovementItem::date).entries
        .sortedByDescending { it.key }
        .map { (date, dayItems) ->
            var balance = 0L
            var excluded = 0
            dayItems.forEach { item ->
                val tx = (item as? MovementItem.Tx)?.transaction ?: return@forEach
                if (tx.currency != baseCurrency) {
                    excluded++
                } else {
                    balance = if (tx.type == TransactionType.INGRESO) {
                        Math.addExact(balance, tx.amountMinor)
                    } else {
                        Math.subtractExact(balance, tx.amountMinor)
                    }
                }
            }
            MovementDayGroup(date, dayItems, balance, excluded)
        }

fun applyFilters(
    items: List<MovementItem>,
    filters: MovementFilters,
    accounts: List<Account>,
    categories: List<Category>,
): List<MovementItem> {
    val accountsById = accounts.associateBy(Account::id)
    val categoriesById = categories.associateBy(Category::id)
    val includedCategoryIds = filters.categoryId?.let { categoryAndDescendantIds(it, categories) }
    val normalizedQuery = filters.query.normalizedForSearch()

    return items.asSequence()
        .filter { item -> matchesKind(item, filters.kind) }
        .filter { item -> matchesSource(item, filters.source) }
        .filter { item -> matchesAccount(item, filters.accountId) }
        .filter { item -> matchesCategory(item, includedCategoryIds) }
        .filter { item -> filters.from == null || !item.date.isBefore(filters.from) }
        .filter { item -> filters.to == null || !item.date.isAfter(filters.to) }
        .filter { item -> filters.minAmountMinor == null || item.amountMinor >= filters.minAmountMinor }
        .filter { item -> filters.maxAmountMinor == null || item.amountMinor <= filters.maxAmountMinor }
        .filter { item ->
            normalizedQuery.isEmpty() || searchableText(item, accountsById, categoriesById)
                .normalizedForSearch()
                .contains(normalizedQuery)
        }
        .toList()
        .sortedWith(filters.sort.comparator())
}

val MovementItem.isAutomatic: Boolean
    get() = this is MovementItem.Tx && transaction.source == TransactionSource.NOTIFICACION

private fun matchesSource(item: MovementItem, source: SourceFilter): Boolean = when (source) {
    SourceFilter.TODOS -> true
    SourceFilter.MANUAL -> item is MovementItem.Move ||
        item is MovementItem.Tx && item.transaction.source == TransactionSource.MANUAL
    SourceFilter.AUTOMATICOS -> item is MovementItem.Tx && item.transaction.source == TransactionSource.NOTIFICACION
    SourceFilter.IMPORTADOS -> item is MovementItem.Tx && item.transaction.source == TransactionSource.IMPORTACION
    SourceFilter.RECURRENTES -> item is MovementItem.Tx && item.transaction.source == TransactionSource.RECURRENTE
}

private fun matchesKind(item: MovementItem, kind: KindFilter): Boolean = when (kind) {
    KindFilter.TODOS -> true
    KindFilter.INGRESOS -> item is MovementItem.Tx && item.transaction.type == TransactionType.INGRESO
    KindFilter.GASTOS -> item is MovementItem.Tx && item.transaction.type == TransactionType.GASTO
    KindFilter.TRANSFERENCIAS -> item is MovementItem.Move
}

private fun matchesAccount(item: MovementItem, accountId: String?): Boolean {
    if (accountId == null) return true
    return when (item) {
        is MovementItem.Tx -> item.transaction.accountId == accountId
        is MovementItem.Move -> item.transfer.fromAccountId == accountId || item.transfer.toAccountId == accountId
    }
}

private fun matchesCategory(item: MovementItem, categoryIds: Set<String>?): Boolean {
    if (categoryIds == null) return true
    return item is MovementItem.Tx && item.transaction.categoryId in categoryIds
}

private fun categoryAndDescendantIds(categoryId: String, categories: List<Category>): Set<String> {
    val result = mutableSetOf(categoryId)
    var added: Boolean
    do {
        added = false
        categories.forEach { category ->
            if (category.id !in result && category.parentId in result) {
                result += category.id
                added = true
            }
        }
    } while (added)
    return result
}

private fun searchableText(
    item: MovementItem,
    accountsById: Map<String, Account>,
    categoriesById: Map<String, Category>,
): String = when (item) {
    is MovementItem.Tx -> listOf(
        item.transaction.description,
        item.transaction.merchant,
        item.transaction.notes,
        item.transaction.categoryId?.let(categoriesById::get)?.name.orEmpty(),
        accountsById[item.transaction.accountId]?.name.orEmpty(),
    ).joinToString(" ")

    is MovementItem.Move -> listOf(
        item.transfer.description,
        accountsById[item.transfer.fromAccountId]?.name.orEmpty(),
        accountsById[item.transfer.toAccountId]?.name.orEmpty(),
    ).joinToString(" ")
}

private fun String.normalizedForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)
    .trim()

private fun MovementSort.comparator(): Comparator<MovementItem> {
    val primary = when (this) {
        MovementSort.FECHA_DESC -> compareByDescending<MovementItem> { it.date }
        MovementSort.FECHA_ASC -> compareBy<MovementItem> { it.date }
        MovementSort.IMPORTE_DESC -> compareByDescending<MovementItem> { it.amountMinor }
        MovementSort.IMPORTE_ASC -> compareBy<MovementItem> { it.amountMinor }
    }
    return primary.thenByDescending { it.createdAt }
}

private val COMBINING_MARKS = "\\p{M}+".toRegex()
