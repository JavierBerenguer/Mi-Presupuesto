package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.PriceQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuoteProvidersTest {
    private val request = QuoteRequest("a", "AAPL", "USD", "XNAS")

    @Test fun `twelve data parsea precio exacto calidad y fecha`() = runTest {
        val provider = TwelveDataQuoteProvider(HttpClient { _, _ -> error("no network") }, InMemorySecretStore())
        val result = provider.parse(
            request,
            HttpResponse(200, """{"symbol":"AAPL","currency":"USD","close":"123.4500","timestamp":1700000000,"is_market_open":false}"""),
        ) as QuoteResult.Success
        assertEquals("123.4500", result.price.toPlainString())
        assertEquals(1_700_000_000_000L, result.asOfEpochMillis)
        assertEquals(PriceQuality.CIERRE, result.quality)
    }

    @Test fun `twelve data clasifica errores y json corrupto`() = runTest {
        val provider = TwelveDataQuoteProvider(HttpClient { _, _ -> error("no network") }, InMemorySecretStore())
        assertFailure(provider.parse(request, HttpResponse(401, "")), QuoteFailure.CLAVE_INVALIDA)
        assertFailure(provider.parse(request, HttpResponse(200, """{"code":429,"status":"error"}""")), QuoteFailure.LIMITE_ALCANZADO)
        assertFailure(provider.parse(request, HttpResponse(200, "no-json")), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(request, HttpResponse(200, """{"currency":"USD","close":"0","timestamp":1}""")), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(request, HttpResponse(200, """{"currency":"USD","close":"-1","timestamp":1}""")), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(request, HttpResponse(200, """{"currency":"USD","close":"no-numero","timestamp":1}""")), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(request, HttpResponse(200, """{"currency":"EUR","close":"1","timestamp":1}""")), QuoteFailure.DIVISA_DISTINTA)
    }

    @Test fun `twelve data sin clave no llama ni expone secretos`() = runTest {
        var called = false
        val provider = TwelveDataQuoteProvider(HttpClient { _, _ -> called = true; HttpResponse(200, "") }, InMemorySecretStore())
        val result = provider.fetch(listOf(request)).single()
        assertFailure(result, QuoteFailure.SIN_CLAVE)
        assertTrue(!called)
        assertTrue(result.toString().contains("secret-value").not())
    }

    @Test fun `twelve data envia clave solo en cabecera`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SecretStore.TWELVE_DATA_KEY, "secret-value") }
        var capturedUrl = ""
        var capturedHeader = ""
        val provider = TwelveDataQuoteProvider(HttpClient { url, headers ->
            capturedUrl = url
            capturedHeader = headers.getValue("Authorization")
            HttpResponse(200, """{"currency":"USD","close":"1","timestamp":1,"is_market_open":true}""")
        }, secrets)
        provider.fetch(listOf(request))
        assertFalse(capturedUrl.contains("secret-value"))
        assertEquals("apikey secret-value", capturedHeader)
    }

    @Test fun `twelve data no llama a los restantes al alcanzar limite`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SecretStore.TWELVE_DATA_KEY, "key") }
        var calls = 0
        val provider = TwelveDataQuoteProvider(HttpClient { _, _ ->
            calls++
            HttpResponse(429, "{}")
        }, secrets)
        val results = provider.fetch(listOf(request, request.copy(assetId = "b")))
        assertEquals(1, calls)
        assertTrue(results.all { (it as QuoteResult.Failure).reason == QuoteFailure.LIMITE_ALCANZADO })
    }

    @Test fun `coingecko agrupa ids y tolera uno ausente`() = runTest {
        val provider = CoinGeckoQuoteProvider(HttpClient { _, _ -> error("no network") }, InMemorySecretStore())
        val requests = listOf(
            QuoteRequest("b", "bitcoin", "EUR"), QuoteRequest("e", "ethereum", "EUR"),
        )
        val results = provider.parse(
            requests,
            HttpResponse(200, """{"bitcoin":{"eur":76975.1200,"last_updated_at":1779092258}}"""),
        )
        assertEquals("76975.12", (results[0] as QuoteResult.Success).price.stripTrailingZeros().toPlainString())
        assertFailure(results[1], QuoteFailure.NO_ENCONTRADO)
    }

    @Test fun `coingecko rechaza precio invalido y respuesta corrupta`() = runTest {
        val provider = CoinGeckoQuoteProvider(HttpClient { _, _ -> error("no network") }, InMemorySecretStore())
        assertFailure(provider.parse(listOf(request.copy(symbol = "coin", currency = "EUR")), HttpResponse(200, """{"coin":{"eur":0,"last_updated_at":1}}""")).single(), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(listOf(request), HttpResponse(200, "{" )).single(), QuoteFailure.RESPUESTA_INVALIDA)
    }

    @Test fun `coingecko consulta varios ids en una sola llamada sin clave`() = runTest {
        var calls = 0
        var headers = emptyMap<String, String>()
        val provider = CoinGeckoQuoteProvider(HttpClient { url, sentHeaders ->
            calls++
            headers = sentHeaders
            assertTrue(url.contains("ids=bitcoin%2Cethereum"))
            HttpResponse(200, """{"bitcoin":{"eur":1,"last_updated_at":1},"ethereum":{"eur":2,"last_updated_at":1}}""")
        }, InMemorySecretStore())
        provider.fetch(listOf(QuoteRequest("b", "bitcoin", "EUR"), QuoteRequest("e", "ethereum", "EUR")))
        assertEquals(1, calls)
        assertTrue(headers.isEmpty())
    }

    private fun assertFailure(result: QuoteResult, reason: QuoteFailure) =
        assertEquals(reason, (result as QuoteResult.Failure).reason)
}
