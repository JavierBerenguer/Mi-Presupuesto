package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.*
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
class AssetDetailViewModelTest {
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

    @Test fun `detalle publica posicion precio operaciones y cuenta de origen`() = runTest {
        ledger.saveAccount(Account("account", "Cuenta inversión", AccountType.INVERSION, "EUR", 100_000, false, 1))
        investments.savePortfolio(Portfolio("portfolio", "Principal", 1, "account"))
        investments.saveAsset(Asset("asset", "ETF Mundo", "ETF", "ISIN", AssetType.ETF, "XETRA", "EUR"))
        investments.addOperation(
            InvestmentOperation(
                "operation", "portfolio", "asset", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
                BigDecimal("2"), BigDecimal("100"), 100, "EUR", "", 1, "account",
            ),
        )
        investments.setManualPrice("asset", BigDecimal("110"), "EUR")

        val state = AssetDetailViewModel("portfolio", "asset", investments, ledger, settings).uiState.first {
            !it.isLoading && it.latestPrice != null
        }

        assertEquals("ETF Mundo", state.asset?.name)
        assertEquals("Cuenta inversión", state.operations.single().accountId?.let { state.accountsById[it]?.name })
        assertEquals(0, BigDecimal("2").compareTo(state.row?.valuation?.position?.quantity))
        assertEquals(22_000L, state.row?.valueMinor)
        assertEquals(0, BigDecimal("1").compareTo(state.row?.valuation?.position?.capitalizedFees))
        assertEquals(0, BigDecimal("1").compareTo(state.row?.valuation?.position?.totalFees))
        assertNull(state.row?.valuation?.position?.realizedReturnPct)
    }

    @Test fun `detalle sin cotizacion conserva precio nulo`() = runTest {
        investments.savePortfolio(Portfolio("portfolio", "Principal", 1))
        investments.saveAsset(Asset("asset", "Fondo", "F", "", AssetType.FONDO_INVERSION, "", "EUR"))
        investments.addOperation(
            InvestmentOperation(
                "operation", "portfolio", "asset", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
                BigDecimal.ONE, BigDecimal.TEN, 0, "EUR", "", 1,
            ),
        )
        investments.addOperation(
            InvestmentOperation(
                "sale", "portfolio", "asset", OperationType.VENTA, LocalDate.of(2026, 1, 2),
                BigDecimal.ONE, BigDecimal("12"), 0, "EUR", "", 2,
            ),
        )
        investments.addOperation(
            InvestmentOperation(
                "dividend", "portfolio", "asset", OperationType.DIVIDENDO, LocalDate.of(2026, 1, 3),
                BigDecimal.ONE, BigDecimal.ONE, 50, "EUR", "", 3,
            ),
        )
        investments.addOperation(
            InvestmentOperation(
                "fee", "portfolio", "asset", OperationType.COMISION, LocalDate.of(2026, 1, 4),
                BigDecimal.ONE, BigDecimal("2"), 0, "EUR", "", 4,
            ),
        )

        val state = AssetDetailViewModel("portfolio", "asset", investments, ledger, settings).uiState.first {
            !it.isLoading && it.operations.size == 4
        }

        assertNull(state.latestPrice)
        assertNull(state.row?.valueMinor)
        assertNull(state.row?.valuation?.unrealizedPnl)
        assertEquals(0, BigDecimal("2").compareTo(state.row?.valuation?.position?.realizedPnl))
        assertEquals(0, BigDecimal("20.00").compareTo(state.row?.valuation?.position?.realizedReturnPct))
        assertEquals(0, BigDecimal("0.5").compareTo(state.row?.valuation?.position?.dividendsNet))
        assertEquals(0, BigDecimal("2").compareTo(state.row?.valuation?.position?.totalFees))
    }

    @Test fun `historial usa fecha hora y createdAt en orden cronologico descendente`() = runTest {
        investments.savePortfolio(Portfolio("portfolio", "Principal", 1))
        investments.saveAsset(Asset("asset", "Fondo", "F", "", AssetType.FONDO_INVERSION, "", "EUR"))
        listOf(
            operation("late-created", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), 30),
            operation("later-time", LocalDate.of(2026, 1, 1), LocalTime.of(10, 0), 10),
            operation("early-created", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), 20),
            operation("next-day", LocalDate.of(2026, 1, 2), LocalTime.of(0, 1), 1),
        ).forEach { investments.addOperation(it) }

        val state = AssetDetailViewModel("portfolio", "asset", investments, ledger, settings).uiState.first {
            !it.isLoading && it.operations.size == 4
        }

        assertEquals(listOf("next-day", "later-time", "late-created", "early-created"), state.operations.map { it.id })
    }

    @Test fun `edicion conserva la hora seleccionada`() = runTest {
        val portfolio = Portfolio("portfolio", "Principal", 1)
        val asset = Asset("asset", "Fondo", "F", "", AssetType.FONDO_INVERSION, "", "EUR")
        val existing = operation("operation", LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, 1)
        investments.savePortfolio(portfolio)
        investments.saveAsset(asset)
        investments.addOperation(existing)
        val viewModel = AssetDetailViewModel("portfolio", "asset", investments, ledger, settings)

        viewModel.updateOperation(
            existing, portfolio, asset, OperationType.COMPRA,
            LocalDateTime.of(2026, 1, 1, 16, 45), BigDecimal.ONE, BigDecimal.TEN,
            0, null, "", {},
        )

        val updated = investments.operations.first { operations ->
            operations.singleOrNull()?.time == LocalTime.of(16, 45)
        }.single()
        assertEquals(LocalTime.of(16, 45), updated.time)
    }

    private fun operation(id: String, date: LocalDate, time: LocalTime, createdAt: Long) = InvestmentOperation(
        id, "portfolio", "asset", OperationType.COMPRA, date, BigDecimal.ONE, BigDecimal.TEN,
        0, "EUR", "", createdAt, time = time,
    )
}
