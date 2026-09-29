package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.quotes.CoinGeckoSearchService
import com.mipatrimonio.app.data.quotes.EodhdSearchService
import com.mipatrimonio.app.data.quotes.HttpClient
import com.mipatrimonio.app.data.quotes.HttpResponse
import com.mipatrimonio.app.data.quotes.InMemorySecretStore
import com.mipatrimonio.app.data.quotes.SecretStore
import com.mipatrimonio.app.testutil.SettingsStoreRule
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.QuoteProvider
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
class AssetsViewModelTest {
    @get:Rule
    val settingsRule = SettingsStoreRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var investments: InvestmentRepository
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        investments = InvestmentRepository(db) { 10L }
        settings = settingsRule.repository
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

    @Test fun `bloquea mismo isin y mic pero permite otro mercado`() = runTest {
        investments.saveAsset(asset().copy(id = "a", isin = "ie00 b4l5 y983", quoteMic = "XETR"))
        val duplicate = runCatching {
            investments.saveAsset(asset().copy(id = "b", isin = "IE00B4L5Y983", quoteMic = "xetr"))
        }.exceptionOrNull()
        assertTrue(duplicate is IllegalArgumentException)

        investments.saveAsset(asset().copy(id = "c", isin = "IE00B4L5Y983", quoteMic = "XAMS"))
        assertEquals(2, investments.assets.first().size)

        investments.saveAsset(asset().copy(id = "d", isin = "US0378331005", quoteMic = null))
        val duplicateWithoutMic = runCatching {
            investments.saveAsset(asset().copy(id = "e", isin = "US0378331005", quoteMic = null))
        }.exceptionOrNull()
        assertTrue(duplicateWithoutMic is IllegalArgumentException)
    }

    @Test fun `propone por isin y nombre y solo guarda activos confirmados`() = runTest {
        investments.saveAsset(asset().copy(isin = "IE00B4L5Y983"))
        investments.saveAsset(Asset("btc", "Bitcoin", "BTC", "", AssetType.CRIPTO, "", "EUR"))
        val secrets = InMemorySecretStore().apply { put(SecretStore.EODHD_KEY, "secret") }
        val http = HttpClient { url, _ ->
            when {
                "/api/search/" in url -> HttpResponse(200, """[
                    {"Code":"IWDA","Exchange":"LSE","Name":"ETF GBP","Type":"ETF","Country":"UK","Currency":"GBP","ISIN":"IE00B4L5Y983","isPrimary":true},
                    {"Code":"EUNL","Exchange":"XETRA","Name":"ETF EUR","Type":"ETF","Country":"Germany","Currency":"EUR","ISIN":"IE00B4L5Y983","isPrimary":false}
                ]""")
                "query=Bitcoin" in url -> HttpResponse(200, """{"coins":[
                    {"id":"wrapped-bitcoin","name":"Wrapped Bitcoin","symbol":"wbtc","market_cap_rank":15},
                    {"id":"bitcoin","name":"Bitcoin","symbol":"btc","market_cap_rank":1}
                ]}""")
                else -> error("URL inesperada")
            }
        }
        val viewModel = AssetsViewModel(
            investments, settings, EodhdSearchService(http, secrets),
            CoinGeckoSearchService(http, secrets), secrets,
        )
        viewModel.openQuoteConfiguration()
        assertEquals(1, viewModel.uiState.first { it.quoteConfiguration.visible }.quoteConfiguration.eodhdCalls)
        viewModel.findQuoteConfigurations()
        val proposals = viewModel.uiState.first { it.quoteConfiguration.proposals.size == 2 }.quoteConfiguration.proposals
        assertEquals("EUNL.XETRA", proposals.first { it.asset.id == "as1" }.symbol)
        assertEquals("bitcoin", proposals.first { it.asset.id == "btc" }.symbol)
        viewModel.setProposalAccepted("btc", false)
        viewModel.saveQuoteConfigurations {}
        val saved = investments.assets.first { assets -> assets.any { it.id == "as1" && it.quoteProvider != null } }
        assertEquals(QuoteProvider.EODHD, saved.first { it.id == "as1" }.quoteProvider)
        assertEquals(null, saved.first { it.id == "btc" }.quoteProvider)
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
