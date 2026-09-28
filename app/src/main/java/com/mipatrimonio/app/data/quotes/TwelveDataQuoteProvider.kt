package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.PriceQuality
import com.mipatrimonio.app.domain.model.QuoteProvider
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import org.json.JSONObject

class TwelveDataQuoteProvider(
    private val http: HttpClient,
    private val secrets: SecretStore,
    private val limiter: SlidingWindowRateLimiter = SlidingWindowRateLimiter(),
) : QuoteService {
    override val kind = QuoteProvider.TWELVE_DATA

    override suspend fun fetch(requests: List<QuoteRequest>): List<QuoteResult> {
        val key = secrets.get(SecretStore.TWELVE_DATA_KEY)?.takeIf(String::isNotBlank)
            ?: return requests.map { QuoteResult.Failure(it, QuoteFailure.SIN_CLAVE) }
        var limitReached = false
        return requests.map { request ->
            if (limitReached) return@map QuoteResult.Failure(request, QuoteFailure.LIMITE_ALCANZADO)
            limiter.acquire()
            val query = buildList {
                add("symbol=${request.symbol.encoded()}")
                request.mic?.takeIf(String::isNotBlank)?.let { add("mic_code=${it.encoded()}") }
            }.joinToString("&")
            try {
                val response = http.get("https://api.twelvedata.com/quote?$query", mapOf("Authorization" to "apikey $key"))
                parse(request, response).also {
                    if (it is QuoteResult.Failure && it.reason == QuoteFailure.LIMITE_ALCANZADO) limitReached = true
                }
            } catch (_: IOException) {
                QuoteResult.Failure(request, QuoteFailure.SIN_CONEXION)
            }
        }
    }

    internal fun parse(request: QuoteRequest, response: HttpResponse): QuoteResult {
        if (response.status == 401) return QuoteResult.Failure(request, QuoteFailure.CLAVE_INVALIDA)
        if (response.status == 429) return QuoteResult.Failure(request, QuoteFailure.LIMITE_ALCANZADO)
        return try {
            val json = JSONObject(response.body)
            if (json.optString("status") == "error") {
                return QuoteResult.Failure(request, when (json.optInt("code")) {
                    401 -> QuoteFailure.CLAVE_INVALIDA
                    429 -> QuoteFailure.LIMITE_ALCANZADO
                    else -> QuoteFailure.NO_ENCONTRADO
                })
            }
            val price = BigDecimal(json.getString("close"))
            val currency = json.getString("currency").uppercase()
            val timestamp = json.getLong("timestamp")
            if (price.signum() <= 0 || timestamp <= 0) throw IllegalArgumentException()
            if (currency != request.currency.uppercase()) {
                QuoteResult.Failure(request, QuoteFailure.DIVISA_DISTINTA)
            } else QuoteResult.Success(
                request, price, currency, Math.multiplyExact(timestamp, 1_000L),
                if (json.optBoolean("is_market_open", true)) PriceQuality.RETRASADO else PriceQuality.CIERRE,
            )
        } catch (_: Exception) {
            QuoteResult.Failure(request, QuoteFailure.RESPUESTA_INVALIDA)
        }
    }

    private fun String.encoded() = URLEncoder.encode(this, Charsets.UTF_8.name())
}
