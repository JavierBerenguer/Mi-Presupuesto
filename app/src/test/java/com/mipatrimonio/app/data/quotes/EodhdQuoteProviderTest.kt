package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.PriceQuality
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EodhdQuoteProviderTest {
    private val now = 1_800_000_000_000L
    private val request = QuoteRequest("a", "EUNL.XETRA", "EUR")
    private val noNetwork = HttpClient { _, _ -> error("sin red") }

    @Test fun `parsea uno y varios manteniendo decimal exacto y ausentes`() {
        val provider = EodhdQuoteProvider(noNetwork, InMemorySecretStore()) { now }
        val recent = now / 1_000L - 60
        val one = provider.parse(listOf(request), HttpResponse(200, """{"code":"EUNL.XETRA","timestamp":$recent,"close":"123.4500"}"""))
        val success = one.single() as QuoteResult.Success
        assertEquals("123.4500", success.price.toPlainString())
        assertEquals(PriceQuality.RETRASADO, success.quality)
        val multiple = provider.parse(
            listOf(request, request.copy(assetId = "b", symbol = "AAPL.US"), request.copy(assetId = "c", symbol = "MISS.US")),
            HttpResponse(200, """[
                {"code":"AAPL.US","timestamp":1,"close":"10.01"},
                {"code":"EUNL.XETRA","timestamp":1,"close":"20.02"}
            ]"""),
        )
        assertEquals("20.02", (multiple[0] as QuoteResult.Success).price.toPlainString())
        assertEquals("10.01", (multiple[1] as QuoteResult.Success).price.toPlainString())
        assertFailure(multiple[2], QuoteFailure.NO_ENCONTRADO)
        assertEquals(PriceQuality.CIERRE, (multiple[0] as QuoteResult.Success).quality)
    }

    @Test fun `clasifica estados json invalido y precio no positivo`() {
        val provider = EodhdQuoteProvider(noNetwork, InMemorySecretStore()) { now }
        listOf(401, 403).forEach { assertFailure(provider.parse(listOf(request), HttpResponse(it, "")).single(), QuoteFailure.CLAVE_INVALIDA) }
        listOf(402, 429).forEach { assertFailure(provider.parse(listOf(request), HttpResponse(it, "")).single(), QuoteFailure.LIMITE_ALCANZADO) }
        assertFailure(provider.parse(listOf(request), HttpResponse(500, "key-in-body")).single(), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(listOf(request), HttpResponse(200, "no-json")).single(), QuoteFailure.RESPUESTA_INVALIDA)
        assertFailure(provider.parse(listOf(request), HttpResponse(200, """{"code":"EUNL.XETRA","timestamp":1,"close":"0"}""")).single(), QuoteFailure.RESPUESTA_INVALIDA)
    }

    @Test fun `agrupa de quince y deja de llamar al alcanzar limite`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SecretStore.EODHD_KEY, "secret-key") }
        var calls = 0
        val sizes = mutableListOf<Int>()
        val http = HttpClient { url, _ ->
            calls++
            val primary = Regex("real-time/([^?]+)").find(url)!!.groupValues[1]
            val others = Regex("[?&]s=([^&]*)").find(url)?.groupValues?.get(1)?.split("%2C").orEmpty()
            sizes += 1 + others.size
            if (calls == 2) HttpResponse(429, "") else {
                val codes = listOf(primary) + others
                HttpResponse(200, codes.joinToString(prefix = "[", postfix = "]") { """{"code":"$it","timestamp":1,"close":"1"}""" })
            }
        }
        val requests = (1..40).map { QuoteRequest("a$it", "S$it.US", "USD") }
        val results = EodhdQuoteProvider(http, secrets) { now }.fetch(requests)
        assertEquals(2, calls)
        assertEquals(listOf(15, 15), sizes)
        assertTrue(results.drop(15).all { it is QuoteResult.Failure && it.reason == QuoteFailure.LIMITE_ALCANZADO })
    }

    @Test fun `ningun error expone la clave`() = runTest {
        val key = "super-secret-eodhd"
        val secrets = InMemorySecretStore().apply { put(SecretStore.EODHD_KEY, key) }
        val responses = listOf(401, 403, 402, 429, 500)
        responses.forEach { status ->
            val result = EodhdQuoteProvider(HttpClient { _, _ -> HttpResponse(status, key) }, secrets).fetch(listOf(request))
            assertFalse(result.toString().contains(key))
        }
        listOf(
            "not-json-$key",
            """{"code":"EUNL.XETRA","timestamp":1,"close":"0","detail":"$key"}""",
            "[]",
        ).forEach { body ->
            val result = EodhdQuoteProvider(HttpClient { _, _ -> HttpResponse(200, body) }, secrets).fetch(listOf(request))
            assertFalse(result.toString().contains(key))
        }
        val offline = EodhdQuoteProvider(HttpClient { _, _ -> throw IOException("url?api_token=$key") }, secrets)
            .fetch(listOf(request))
        assertFailure(offline.single(), QuoteFailure.SIN_CONEXION)
        assertFalse(offline.toString().contains(key))
        val withoutKey = EodhdQuoteProvider(HttpClient { _, _ -> error("no debe llamar") }, InMemorySecretStore())
            .fetch(listOf(request))
        assertFailure(withoutKey.single(), QuoteFailure.SIN_CLAVE)
        assertFalse(withoutKey.toString().contains(key))
    }

    @Test fun `cliente http sanea una url invalida con secreto`() = runTest {
        val key = "secret-in-invalid-url"
        val error = runCatching {
            UrlConnectionHttpClient().get("not-a-url?api_token=$key", emptyMap())
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertFalse(error?.message.orEmpty().contains(key))
    }

    private fun assertFailure(result: QuoteResult, expected: QuoteFailure) =
        assertEquals(expected, (result as QuoteResult.Failure).reason)
}
