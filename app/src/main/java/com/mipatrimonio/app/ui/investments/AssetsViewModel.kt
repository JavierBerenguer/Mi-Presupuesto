package com.mipatrimonio.app.ui.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.AssetDependencies
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.QuoteProvider
import com.mipatrimonio.app.data.quotes.CoinGeckoSearchService
import com.mipatrimonio.app.data.quotes.EodhdSearchService
import com.mipatrimonio.app.data.quotes.LookupFailure
import com.mipatrimonio.app.data.quotes.LookupResult
import com.mipatrimonio.app.data.quotes.SecretStore
import com.mipatrimonio.app.data.quotes.bestCryptoResult
import com.mipatrimonio.app.data.quotes.selectEodhdListing
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssetFilter { CON_POSICION, SIN_POSICION, ARCHIVADOS }

data class ManagedAsset(val asset: Asset, val quantity: BigDecimal, val valueMinor: Long?, val hasOperations: Boolean)

fun filterAssets(assets: List<ManagedAsset>, query: String, filter: AssetFilter): List<ManagedAsset> = assets.filter { item ->
    val matches = query.isBlank() || listOf(item.asset.name, item.asset.ticker, item.asset.isin, item.asset.market)
        .any { it.contains(query.trim(), ignoreCase = true) }
    matches && when (filter) {
        AssetFilter.CON_POSICION -> !item.asset.archived && item.quantity.signum() > 0
        AssetFilter.SIN_POSICION -> !item.asset.archived && item.quantity.signum() == 0
        AssetFilter.ARCHIVADOS -> item.asset.archived
    }
}

data class AssetsUiState(
    val isLoading: Boolean = true,
    val assets: List<ManagedAsset> = emptyList(),
    val dependencies: Map<String, AssetDependencies> = emptyMap(),
    val hideAmounts: Boolean = false,
    val error: String? = null,
    val quoteConfiguration: QuoteConfigurationState = QuoteConfigurationState(),
)

data class QuoteConfigurationProposal(
    val asset: Asset,
    val provider: QuoteProvider? = null,
    val symbol: String? = null,
    val market: String? = null,
    val candidateName: String? = null,
    val candidateCode: String? = null,
    val candidateCurrency: String? = null,
    val marketCapRank: Int? = null,
    val accepted: Boolean = true,
    val failure: LookupFailure? = null,
) {
    val configurable: Boolean get() = provider != null && symbol != null && failure == null
}

data class QuoteConfigurationState(
    val visible: Boolean = false,
    val started: Boolean = false,
    val loading: Boolean = false,
    val eodhdCalls: Int = 0,
    val proposals: List<QuoteConfigurationProposal> = emptyList(),
)

internal fun Asset.supportsAutomaticQuoteConfiguration(): Boolean = quoteProvider == null && !archived && when (type) {
    AssetType.ACCION, AssetType.ETF, AssetType.FONDO_INDEXADO, AssetType.FONDO_INVERSION -> isin.isNotBlank()
    AssetType.CRIPTO -> true
    else -> false
}

class AssetsViewModel(
    private val investments: InvestmentRepository,
    settings: SettingsRepository,
    private val eodhd: EodhdSearchService? = null,
    private val coinGecko: CoinGeckoSearchService? = null,
    private val secrets: SecretStore? = null,
) : ViewModel() {
    private val dependencies = MutableStateFlow<Map<String, AssetDependencies>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)
    private val quoteConfiguration = MutableStateFlow(QuoteConfigurationState())

    val uiState: StateFlow<AssetsUiState> = combine(
        investments.assets,
        investments.operations,
        investments.latestPrices,
        settings.settings,
        combine(dependencies, error, quoteConfiguration) { deps, message, configuration -> Triple(deps, message, configuration) },
    ) { assets, operations, prices, currentSettings, local ->
        AssetsUiState(
            isLoading = false,
            assets = assets.map { asset ->
                val assetOperations = operations.filter { it.assetId == asset.id }
                val quantity = assetOperations.groupBy { it.portfolioId }.values.fold(BigDecimal.ZERO) { total, group ->
                    total.add(PositionCalculator.compute(group).quantity)
                }
                val value = prices[asset.id]?.price?.let { price ->
                    runCatching { MoneyMath.toMinor(quantity.multiply(price), asset.currency) }.getOrNull()
                }
                ManagedAsset(asset, quantity, value, assetOperations.isNotEmpty())
            }.sortedWith(compareBy({ it.asset.name }, { it.asset.ticker })),
            dependencies = local.first,
            hideAmounts = currentSettings.hideAmounts,
            error = local.second,
            quoteConfiguration = local.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    fun inspect(assetId: String) {
        viewModelScope.launch {
            runCatching { investments.assetDependencies(assetId) }
                .onSuccess { dependencies.value += assetId to it }
                .onFailure { error.value = it.message }
        }
    }

    fun clearError() { error.value = null }

    fun openQuoteConfiguration() {
        viewModelScope.launch {
            val eligible = investments.assets.first().filter(Asset::supportsAutomaticQuoteConfiguration)
            quoteConfiguration.value = QuoteConfigurationState(
                visible = true,
                eodhdCalls = eligible.count { it.type != AssetType.CRIPTO },
            )
        }
    }

    fun closeQuoteConfiguration() { quoteConfiguration.value = QuoteConfigurationState() }

    fun findQuoteConfigurations() {
        val eodhdService = eodhd ?: return
        val cryptoService = coinGecko ?: return
        viewModelScope.launch {
            val eligible = investments.assets.first().filter(Asset::supportsAutomaticQuoteConfiguration)
            quoteConfiguration.value = quoteConfiguration.value.copy(started = true, loading = true, proposals = emptyList())
            val twelveConfigured = secrets?.isConfigured(SecretStore.TWELVE_DATA_KEY) == true
            var eodhdLimited = false
            val proposals = eligible.map { asset ->
                if (asset.type == AssetType.CRIPTO) cryptoProposal(asset, cryptoService)
                else if (eodhdLimited) QuoteConfigurationProposal(asset, failure = LookupFailure.LIMITE_ALCANZADO)
                else eodhdProposal(asset, eodhdService, twelveConfigured).also {
                    if (it.failure == LookupFailure.LIMITE_ALCANZADO) eodhdLimited = true
                }
            }
            quoteConfiguration.value = quoteConfiguration.value.copy(loading = false, proposals = proposals)
        }
    }

    fun setProposalAccepted(assetId: String, accepted: Boolean) {
        quoteConfiguration.value = quoteConfiguration.value.copy(
            proposals = quoteConfiguration.value.proposals.map {
                if (it.asset.id == assetId) it.copy(accepted = accepted) else it
            },
        )
    }

    fun saveQuoteConfigurations(onSaved: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                quoteConfiguration.value.proposals.filter { it.accepted && it.configurable }.forEach { proposal ->
                    investments.saveAsset(
                        proposal.asset.copy(
                            market = proposal.market.orEmpty(), quoteProvider = proposal.provider,
                            quoteSymbol = proposal.symbol, quoteMic = null,
                        ),
                    )
                }
            }.onSuccess { quoteConfiguration.value = QuoteConfigurationState(); onSaved() }
                .onFailure { error.value = it.message.orEmpty() }
        }
    }

    private suspend fun eodhdProposal(
        asset: Asset,
        service: EodhdSearchService,
        twelveConfigured: Boolean,
    ): QuoteConfigurationProposal = when (val result = service.search(asset.isin)) {
        is LookupResult.Failure -> QuoteConfigurationProposal(asset, failure = result.reason)
        is LookupResult.Success -> {
            val listing = selectEodhdListing(result.value, asset.currency)
                ?: return QuoteConfigurationProposal(asset, failure = LookupFailure.NO_ENCONTRADO)
            val useTwelve = listing.exchange.equals("US", true) && twelveConfigured &&
                (asset.type == AssetType.ACCION || asset.type == AssetType.ETF)
            QuoteConfigurationProposal(
                asset = asset,
                provider = if (useTwelve) QuoteProvider.TWELVE_DATA else QuoteProvider.EODHD,
                symbol = if (useTwelve) listing.code else listing.symbol,
                market = listing.exchange,
                candidateName = listing.name,
                candidateCode = listing.code,
                candidateCurrency = listing.currency,
            )
        }
    }

    private suspend fun cryptoProposal(asset: Asset, service: CoinGeckoSearchService): QuoteConfigurationProposal {
        val byName = service.search(asset.name)
        val firstItems = (byName as? LookupResult.Success)?.value.orEmpty()
        val result = if (firstItems.isEmpty() && asset.ticker.isNotBlank()) service.search(asset.ticker) else byName
        return when (result) {
            is LookupResult.Failure -> QuoteConfigurationProposal(asset, failure = result.reason)
            is LookupResult.Success -> {
                val coin = bestCryptoResult(result.value)
                    ?: return QuoteConfigurationProposal(asset, failure = LookupFailure.NO_ENCONTRADO)
                QuoteConfigurationProposal(
                    asset = asset, provider = QuoteProvider.COINGECKO, symbol = coin.id, market = "CoinGecko",
                    candidateName = coin.name, candidateCode = coin.symbol, marketCapRank = coin.marketCapRank,
                )
            }
        }
    }

    fun save(
        existing: Asset,
        name: String,
        ticker: String,
        isin: String,
        type: AssetType,
        market: String,
        currency: String,
        quoteProvider: QuoteProvider?,
        quoteSymbol: String?,
        quoteMic: String?,
        onSaved: () -> Unit,
    ) = launchAction(onSaved) {
        investments.saveAsset(existing.copy(
            name = name.trim(), ticker = ticker.trim(), isin = isin.trim(), type = type,
            market = market.trim(), currency = currency, quoteProvider = quoteProvider,
            quoteSymbol = quoteSymbol, quoteMic = quoteMic,
        ))
    }

    fun delete(asset: Asset, onDeleted: () -> Unit) = launchAction(onDeleted) { investments.deleteAsset(asset.id) }

    fun setArchived(asset: Asset, archived: Boolean, onChanged: () -> Unit) =
        launchAction(onChanged) { investments.setAssetArchived(asset.id, archived) }

    private fun launchAction(onSuccess: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { error.value = null; onSuccess() }
                .onFailure { error.value = it.message.orEmpty() }
        }
    }
}
