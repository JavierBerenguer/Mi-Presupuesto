package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.*
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
class AssetDetailViewModelTest {
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

        val state = AssetDetailViewModel("portfolio", "asset", investments, ledger, settings).uiState.first { !it.isLoading }

        assertNull(state.latestPrice)
        assertNull(state.row?.valueMinor)
    }
}
