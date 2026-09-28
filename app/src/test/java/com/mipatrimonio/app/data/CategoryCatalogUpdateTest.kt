package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AccountEntity
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.BudgetCategoryEntity
import com.mipatrimonio.app.data.db.BudgetEntity
import com.mipatrimonio.app.data.db.CategoryEntity
import com.mipatrimonio.app.data.db.TransactionEntity
import com.mipatrimonio.app.data.repository.DefaultCategories
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryCatalogUpdateTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test
    fun `base vacia recibe catalogo completo sin ids antiguos`() = runTest {
        ledger.updateDefaultCategoryCatalogIfNeeded(settingsRule.repository)

        val categories = ledger.categories.first()
        assertEquals(DefaultCategories.all.toSet(), categories.toSet())
        assertTrue(categories.none { it.id in DefaultCategories.legacyIds })
        assertEquals(2, settingsRule.repository.settings.first().categoryCatalogVersion)
    }

    @Test
    fun `actualizacion archiva antiguas conserva referencias y no toca categorias del usuario`() = runTest {
        val oldCategories = DefaultCategories.legacyIds.mapIndexed { index, id ->
            CategoryEntity(id, "Antigua $index", "GASTO", null, index.toLong(), false, index)
        }
        val old = oldCategories.single { it.id == "cat-alimentacion" }
        val own = CategoryEntity("usuario-coche", "Coche", "GASTO", null, 2, false, 1)
        val existingCatalogId = CategoryEntity("mp-coche", "Mi coche", "GASTO", null, 99, false, 999)
        db.categoryDao().upsertAll(oldCategories + own + existingCatalogId)
        db.accountDao().insertAllForRestore(listOf(AccountEntity("a", "Cuenta", "CORRIENTE", "EUR", 0, false, 1, 1)))
        db.transactionDao().insertAllForRestore(listOf(TransactionEntity("t", "GASTO", 250, "EUR", LocalDate.of(2026, 9, 1).toEpochDay(), "a", old.id, "", "", "", "MANUAL", 1, 1)))
        db.budgetDao().insertAllForRestore(listOf(BudgetEntity("b", null, "MENSUAL", 1_000, "EUR", false, 1, "Comida", LocalDate.of(2026, 9, 1).toEpochDay(), null, 90)))
        db.budgetDao().insertAllRulesForRestore(listOf(BudgetCategoryEntity("b", old.id, true)))

        ledger.updateDefaultCategoryCatalogIfNeeded(settingsRule.repository)

        val categories = ledger.categories.first()
        assertTrue(DefaultCategories.legacyIds.all { id -> categories.single { it.id == id }.archived })
        assertFalse(categories.single { it.id == own.id }.archived)
        assertEquals("Mi coche", categories.single { it.id == "mp-coche" }.name)
        assertEquals(99L, categories.single { it.id == "mp-coche" }.colorArgb)
        assertEquals(2, categories.count { it.name == "Coche" || it.name == "Mi coche" })
        assertEquals(old.id, ledger.transactions.first().single().categoryId)
        val budget = ledger.budgets.first().single()
        assertEquals(old.id, budget.categoryRules.single().categoryId)
        assertEquals(250L, BudgetCalculator.status(budget, ledger.transactions.first(), categories, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).spentMinor)
    }

    @Test
    fun `segunda ejecucion no repone ni desarchiva categorias del catalogo`() = runTest {
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.updateDefaultCategoryCatalogIfNeeded(settingsRule.repository)
        val category = ledger.categories.first().single { it.id == "mp-coche" }
        ledger.saveCategory(category.copy(archived = true))

        ledger.updateDefaultCategoryCatalogIfNeeded(settingsRule.repository)

        assertTrue(ledger.categories.first().single { it.id == "mp-coche" }.archived)
        assertEquals(107, ledger.categories.first().size)
    }

    @Test
    fun `categoria del catalogo eliminada no reaparece tras actualizar de nuevo`() = runTest {
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.updateDefaultCategoryCatalogIfNeeded(settingsRule.repository)
        ledger.deleteCategory("mp-coche-parking", null)

        ledger.updateDefaultCategoryCatalogIfNeeded(settingsRule.repository)

        assertTrue(ledger.categories.first().none { it.id == "mp-coche-parking" })
        assertEquals(106, ledger.categories.first().size)
    }
}
