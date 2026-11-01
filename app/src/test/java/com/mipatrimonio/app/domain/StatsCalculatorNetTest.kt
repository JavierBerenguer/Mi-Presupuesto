package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculatorNetTest {
    private val date = LocalDate.of(2026, 3, 10)
    private val range = LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31)
    private val categories = listOf(
        category("income", "Ingresos"), category("salary", "Nómina", "income"), category("leisure", "Ocio"),
    )

    @Test fun `el tipo decide aunque la categoria pertenezca a otra raiz`() {
        val result = StatsCalculator.totals(
            listOf(tx(GASTO, 200_00, category = "salary"), tx(INGRESO, 125_00, category = "leisure")),
            "EUR", range, date, categories,
        )
        assertEquals(125_00L, result.incomeMinor)
        assertEquals(200_00L, result.expenseMinor)
    }

    @Test fun `distribucion no resta ingresos`() {
        val result = StatsCalculator.expenseByCategory(
            listOf(tx(GASTO, 80_00, category = "leisure"), tx(INGRESO, 100_00, category = "leisure")),
            categories, "EUR", range, date,
        )
        assertEquals(80_00L, result.single().amountMinor)
    }

    @Test fun `grupos conservan el neto y pueden convertirse en ingreso`() {
        val expense = tx(GASTO, 100_00, category = "leisure", id = "expense")
        val refund = tx(INGRESO, 30_00, category = "leisure", id = "refund").copy(groupParentId = expense.id)
        val expense2 = tx(GASTO, 100_00, category = "leisure", id = "expense2")
        val overRefund = tx(INGRESO, 120_00, category = "leisure", id = "over").copy(groupParentId = expense2.id)
        val result = StatsCalculator.totals(
            listOf(expense, refund, expense2, overRefund), "EUR", range, date, categories,
        )
        assertEquals(20_00L, result.incomeMinor)
        assertEquals(70_00L, result.expenseMinor)
    }

    @Test fun `raices excluidas solo afectan a gastos`() {
        val result = StatsCalculator.totals(
            listOf(tx(GASTO, 90_00, category = "leisure"), tx(INGRESO, 20_00, category = "leisure")),
            "EUR", range, date, categories, setOf("leisure"),
        )
        assertEquals(20_00L, result.incomeMinor)
        assertEquals(0L, result.expenseMinor)
    }

    @Test fun `serie mensual sigue la regla por tipo`() {
        val result = StatsCalculator.monthlySeries(
            listOf(tx(GASTO, 90_00, category = "salary"), tx(INGRESO, 20_00, category = "leisure")),
            "EUR", YearMonth.of(2026, 3), 1, date, categories,
        ).single()
        assertEquals(20_00L, result.incomeMinor)
        assertEquals(90_00L, result.expenseMinor)
    }

    private fun category(id: String, name: String, parentId: String? = null) =
        Category(id, name, parentId, 0xFF000000, false)
}
