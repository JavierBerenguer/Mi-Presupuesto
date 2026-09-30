package com.mipatrimonio.app.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.CategoryEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.testutil.awaitValue
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoriesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `categoria sin uso abre confirmacion simple`() = runTest {
        ledger.saveCategory(category("unused"))
        val viewModel = readyViewModel()

        viewModel.requestDelete(category("unused"))

        val deletion = viewModel.uiState.first { it.deletion != null }.deletion!!
        assertFalse(deletion.usage.isUsed)
        assertFalse(deletion.targetSelected)
    }

    @Test
    fun `las categorias empiezan plegadas y se pueden desplegar y volver a plegar`() = runTest {
        ledger.saveCategory(category("parent"))
        ledger.saveCategory(category("child", parentId = "parent"))
        val viewModel = readyViewModel()

        assertTrue(viewModel.uiState.first { !it.isLoading }.expandedCategoryIds.isEmpty())

        viewModel.toggleCategoryExpanded("parent")
        assertEquals(
            setOf("parent"),
            viewModel.uiState.first { "parent" in it.expandedCategoryIds }.expandedCategoryIds,
        )

        viewModel.toggleCategoryExpanded("parent")
        assertTrue(viewModel.uiState.first { "parent" !in it.expandedCategoryIds }.expandedCategoryIds.isEmpty())
    }

    @Test
    fun `editar una categoria conserva su estado desplegado`() = runTest {
        val parent = category("parent")
        ledger.saveCategory(parent)
        val viewModel = readyViewModel()
        viewModel.toggleCategoryExpanded(parent.id)

        viewModel.saveCategory(parent, "Nombre nuevo", null, parent.colorArgb) {}

        val state = viewModel.uiState.first { uiState ->
            uiState.categories.any { it.id == parent.id && it.name == "Nombre nuevo" }
        }
        assertTrue(parent.id in state.expandedCategoryIds)
    }

    @Test
    fun `crear una subcategoria deja desplegada su categoria principal`() = runTest {
        val parent = category("parent")
        ledger.saveCategory(parent)
        val viewModel = readyViewModel()

        viewModel.saveCategory(null, "Hija", parent.id, 2L) {}

        val state = viewModel.uiState.first { uiState ->
            uiState.categories.any { it.parentId == parent.id && it.name == "Hija" }
        }
        assertTrue(parent.id in state.expandedCategoryIds)
    }

    @Test
    fun `categoria heredada de ingreso aparece y puede ser padre`() = runTest {
        db.categoryDao().upsert(CategoryEntity("income", "Ingresos", "INGRESO", null, 1, false, 0))
        val viewModel = readyViewModel()

        assertEquals("Ingresos", viewModel.uiState.first { !it.isLoading }.categories.single().name)
        viewModel.saveCategory(null, "Intereses", "income", 2L) {}

        val state = viewModel.uiState.first { uiState ->
            uiState.categories.any { it.name == "Intereses" && it.parentId == "income" }
        }
        assertTrue("income" in state.expandedCategoryIds)
    }

    @Test
    fun `categoria usada exige seleccionar incluso sin categoria`() = runTest {
        ledger.saveAccount(Account("account", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        ledger.saveCategory(category("used"))
        ledger.saveTransaction(
            Transaction(
                "txn", TransactionType.GASTO, 500, "EUR", DAY, "account", "used",
                "", "", "", TransactionSource.MANUAL, 1, 1,
            ),
        )
        val viewModel = readyViewModel()
        viewModel.requestDelete(category("used"))
        viewModel.uiState.first { it.deletion?.usage?.isUsed == true }

        viewModel.confirmDelete()
        assertTrue(ledger.categories.awaitValue { categories -> categories.any { it.id == "used" } }.any { it.id == "used" })

        viewModel.selectDeleteTarget(null)
        assertTrue(viewModel.uiState.first { it.deletion?.targetSelected == true }.deletion!!.targetSelected)
        viewModel.confirmDelete()
        assertNull(viewModel.uiState.awaitValue { it.deletion == null }.deletion)
        val transaction = ledger.transactions.awaitValue { transactions ->
            transactions.singleOrNull()?.let { it.categoryId == null } == true
        }.single()
        assertNull(transaction.categoryId)
    }

    @Test
    fun `presupuesto ofrece cualquier categoria activa fuera del arbol eliminado`() = runTest {
        val categories = listOf(
            category("source"),
            category("source-child", parentId = "source"),
            category("expense"),
            category("expense-child", parentId = "expense"),
            category("income"),
            category("archived", archived = true),
        )
        categories.forEach { ledger.saveCategory(it) }
        ledger.saveBudget(
            Budget(
                "budget", "Presupuesto", 10_000, "EUR", BudgetPeriod.MENSUAL, DAY, null, 90,
                listOf(BudgetCategoryRule("source-child", true)), false,
            ),
        )
        val viewModel = readyViewModel()

        viewModel.requestDelete(categories.first())

        val deletion = viewModel.uiState.first { it.deletion != null }.deletion!!
        assertEquals(1, deletion.usage.budgets)
        assertEquals(listOf("expense", "expense-child", "income"), deletion.targets.map { it.category.id })
        assertEquals(listOf("expense", "expense · expense-child", "income"), deletion.targets.map { it.label })
        assertFalse(deletion.targetSelected)
    }

    private suspend fun readyViewModel(): CategoriesViewModel {
        val viewModel = CategoriesViewModel(ledger)
        viewModel.uiState.first { !it.isLoading }
        return viewModel
    }

    private fun category(
        id: String,
        parentId: String? = null,
        archived: Boolean = false,
    ) = Category(id, id, parentId, 1, archived)

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 9, 1)
    }
}
