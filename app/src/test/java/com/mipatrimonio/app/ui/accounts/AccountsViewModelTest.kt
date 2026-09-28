package com.mipatrimonio.app.ui.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.NotificationAuthorizationEntity
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.testutil.SettingsStoreRule
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.math.BigDecimal
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountsViewModelTest {
    @get:Rule
    val settingsRule = SettingsStoreRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 10L }
        investments = InvestmentRepository(db) { 10L }
        settings = settingsRule.repository
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `elimina una cuenta sin referencias`() = runTest {
        ledger.saveAccount(account("a1"))
        val viewModel = AccountsViewModel(ledger, settings)
        val managed = viewModel.uiState.first { !it.isLoading }.accounts.single().account

        viewModel.delete(managed) {}

        assertTrue(viewModel.uiState.first { !it.isLoading && it.accounts.isEmpty() }.accounts.isEmpty())
    }

    @Test fun `informa todos los recuentos que bloquean eliminar`() = runTest {
        ledger.saveAccount(account("a1", initial = 10_000))
        ledger.saveAccount(account("a2"))
        ledger.saveTransaction(transaction("t1", "a1"))
        ledger.saveTransfer(Transfer("tr1", "a1", "a2", 100, 100, LocalDate.of(2026, 1, 1), "", 1))
        investments.savePortfolio(Portfolio("p1", "Cartera", 1, "a1"))
        investments.saveAsset(Asset("as1", "ETF", "ETF", "", AssetType.ETF, "", "EUR"))
        investments.addOperation(operation("a1"))
        db.notificationDao().upsertAuthorizationRule(NotificationAuthorizationEntity("bank.app", true, "a1", 1))
        val viewModel = AccountsViewModel(ledger, settings)

        viewModel.inspect("a1")

        val deps = viewModel.uiState.first { it.dependencies["a1"] != null }.dependencies.getValue("a1")
        assertEquals(1, deps.transactions)
        assertEquals(1, deps.transfers)
        assertEquals(1, deps.investmentOperations)
        assertEquals(1, deps.portfolios)
        assertEquals(1, deps.notificationApps)
        assertFalse(deps.canDelete)
        assertTrue(runCatching { ledger.deleteAccount("a1") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun `archivar conserva saldo y desvincula la cartera`() = runTest {
        ledger.saveAccount(account("a1", initial = 12_345))
        investments.savePortfolio(Portfolio("p1", "Cartera", 1, "a1"))
        val viewModel = AccountsViewModel(ledger, settings)
        val before = viewModel.uiState.first { !it.isLoading && it.accounts.isNotEmpty() }.accounts.single()

        viewModel.setArchived(before.account, true) {}

        val after = viewModel.uiState.first { it.accounts.singleOrNull()?.account?.archived == true }.accounts.single()
        assertEquals(12_345L, after.balanceMinor)
        assertNull(investments.portfolios.first().single().defaultAccountId)
    }

    @Test fun `divisa cambia sin historial y se bloquea con movimientos`() = runTest {
        ledger.saveAccount(account("a1"))
        ledger.saveAccount(account("a1", currency = "USD"))
        assertEquals("USD", ledger.accounts.first().single().currency)
        ledger.saveTransaction(transaction("t1", "a1", "USD"))

        val failure = runCatching { ledger.saveAccount(account("a1", currency = "GBP")) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun `filtros separan activas archivadas y todas`() {
        val active = ManagedAccount(account("a1"), 0)
        val archived = ManagedAccount(account("a2").copy(archived = true), 0)
        assertEquals(listOf(active), filterAccounts(listOf(active, archived), AccountFilter.ACTIVAS))
        assertEquals(listOf(archived), filterAccounts(listOf(active, archived), AccountFilter.ARCHIVADAS))
        assertEquals(2, filterAccounts(listOf(active, archived), AccountFilter.TODAS).size)
    }

    private fun account(id: String, initial: Long = 0, currency: String = "EUR") =
        Account(id, "Cuenta", AccountType.CORRIENTE, currency, initial, false, 1)

    private fun transaction(id: String, accountId: String, currency: String = "EUR") = Transaction(
        id, TransactionType.GASTO, 100, currency, LocalDate.of(2026, 1, 1), accountId, null,
        "", "", "", TransactionSource.MANUAL, 1, 1,
    )

    private fun operation(accountId: String) = InvestmentOperation(
        "op1", "p1", "as1", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
        BigDecimal.ONE, BigDecimal.TEN, 0, "EUR", "", 1, accountId,
    )
}
