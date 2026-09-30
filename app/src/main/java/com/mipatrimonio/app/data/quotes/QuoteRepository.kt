package com.mipatrimonio.app.data.quotes

import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.QuoteProvider

data class QuoteRefreshSummary(
    val updated: Int,
    val failures: Map<QuoteFailure, Int>,
    val skippedWithoutProvider: Int,
    val providers: Map<QuoteProvider, ProviderQuoteRefreshSummary> = emptyMap(),
) {
    val failed: Int get() = failures.values.sum()
}

data class ProviderQuoteRefreshSummary(
    val updated: Int = 0,
    val failures: Map<QuoteFailure, Int> = emptyMap(),
) { val failed: Int get() = failures.values.sum() }

class QuoteRepository(
    private val db: AppDatabase,
    private val investments: InvestmentRepository,
    services: List<QuoteService>,
) {
    private val services = services.associateBy(QuoteService::kind)

    suspend fun refreshAll(): QuoteRefreshSummary {
        val dao = db.investmentDao()
        val allOpen = dao.getActiveAssets().map { it.toDomain() }.filter { asset ->
            dao.operationsForAsset(asset.id).map { it.toDomain() }.groupBy { it.portfolioId }
                .values.sumOf { PositionCalculator.compute(it).quantity }.signum() > 0
        }
        val candidates = allOpen.filter { it.quoteProvider != null }
        val invalid = candidates.filter { it.quoteSymbol.isNullOrBlank() }
        val results = candidates.filterNot { it.quoteSymbol.isNullOrBlank() }
            .groupBy(Asset::quoteProvider)
            .flatMap { (provider, assets) ->
                val service = provider?.let(services::get)
                val providerResults = if (service == null) assets.map {
                    QuoteResult.Failure(it.toRequest(), QuoteFailure.NO_ENCONTRADO)
                } else service.fetch(assets.map { it.toRequest() })
                providerResults.map { provider to it }
            }
        var updated = 0
        val failures = mutableMapOf<QuoteFailure, Int>()
        val providerUpdated = mutableMapOf<QuoteProvider, Int>()
        val providerFailures = mutableMapOf<QuoteProvider, MutableMap<QuoteFailure, Int>>()
        invalid.forEach { asset ->
            failures.increment(QuoteFailure.NO_ENCONTRADO)
            asset.quoteProvider?.let { providerFailures.getOrPut(it) { mutableMapOf() }.increment(QuoteFailure.NO_ENCONTRADO) }
        }
        results.forEach { (provider, result) ->
            when (result) {
                is QuoteResult.Failure -> {
                    failures.increment(result.reason)
                    provider?.let { providerFailures.getOrPut(it) { mutableMapOf() }.increment(result.reason) }
                }
                is QuoteResult.Success -> {
                    if (result.currency.uppercase() != result.request.currency.uppercase()) {
                        failures.increment(QuoteFailure.DIVISA_DISTINTA)
                        provider?.let { providerFailures.getOrPut(it) { mutableMapOf() }.increment(QuoteFailure.DIVISA_DISTINTA) }
                    } else {
                        investments.addProviderPrice(
                            result.request.assetId, result.price, result.currency,
                            result.asOfEpochMillis, result.quality,
                        )
                        updated++
                        provider?.let { providerUpdated[it] = providerUpdated.getOrDefault(it, 0) + 1 }
                    }
                }
            }
        }
        val providerKinds = providerUpdated.keys + providerFailures.keys
        return QuoteRefreshSummary(
            updated, failures.toMap(), allOpen.count { it.quoteProvider == null },
            providerKinds.associateWith { provider ->
                ProviderQuoteRefreshSummary(providerUpdated.getOrDefault(provider, 0), providerFailures[provider].orEmpty())
            },
        )
    }

    private fun Asset.toRequest() = QuoteRequest(id, quoteSymbol.orEmpty(), currency, quoteMic)
    private fun MutableMap<QuoteFailure, Int>.increment(reason: QuoteFailure) {
        this[reason] = getOrDefault(reason, 0) + 1
    }
}
