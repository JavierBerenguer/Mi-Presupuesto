package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
class AssetLookupServicesTest {
    private val noNetwork = HttpClient { _, _ -> error("no network") }

    @Test fun `normaliza y valida isin reales y rechaza formato o control incorrecto`() {
        assertEquals("US0378331005", normalizeIsin(" us037 833 1005 "))
        assertTrue(isValidIsin("US0378331005"))
        assertTrue(isValidIsin("ie00 b4l5 y983"))
        assertFalse(isValidIsin("US0378331004"))
        assertFalse(isValidIsin("ES123"))
        assertFalse(isValidIsin("1S0378331005"))
    }

    @Test fun `openfigi parsea varios listados y prioriza mercados europeos`() {
        val service = OpenFigiService(noNetwork, InMemorySecretStore())
        val result = service.parse(HttpResponse(200, """[{"data":[
          {"name":"Apple","ticker":"AAPL","exchCode":"UW","securityType":"Common Stock","securityType2":"Common Stock","marketSector":"Equity"},
          {"name":"Apple","ticker":"APC","exchCode":"GY","securityType":"Common Stock","securityType2":"Common Stock","marketSector":"Equity"}
        ]}]""")) as LookupResult.Success<List<OpenFigiListing>>
        assertEquals(listOf("GY", "UW"), result.value.map { it.exchangeCode })
    }

    @Test fun `openfigi cubre uno warning error corrupta y limite`() {
        val service = OpenFigiService(noNetwork, InMemorySecretStore())
        val one = service.parse(HttpResponse(200, """[{"data":[{"name":"Fondo","ticker":"F1","exchCode":"XX","securityType":"Open-End Fund"}]}]""")) as LookupResult.Success<List<OpenFigiListing>>
        assertEquals(1, one.value.size)
        assertFailure(service.parse(HttpResponse(200, """[{"warning":"No identifier found."}]""")), LookupFailure.NO_ENCONTRADO)
        assertFailure(service.parse(HttpResponse(200, """[{"error":"bad job"}]""")), LookupFailure.RESPUESTA_INVALIDA)
        assertFailure(service.parse(HttpResponse(200, "{")), LookupFailure.RESPUESTA_INVALIDA)
        assertFailure(service.parse(HttpResponse(429, "")), LookupFailure.LIMITE_ALCANZADO)
    }

    @Test fun `mapea tipos y mercados incluidos y desconocidos`() {
        fun listing(type: String) = OpenFigiListing("N", "T", "GY", type, "", "")
        assertEquals(AssetType.ACCION, assetTypeFor(listing("Depositary Receipt")))
        assertEquals(AssetType.ACCION, assetTypeFor(listing("REIT")))
        assertEquals(AssetType.ETF, assetTypeFor(listing("Equity ETF")))
        assertEquals(AssetType.FONDO_INVERSION, assetTypeFor(listing("Mutual Fund")))
        assertNull(assetTypeFor(listing("Corporate Bond")))
        assertEquals(MarketInfo("XETRA", "XETR", "EUR", true), AssetMarkets.resolve("gy"))
        assertEquals(
            mapOf(
                "US" to Triple("EE. UU.", null, "USD"), "UN" to Triple("NYSE", "XNYS", "USD"),
                "UW" to Triple("NASDAQ", "XNAS", "USD"), "UQ" to Triple("NASDAQ", "XNAS", "USD"),
                "GY" to Triple("XETRA", "XETR", "EUR"), "GR" to Triple("Fráncfort", "XFRA", "EUR"),
                "SM" to Triple("Bolsa de Madrid", "XMAD", "EUR"), "FP" to Triple("Euronext París", "XPAR", "EUR"),
                "NA" to Triple("Euronext Ámsterdam", "XAMS", "EUR"), "BB" to Triple("Euronext Bruselas", "XBRU", "EUR"),
                "IM" to Triple("Borsa Italiana", "XMIL", "EUR"), "PL" to Triple("Euronext Lisboa", "XLIS", "EUR"),
                "ID" to Triple("Euronext Dublín", "XDUB", "EUR"), "LN" to Triple("Londres", "XLON", "GBP"),
                "SW" to Triple("SIX Suiza", "XSWX", "CHF"),
            ),
            AssetMarkets.byExchangeCode.mapValues { (_, info) -> Triple(info.name, info.mic, info.currency) },
        )
        assertEquals(MarketInfo("ZZ", null, null, false), AssetMarkets.resolve("ZZ"))
    }

    @Test fun `coingecko parsea rango nulo y limita resultados`() {
        val service = CoinGeckoSearchService(noNetwork, InMemorySecretStore())
        val coins = (1..12).joinToString(",") { index ->
            """{"id":"c$index","name":"Coin $index","symbol":"s$index","market_cap_rank":${if (index == 1) "null" else index}}"""
        }
        val result = service.parse(HttpResponse(200, """{"coins":[$coins]}""")) as LookupResult.Success<List<CryptoSearchItem>>
        assertEquals(10, result.value.size)
        assertNull(result.value.first().marketCapRank)
        assertEquals("S1", result.value.first().symbol)
        assertTrue((service.parse(HttpResponse(200, """{"coins":[]}""")) as LookupResult.Success<List<CryptoSearchItem>>).value.isEmpty())
        assertFailure(service.parse(HttpResponse(200, "not-json")), LookupFailure.RESPUESTA_INVALIDA)
    }

    @Test fun `openfigi envia isin normalizado y clave solo en cabecera`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SecretStore.OPEN_FIGI_KEY, "figi-secret") }
        var capturedUrl = ""
        var capturedBody = ""
        var capturedKey = ""
        val http = object : HttpClient {
            override suspend fun get(url: String, headers: Map<String, String>) = error("no get")
            override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
                capturedUrl = url
                capturedBody = body
                capturedKey = headers.getValue("X-OPENFIGI-APIKEY")
                return HttpResponse(200, """[{"warning":"No identifier found."}]""")
            }
        }
        OpenFigiService(http, secrets).search("us037 8331005")
        assertEquals("figi-secret", capturedKey)
        assertTrue(capturedBody.contains("US0378331005"))
        assertFalse(capturedUrl.contains("figi-secret"))
        assertFalse(capturedBody.contains("figi-secret"))
    }

    private fun assertFailure(result: LookupResult<*>, expected: LookupFailure) =
        assertEquals(expected, (result as LookupResult.Failure).reason)
}
