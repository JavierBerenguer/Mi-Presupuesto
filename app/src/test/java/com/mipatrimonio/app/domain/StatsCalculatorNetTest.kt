package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorNetTest {
    private val date = LocalDate.of(2026, 3, 10)
    private val range = LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31)
    private val categories = listOf(
        namedCategory("custom-income", "Íngresos"),
        namedCategory("salary", "Nómina", "custom-income"),
        namedCategory("leisure", "Ocio"),
    )

    @Test fun `gasto en ingresos resta de ingresos`() {
        val result = StatsCalculator.totals(
            listOf(tx(GASTO, 200_00, category = "salary")), "EUR", range, date, categories,
        )

        assertEquals(-200_00L, result.incomeMinor)
        assertEquals(0L, result.expenseMinor)
    }

    @Test fun `ingreso en ocio resta del gasto`() {
        val result = StatsCalculator.totals(
            listOf(tx(GASTO, 300_00, category = "leisure"), tx(INGRESO, 125_00, category = "leisure")),
            "EUR", range, date, categories,
        )

        assertEquals(175_00L, result.expenseMinor)
        assertEquals(0L, result.incomeMinor)
    }

    @Test fun `sin categoria se cuenta por tipo`() {
        val result = StatsCalculator.totals(
            listOf(tx(GASTO, 75_00), tx(INGRESO, 500_00)), "EUR", range, date, categories,
        )

        assertEquals(500_00L, result.incomeMinor)
        assertEquals(75_00L, result.expenseMinor)
    }

    @Test fun `distribucion omite ingresos y raices con neto no positivo`() {
        val transactions = listOf(
            tx(GASTO, 100_00, category = "salary"),
            tx(GASTO, 80_00, category = "leisure"),
            tx(INGRESO, 100_00, category = "leisure"),
        )

        assertTrue(StatsCalculator.expenseByCategory(transactions, categories, "EUR", range, date).isEmpty())
    }

    @Test fun `serie mensual aplica la misma regla neta`() {
        val result = StatsCalculator.monthlySeries(
            listOf(tx(GASTO, 90_00, category = "salary"), tx(INGRESO, 20_00, category = "leisure")),
            "EUR", YearMonth.of(2026, 3), 1, date, categories,
        ).single()

        assertEquals(-90_00L, result.incomeMinor)
        assertEquals(-20_00L, result.expenseMinor)
    }

    private fun namedCategory(id: String, name: String, parentId: String? = null) =
        Category(id, name, parentId, 0xFF000000, false)
}
