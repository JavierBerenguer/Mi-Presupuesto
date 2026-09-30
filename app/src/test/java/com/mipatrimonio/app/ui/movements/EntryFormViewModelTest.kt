package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
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
class EntryFormViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedToday = LocalDate.of(2026, 9, 25)
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
    fun `guarda gasto e ingreso aceptando coma y punto`() = runTest {
        ledger.saveAccount(account("a"))

        val expense = viewModel()
        expense.ready()
        expense.setAmount("12,34")
        expense.setTitle("Compra")
        expense.save()

        val income = viewModel()
        income.ready()
        income.setKind(EntryKind.INCOME)
        income.setAmount("45.67")
        income.save()

        val transactions = ledger.transactions.awaitValue { it.size == 2 }
        assertEquals(2, transactions.size)
        assertEquals(1_234L, transactions.first { it.type == TransactionType.GASTO }.amountMinor)
        assertEquals(4_567L, transactions.first { it.type == TransactionType.INGRESO }.amountMinor)
    }

    @Test
    fun `guarda transferencia categorizada sin crear ingreso ni gasto`() = runTest {
        ledger.saveAccount(account("eur-1"))
        ledger.saveAccount(account("eur-2"))
        ledger.saveCategory(category("salary"))
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.setKind(EntryKind.TRANSFER)
        viewModel.setCategory("salary")
        viewModel.setAmount("25")
        viewModel.save()

        val transfer = ledger.transfers.awaitValue { it.size == 1 }.single()
        assertTrue(ledger.transactions.awaitValue { it.isEmpty() }.isEmpty())
        assertEquals(2_500L, transfer.fromAmountMinor)
        assertEquals(2_500L, transfer.toAmountMinor)
        assertEquals("salary", transfer.categoryId)
    }

    @Test
    fun `exige importe destino para divisas distintas y permite intercambiar cuentas`() = runTest {
        ledger.saveAccount(account("eur", currency = "EUR"))
        ledger.saveAccount(account("usd", currency = "USD"))
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.setKind(EntryKind.TRANSFER)
        viewModel.setAmount("10")
        viewModel.save()
        assertEquals(EntryFormError.DESTINATION_AMOUNT_REQUIRED, viewModel.uiState.value.error)

        viewModel.setDestinationAmount("11")
        viewModel.swapAccounts()
        val swapped = viewModel.uiState.value.values
        assertEquals("usd", swapped.accountId)
        assertEquals("eur", swapped.destinationAccountId)
        assertEquals("11", swapped.amount)
        assertEquals("10", swapped.destinationAmount)
        assertTrue(viewModel.uiState.value.isCrossCurrency)
        assertEquals("0.909091", viewModel.uiState.value.exchangeRate)
    }

    @Test
    fun `valida importes vacios cero negativos enormes y cuentas iguales`() = runTest {
        ledger.saveAccount(account("a"))
        ledger.saveAccount(account("b"))
        val viewModel = viewModel()
        viewModel.ready()

        listOf("", "0", "-1", "999999999999999999999999999999999999").forEach { invalid ->
            viewModel.setAmount(invalid)
            viewModel.save()
            assertEquals(EntryFormError.INVALID_AMOUNT, viewModel.uiState.value.error)
        }

        viewModel.setKind(EntryKind.TRANSFER)
        viewModel.setAmount("1")
        viewModel.setDestinationAccount("a")
        viewModel.save()
        assertEquals(EntryFormError.ACCOUNTS_MUST_DIFFER, viewModel.uiState.value.error)
    }

    @Test
    fun `edita transaccion y transferencia existentes sin duplicarlas y bloquea el tipo`() = runTest {
        ledger.saveAccount(account("a"))
        ledger.saveAccount(account("b"))
        ledger.saveTransaction(
            transaction("tx", TransactionType.GASTO, 100, "a", merchant = "Tienda", notes = "Nota"),
        )
        ledger.saveTransfer(Transfer("tr", "a", "b", 200, 200, fixedToday, "Antes", 2))

        val transactionVm = viewModel("tx")
        transactionVm.ready()
        transactionVm.setKind(EntryKind.TRANSFER)
        assertEquals(EntryKind.EXPENSE, transactionVm.uiState.value.values.kind)
        transactionVm.setAmount("3")
        transactionVm.setTitle("Después")
        transactionVm.save()

        val transferVm = viewModel("tr")
        transferVm.ready()
        transferVm.setKind(EntryKind.INCOME)
        assertEquals(EntryKind.TRANSFER, transferVm.uiState.value.values.kind)
        transferVm.setAmount("4")
        transferVm.save()

        val transactions = ledger.transactions.awaitValue { transactions ->
            transactions.singleOrNull()?.let { it.amountMinor == 300L && it.description == "Después" } == true
        }
        val transfers = ledger.transfers.awaitValue { transfers ->
            transfers.singleOrNull()?.fromAmountMinor == 400L
        }
        val transaction = transactions.single()
        val transfer = transfers.single()
        assertEquals(1, transactions.size)
        assertEquals(300L, transaction.amountMinor)
        assertEquals("Después", transaction.description)
        assertEquals("Tienda", transaction.merchant)
        assertEquals("Nota", transaction.notes)
        assertEquals(1, transfers.size)
        assertEquals(400L, transfer.fromAmountMinor)
    }

    @Test
    fun `ofrece todas las categorias activas para cualquier tipo de apunte`() = runTest {
        ledger.saveAccount(account("a"))
        ledger.saveAccount(account("b"))
        ledger.saveCategory(category("expense"))
        ledger.saveCategory(category("income"))

        val viewModel = viewModel()
        val expected = setOf("expense", "income")
        assertEquals(expected, viewModel.ready().availableCategories.map { it.id }.toSet())
        viewModel.setCategory("income")
        viewModel.setAmount("1")
        viewModel.save()
        val expenseTransaction = ledger.transactions.awaitValue { transactions ->
            transactions.singleOrNull()?.categoryId == "income"
        }.single()
        assertEquals("income", expenseTransaction.categoryId)

        val income = viewModel()
        income.ready()
        income.setKind(EntryKind.INCOME)
        assertEquals(expected, income.uiState.value.availableCategories.map { it.id }.toSet())
        income.setCategory("expense")
        income.setAmount("2")
        income.save()
        val incomeTransaction = ledger.transactions.awaitValue { transactions ->
            transactions.firstOrNull { it.type == TransactionType.INGRESO }?.categoryId == "expense"
        }.first { it.type == TransactionType.INGRESO }
        assertEquals("expense", incomeTransaction.categoryId)

        val transfer = viewModel()
        transfer.ready()
        transfer.setKind(EntryKind.TRANSFER)
        assertEquals(expected, transfer.uiState.value.availableCategories.map { it.id }.toSet())
    }

    @Test
    fun `edita transferencia para asignar y quitar categoria y conserva una archivada existente`() = runTest {
        ledger.saveAccount(account("a"))
        ledger.saveAccount(account("b"))
        ledger.saveCategory(category("active"))
        ledger.saveCategory(category("archived", archived = true))
        ledger.saveTransfer(
            Transfer("tr", "a", "b", 100, 100, fixedToday, "", 1, categoryId = "archived"),
        )

        val editing = viewModel("tr")
        val initial = editing.ready()
        assertEquals("archived", initial.selectedCategory?.id)
        assertTrue(initial.selectedCategoryIsArchived)
        assertFalse(initial.availableCategories.any { it.id == "archived" })

        editing.setCategory("active")
        editing.save()
        val categorized = ledger.transfers.awaitValue { transfers ->
            transfers.singleOrNull()?.categoryId == "active"
        }.single()
        assertEquals("active", categorized.categoryId)

        val removeCategory = viewModel("tr")
        removeCategory.ready()
        removeCategory.setCategory(null)
        removeCategory.save()
        val uncategorized = ledger.transfers.awaitValue { transfers ->
            transfers.singleOrNull()?.let { it.categoryId == null } == true
        }.single()
        assertNull(uncategorized.categoryId)
    }

    @Test
    fun `usa como cuenta por defecto la del ultimo apunte y nunca una archivada`() = runTest {
        ledger.saveAccount(account("first"))
        ledger.saveAccount(account("latest"))
        ledger.saveAccount(account("transfer-source"))
        ledger.saveTransaction(transaction("tx", TransactionType.GASTO, 100, "first", createdAt = 10))
        ledger.saveTransfer(Transfer("tr", "transfer-source", "first", 100, 100, fixedToday, "", 20))

        assertEquals("transfer-source", viewModel().ready().values.accountId)

        ledger.saveTransaction(transaction("archived-last", TransactionType.GASTO, 100, "latest", createdAt = 30))
        ledger.setAccountArchived("latest", true)
        assertEquals("transfer-source", viewModel().ready().values.accountId)
    }

    @Test
    fun `categorias frecuentes usan los ultimos noventa dias y el tipo actual`() = runTest {
        ledger.saveAccount(account("a"))
        ledger.saveCategory(category("food"))
        ledger.saveCategory(category("transport"))
        ledger.saveCategory(category("salary"))
        ledger.saveTransaction(transaction("f1", TransactionType.GASTO, 100, "a", "food", fixedToday))
        ledger.saveTransaction(transaction("f2", TransactionType.GASTO, 100, "a", "food", fixedToday.minusDays(89)))
        ledger.saveTransaction(transaction("t1", TransactionType.GASTO, 100, "a", "transport", fixedToday))
        ledger.saveTransaction(transaction("old", TransactionType.GASTO, 100, "a", "transport", fixedToday.minusDays(90)))
        ledger.saveTransaction(transaction("salary", TransactionType.INGRESO, 100, "a", "salary", fixedToday))

        val viewModel = viewModel()
        assertEquals(listOf("food", "transport"), viewModel.ready().frequentCategories.map { it.id })
        viewModel.setKind(EntryKind.INCOME)
        assertEquals(listOf("salary"), viewModel.uiState.value.frequentCategories.map { it.id })
    }

    @Test
    fun `guardar y anadir otro conserva cuenta fecha y tipo pero limpia los datos`() = runTest {
        ledger.saveAccount(account("a"))
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.setKind(EntryKind.INCOME)
        viewModel.setDate(fixedToday.minusDays(3))
        viewModel.setAmount("9")
        viewModel.setTitle("Ingreso")
        viewModel.setComment("Comentario")
        viewModel.save(addAnother = true)

        // uiState solo se actualiza mientras hay un recolector: se espera al estado ya reiniciado.
        val state = viewModel.uiState.awaitValue { it.values.amount.isEmpty() && !it.isDirty }
        val values = state.values
        assertEquals(EntryKind.INCOME, values.kind)
        assertEquals("a", values.accountId)
        assertEquals(fixedToday.minusDays(3), values.date)
        assertEquals("", values.amount)
        assertEquals("", values.title)
        assertEquals("", values.comment)
        assertFalse(state.isDirty)
        assertEquals(1, ledger.transactions.awaitValue { it.size == 1 }.size)
    }

    @Test
    fun `cuentas archivadas no se ofrecen ni se pueden guardar`() = runTest {
        ledger.saveAccount(account("active"))
        ledger.saveAccount(account("archived", archived = true))
        val viewModel = viewModel()
        val state = viewModel.ready()
        assertEquals(listOf("active"), state.activeAccounts.map { it.id })

        viewModel.setAccount("archived")
        viewModel.setAmount("1")
        viewModel.save()
        assertEquals(EntryFormError.ACCOUNT_REQUIRED, viewModel.uiState.value.error)
    }

    private fun viewModel(id: String? = null) = EntryFormViewModel(
        ledger = ledger,
        entryId = id,
        today = { fixedToday },
        clock = { 10_000L },
    )

    private suspend fun EntryFormViewModel.ready(): EntryFormUiState = uiState.first { !it.isLoading }

    private fun account(id: String, currency: String = "EUR", archived: Boolean = false) = Account(
        id = id,
        name = "Cuenta $id",
        type = AccountType.CORRIENTE,
        currency = currency,
        initialBalanceMinor = 0,
        archived = archived,
        createdAt = 1,
    )

    private fun category(id: String, archived: Boolean = false) = Category(
        id = id,
        name = id,
        parentId = null,
        colorArgb = 0,
        archived = archived,
    )

    private fun transaction(
        id: String,
        type: TransactionType,
        amount: Long,
        accountId: String,
        categoryId: String? = null,
        date: LocalDate = fixedToday,
        createdAt: Long = 1,
        merchant: String = "",
        notes: String = "",
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amount,
        currency = "EUR",
        date = date,
        accountId = accountId,
        categoryId = categoryId,
        description = "",
        merchant = merchant,
        notes = notes,
        source = TransactionSource.MANUAL,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
