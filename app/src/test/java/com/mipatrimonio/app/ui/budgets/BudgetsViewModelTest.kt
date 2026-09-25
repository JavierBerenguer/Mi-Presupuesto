package com.mipatrimonio.app.ui.budgets

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        settings = SettingsRepository(context)
        settings.setHideAmounts(false)
        ledger.saveAccount(account("eur", "EUR", 100_000_00))
        ledger.saveAccount(account("eur2", "EUR", 0))
        ledger.saveAccount(account("usd", "USD", 100_000_00))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `top cinco mas otros suma cien agrega subcategorias y excluye transferencias y divisas`() = runTest {
        val month = YearMonth.now()
        val roots = (1..6).map { index -> category("c$index", "Categoría $index") }
        roots.forEach { ledger.saveCategory(it) }
        ledger.saveCategory(category("sub", "Subcategoría", parentId = "c1"))
        val amounts = listOf(600_00L, 900_00L, 800_00L, 700_00L, 600_00L, 500_00L)
        amounts.forEachIndexed { index, amount ->
            ledger.saveTransaction(tx("t$index", amount, month.atDay(10), "eur", "EUR", "c${index + 1}"))
        }
        ledger.saveTransaction(tx("sub-tx", 400_00, month.atDay(11), "eur", "EUR", "sub"))
        ledger.saveTransaction(tx("foreign", 999_00, month.atDay(12), "usd", "USD", "c1"))
        ledger.saveTransfer(Transfer("transfer", "eur", "eur2", 5_000_00, 5_000_00, month.atDay(13), "", 1))
        settings.setHideAmounts(true)

        val viewModel = BudgetsViewModel(ledger, settings)
        val state = viewModel.uiState.first {
            it.expenseStatistics.totalMinor == 4_500_00L && it.hideAmounts
        }

        assertEquals(6, state.expenseStatistics.categories.size)
        assertEquals(100, state.expenseStatistics.categories.sumOf { it.percentage })
        assertEquals(1, state.expenseStatistics.excludedCount)
        assertEquals(1_000_00L, state.expenseStatistics.categories.first { it.categoryId == "c1" }.amountMinor)
        assertEquals(500_00L, state.expenseStatistics.categories.single { it.isOther }.amountMinor)
        assertEquals(4_500_00L, state.expenseStatistics.totalMinor)
    }

    @Test
    fun `estados vacio superado umbral noventa cambio de mes anual y subcategoria propia`() = runTest {
        val month = YearMonth.now()
        ledger.saveCategory(category("parent", "Vivienda"))
        ledger.saveCategory(category("child", "Alquiler", parentId = "parent"))
        val viewModel = BudgetsViewModel(ledger, settings)
        assertTrue(viewModel.uiState.first { !it.loading }.statuses.isEmpty())

        ledger.saveBudget(Budget("global", null, BudgetPeriod.MENSUAL, 1_000_00, "EUR", false))
        ledger.saveBudget(Budget("parent-budget", "parent", BudgetPeriod.MENSUAL, 1_000_00, "EUR", false))
        ledger.saveBudget(Budget("child-budget", "child", BudgetPeriod.MENSUAL, 500_00, "EUR", false))
        ledger.saveBudget(Budget("annual", "parent", BudgetPeriod.ANUAL, 10_000_00, "EUR", false))
        ledger.saveTransaction(tx("expense", 901_00, month.atDay(10), "eur", "EUR", "child"))
        ledger.saveTransaction(tx("income", 50_00, month.atDay(10), "eur", "EUR", null, TransactionType.INGRESO))

        val populated = viewModel.uiState.first { it.statuses.size == 4 && it.expenseStatistics.totalMinor == 901_00L }
        val global = populated.statuses.first { it.budget.id == "global" }
        val parent = populated.statuses.first { it.budget.id == "parent-budget" }
        val child = populated.statuses.first { it.budget.id == "child-budget" }
        val annual = populated.statuses.first { it.budget.id == "annual" }
        assertFalse(global.remainingMinor < 0)
        assertTrue(usesExpenseWarning(parent))
        assertTrue(child.remainingMinor < 0)
        assertEquals(901_00L, child.spentMinor)
        assertEquals(901_00L, annual.spentMinor)
        assertEquals(50_00L, populated.incomeStatistics.totalMinor)

        viewModel.previousMonth()
        val previous = viewModel.uiState.first { it.selectedMonth == month.minusMonths(1) }
        assertTrue(previous.expenseStatistics.categories.isEmpty())
        assertEquals(0L, previous.statuses.first { it.budget.id == "global" }.spentMinor)
    }

    @Test
    fun `admite global sin categorias categoria sin global y un solo segmento`() = runTest {
        val month = YearMonth.now()
        ledger.saveBudget(Budget("global", null, BudgetPeriod.MENSUAL, 1_000_00, "EUR", false))
        ledger.saveTransaction(tx("only", 100_00, month.atDay(5), "eur", "EUR", null))
        val viewModel = BudgetsViewModel(ledger, settings)

        val globalOnly = viewModel.uiState.first { it.statuses.singleOrNull()?.budget?.id == "global" }
        assertEquals(1, globalOnly.expenseStatistics.categories.size)
        assertEquals(100, globalOnly.expenseStatistics.categories.single().percentage)
        assertTrue(globalOnly.statuses.none { it.budget.categoryId != null })

        ledger.deleteBudget("global")
        ledger.saveCategory(category("food", "Alimentación"))
        ledger.saveBudget(Budget("food-budget", "food", BudgetPeriod.MENSUAL, 500_00, "EUR", false))
        val categoryOnly = viewModel.uiState.first { it.statuses.singleOrNull()?.budget?.id == "food-budget" }
        assertTrue(categoryOnly.statuses.none { it.budget.categoryId == null })
    }

    private fun account(id: String, currency: String, initial: Long) =
        Account(id, "Cuenta $id", AccountType.CORRIENTE, currency, initial, false, 1)

    private fun category(id: String, name: String, parentId: String? = null) =
        Category(id, name, CategoryKind.GASTO, parentId, 0xFF336655, false)

    private fun tx(
        id: String,
        amount: Long,
        date: LocalDate,
        accountId: String,
        currency: String,
        categoryId: String?,
        type: TransactionType = TransactionType.GASTO,
    ) = Transaction(
        id, type, amount, currency, date, accountId, categoryId, "", "", "",
        TransactionSource.MANUAL, 1, 1,
    )
}
