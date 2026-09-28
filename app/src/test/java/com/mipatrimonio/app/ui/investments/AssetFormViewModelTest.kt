package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.quotes.CoinGeckoSearchService
import com.mipatrimonio.app.data.quotes.HttpClient
import com.mipatrimonio.app.data.quotes.HttpResponse
import com.mipatrimonio.app.data.quotes.InMemorySecretStore
import com.mipatrimonio.app.data.quotes.OpenFigiService
import com.mipatrimonio.app.data.quotes.SecretStore
import com.mipatrimonio.app.data.quotes.TwelveDataAssetService
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.QuoteProvider
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
class AssetFormViewModelTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var investments: InvestmentRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        investments = InvestmentRepository(db) { 100L }
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `isin invalido no llama a red`() = runTest {
        var calls = 0
        val viewModel = form(http(getHandler = { _, _ -> calls++; HttpResponse(500, "") }, postHandler = { _, _, _ -> calls++; HttpResponse(500, "") }))
        viewModel.initialize(null)
        viewModel.setIsin("US0378331004")
        viewModel.searchIsin()
        assertEquals(AssetFormNotice.ISIN_INVALIDO, viewModel.state.first { it.notice != null }.notice)
        assertEquals(0, calls)
    }

    @Test fun `buscar elegir editar y guardar usa divisa real y primer precio`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SecretStore.TWELVE_DATA_KEY, "twelve-secret") }
        var capturedHeaders = emptyMap<String, String>()
        val client = http(
            getHandler = { url, headers ->
                assertTrue(url.contains("symbol=APC"))
                capturedHeaders = headers
                HttpResponse(200, """{"currency":"USD","close":"123.45","timestamp":10,"is_market_open":false}""")
            },
            postHandler = { _, headers, _ ->
                assertFalse(headers.values.any { it.contains("twelve-secret") })
                HttpResponse(200, """[{"data":[
                  {"name":"Apple US","ticker":"AAPL","exchCode":"UW","securityType":"Common Stock"},
                  {"name":"Apple Europe","ticker":"APC","exchCode":"GY","securityType":"Common Stock"}
                ]}]""")
            },
        )
        val viewModel = form(client, secrets)
        viewModel.initialize(null)
        viewModel.setIsin("us037 8331005")
        viewModel.searchIsin()
        val choices = viewModel.state.first { it.listings.size == 2 }
        viewModel.selectListing(choices.listings.first())
        val filled = viewModel.state.first { !it.loading && it.providerQuote != null }
        assertEquals("Apple Europe", filled.name)
        assertEquals("APC", filled.ticker)
        assertEquals("XETRA", filled.market)
        assertEquals(AssetType.ACCION, filled.type)
        assertEquals(QuoteProvider.TWELVE_DATA, filled.quoteProvider)
        assertEquals("XETR", filled.quoteMic)
        assertEquals("USD", filled.currency)
        assertEquals("apikey twelve-secret", capturedHeaders["Authorization"])

        viewModel.setName("Apple editada")
        viewModel.setTicker("APCE")
        viewModel.setSaveProviderPrice(true)
        val saved = CompletableDeferred<Unit>()
        viewModel.save(null) { saved.complete(Unit) }
        withContext(Dispatchers.Default) { withTimeout(10_000) { saved.await() } }
        val asset = investments.assets.first().single()
        assertEquals("Apple editada", asset.name)
        assertEquals("APCE", asset.ticker)
        assertEquals("123.45", investments.latestPrices.first().getValue(asset.id).price.toPlainString())
        assertFalse(viewModel.state.first { it.name == "Apple editada" }.toString().contains("twelve-secret"))
    }

    @Test fun `sin clave twelve data conserva sugerencia y pide confirmacion`() = runTest {
        val client = http(
            getHandler = { _, _ -> error("Twelve Data no debe llamarse sin clave") },
            postHandler = { _, _, _ -> HttpResponse(200, """[{"data":[{"name":"Apple","ticker":"AAPL","exchCode":"UW","securityType":"Common Stock"}]}]""") },
        )
        val viewModel = form(client)
        viewModel.initialize(null)
        viewModel.setIsin("US0378331005")
        viewModel.searchIsin()
        val state = viewModel.state.first { it.notice == AssetFormNotice.CONFIRMAR_DIVISA }
        assertEquals("USD", state.currency)
        assertEquals("XNAS", state.quoteMic)
    }

    @Test fun `busqueda cripto aplica id y admite rango nulo`() = runTest {
        val client = http(
            getHandler = { _, _ -> HttpResponse(200, """{"coins":[{"id":"bitcoin","name":"Bitcoin","symbol":"btc","market_cap_rank":null}]}""") },
            postHandler = { _, _, _ -> error("no post") },
        )
        val viewModel = form(client)
        viewModel.initialize(null)
        viewModel.setType(AssetType.CRIPTO)
        viewModel.setCryptoQuery("btc")
        viewModel.searchCrypto()
        val result = viewModel.state.first { it.cryptoResults.isNotEmpty() }.cryptoResults.single()
        viewModel.selectCrypto(result)
        val state = viewModel.state.first { it.quoteSymbol == "bitcoin" }
        assertEquals("Bitcoin", state.name)
        assertEquals("BTC", state.ticker)
        assertEquals("bitcoin", state.quoteSymbol)
        assertEquals("CoinGecko", state.market)
    }

    @Test fun `sin conexion conserva campos manuales y no filtra secretos`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SecretStore.OPEN_FIGI_KEY, "figi-secret") }
        val viewModel = form(http({ _, _ -> throw IOException() }, { _, _, _ -> throw IOException() }), secrets)
        viewModel.initialize(null)
        viewModel.setName("Manual")
        viewModel.setIsin("US0378331005")
        viewModel.searchIsin()
        val state = viewModel.state.first { it.failure != null }
        assertEquals("Manual", state.name)
        assertFalse(state.toString().contains("figi-secret"))
    }

    private fun form(client: HttpClient, secrets: InMemorySecretStore = InMemorySecretStore()) = AssetFormViewModel(
        investments, settingsRule.repository, OpenFigiService(client, secrets),
        CoinGeckoSearchService(client, secrets), TwelveDataAssetService(client, secrets),
    )

    private fun http(
        getHandler: suspend (String, Map<String, String>) -> HttpResponse,
        postHandler: suspend (String, Map<String, String>, String) -> HttpResponse,
    ) = object : HttpClient {
        override suspend fun get(url: String, headers: Map<String, String>) = getHandler(url, headers)
        override suspend fun post(url: String, headers: Map<String, String>, body: String) = postHandler(url, headers, body)
    }
}
