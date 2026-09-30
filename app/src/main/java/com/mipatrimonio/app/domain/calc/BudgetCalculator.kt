package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.movementStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class BudgetLevel { NORMAL, AVISO, SUPERADO }

data class BudgetStatus(
    val budget: Budget,
    val spentMinor: Long,
    val remainingMinor: Long,
    val percentage: Int,
    val level: BudgetLevel,
    val range: ClosedRange<LocalDate>,
    val excludedCount: Int,
    val applies: Boolean = true,
) {
    /** Solo para componentes gráficos; las decisiones financieras usan [percentage]. */
    val consumedRatio: Double get() = percentage.toDouble() / 100.0

    constructor(
        budget: Budget,
        spentMinor: Long,
        remainingMinor: Long,
        consumedRatio: Double,
        level: BudgetLevel,
        range: ClosedRange<LocalDate>,
        excludedCount: Int,
    ) : this(
        budget, spentMinor, remainingMinor,
        BigDecimal.valueOf(consumedRatio).multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP).toInt(),
        level, range, excludedCount,
    )
}

object BudgetCalculator {
    fun periodRange(period: BudgetPeriod, reference: LocalDate): ClosedRange<LocalDate> = when (period) {
        BudgetPeriod.SEMANAL -> reference..reference.plusDays(6)
        BudgetPeriod.MENSUAL -> YearMonth.from(reference).let { it.atDay(1)..it.atEndOfMonth() }
        BudgetPeriod.TRIMESTRAL -> reference..reference.plusMonths(3).minusDays(1)
        BudgetPeriod.SEMESTRAL -> reference..reference.plusMonths(6).minusDays(1)
        BudgetPeriod.ANUAL -> LocalDate.of(reference.year, 1, 1)..LocalDate.of(reference.year, 12, 31)
        BudgetPeriod.UNICO -> reference..reference
    }

    fun windowFor(budget: Budget, reference: LocalDate): ClosedRange<LocalDate>? {
        val month = YearMonth.from(reference)
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        if (budget.startDate > monthEnd || budget.endDate?.let { it < monthStart } == true) return null
        if (budget.period == BudgetPeriod.UNICO) {
            val end = requireNotNull(budget.endDate) { "Un presupuesto único necesita fecha final" }
            return budget.startDate..end
        }
        var index = 0L
        while (true) {
            val start = periodStart(budget.startDate, budget.period, index)
            val naturalEnd = periodStart(budget.startDate, budget.period, index + 1).minusDays(1)
            val end = budget.endDate?.let { minOf(naturalEnd, it) } ?: naturalEnd
            if (end >= monthStart && start <= monthEnd) return start..end
            if (start > monthEnd || budget.endDate?.let { start > it } == true) return null
            index++
        }
    }

    fun status(
        budget: Budget,
        transactions: List<Transaction>,
        categories: List<Category>,
        reference: LocalDate,
        today: LocalDate,
    ): BudgetStatus {
        val range = windowFor(budget, reference)
        if (range == null) {
            val anchor = budget.startDate
            return BudgetStatus(budget, 0, budget.limitMinor, 0, BudgetLevel.NORMAL, anchor..anchor, 0, false)
        }
        return statusForRange(budget, transactions, categories, range, today)
    }

    fun statusForRange(
        budget: Budget,
        transactions: List<Transaction>,
        categories: List<Category>,
        range: ClosedRange<LocalDate>,
        today: LocalDate,
    ): BudgetStatus {
        val parentOf = categories.associate { it.id to it.parentId }
        var spent = 0L
        var excluded = 0
        for (transaction in transactions) {
            if (
                transaction.type != TransactionType.GASTO || transaction.date !in range ||
                movementStatus(transaction.date, today) != MovementStatus.EJECUTADO
            ) continue
            if (!matches(budget.categoryRules, transaction.categoryId, parentOf)) continue
            if (transaction.currency != budget.currency) excluded++
            else spent = Math.addExact(spent, transaction.amountMinor)
        }
        val percentage = percentage(spent, budget.limitMinor)
        val level = when {
            spent > budget.limitMinor -> BudgetLevel.SUPERADO
            percentage >= budget.alertThresholdPct -> BudgetLevel.AVISO
            else -> BudgetLevel.NORMAL
        }
        return BudgetStatus(
            budget, spent, Math.subtractExact(budget.limitMinor, spent), percentage, level, range, excluded,
        )
    }

    fun recentWindows(budget: Budget, reference: LocalDate, count: Int = 6): List<ClosedRange<LocalDate>> {
        require(count > 0) { "El número de ventanas debe ser positivo" }
        if (budget.period == BudgetPeriod.UNICO) {
            return budget.endDate?.let { listOf(budget.startDate..it) }.orEmpty()
        }
        val windows = ArrayDeque<ClosedRange<LocalDate>>(count)
        var index = 0L
        while (true) {
            val start = periodStart(budget.startDate, budget.period, index)
            if (start > reference || budget.endDate?.let { start > it } == true) break
            val naturalEnd = periodStart(budget.startDate, budget.period, index + 1).minusDays(1)
            val end = budget.endDate?.let { minOf(naturalEnd, it) } ?: naturalEnd
            windows.addLast(start..end)
            if (windows.size > count) windows.removeFirst()
            index++
        }
        return windows.toList().asReversed()
    }

    fun matchingTransactions(
        budget: Budget,
        transactions: List<Transaction>,
        categories: List<Category>,
        range: ClosedRange<LocalDate>,
        today: LocalDate,
    ): List<Transaction> {
        val parentOf = categories.associate { it.id to it.parentId }
        return transactions.filter {
            it.type == TransactionType.GASTO && it.currency == budget.currency && it.date in range &&
                movementStatus(it.date, today) == MovementStatus.EJECUTADO &&
                matches(budget.categoryRules, it.categoryId, parentOf)
        }
    }

    private fun periodStart(start: LocalDate, period: BudgetPeriod, index: Long): LocalDate = when (period) {
        BudgetPeriod.SEMANAL -> start.plusDays(Math.multiplyExact(index, 7L))
        BudgetPeriod.MENSUAL -> start.plusMonths(index)
        BudgetPeriod.TRIMESTRAL -> start.plusMonths(Math.multiplyExact(index, 3L))
        BudgetPeriod.SEMESTRAL -> start.plusMonths(Math.multiplyExact(index, 6L))
        BudgetPeriod.ANUAL -> start.plusYears(index)
        BudgetPeriod.UNICO -> start
    }

    private fun matches(
        rules: List<BudgetCategoryRule>,
        transactionCategory: String?,
        parentOf: Map<String, String?>,
    ): Boolean {
        if (rules.isEmpty()) return true
        if (transactionCategory == null) return false
        return rules.any { rule ->
            transactionCategory == rule.categoryId ||
                (rule.includeSubcategories && parentOf[transactionCategory] == rule.categoryId)
        }
    }

    /** Porcentaje entero HALF_UP; puede superar 100 y nunca usa Double. */
    private fun percentage(spentMinor: Long, limitMinor: Long): Int {
        if (limitMinor <= 0L) return 0
        val rounded = BigDecimal.valueOf(spentMinor).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(limitMinor), 0, RoundingMode.HALF_UP)
        return runCatching { rounded.intValueExact() }.getOrElse { Int.MAX_VALUE }
    }
}
