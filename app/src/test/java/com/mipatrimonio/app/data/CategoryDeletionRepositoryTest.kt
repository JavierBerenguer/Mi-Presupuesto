package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.BudgetEntity
import com.mipatrimonio.app.data.db.CategoryEntity
import com.mipatrimonio.app.data.db.RecurringRuleEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryDeletionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 10_000L }
        runBlocking {
            ledger.saveAccount(account("from", 50_000))
            ledger.saveAccount(account("to", 5_000))
        }
    }

    @After fun tearDown() = db.close()

    @Test
    fun `elimina categoria sin uso sin exigir destino`() = runTest {
        ledger.saveCategory(category("unused"))

        val before = ledger.categoryUsage("unused")
        ledger.deleteCategory("unused", null)

        assertFalse(before.isUsed)
        assertEquals(0, before.subcategories)
        assertTrue(ledger.categories.first().none { it.id == "unused" })
    }

    @Test
    fun `subcategoria con movimiento pasa a sin categoria sin cambiar importe ni saldo`() = runTest {
        ledger.saveCategory(category("root"))
        ledger.saveCategory(category("child", parentId = "root"))
        ledger.saveTransaction(transaction("txn", "child", 1_250))
        val balanceBefore = balance("from")

        assertEquals(1, ledger.categoryUsage("child").transactions)
        ledger.deleteCategory("child", null)

        val moved = ledger.transactions.first().single()
        assertNull(moved.categoryId)
        assertEquals(1_250L, moved.amountMinor)
        assertEquals(balanceBefore, balance("from"))
        assertEquals(listOf("root"), ledger.categories.first().map { it.id })
    }

    @Test
    fun `principal traspasa referencias propias y de hijas en una transaccion`() = runTest {
        ledger.saveCategory(category("root"))
        ledger.saveCategory(category("child", parentId = "root"))
        ledger.saveCategory(category("target"))
        ledger.saveTransaction(transaction("txn-root", "root", 700))
        ledger.saveTransaction(transaction("txn-child", "child", 900))
        ledger.saveTransfer(Transfer("transfer", "from", "to", 2_000, 2_000, DAY, "", 1, "child"))
        db.recurringRuleDao().upsert(recurringRule("rule", "root"))
        val balancesBefore = listOf(balance("from"), balance("to"))

        val usage = ledger.categoryUsage("root")
        ledger.deleteCategory("root", "target")

        assertEquals(2, usage.transactions)
        assertEquals(1, usage.transfers)
        assertEquals(1, usage.recurringRules)
        assertEquals(1, usage.subcategories)
        assertTrue(ledger.transactions.first().all { it.categoryId == "target" })
        assertEquals("target", ledger.transfers.first().single().categoryId)
        assertEquals("target", db.recurringRuleDao().getById("rule")?.categoryId)
        assertEquals(balancesBefore, listOf(balance("from"), balance("to")))
        assertEquals(listOf("target"), ledger.categories.first().map { it.id })
    }

    @Test
    fun `presupuesto converge al destino sin duplicar regla`() = runTest {
        ledger.saveCategory(category("source"))
        ledger.saveCategory(category("target"))
        ledger.saveBudget(
            budget(
                listOf(
                    BudgetCategoryRule("source", true),
                    BudgetCategoryRule("target", false),
                ),
            ),
        )
        val before = ledger.budgets.first().single()

        assertEquals(1, ledger.categoryUsage("source").budgets)
        ledger.deleteCategory("source", "target")

        val after = ledger.budgets.first().single()
        assertEquals(before.limitMinor, after.limitMinor)
        assertEquals(before.name, after.name)
        assertEquals(listOf(BudgetCategoryRule("target", false)), after.categoryRules)
        assertEquals(0, ledger.categoryUsage("target").subcategories)
    }

    @Test
    fun `traspasa tambien la categoria directa de presupuestos antiguos`() = runTest {
        ledger.saveCategory(category("source"))
        ledger.saveCategory(category("target"))
        db.budgetDao().insertAllForRestore(
            listOf(
                BudgetEntity(
                    "legacy", "source", "MENSUAL", 15_000, "EUR", false, 1,
                    "Antiguo", DAY.toEpochDay(), null, 90,
                ),
            ),
        )

        assertEquals(1, ledger.categoryUsage("source").budgets)
        ledger.deleteCategory("source", "target")

        val categoryId = db.openHelper.readableDatabase.query(
            "SELECT categoryId FROM budget WHERE id = 'legacy'",
        ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
        assertEquals("target", categoryId)
    }

    @Test
    fun `presupuesto acepta como destino una categoria antigua de ingreso externa al arbol`() = runTest {
        ledger.saveCategory(category("root"))
        ledger.saveCategory(category("child", parentId = "root"))
        db.categoryDao().upsert(CategoryEntity("income", "income", "INGRESO", null, 1, false, 0))
        ledger.saveCategory(category("expense"))
        ledger.saveBudget(budget(listOf(BudgetCategoryRule("child", true))))

        assertFails { ledger.deleteCategory("root", null) }
        assertFails { ledger.deleteCategory("root", "root") }
        assertFails { ledger.deleteCategory("root", "child") }
        assertEquals(setOf("root", "child", "income", "expense"), ledger.categories.first().mapTo(mutableSetOf()) { it.id })

        ledger.deleteCategory("root", "income")
        assertEquals(listOf(BudgetCategoryRule("income", true)), ledger.budgets.first().single().categoryRules)
    }

    @Test
    fun `fallo a mitad revierte movimientos transferencias y categorias`() = runTest {
        ledger.saveCategory(category("source"))
        ledger.saveCategory(category("target"))
        ledger.saveTransaction(transaction("txn", "source", 500))
        ledger.saveTransfer(Transfer("transfer", "from", "to", 1_000, 1_000, DAY, "", 1, "source"))
        db.recurringRuleDao().upsert(recurringRule("rule", "source"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_category_move BEFORE UPDATE OF categoryId ON recurring_rule " +
                "BEGIN SELECT RAISE(ABORT, 'fallo simulado'); END",
        )

        assertFails { ledger.deleteCategory("source", "target") }

        assertEquals("source", ledger.transactions.first().single().categoryId)
        assertEquals("source", ledger.transfers.first().single().categoryId)
        assertEquals("source", db.recurringRuleDao().getById("rule")?.categoryId)
        assertTrue(ledger.categories.first().any { it.id == "source" })
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException || error?.message?.contains("fallo simulado") == true)
    }

    private suspend fun balance(accountId: String): Long = BalanceCalculator.balance(
        account = ledger.accounts.first().first { it.id == accountId },
        transactions = ledger.transactions.first(),
        transfers = ledger.transfers.first(),
        today = DAY,
    )

    private fun account(id: String, initial: Long) =
        Account(id, id, AccountType.CORRIENTE, "EUR", initial, false, 1)

    private fun category(
        id: String,
        parentId: String? = null,
    ) = Category(id, id, parentId, 1, false)

    private fun transaction(id: String, categoryId: String, amountMinor: Long) = Transaction(
        id, TransactionType.GASTO, amountMinor, "EUR", DAY, "from", categoryId,
        "", "", "", TransactionSource.MANUAL, 1, 1,
    )

    private fun budget(rules: List<BudgetCategoryRule>) = Budget(
        id = "budget",
        name = "Presupuesto",
        limitMinor = 20_000,
        currency = "EUR",
        period = BudgetPeriod.MENSUAL,
        startDate = DAY,
        endDate = null,
        alertThresholdPct = 90,
        categoryRules = rules,
        archived = false,
    )

    private fun recurringRule(id: String, categoryId: String) = RecurringRuleEntity(
        id, "GASTO", 300, "EUR", "from", null, categoryId, "", "", DAY.toEpochDay(),
        1, "MES", null, "NO", null, null, false, 1, 1,
    )

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 9, 1)
    }
}
