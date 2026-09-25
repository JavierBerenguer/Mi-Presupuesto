package com.mipatrimonio.app.ui.budgets

import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/** Global primero; después por consumo descendente; desempate por nombre. */
fun sortStatuses(statuses: List<BudgetStatus>, categoryName: (String?) -> String): List<BudgetStatus> =
    statuses.sortedWith(
        compareBy<BudgetStatus> { it.budget.categoryId != null }
            .thenByDescending { it.consumedRatio }
            .thenBy { categoryName(it.budget.categoryId).lowercase() },
    )

/** Dos presupuestos activos no pueden compartir categoría (o ser ambos globales) y periodo. */
fun isDuplicate(existing: List<Budget>, candidate: Budget): Boolean =
    existing.any {
        it.id != candidate.id && !it.archived &&
            it.categoryId == candidate.categoryId && it.period == candidate.period
    }

/** Porcentaje entero para mostrar; no interviene en importes. */
fun consumedPercent(ratio: Double): Int = Math.round(ratio * 100).toInt().coerceAtLeast(0)

fun usesExpenseWarning(status: BudgetStatus): Boolean = status.remainingMinor < 0 || status.consumedRatio > 0.9

enum class StatisticsKind { GASTOS, INGRESOS }

data class StatisticsCategory(
    val categoryId: String?,
    val amountMinor: Long,
    val percentage: Int,
    val isOther: Boolean = false,
)

data class MonthlyStatistics(
    val categories: List<StatisticsCategory>,
    val totalMinor: Long,
    val excludedCount: Int,
)

/** Agrupa por categoría raíz y conserva como segmentos las cinco mayores más «Otros». */
fun monthlyStatistics(
    transactions: List<Transaction>,
    categories: List<Category>,
    baseCurrency: String,
    month: YearMonth,
    kind: StatisticsKind,
): MonthlyStatistics {
    val expectedType = if (kind == StatisticsKind.GASTOS) TransactionType.GASTO else TransactionType.INGRESO
    val range = month.atDay(1)..month.atEndOfMonth()
    val byId = categories.associateBy(Category::id)
    val matching = transactions.filter { it.type == expectedType && it.date in range }
    val excludedCount = matching.count { it.currency != baseCurrency }
    val sorted = if (kind == StatisticsKind.GASTOS) {
        StatsCalculator.expenseByCategory(transactions, categories, baseCurrency, range)
            .map { RawStatisticsCategory(it.categoryId, it.amountMinor) }
    } else {
        val sums = linkedMapOf<String?, Long>()
        matching.filter { it.currency == baseCurrency }.forEach { transaction ->
            val category = transaction.categoryId?.let(byId::get)
            val rootId = category?.parentId ?: category?.id
            sums[rootId] = Math.addExact(sums[rootId] ?: 0L, transaction.amountMinor)
        }
        sums.map { RawStatisticsCategory(it.key, it.value) }.sortedByDescending { it.amountMinor }
    }
    val visible = if (sorted.size <= MAX_STATISTICS_CATEGORIES) {
        sorted
    } else {
        sorted.take(MAX_STATISTICS_CATEGORIES) + RawStatisticsCategory(
            categoryId = null,
            amountMinor = sorted.drop(MAX_STATISTICS_CATEGORIES).fold(0L) { total, item ->
                Math.addExact(total, item.amountMinor)
            },
            isOther = true,
        )
    }
    val total = visible.fold(0L) { sum, item -> Math.addExact(sum, item.amountMinor) }
    val percentages = roundedPercentages(visible.map { it.amountMinor })
    return MonthlyStatistics(
        categories = visible.mapIndexed { index, item ->
            StatisticsCategory(item.categoryId, item.amountMinor, percentages[index], item.isOther)
        },
        totalMinor = total,
        excludedCount = excludedCount,
    )
}

fun daysRemaining(month: YearMonth, today: LocalDate): Int = when {
    month < YearMonth.from(today) -> 0
    month > YearMonth.from(today) -> month.lengthOfMonth()
    else -> month.lengthOfMonth() - today.dayOfMonth + 1
}

private data class RawStatisticsCategory(
    val categoryId: String?,
    val amountMinor: Long,
    val isOther: Boolean = false,
)

private fun roundedPercentages(amounts: List<Long>): List<Int> {
    val total = amounts.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
    if (total <= 0L) return List(amounts.size) { 0 }
    val exact = amounts.map { it.toBigDecimal().multiply(100.toBigDecimal()).divide(total.toBigDecimal(), 12, java.math.RoundingMode.HALF_EVEN) }
    val result = exact.map { it.setScale(0, java.math.RoundingMode.FLOOR).intValueExact() }.toMutableList()
    val pointsLeft = 100 - result.sum()
    exact.indices
        .sortedWith(compareByDescending<Int> { exact[it].remainder(java.math.BigDecimal.ONE) }.thenBy { it })
        .take(pointsLeft)
        .forEach { result[it]++ }
    return result
}

private const val MAX_STATISTICS_CATEGORIES = 5
