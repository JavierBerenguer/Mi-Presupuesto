package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.domain.model.PriceQuality
import com.mipatrimonio.app.domain.model.QuoteProvider
import java.math.BigDecimal

data class QuoteRequest(
    val assetId: String,
    val symbol: String,
    val currency: String,
    val mic: String? = null,
)

sealed interface QuoteResult {
    val request: QuoteRequest

    data class Success(
        override val request: QuoteRequest,
        val price: BigDecimal,
        val currency: String,
        val asOfEpochMillis: Long,
        val quality: PriceQuality,
    ) : QuoteResult

    data class Failure(override val request: QuoteRequest, val reason: QuoteFailure) : QuoteResult
}

enum class QuoteFailure {
    SIN_CLAVE, CLAVE_INVALIDA, LIMITE_ALCANZADO, NO_ENCONTRADO, SIN_CONEXION,
    RESPUESTA_INVALIDA, DIVISA_DISTINTA,
}

interface QuoteService {
    val kind: QuoteProvider
    suspend fun fetch(requests: List<QuoteRequest>): List<QuoteResult>
}

interface SecretStore {
    suspend fun put(name: String, value: String)
    suspend fun get(name: String): String?
    suspend fun remove(name: String)
    suspend fun isConfigured(name: String): Boolean = !get(name).isNullOrBlank()

    companion object {
        const val TWELVE_DATA_KEY = "twelve_data"
        const val COINGECKO_KEY = "coingecko"
    }
}

class InMemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun put(name: String, value: String) { values[name] = value }
    override suspend fun get(name: String): String? = values[name]
    override suspend fun remove(name: String) { values.remove(name) }
}
