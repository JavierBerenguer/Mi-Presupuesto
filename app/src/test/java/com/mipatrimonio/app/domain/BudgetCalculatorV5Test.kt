package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.category
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.BudgetLevel
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorV5Test {
    private val categories = listOf(
        category("parent"), category("child", parentId = "parent"), category("other"),
    )

    @Test fun `reglas distinguen padre directo todas las subcategorias y subcategoria suelta`() {
        val transactions = listOf(
            tx(GASTO, 10, category = "parent"), tx(GASTO, 20, category = "child"), tx(GASTO, 40, category = "other"),
        )
        assertEquals(10, BudgetCalculator.status(budget(rules = listOf(BudgetCategoryRule("parent", false))), transactions, categories, date(), today()).spentMinor)
        assertEquals(30, BudgetCalculator.status(budget(rules = listOf(BudgetCategoryRule("parent", true))), transactions, categories, date(), today()).spentMinor)
        assertEquals(20, BudgetCalculator.status(budget(rules = listOf(BudgetCategoryRule("child", false))), transactions, categories, date(), today()).spentMinor)
        assertEquals(70, BudgetCalculator.status(budget(), transactions, categories, date(), today()).spentMinor)
    }

    @Test fun `semanal cruza mes y fin anterior deja de aplicar`() {
        val weekly = budget(period = BudgetPeriod.SEMANAL, start = LocalDate.of(2026, 1, 29))
        assertEquals(LocalDate.of(2026, 1, 29), BudgetCalculator.windowFor(weekly, LocalDate.of(2026, 2, 1))?.start)
        assertEquals(LocalDate.of(2026, 2, 4), BudgetCalculator.windowFor(weekly, LocalDate.of(2026, 2, 1))?.endInclusive)
        assertNull(BudgetCalculator.windowFor(weekly.copy(endDate = LocalDate.of(2026, 1, 31)), LocalDate.of(2026, 2, 1)))
        val history = BudgetCalculator.recentWindows(weekly, LocalDate.of(2026, 3, 10))
        assertEquals(6, history.size)
        assertEquals(LocalDate.of(2026, 3, 5), history.first().start)
        assertEquals(LocalDate.of(2026, 1, 29), history.last().start)
    }

    @Test fun `anclaje en dia 31 se recupera tras febrero y cambia de anio`() {
        val monthly = budget(start = LocalDate.of(2025, 12, 31))
        assertEquals(LocalDate.of(2026, 1, 31), BudgetCalculator.windowFor(monthly, LocalDate.of(2026, 2, 1))?.start)
        assertEquals(LocalDate.of(2026, 3, 31), BudgetCalculator.windowFor(monthly, LocalDate.of(2026, 4, 1))?.start)
    }

    @Test fun `unico usa rango inclusivo y exige fin`() {
        val unique = budget(period = BudgetPeriod.UNICO, start = date(), end = date().plusDays(2))
        val result = BudgetCalculator.status(
            unique, listOf(tx(GASTO, 25, date = date().plusDays(2))), categories, date(), date().plusDays(2),
        )
        assertEquals(25, result.spentMinor)
        assertTrue(result.applies)
    }

    @Test fun `umbral exacto redondeo superado y divisa distinta`() {
        val budget = budget(limit = 3, threshold = 67)
        val exact = BudgetCalculator.status(budget, listOf(tx(GASTO, 2)), categories, date(), today())
        assertEquals(67, exact.percentage)
        assertEquals(BudgetLevel.AVISO, exact.level)
        val over = BudgetCalculator.status(
            budget, listOf(tx(GASTO, 4), tx(GASTO, 99, currency = "USD")), categories, date(), today(),
        )
        assertEquals(BudgetLevel.SUPERADO, over.level)
        assertEquals(-1, over.remainingMinor)
        assertEquals(1, over.excludedCount)
    }

    @Test fun `categoria archivada sigue computando por id`() {
        val archived = categories.first().copy(archived = true)
        val result = BudgetCalculator.status(
            budget(rules = listOf(BudgetCategoryRule("parent", true))),
            listOf(tx(GASTO, 10, category = "child")), listOf(archived, categories[1]), date(), today(),
        )
        assertEquals(10, result.spentMinor)
    }

    private fun date() = LocalDate.of(2026, 3, 1)
    private fun today() = LocalDate.of(2026, 3, 31)
    private fun budget(
        period: BudgetPeriod = BudgetPeriod.MENSUAL,
        start: LocalDate = date(),
        end: LocalDate? = null,
        limit: Long = 100,
        threshold: Int = 90,
        rules: List<BudgetCategoryRule> = emptyList(),
    ) = Budget("b", "Casa", limit, "EUR", period, start, end, threshold, rules, false)
}
