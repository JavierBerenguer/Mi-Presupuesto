package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.PriceQuality
import java.math.BigDecimal
import java.io.IOException
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

enum class LookupFailure { SIN_CONEXION, NO_ENCONTRADO, LIMITE_ALCANZADO, RESPUESTA_INVALIDA, CLAVE_INVALIDA }

sealed interface LookupResult<out T> {
    data class Success<T>(val value: T) : LookupResult<T>
    data class Failure(val reason: LookupFailure) : LookupResult<Nothing>
}

data class MarketInfo(val name: String, val mic: String?, val currency: String?, val european: Boolean)

object AssetMarkets {
    val byExchangeCode = linkedMapOf(
        "GY" to MarketInfo("XETRA", "XETR", Currencies.EUR, true),
        "GR" to MarketInfo("Fráncfort", "XFRA", Currencies.EUR, true),
        "SM" to MarketInfo("Bolsa de Madrid", "XMAD", Currencies.EUR, true),
        "FP" to MarketInfo("Euronext París", "XPAR", Currencies.EUR, true),
        "NA" to MarketInfo("Euronext Ámsterdam", "XAMS", Currencies.EUR, true),
        "BB" to MarketInfo("Euronext Bruselas", "XBRU", Currencies.EUR, true),
        "IM" to MarketInfo("Borsa Italiana", "XMIL", Currencies.EUR, true),
        "PL" to MarketInfo("Euronext Lisboa", "XLIS", Currencies.EUR, true),
        "ID" to MarketInfo("Euronext Dublín", "XDUB", Currencies.EUR, true),
        "LN" to MarketInfo("Londres", "XLON", "GBP", true),
        "SW" to MarketInfo("SIX Suiza", "XSWX", "CHF", true),
        "US" to MarketInfo("EE. UU.", null, "USD", false),
        "UN" to MarketInfo("NYSE", "XNYS", "USD", false),
        "UW" to MarketInfo("NASDAQ", "XNAS", "USD", false),
        "UQ" to MarketInfo("NASDAQ", "XNAS", "USD", false),
    )

    fun resolve(code: String): MarketInfo = byExchangeCode[code.uppercase()]
        ?: MarketInfo(code, null, null, false)
}

data class OpenFigiListing(
    val name: String,
    val ticker: String,
    val exchangeCode: String,
    val securityType: String,
    val securityType2: String,
    val marketSector: String,
) {
    val marketInfo get() = AssetMarkets.resolve(exchangeCode)
}

data class CryptoSearchItem(val id: String, val name: String, val symbol: String, val marketCapRank: Int?)
data class DiscoveredQuote(
    val currency: String,
    val price: BigDecimal,
    val asOfEpochMillis: Long,
    val quality: PriceQuality,
)

fun normalizeIsin(value: String): String = value.filterNot(Char::isWhitespace).uppercase()

fun isValidIsin(value: String): Boolean {
    val isin = normalizeIsin(value)
    if (!isin.matches(Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]"))) return false
    val digits = buildString {
        isin.forEach { character ->
            if (character.isDigit()) append(character) else append(character.code - 'A'.code + 10)
        }
    }
    var sum = 0
    var doubleDigit = false
    for (index in digits.indices.reversed()) {
        var digit = digits[index].digitToInt()
        if (doubleDigit) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        doubleDigit = !doubleDigit
    }
    return sum % 10 == 0
}

fun assetTypeFor(listing: OpenFigiListing): AssetType? {
    val values = listOf(listing.securityType, listing.securityType2, listing.marketSector)
        .joinToString(" ").lowercase()
    return when {
        "etf" in values || "etp" in values -> AssetType.ETF
        listOf("open-end fund", "mutual fund", "fund of funds").any(values::contains) -> AssetType.FONDO_INVERSION
        listOf("common stock", "depositary receipt", "reit").any(values::contains) -> AssetType.ACCION
        else -> null
    }
}

fun sortOpenFigiListings(items: List<OpenFigiListing>): List<OpenFigiListing> = items.sortedWith(
    compareBy<OpenFigiListing> { if (it.marketInfo.european) 0 else if (it.exchangeCode.uppercase() in AssetMarkets.byExchangeCode) 1 else 2 }
        .thenBy { it.marketInfo.name }
        .thenBy { it.ticker },
)

class OpenFigiService(private val http: HttpClient, private val secrets: SecretStore) {
    suspend fun search(isin: String): LookupResult<List<OpenFigiListing>> {
        val normalized = normalizeIsin(isin)
        val headers = buildMap {
            put("Content-Type", "application/json")
            secrets.get(SecretStore.OPEN_FIGI_KEY)?.takeIf(String::isNotBlank)?.let { put("X-OPENFIGI-APIKEY", it) }
        }
        return try {
            parse(http.post("https://api.openfigi.com/v3/mapping", headers, JSONArray().put(JSONObject().put("idType", "ID_ISIN").put("idValue", normalized)).toString()))
        } catch (_: IOException) {
            LookupResult.Failure(LookupFailure.SIN_CONEXION)
        } catch (_: UnsupportedOperationException) {
            LookupResult.Failure(LookupFailure.SIN_CONEXION)
        }
    }

    internal fun parse(response: HttpResponse): LookupResult<List<OpenFigiListing>> {
        if (response.status == 401 || response.status == 403) return LookupResult.Failure(LookupFailure.CLAVE_INVALIDA)
        if (response.status == 429) return LookupResult.Failure(LookupFailure.LIMITE_ALCANZADO)
        if (response.status !in 200..299) return LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
        return try {
            val job = JSONArray(response.body).getJSONObject(0)
            if (job.has("warning")) return LookupResult.Failure(LookupFailure.NO_ENCONTRADO)
            if (job.has("error")) return LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
            val data = job.getJSONArray("data")
            val listings = (0 until data.length()).map { index ->
                val item = data.getJSONObject(index)
                OpenFigiListing(
                    item.optString("name"), item.optString("ticker"), item.optString("exchCode"),
                    item.optString("securityType"), item.optString("securityType2"), item.optString("marketSector"),
                )
            }.filter { it.name.isNotBlank() && it.ticker.isNotBlank() }
            if (listings.isEmpty()) LookupResult.Failure(LookupFailure.NO_ENCONTRADO)
            else LookupResult.Success(sortOpenFigiListings(listings))
        } catch (_: Exception) {
            LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
        }
    }
}

class CoinGeckoSearchService(private val http: HttpClient, private val secrets: SecretStore) {
    suspend fun search(query: String): LookupResult<List<CryptoSearchItem>> = try {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val headers = secrets.get(SecretStore.COINGECKO_KEY)?.takeIf(String::isNotBlank)
            ?.let { mapOf("x-cg-demo-api-key" to it) }.orEmpty()
        parse(http.get("https://api.coingecko.com/api/v3/search?query=$encoded", headers))
    } catch (_: IOException) {
        LookupResult.Failure(LookupFailure.SIN_CONEXION)
    }

    internal fun parse(response: HttpResponse): LookupResult<List<CryptoSearchItem>> {
        if (response.status == 401 || response.status == 403) return LookupResult.Failure(LookupFailure.CLAVE_INVALIDA)
        if (response.status == 429) return LookupResult.Failure(LookupFailure.LIMITE_ALCANZADO)
        if (response.status !in 200..299) return LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
        return try {
            val coins = JSONObject(response.body).getJSONArray("coins")
            LookupResult.Success((0 until minOf(10, coins.length())).map { index ->
                val coin = coins.getJSONObject(index)
                CryptoSearchItem(
                    coin.getString("id"), coin.getString("name"), coin.getString("symbol").uppercase(),
                    if (coin.isNull("market_cap_rank")) null else coin.getInt("market_cap_rank"),
                )
            })
        } catch (_: Exception) {
            LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
        }
    }
}

class TwelveDataAssetService(private val http: HttpClient, private val secrets: SecretStore) {
    suspend fun discover(symbol: String, mic: String?): LookupResult<DiscoveredQuote> {
        val key = secrets.get(SecretStore.TWELVE_DATA_KEY)?.takeIf(String::isNotBlank)
            ?: return LookupResult.Failure(LookupFailure.CLAVE_INVALIDA)
        val query = buildList {
            add("symbol=${symbol.encoded()}")
            mic?.takeIf(String::isNotBlank)?.let { add("mic_code=${it.encoded()}") }
        }.joinToString("&")
        return try {
            parse(http.get("https://api.twelvedata.com/quote?$query", mapOf("Authorization" to "apikey $key")))
        } catch (_: IOException) {
            LookupResult.Failure(LookupFailure.SIN_CONEXION)
        }
    }

    internal fun parse(response: HttpResponse): LookupResult<DiscoveredQuote> {
        if (response.status == 401 || response.status == 403) return LookupResult.Failure(LookupFailure.CLAVE_INVALIDA)
        if (response.status == 429) return LookupResult.Failure(LookupFailure.LIMITE_ALCANZADO)
        if (response.status !in 200..299) return LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
        return try {
            val json = JSONObject(response.body)
            if (json.optString("status") == "error") {
                return LookupResult.Failure(if (json.optInt("code") == 429) LookupFailure.LIMITE_ALCANZADO else LookupFailure.NO_ENCONTRADO)
            }
            val price = BigDecimal(json.getString("close"))
            val timestamp = json.getLong("timestamp")
            val currency = json.getString("currency").uppercase()
            if (price.signum() <= 0 || timestamp <= 0 || currency.isBlank()) throw IllegalArgumentException()
            LookupResult.Success(
                DiscoveredQuote(
                    currency, price, Math.multiplyExact(timestamp, 1_000L),
                    if (json.optBoolean("is_market_open", true)) PriceQuality.RETRASADO else PriceQuality.CIERRE,
                ),
            )
        } catch (_: Exception) {
            LookupResult.Failure(LookupFailure.RESPUESTA_INVALIDA)
        }
    }

    private fun String.encoded() = URLEncoder.encode(this, Charsets.UTF_8.name())
}
