package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssetsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var investments: InvestmentRepository
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        investments = InvestmentRepository(db) { 10L }
        settings = SettingsRepository(context)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `deriva posicion y valor y aplica busqueda y filtros`() = runTest {
        seedOperation()
        investments.setManualPrice("as1", BigDecimal("12.50"), "EUR")
        investments.saveAsset(Asset("as2", "Bitcoin", "BTC", "", AssetType.CRIPTO, "", "EUR"))
        val state = AssetsViewModel(investments, settings).uiState.first { !it.isLoading && it.assets.size == 2 }

        val etf = state.assets.first { it.asset.id == "as1" }
        assertEquals(0, BigDecimal("2").compareTo(etf.quantity))
        assertEquals(2_500L, etf.valueMinor)
        assertEquals(listOf(etf), filterAssets(state.assets, "XETRA", AssetFilter.CON_POSICION))
        assertEquals("as2", filterAssets(state.assets, "bitcoin", AssetFilter.SIN_POSICION).single().asset.id)
        assertTrue(filterAssets(state.assets, "", AssetFilter.ARCHIVADOS).isEmpty())
    }

    @Test fun `activo con operaciones bloquea divisa y eliminacion`() = runTest {
        seedOperation()
        val viewModel = AssetsViewModel(investments, settings)
        viewModel.inspect("as1")
        val deps = viewModel.uiState.first { it.dependencies["as1"] != null }.dependencies.getValue("as1")
        assertEquals(1, deps.operations)
        assertFalse(deps.canDelete)

        val currencyFailure = runCatching { investments.saveAsset(asset().copy(currency = "USD")) }.exceptionOrNull()
        val deleteFailure = runCatching { investments.deleteAsset("as1") }.exceptionOrNull()
        assertTrue(currencyFailure is IllegalArgumentException)
        assertTrue(deleteFailure is IllegalArgumentException)
    }

    @Test fun `archiva y reactiva conservando posicion e historial`() = runTest {
        seedOperation()
        val viewModel = AssetsViewModel(investments, settings)
        val asset = viewModel.uiState.first { it.assets.isNotEmpty() }.assets.single().asset

        viewModel.setArchived(asset, true) {}
        val archived = viewModel.uiState.first { it.assets.single().asset.archived }
        assertEquals("as1", filterAssets(archived.assets, "", AssetFilter.ARCHIVADOS).single().asset.id)
        assertTrue(filterAssets(archived.assets, "", AssetFilter.CON_POSICION).isEmpty())
        assertEquals(1, investments.assetDependencies("as1").operations)

        val rejected = runCatching {
            investments.addOperation(
                InvestmentOperation(
                    "op2", "p1", "as1", OperationType.COMPRA, LocalDate.of(2026, 2, 1),
                    BigDecimal.ONE, BigDecimal.TEN, 0, "EUR", "", 2,
                ),
            )
        }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)

        viewModel.setArchived(archived.assets.single().asset, false) {}
        assertFalse(viewModel.uiState.first { !it.assets.single().asset.archived }.assets.single().asset.archived)
    }

    @Test fun `eliminar activo sin operaciones borra sus precios en cascada`() = runTest {
        investments.saveAsset(asset())
        investments.setManualPrice("as1", BigDecimal.TEN, "EUR")
        assertEquals(1, investments.assetDependencies("as1").manualPrices)

        investments.deleteAsset("as1")

        assertTrue(investments.assets.first().isEmpty())
        assertTrue(investments.latestPrices.first().isEmpty())
    }

    private suspend fun seedOperation() {
        investments.savePortfolio(Portfolio("p1", "Cartera", 1))
        investments.saveAsset(asset())
        investments.addOperation(
            InvestmentOperation(
                "op1", "p1", "as1", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
                BigDecimal("2"), BigDecimal.TEN, 0, "EUR", "", 1,
            ),
        )
    }

    private fun asset() = Asset("as1", "ETF Mundo", "IWDA", "", AssetType.ETF, "XETRA", "EUR")
}
