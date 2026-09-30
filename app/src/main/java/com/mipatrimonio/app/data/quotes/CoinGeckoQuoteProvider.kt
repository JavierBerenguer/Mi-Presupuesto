package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.PriceQuality
import com.mipatrimonio.app.domain.model.QuoteProvider
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import org.json.JSONObject

class CoinGeckoQuoteProvider(
    private val http: HttpClient,
    private val secrets: SecretStore,
) : QuoteService {
    override val kind = QuoteProvider.COINGECKO

    override suspend fun fetch(requests: List<QuoteRequest>): List<QuoteResult> {
        if (requests.isEmpty()) return emptyList()
        val ids = requests.joinToString(",") { it.symbol }.encoded()
        val currencies = requests.map { it.currency.lowercase() }.distinct().joinToString(",").encoded()
        val headers = secrets.get(SecretStore.COINGECKO_KEY)?.takeIf(String::isNotBlank)
            ?.let { mapOf("x-cg-demo-api-key" to it) }.orEmpty()
        return try {
            val response = http.get(
                "https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=$currencies&include_last_updated_at=true&precision=full",
                headers,
            )
            parse(requests, response)
        } catch (_: IOException) {
            requests.map { QuoteResult.Failure(it, QuoteFailure.SIN_CONEXION) }
        }
    }

    internal fun parse(requests: List<QuoteRequest>, response: HttpResponse): List<QuoteResult> {
        val failure = when (response.status) {
            401, 403 -> QuoteFailure.CLAVE_INVALIDA
            429 -> QuoteFailure.LIMITE_ALCANZADO
            else -> null
        }
        if (failure != null) return requests.map { QuoteResult.Failure(it, failure) }
        return try {
            val root = JSONObject(response.body)
            requests.map { request ->
                val item = root.optJSONObject(request.symbol)
                    ?: return@map QuoteResult.Failure(request, QuoteFailure.NO_ENCONTRADO)
                try {
                    val key = request.currency.lowercase()
                    val raw = item.opt(key)
                        ?: return@map QuoteResult.Failure(request, QuoteFailure.DIVISA_DISTINTA)
                    val price = BigDecimal(raw.toString())
                    val timestamp = item.getLong("last_updated_at")
                    if (price.signum() <= 0 || timestamp <= 0) throw IllegalArgumentException()
                    QuoteResult.Success(
                        request, price, request.currency.uppercase(), Math.multiplyExact(timestamp, 1_000L),
                        PriceQuality.RETRASADO,
                    )
                } catch (_: Exception) {
                    QuoteResult.Failure(request, QuoteFailure.RESPUESTA_INVALIDA)
                }
            }
        } catch (_: Exception) {
            requests.map { QuoteResult.Failure(it, QuoteFailure.RESPUESTA_INVALIDA) }
        }
    }

    private fun String.encoded() = URLEncoder.encode(this, Charsets.UTF_8.name())
}
