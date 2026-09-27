package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LedgerRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = LedgerRepository(db) { 10L }
    }
    @After fun tearDown() = db.close()

    @Test fun `guardar reemplaza cabecera y reglas atomica y rechaza categoria archivada`() = runTest {
        repository.saveCategory(category("food"))
        repository.saveCategory(category("leisure"))
        repository.saveBudget(budget(listOf(BudgetCategoryRule("food", true))))
        repository.saveBudget(budget(listOf(BudgetCategoryRule("leisure", false))).copy(name = "Actualizado"))
        val saved = repository.budgets.first().single()
        assertEquals("Actualizado", saved.name)
        assertEquals(listOf(BudgetCategoryRule("leisure", false)), saved.categoryRules)

        repository.saveCategory(category("archived", archived = true))
        val failure = runCatching {
            repository.saveBudget(budget(listOf(BudgetCategoryRule("archived", true))).copy(name = "Inválido"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        val unchanged = repository.budgets.first().single()
        assertEquals("Actualizado", unchanged.name)
        assertEquals(listOf(BudgetCategoryRule("leisure", false)), unchanged.categoryRules)
    }

    @Test fun `editar un presupuesto existente no falla si su categoria vinculada se archivo despues`() = runTest {
        repository.saveCategory(category("food"))
        repository.saveBudget(budget(listOf(BudgetCategoryRule("food", true))))

        repository.saveCategory(category("food", archived = true))
        repository.saveBudget(budget(listOf(BudgetCategoryRule("food", true))).copy(name = "Renombrado", limitMinor = 20_000))

        val saved = repository.budgets.first().single()
        assertEquals("Renombrado", saved.name)
        assertEquals(20_000L, saved.limitMinor)
        assertEquals(listOf(BudgetCategoryRule("food", true)), saved.categoryRules)
    }

    private fun category(id: String, archived: Boolean = false) =
        Category(id, id, CategoryKind.GASTO, null, 1, archived)
    private fun budget(rules: List<BudgetCategoryRule>) = Budget(
        "budget", "Casa", 10_000, "EUR", BudgetPeriod.MENSUAL, LocalDate.of(2026, 1, 1), null, 90, rules, false,
    )
}
