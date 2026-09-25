package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        advanceUntilIdle()

        val income = viewModel()
        income.ready()
        income.setKind(EntryKind.INCOME)
        income.setAmount("45.67")
        income.save()
        advanceUntilIdle()

        val transactions = ledger.transactions.first()
        assertEquals(2, transactions.size)
        assertEquals(1_234L, transactions.first { it.type == TransactionType.GASTO }.amountMinor)
        assertEquals(4_567L, transactions.first { it.type == TransactionType.INGRESO }.amountMinor)
    }

    @Test
    fun `guarda transferencia y no crea ingreso ni gasto`() = runTest {
        ledger.saveAccount(account("eur-1"))
        ledger.saveAccount(account("eur-2"))
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.setKind(EntryKind.TRANSFER)
        viewModel.setAmount("25")
        viewModel.save()
        advanceUntilIdle()

        assertTrue(ledger.transactions.first().isEmpty())
        val transfer = ledger.transfers.first().single()
        assertEquals(2_500L, transfer.fromAmountMinor)
        assertEquals(2_500L, transfer.toAmountMinor)
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
        advanceUntilIdle()

        assertEquals(1, ledger.transactions.first().size)
        assertEquals(300L, ledger.transactions.first().single().amountMinor)
        assertEquals("Después", ledger.transactions.first().single().description)
        assertEquals("Tienda", ledger.transactions.first().single().merchant)
        assertEquals("Nota", ledger.transactions.first().single().notes)
        assertEquals(1, ledger.transfers.first().size)
        assertEquals(400L, ledger.transfers.first().single().fromAmountMinor)
    }

    @Test
    fun `cambiar tipo limpia categoria incompatible y una archivada existente se muestra con aviso`() = runTest {
        ledger.saveAccount(account("a"))
        ledger.saveCategory(category("expense", CategoryKind.GASTO))
        ledger.saveCategory(category("income", CategoryKind.INGRESO))
        val newEntry = viewModel()
        newEntry.ready()
        newEntry.setCategory("expense")
        newEntry.setKind(EntryKind.INCOME)
        assertNull(newEntry.uiState.value.values.categoryId)

        ledger.saveCategory(category("archived", CategoryKind.GASTO, archived = true))
        ledger.saveTransaction(transaction("old", TransactionType.GASTO, 100, "a", "archived"))
        val editing = viewModel("old")
        val state = editing.ready()
        assertEquals("archived", state.selectedCategory?.id)
        assertTrue(state.selectedCategoryIsArchived)
        assertFalse(state.availableCategories.any { it.id == "archived" })
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
        ledger.saveCategory(category("food", CategoryKind.GASTO))
        ledger.saveCategory(category("transport", CategoryKind.GASTO))
        ledger.saveCategory(category("salary", CategoryKind.INGRESO))
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
        advanceUntilIdle()

        // uiState solo se actualiza mientras hay un recolector: se espera al estado ya reiniciado.
        val state = viewModel.uiState.first { it.values.amount.isEmpty() && !it.isDirty }
        val values = state.values
        assertEquals(EntryKind.INCOME, values.kind)
        assertEquals("a", values.accountId)
        assertEquals(fixedToday.minusDays(3), values.date)
        assertEquals("", values.amount)
        assertEquals("", values.title)
        assertEquals("", values.comment)
        assertFalse(state.isDirty)
        assertEquals(1, ledger.transactions.first().size)
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

    private fun category(id: String, kind: CategoryKind, archived: Boolean = false) = Category(
        id = id,
        name = id,
        kind = kind,
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
