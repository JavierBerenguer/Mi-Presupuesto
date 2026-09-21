package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

enum class BudgetLevel { NORMAL, AVISO, SUPERADO }

data class BudgetStatus(
    val budget: Budget,
    val spentMinor: Long,
    val remainingMinor: Long,
    /** Fracción consumida (0.0–…); solo para presentación, no interviene en importes. */
    val consumedRatio: Double,
    val level: BudgetLevel,
    val range: ClosedRange<LocalDate>,
    /** Gastos del periodo en otra divisa, no contabilizados por falta de conversión. */
    val excludedCount: Int,
)

object BudgetCalculator {
    const val WARNING_THRESHOLD = 0.8

    fun periodRange(period: BudgetPeriod, reference: LocalDate): ClosedRange<LocalDate> = when (period) {
        BudgetPeriod.MENSUAL -> YearMonth.from(reference).let { it.atDay(1)..it.atEndOfMonth() }
        BudgetPeriod.ANUAL -> LocalDate.of(reference.year, 1, 1)..LocalDate.of(reference.year, 12, 31)
    }

    fun status(
        budget: Budget,
        transactions: List<Transaction>,
        categories: List<Category>,
        reference: LocalDate,
    ): BudgetStatus {
        val range = periodRange(budget.period, reference)
        val parentOf = categories.associate { it.id to it.parentId }
        var spent = 0L
        var excluded = 0
        for (t in transactions) {
            if (t.type != TransactionType.GASTO || t.date !in range) continue
            if (!matches(budget.categoryId, t.categoryId, parentOf)) continue
            if (t.currency != budget.currency) {
                excluded++
                continue
            }
            spent = Math.addExact(spent, t.amountMinor)
        }
        val ratio = if (budget.limitMinor > 0) spent.toDouble() / budget.limitMinor.toDouble() else 0.0
        val level = when {
            spent > budget.limitMinor -> BudgetLevel.SUPERADO
            ratio >= WARNING_THRESHOLD -> BudgetLevel.AVISO
            else -> BudgetLevel.NORMAL
        }
        return BudgetStatus(budget, spent, budget.limitMinor - spent, ratio, level, range, excluded)
    }

    private fun matches(budgetCategory: String?, txCategory: String?, parentOf: Map<String, String?>): Boolean {
        if (budgetCategory == null) return true
        if (txCategory == null) return false
        return txCategory == budgetCategory || parentOf[txCategory] == budgetCategory
    }
}
