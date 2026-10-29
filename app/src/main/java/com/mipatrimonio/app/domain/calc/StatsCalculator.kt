package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.movementStatus
import java.time.LocalDate
import java.time.YearMonth
import java.text.Normalizer
import java.util.Locale

data class PeriodTotals(val incomeMinor: Long, val expenseMinor: Long, val excludedCount: Int) {
    val balanceMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}

data class MonthTotals(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long)

/** [categoryId] null = sin categoría. */
data class CategorySpend(val categoryId: String?, val amountMinor: Long)

/**
 * Estadísticas en divisa base. Los movimientos en otras divisas no se convierten (no hay tipo de cambio
 * fiable en el MVP): se excluyen y se cuentan en `excludedCount` para informar al usuario.
 * Las transferencias no forman parte de la lista de movimientos, por lo que nunca cuentan como gasto.
 */
object StatsCalculator {
    fun totals(
        transactions: List<Transaction>,
        baseCurrency: String,
        range: ClosedRange<LocalDate>,
        today: LocalDate,
        categories: List<Category> = emptyList(),
        excludedExpenseRootIds: Set<String> = emptySet(),
    ): PeriodTotals {
        var income = 0L
        var expense = 0L
        var excluded = 0
        for (t in TransactionGroups.netForStatistics(transactions)) {
            if (t.date !in range || movementStatus(t.date, today) != MovementStatus.EJECUTADO) continue
            if (t.currency != baseCurrency) {
                excluded++
                continue
            }
            val root = rootCategory(t.categoryId, categories)
            when {
                root?.isIncomeRoot() == true -> {
                    val signed = if (t.type == TransactionType.INGRESO) t.amountMinor else Math.negateExact(t.amountMinor)
                    income = Math.addExact(income, signed)
                }
                root != null -> {
                    if (root.id in excludedExpenseRootIds) continue
                    val signed = if (t.type == TransactionType.GASTO) t.amountMinor else Math.negateExact(t.amountMinor)
                    expense = Math.addExact(expense, signed)
                }
                t.categoryId != null -> {
                    val isIncomeRoot = t.categoryId == INCOME_ROOT_ID
                    if (isIncomeRoot) {
                        val signed = if (t.type == TransactionType.INGRESO) t.amountMinor else Math.negateExact(t.amountMinor)
                        income = Math.addExact(income, signed)
                    } else {
                        if (t.categoryId in excludedExpenseRootIds) continue
                        val signed = if (t.type == TransactionType.GASTO) t.amountMinor else Math.negateExact(t.amountMinor)
                        expense = Math.addExact(expense, signed)
                    }
                }
                t.type == TransactionType.INGRESO -> income = Math.addExact(income, t.amountMinor)
                else -> expense = Math.addExact(expense, t.amountMinor)
            }
        }
        return PeriodTotals(income, expense, excluded)
    }

    fun monthRange(month: YearMonth): ClosedRange<LocalDate> = month.atDay(1)..month.atEndOfMonth()

    /** Serie de [months] meses terminando en [endMonth], del más antiguo al más reciente. */
    fun monthlySeries(
        transactions: List<Transaction>,
        baseCurrency: String,
        endMonth: YearMonth,
        months: Int,
        today: LocalDate,
        categories: List<Category> = emptyList(),
        excludedExpenseRootIds: Set<String> = emptySet(),
    ): List<MonthTotals> {
        require(months > 0) { "months debe ser positivo" }
        return (months - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            val totals = totals(transactions, baseCurrency, monthRange(m), today, categories, excludedExpenseRootIds)
            MonthTotals(m, totals.incomeMinor, totals.expenseMinor)
        }
    }

    /** Gasto agrupado por categoría raíz (las subcategorías se suman a su padre), de mayor a menor. */
    fun expenseByCategory(
        transactions: List<Transaction>,
        categories: List<Category>,
        baseCurrency: String,
        range: ClosedRange<LocalDate>,
        today: LocalDate,
    ): List<CategorySpend> {
        val sums = LinkedHashMap<String?, Long>()
        for (t in TransactionGroups.netForStatistics(transactions)) {
            if (t.currency != baseCurrency || t.date !in range || movementStatus(t.date, today) != MovementStatus.EJECUTADO) continue
            val rootCategory = rootCategory(t.categoryId, categories)
            val root = rootCategory?.id ?: t.categoryId
            if (rootCategory?.isIncomeRoot() == true || root == INCOME_ROOT_ID) continue
            if (root == null && t.type == TransactionType.INGRESO) continue
            val signed = if (t.type == TransactionType.GASTO) t.amountMinor else Math.negateExact(t.amountMinor)
            sums[root] = Math.addExact(sums[root] ?: 0L, signed)
        }
        return sums.filterValues { it > 0L }
            .map { CategorySpend(it.key, it.value) }
            .sortedByDescending { it.amountMinor }
    }

    internal fun rootCategory(categoryId: String?, categories: List<Category>): Category? {
        val byId = categories.associateBy(Category::id)
        var current = categoryId?.let { byId[it] } ?: return null
        val visited = mutableSetOf<String>()
        while (current.parentId != null && visited.add(current.id)) {
            current = byId[current.parentId] ?: break
        }
        return current
    }

    internal fun investmentRootIds(categories: List<Category>): Set<String> = categories.asSequence()
        .filter { it.parentId == null && (it.id == INVESTMENT_ROOT_ID || it.name.normalized() == "inversiones") }
        .mapTo(mutableSetOf(INVESTMENT_ROOT_ID), Category::id)

    private fun Category.isIncomeRoot(): Boolean = id == INCOME_ROOT_ID || name.normalized() == "ingresos"

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)

    private const val INCOME_ROOT_ID = "mp-ingresos"
    private const val INVESTMENT_ROOT_ID = "mp-inversiones"
}
