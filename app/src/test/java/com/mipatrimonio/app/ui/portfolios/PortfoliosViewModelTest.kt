package com.mipatrimonio.app.ui.portfolios

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.testutil.SettingsStoreRule
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
class PortfoliosViewModelTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db) { 100L }
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `crea renombra y cambia o quita cuenta predeterminada`() = runTest {
        ledger.saveAccount(Account("a", "Broker", AccountType.INVERSION, "EUR", 0, false, 1))
        val viewModel = PortfoliosViewModel(ledger, investments, settingsRule.repository)
        var saved = false
        viewModel.save(null, "Principal", "a") { saved = true }
        val created = viewModel.uiState.first { !it.isLoading && it.portfolios.size == 1 }.portfolios.single().portfolio
        assertTrue(saved)
        assertEquals("a", created.defaultAccountId)

        viewModel.save(created, "Jubilación", null) {}
        val renamed = viewModel.uiState.first { it.portfolios.singleOrNull()?.portfolio?.name == "Jubilación" }
        assertNull(renamed.portfolios.single().portfolio.defaultAccountId)
    }

    @Test fun `archiva reactiva filtra e invalida seleccion`() = runTest {
        val portfolio = Portfolio("p", "Principal", 1)
        investments.savePortfolio(portfolio)
        settingsRule.repository.setSelectedPortfolioId("p")
        val viewModel = PortfoliosViewModel(ledger, investments, settingsRule.repository)
        viewModel.uiState.first { !it.isLoading }

        viewModel.setArchived(portfolio, true) {}
        val archived = viewModel.uiState.first { it.portfolios.singleOrNull()?.portfolio?.archived == true }
        assertTrue(filterPortfolios(archived.portfolios, PortfolioFilter.ACTIVAS).isEmpty())
        assertEquals(1, filterPortfolios(archived.portfolios, PortfolioFilter.ARCHIVADAS).size)
        assertNull(settingsRule.repository.settings.first { it.selectedPortfolioId == null }.selectedPortfolioId)

        viewModel.setArchived(portfolio.copy(archived = true), false) {}
        assertFalse(viewModel.uiState.first { it.portfolios.singleOrNull()?.portfolio?.archived == false }.portfolios.single().portfolio.archived)
    }

    @Test fun `muestra posiciones y bloquea eliminar cartera con operaciones`() = runTest {
        val portfolio = Portfolio("p", "Principal", 1)
        investments.savePortfolio(portfolio)
        investments.saveAsset(Asset("asset", "ETF", "ETF", "", AssetType.ETF, "", "EUR"))
        investments.addOperation(
            InvestmentOperation(
                "op", "p", "asset", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
                BigDecimal.ONE, BigDecimal.TEN, 0, "EUR", "", 1,
            ),
        )
        val viewModel = PortfoliosViewModel(ledger, investments, settingsRule.repository)
        val state = viewModel.uiState.first { !it.isLoading && it.portfolios.singleOrNull()?.openAssets == 1 }
        viewModel.inspect("p")
        assertEquals(1, viewModel.uiState.first { it.dependencies["p"] != null }.dependencies.getValue("p").operations)

        viewModel.delete(state.portfolios.single().portfolio) {}
        assertTrue(viewModel.uiState.first { it.error != null }.error!!.contains("1 operaciones"))
        assertEquals(1, investments.portfolios.first().size)
    }
}
