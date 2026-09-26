package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvestmentsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db) { 1_000L }
        settings = SettingsRepository(context)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `selecciona cartera y persiste el selector`() = runTest {
        settings.setSelectedPortfolioId(null)
        investments.savePortfolio(Portfolio("p1", "Principal", 1))
        investments.savePortfolio(Portfolio("p2", "Jubilación", 2))
        val viewModel = InvestmentsViewModel(ledger, investments, settings)
        viewModel.uiState.first { !it.isLoading && it.portfolios.size == 2 }

        viewModel.selectPortfolio("p2")

        assertEquals("p2", viewModel.uiState.first { it.selectedPortfolioId == "p2" }.selectedPortfolioId)
        assertEquals("p2", settings.settings.first { it.selectedPortfolioId == "p2" }.selectedPortfolioId)

        viewModel.selectPortfolio(null)
        assertNull(viewModel.uiState.first { it.selectedPortfolioId == null }.selectedPortfolioId)
        assertNull(settings.settings.first { it.selectedPortfolioId == null }.selectedPortfolioId)
    }

    @Test fun `seleccion inexistente vuelve a agregado`() = runTest {
        settings.setSelectedPortfolioId("eliminada")
        val viewModel = InvestmentsViewModel(ledger, investments, settings)

        viewModel.uiState.first { !it.isLoading }

        assertNull(settings.settings.first { it.selectedPortfolioId == null }.selectedPortfolioId)
    }

    @Test fun `calcula distribucion y dividendos netos de la seleccion`() = runTest {
        settings.setSelectedPortfolioId(null)
        investments.savePortfolio(Portfolio("p1", "Principal", 1))
        investments.saveAsset(Asset("a1", "ETF Mundo", "ETF", "", AssetType.ETF, "XETRA", "EUR"))
        investments.addOperation(op("buy", OperationType.COMPRA, "10", "10", 0))
        investments.addOperation(op("div", OperationType.DIVIDENDO, "10", "1", 150))
        investments.setManualPrice("a1", BigDecimal("12"), "EUR")

        val state = InvestmentsViewModel(ledger, investments, settings).uiState.first {
            !it.isLoading && it.dividends.isNotEmpty() && it.allocationsByType.isNotEmpty()
        }

        assertEquals(12_000L, state.allocationsByType.single().valueMinor)
        assertEquals("Principal", state.allocationsByPortfolio.single().label)
        assertEquals("EUR", state.allocationsByCurrency.single().label)
        assertEquals(1_000L, state.dividends.single().grossMinor)
        assertEquals(150L, state.dividends.single().withholdingMinor)
        assertEquals(850L, state.dividends.single().netMinor)
    }

    private fun op(id: String, type: OperationType, quantity: String, price: String, fees: Long) = InvestmentOperation(
        id, "p1", "a1", type, LocalDate.of(2026, 1, if (type == OperationType.COMPRA) 1 else 2),
        BigDecimal(quantity), BigDecimal(price), fees, "EUR", "", 1,
    )
}
