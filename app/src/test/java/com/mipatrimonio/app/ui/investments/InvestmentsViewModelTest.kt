package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.testutil.SettingsStoreRule
import com.mipatrimonio.app.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db) { 1_000L }
        settings = settingsRule.repository
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

    @Test fun `seleccion archivada vuelve a agregado y conserva la cartera para calculos`() = runTest {
        investments.savePortfolio(Portfolio("p1", "Principal", 1, archived = true))
        settings.setSelectedPortfolioId("p1")
        val viewModel = InvestmentsViewModel(ledger, investments, settings)

        val state = viewModel.uiState.first { !it.isLoading && it.portfolios.size == 1 }

        assertNull(state.selectedPortfolioId)
        assertTrue(state.portfolios.single().archived)
        assertNull(settings.settings.first { it.selectedPortfolioId == null }.selectedPortfolioId)
    }

    @Test fun `selector de operaciones excluye carteras archivadas`() {
        val active = Portfolio("active", "Activa", 1)
        val archived = Portfolio("archived", "Archivada", 2, archived = true)
        assertEquals(listOf(active), operationPortfolios(listOf(active, archived)))
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

    @Test fun `publica desglose de rentabilidad y comisiones de la seleccion`() = runTest {
        settings.setSelectedPortfolioId(null)
        investments.savePortfolio(Portfolio("p1", "Principal", 1))
        investments.saveAsset(Asset("a1", "ETF Mundo", "ETF", "", AssetType.ETF, "XETRA", "EUR"))
        investments.addOperation(op("buy", OperationType.COMPRA, "10", "10", 100))
        investments.addOperation(op("sale", OperationType.VENTA, "4", "15", 50))
        investments.addOperation(op("div", OperationType.DIVIDENDO, "10", "1", 150))
        investments.addOperation(op("fee", OperationType.COMISION, "1", "2", 0))
        investments.setManualPrice("a1", BigDecimal("12"), "EUR")

        val state = InvestmentsViewModel(ledger, investments, settings).uiState.first {
            !it.isLoading && it.totalFeesMinor == 350L
        }

        assertEquals(6_060L, state.totalCostMinor)
        assertEquals(7_200L, state.totalValueMinor)
        assertEquals(1_140L, state.totalUnrealizedMinor)
        assertEquals(BigDecimal("18.81"), state.totalUnrealizedPct)
        assertEquals(1_910L, state.totalRealizedMinor)
        assertEquals(BigDecimal("47.28"), state.totalRealizedPct)
        assertEquals(850L, state.totalDividendsNetMinor)
        assertEquals(350L, state.totalFeesMinor)
    }

    @Test fun `nueva operacion usa la hora actual por defecto`() = runTest {
        val portfolio = Portfolio("p1", "Principal", 1)
        val asset = Asset("a1", "ETF Mundo", "ETF", "", AssetType.ETF, "XETRA", "EUR")
        investments.savePortfolio(portfolio)
        investments.saveAsset(asset)
        val current = LocalDateTime.of(2026, 4, 5, 14, 37, 21)
        val viewModel = InvestmentsViewModel(ledger, investments, settings, now = { current })

        viewModel.addOperation(
            portfolio, asset, OperationType.COMPRA, current.toLocalDate(), BigDecimal.ONE,
            BigDecimal.TEN, 0, null, "", {},
        )

        val operation = investments.operations.first { it.isNotEmpty() }.single()
        assertEquals(LocalTime.of(14, 37, 21), operation.time)
    }

    @Test fun `cartera sin operaciones publica desglose a cero y porcentajes nulos`() = runTest {
        settings.setSelectedPortfolioId(null)
        investments.savePortfolio(Portfolio("p1", "Principal", 1))

        val state = InvestmentsViewModel(ledger, investments, settings).uiState.first {
            !it.isLoading && it.portfolios.size == 1
        }

        assertEquals(0L, state.totalCostMinor)
        assertEquals(0L, state.totalValueMinor)
        assertEquals(0L, state.totalUnrealizedMinor)
        assertNull(state.totalUnrealizedPct)
        assertEquals(0L, state.totalRealizedMinor)
        assertNull(state.totalRealizedPct)
        assertEquals(0L, state.totalDividendsNetMinor)
        assertEquals(0L, state.totalFeesMinor)
    }

    private fun op(id: String, type: OperationType, quantity: String, price: String, fees: Long) = InvestmentOperation(
        id, "p1", "a1", type, LocalDate.of(2026, 1, if (type == OperationType.COMPRA) 1 else 2),
        BigDecimal(quantity), BigDecimal(price), fees, "EUR", "", 1,
    )
}
