package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.testutil.SettingsStoreRule
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
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
class MovementAccountsViewModelTest {
    @get:Rule
    val settingsRule = SettingsStoreRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db)
        settings = settingsRule.repository
        settings.setMovementsIncludedAccountIds(emptySet())
        settings.setMovementsAllAccounts(true)
        settings.setHideAmounts(false)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `por defecto selecciona todas las activas y persiste seleccion individual`() = runTest {
        ledger.saveAccount(account("a", 1_000))
        ledger.saveAccount(account("b", 2_000))
        val viewModel = MovementAccountsViewModel(ledger, investments, settings)
        val initial = viewModel.uiState.first { !it.isLoading && it.accounts.size == 2 }
        assertTrue(initial.allSelected)
        assertEquals(3_000L, initial.totalMinor)

        viewModel.toggleAccount("a")

        val updated = viewModel.uiState.first { state ->
            !state.allSelected && state.accounts.single { it.account.id == "a" }.selected.not()
        }
        assertEquals(3_000L, updated.totalMinor)
        assertEquals(setOf("b"), settings.settings.first().movementsIncludedAccountIds)
    }

    @Test
    fun `deseleccionar todas persiste ninguna cuenta en vez de volver a todas`() = runTest {
        ledger.saveAccount(account("a", 1_000))
        ledger.saveAccount(account("b", 2_000))
        val viewModel = MovementAccountsViewModel(ledger, investments, settings)
        viewModel.uiState.first { !it.isLoading && it.allSelected }

        viewModel.toggleAll()

        val updated = viewModel.uiState.first { !it.isLoading && it.accounts.none(MovementAccountChoice::selected) }
        assertFalse(updated.allSelected)
        val persisted = settings.settings.first()
        assertFalse(persisted.movementsAllAccounts)
        assertTrue(persisted.movementsIncludedAccountIds.isEmpty())
    }

    @Test
    fun `archivada con saldo se muestra si estaba incluida explicitamente`() = runTest {
        ledger.saveAccount(account("archived", 700).copy(archived = true))
        settings.setMovementsIncludedAccountIds(setOf("archived"))
        settings.setMovementsAllAccounts(false)
        val viewModel = MovementAccountsViewModel(ledger, investments, settings)

        val state = viewModel.uiState.first { !it.isLoading && it.accounts.isNotEmpty() }

        assertEquals("archived", state.accounts.single().account.id)
        assertTrue(state.accounts.single().selected)
        assertFalse(state.accounts.single().balanceMinor == 0L)
    }

    @Test
    fun `saldo de cuenta incluye compra de inversion`() = runTest {
        ledger.saveAccount(account("bank", 500_00))
        investments.savePortfolio(Portfolio("portfolio", "Cartera", 1))
        investments.saveAsset(Asset("asset", "Fondo", "", "", AssetType.ETF, "", "EUR"))
        investments.addOperation(
            InvestmentOperation(
                "buy", "portfolio", "asset", OperationType.COMPRA, LocalDate.now(), BigDecimal.ONE,
                BigDecimal("100.00"), 0, "EUR", "", 1, "bank",
            ),
        )

        val state = MovementAccountsViewModel(ledger, investments, settings).uiState.first {
            !it.isLoading && it.accounts.singleOrNull()?.balanceMinor == 400_00L
        }

        assertEquals(400_00L, state.accounts.single().balanceMinor)
        assertEquals(400_00L, state.totalMinor)
    }

    private fun account(id: String, initial: Long) =
        Account(id, id, AccountType.CORRIENTE, "EUR", initial, false, 1)
}
