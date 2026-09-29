package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.PriceQuality
import com.mipatrimonio.app.domain.model.QuoteProvider
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class EodhdQuoteProvider(
    private val http: HttpClient,
    private val secrets: SecretStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : QuoteService {
    override val kind = QuoteProvider.EODHD

    override suspend fun fetch(requests: List<QuoteRequest>): List<QuoteResult> {
        if (requests.isEmpty()) return emptyList()
        val key = secrets.get(SecretStore.EODHD_KEY)?.takeIf(String::isNotBlank)
            ?: return requests.map { QuoteResult.Failure(it, QuoteFailure.SIN_CLAVE) }
        val results = mutableListOf<QuoteResult>()
        var limited = false
        requests.chunked(MAX_BATCH).forEach { batch ->
            if (limited) {
                results += batch.map { QuoteResult.Failure(it, QuoteFailure.LIMITE_ALCANZADO) }
            } else {
                val primary = batch.first().symbol.encoded()
                val additional = batch.drop(1).joinToString(",") { it.symbol }.encoded()
                val suffix = if (additional.isBlank()) "" else "&s=$additional"
                val url = "https://eodhd.com/api/real-time/$primary?api_token=${key.encoded()}&fmt=json$suffix"
                val parsed = try {
                    parse(batch, http.get(url, emptyMap()))
                } catch (_: IOException) {
                    batch.map { QuoteResult.Failure(it, QuoteFailure.SIN_CONEXION) }
                }
                results += parsed
                limited = parsed.any { it is QuoteResult.Failure && it.reason == QuoteFailure.LIMITE_ALCANZADO }
            }
        }
        return results
    }

    internal fun parse(requests: List<QuoteRequest>, response: HttpResponse): List<QuoteResult> {
        val failure = when (response.status) {
            401, 403 -> QuoteFailure.CLAVE_INVALIDA
            402, 429 -> QuoteFailure.LIMITE_ALCANZADO
            else -> null
        }
        if (failure != null) return requests.map { QuoteResult.Failure(it, failure) }
        if (response.status !in 200..299) return requests.map { QuoteResult.Failure(it, QuoteFailure.RESPUESTA_INVALIDA) }
        return try {
            val items = if (response.body.trimStart().startsWith("[")) {
                val array = JSONArray(response.body)
                (0 until array.length()).map(array::getJSONObject)
            } else listOf(JSONObject(response.body))
            val byCode = items.mapNotNull { item ->
                item.optString("code").takeIf(String::isNotBlank)?.uppercase()?.let { it to item }
            }.toMap()
            requests.map { request ->
                val item = byCode[request.symbol.uppercase()]
                    ?: return@map QuoteResult.Failure(request, QuoteFailure.NO_ENCONTRADO)
                parseItem(request, item)
            }
        } catch (_: Exception) {
            requests.map { QuoteResult.Failure(it, QuoteFailure.RESPUESTA_INVALIDA) }
        }
    }

    private fun parseItem(request: QuoteRequest, item: JSONObject): QuoteResult = try {
        val price = BigDecimal(item.get("close").toString())
        val timestamp = item.getLong("timestamp")
        if (price.signum() <= 0 || timestamp <= 0) throw IllegalArgumentException()
        val asOf = Math.multiplyExact(timestamp, 1_000L)
        QuoteResult.Success(
            request, price, request.currency.uppercase(), asOf,
            if (nowEpochMillis() - asOf > SIX_HOURS_MILLIS) PriceQuality.CIERRE else PriceQuality.RETRASADO,
        )
    } catch (_: Exception) {
        QuoteResult.Failure(request, QuoteFailure.RESPUESTA_INVALIDA)
    }

    private fun String.encoded() = URLEncoder.encode(this, Charsets.UTF_8.name())

    private companion object {
        const val MAX_BATCH = 15
        const val SIX_HOURS_MILLIS = 6L * 60L * 60L * 1_000L
    }
}
