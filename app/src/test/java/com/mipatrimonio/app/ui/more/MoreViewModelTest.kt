package com.mipatrimonio.app.ui.more

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoreViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `sin cuentas publica un estado vacio no cargando`() = runTest {
        val state = MoreViewModel(ledger, investments).uiState.first { !it.isLoading }

        assertTrue(state.accounts.isEmpty())
        assertTrue(state.totals.isEmpty())
    }

    @Test
    fun `muestra cuentas activas con saldo derivado y total por divisa`() = runTest {
        ledger.saveAccount(account("b", "Ahorro", "EUR", 20_00))
        ledger.saveAccount(account("a", "Diaria", "EUR", 100_00))
        ledger.saveAccount(account("usd", "Dólares", "USD", 50_00))
        ledger.saveAccount(account("old", "Archivada", "EUR", 999_00, archived = true))
        ledger.saveTransaction(transaction("income", TransactionType.INGRESO, 30_00, "a"))
        ledger.saveTransaction(transaction("expense", TransactionType.GASTO, 5_00, "b"))

        val state = MoreViewModel(ledger, investments).uiState.first {
            !it.isLoading && it.accounts.size == 3
        }

        assertEquals(listOf("Ahorro", "Diaria", "Dólares"), state.accounts.map { it.account.name })
        assertFalse(state.accounts.any { it.account.id == "old" })
        assertEquals(15_00L, state.accounts.first { it.account.id == "b" }.balanceMinor)
        assertEquals(130_00L, state.accounts.first { it.account.id == "a" }.balanceMinor)
        assertEquals(
            listOf(CurrencyTotal("EUR", 145_00), CurrencyTotal("USD", 50_00)),
            state.totals,
        )
    }

    @Test
    fun `cuenta de inversion puede crear cartera vinculada`() = runTest {
        val viewModel = MoreViewModel(ledger, investments)

        viewModel.createAccount("Broker", AccountType.INVERSION, "EUR", "100", true) {}

        val account = ledger.accounts.first { it.isNotEmpty() }.single()
        val portfolio = investments.portfolios.first { it.isNotEmpty() }.single()
        assertEquals(account.id, portfolio.defaultAccountId)
        assertEquals("Broker", portfolio.name)
    }

    @Test
    fun `cuenta de inversion puede crearse sin cartera vinculada`() = runTest {
        val viewModel = MoreViewModel(ledger, investments)

        viewModel.createAccount("Broker", AccountType.INVERSION, "EUR", "", false) {}

        ledger.accounts.first { it.isNotEmpty() }
        assertTrue(investments.portfolios.first().isEmpty())
    }

    private fun account(
        id: String,
        name: String,
        currency: String,
        initial: Long,
        archived: Boolean = false,
    ) = Account(id, name, AccountType.CORRIENTE, currency, initial, archived, 1)

    private fun transaction(id: String, type: TransactionType, amount: Long, accountId: String) = Transaction(
        id = id,
        type = type,
        amountMinor = amount,
        currency = "EUR",
        date = LocalDate.of(2026, 9, 25),
        accountId = accountId,
        categoryId = null,
        description = "",
        merchant = "",
        notes = "",
        source = TransactionSource.MANUAL,
        createdAt = 1,
        updatedAt = 1,
    )
}
