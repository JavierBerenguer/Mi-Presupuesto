package com.mipatrimonio.app.ui.budgets

import com.mipatrimonio.app.domain.calc.BudgetLevel
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
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
}
