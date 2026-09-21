package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

data class PeriodTotals(val incomeMinor: Long, val expenseMinor: Long, val excludedCount: Int) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
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
    fun totals(transactions: List<Transaction>, baseCurrency: String, range: ClosedRange<LocalDate>): PeriodTotals {
        var income = 0L
        var expense = 0L
        var excluded = 0
        for (t in transactions) {
            if (t.date !in range) continue
            if (t.currency != baseCurrency) {
                excluded++
                continue
            }
            when (t.type) {
                TransactionType.INGRESO -> income = Math.addExact(income, t.amountMinor)
                TransactionType.GASTO -> expense = Math.addExact(expense, t.amountMinor)
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
    ): List<MonthTotals> {
        require(months > 0) { "months debe ser positivo" }
        return (months - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            val totals = totals(transactions, baseCurrency, monthRange(m))
            MonthTotals(m, totals.incomeMinor, totals.expenseMinor)
        }
    }

    /** Gasto agrupado por categoría raíz (las subcategorías se suman a su padre), de mayor a menor. */
    fun expenseByCategory(
        transactions: List<Transaction>,
        categories: List<Category>,
        baseCurrency: String,
        range: ClosedRange<LocalDate>,
    ): List<CategorySpend> {
        val byId = categories.associateBy { it.id }
        val sums = LinkedHashMap<String?, Long>()
        for (t in transactions) {
            if (t.type != TransactionType.GASTO || t.currency != baseCurrency || t.date !in range) continue
            val cat = t.categoryId?.let { byId[it] }
            val root = cat?.parentId ?: cat?.id
            sums[root] = Math.addExact(sums[root] ?: 0L, t.amountMinor)
        }
        return sums.map { CategorySpend(it.key, it.value) }.sortedByDescending { it.amountMinor }
    }
}
