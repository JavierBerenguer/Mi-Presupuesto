package com.mipatrimonio.app.ui.budgets

import com.mipatrimonio.app.domain.calc.BudgetLevel
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.InvestmentCashFlow
import com.mipatrimonio.app.domain.calc.InvestmentStats
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetRulesTest {
    private fun budget(id: String, category: String?, period: BudgetPeriod = BudgetPeriod.MENSUAL, archived: Boolean = false) =
        Budget(id, category, period, 100_00, "EUR", archived)

    private fun status(b: Budget, ratio: Double) = BudgetStatus(
        b, 0, 0, ratio, BudgetLevel.NORMAL, LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 1, 31), 0,
    )

    private val names = mapOf("a" to "Alimentación", "b" to "Ocio", "c" to "Compras")

    @Test
    fun `orden global primero luego consumo descendente y desempate por nombre`() {
        val statuses = listOf(
            status(budget("1", "a"), 0.5),
            status(budget("2", "b"), 0.9),
            status(budget("3", null), 0.1),
            status(budget("4", "c"), 0.5),
        )
        val ids = sortStatuses(statuses) { names[it].orEmpty() }.map { it.budget.id }
        assertEquals(listOf("3", "2", "1", "4"), ids) // 1 (Alimentación) antes que 4 (Compras) con igual consumo
    }

    @Test
    fun `duplicado misma categoria y periodo`() {
        val existing = listOf(budget("1", "a"))
        assertTrue(isDuplicate(existing, budget("2", "a")))
        assertFalse(isDuplicate(existing, budget("2", "a", BudgetPeriod.ANUAL)))
        assertFalse(isDuplicate(existing, budget("2", "b")))
    }

    @Test
    fun `global contra categoria no es duplicado y dos globales si`() {
        val existing = listOf(budget("1", null))
        assertFalse(isDuplicate(existing, budget("2", "a")))
        assertTrue(isDuplicate(existing, budget("2", null)))
    }

    @Test
    fun `editar ignora el propio id y los archivados`() {
        assertFalse(isDuplicate(listOf(budget("1", "a")), budget("1", "a")))
        assertFalse(isDuplicate(listOf(budget("1", "a", archived = true)), budget("2", "a")))
    }

    @Test
    fun `porcentaje entero no negativo`() {
        assertEquals(80, consumedPercent(0.8))
        assertEquals(101, consumedPercent(1.01))
        assertEquals(0, consumedPercent(-0.5))
    }

    @Test
    fun `noventa exacto avisa y por debajo no`() {
        assertFalse(usesExpenseWarning(status(budget("1", "a"), 0.894))) // 89.4 % redondea HALF_UP a 89
        assertTrue(usesExpenseWarning(status(budget("1", "a"), 0.9)))
        assertTrue(usesExpenseWarning(status(budget("2", "a"), 0.901)))
    }

    @Test fun `presupuesto de comision cuenta la comision y ocio no cuenta el dividendo`() {
        val date = LocalDate.of(2026, 3, 10)
        val categories = listOf(
            Category("invest", "Inversiones", null, 0, false),
            Category("fee", "Comisión", "invest", 0, false, investmentKind = InvestmentKind.COMISION),
            Category("div", "Dividendo", "invest", 0, false, investmentKind = InvestmentKind.DIVIDENDO),
            Category("leisure", "Ocio", null, 0, false),
        )
        val transactions = InvestmentStats.statTransactions(
            listOf(
                InvestmentCashFlow("fee", "a", date, InvestmentKind.COMISION, 1_00),
                InvestmentCashFlow("div", "a", date, InvestmentKind.DIVIDENDO, 20_00),
            ),
            categories,
        )
        val feeBudget = Budget(
            "fee-budget", "Comisiones", 100_00, "EUR", BudgetPeriod.MENSUAL, date.withDayOfMonth(1), null, 90,
            listOf(BudgetCategoryRule("fee", false)), false,
        )
        val leisureBudget = feeBudget.copy(id = "leisure-budget", categoryRules = listOf(BudgetCategoryRule("leisure", false)))

        assertEquals(1_00L, BudgetCalculator.status(feeBudget, transactions, categories, date, date).spentMinor)
        assertEquals(0L, BudgetCalculator.status(leisureBudget, transactions, categories, date, date).spentMinor)
    }
}
