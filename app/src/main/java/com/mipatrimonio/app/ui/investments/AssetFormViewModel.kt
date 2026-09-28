package com.mipatrimonio.app.ui.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.quotes.CoinGeckoSearchService
import com.mipatrimonio.app.data.quotes.CryptoSearchItem
import com.mipatrimonio.app.data.quotes.DiscoveredQuote
import com.mipatrimonio.app.data.quotes.LookupFailure
import com.mipatrimonio.app.data.quotes.LookupResult
import com.mipatrimonio.app.data.quotes.OpenFigiListing
import com.mipatrimonio.app.data.quotes.OpenFigiService
import com.mipatrimonio.app.data.quotes.TwelveDataAssetService
import com.mipatrimonio.app.data.quotes.assetTypeFor
import com.mipatrimonio.app.data.quotes.isValidIsin
import com.mipatrimonio.app.data.quotes.normalizeIsin
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.QuoteProvider
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class AssetFormNotice { ISIN_INVALIDO, COMPLETADO, REVISAR_TIPO, CONFIRMAR_DIVISA, SIN_RESULTADOS }

data class AssetFormState(
    val initializedFor: String? = null,
    val baseCurrency: String = Currencies.EUR,
    val name: String = "",
    val ticker: String = "",
    val isin: String = "",
    val type: AssetType = AssetType.ACCION,
    val market: String = "",
    val currency: String = Currencies.EUR,
    val quoteProvider: QuoteProvider? = null,
    val quoteSymbol: String = "",
    val quoteMic: String = "",
    val listings: List<OpenFigiListing> = emptyList(),
    val cryptoResults: List<CryptoSearchItem> = emptyList(),
    val cryptoQuery: String = "",
    val providerQuote: DiscoveredQuote? = null,
    val saveProviderPrice: Boolean = false,
    val loading: Boolean = false,
    val notice: AssetFormNotice? = null,
    val failure: LookupFailure? = null,
    val saveError: String? = null,
)

class AssetFormViewModel(
    private val investments: InvestmentRepository,
    private val settings: SettingsRepository,
    private val openFigi: OpenFigiService,
    private val coinGecko: CoinGeckoSearchService,
    private val twelveData: TwelveDataAssetService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssetFormState())
    private var lookupJob: Job? = null
    val state: StateFlow<AssetFormState> = mutableState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AssetFormState(),
    )

    fun initialize(asset: Asset?) {
        lookupJob?.cancel()
        val key = asset?.id ?: NEW_ASSET
        lookupJob = viewModelScope.launch {
            val baseCurrency = settings.settings.first().baseCurrency
            mutableState.value = AssetFormState(
                initializedFor = key, baseCurrency = baseCurrency,
                name = asset?.name.orEmpty(), ticker = asset?.ticker.orEmpty(), isin = asset?.isin.orEmpty(),
                type = asset?.type ?: AssetType.ACCION, market = asset?.market.orEmpty(),
                currency = asset?.currency ?: baseCurrency, quoteProvider = asset?.quoteProvider,
                quoteSymbol = asset?.quoteSymbol.orEmpty(), quoteMic = asset?.quoteMic.orEmpty(),
            )
        }
    }

    fun setName(value: String) = update { copy(name = value, saveError = null) }
    fun setTicker(value: String) = update { copy(ticker = value, saveError = null) }
    fun setIsin(value: String) = update { copy(isin = value, notice = null, failure = null, saveError = null) }
    fun setType(value: AssetType) = update {
        copy(
            type = value,
            quoteProvider = if (value == AssetType.CRIPTO) QuoteProvider.COINGECKO else quoteProvider.takeIf { value == AssetType.ACCION || value == AssetType.ETF },
            isin = if (value == AssetType.CRIPTO) "" else isin,
            currency = if (value == AssetType.CRIPTO) baseCurrency else currency,
            listings = if (value == AssetType.CRIPTO) emptyList() else listings,
        )
    }
    fun setMarket(value: String) = update { copy(market = value) }
    fun setCurrency(value: String) = update { copy(currency = value, notice = null) }
    fun setQuoteProvider(value: QuoteProvider?) = update { copy(quoteProvider = value) }
    fun setQuoteSymbol(value: String) = update { copy(quoteSymbol = value) }
    fun setQuoteMic(value: String) = update { copy(quoteMic = value) }
    fun setCryptoQuery(value: String) = update { copy(cryptoQuery = value, failure = null, notice = null) }
    fun setSaveProviderPrice(value: Boolean) = update { copy(saveProviderPrice = value) }

    fun searchIsin() {
        val normalized = normalizeIsin(mutableState.value.isin)
        if (!isValidIsin(normalized)) {
            update { copy(isin = normalized, notice = AssetFormNotice.ISIN_INVALIDO, listings = emptyList()) }
            return
        }
        lookupJob = viewModelScope.launch {
            update { copy(isin = normalized, loading = true, notice = null, failure = null, listings = emptyList()) }
            when (val result = openFigi.search(normalized)) {
                is LookupResult.Failure -> update { copy(loading = false, failure = result.reason) }
                is LookupResult.Success -> {
                    if (result.value.size == 1) selectListing(result.value.single())
                    else update { copy(loading = false, listings = result.value) }
                }
            }
        }
    }

    fun selectListing(listing: OpenFigiListing) {
        val mappedType = assetTypeFor(listing)
        val type = mappedType ?: mutableState.value.type
        val market = listing.marketInfo
        val provider = if (type == AssetType.ACCION || type == AssetType.ETF) QuoteProvider.TWELVE_DATA else null
        update {
            copy(
                name = listing.name, ticker = listing.ticker, market = market.name, type = type,
                currency = market.currency.orEmpty(), quoteProvider = provider,
                quoteSymbol = if (provider == null) "" else listing.ticker,
                quoteMic = if (provider == null) "" else market.mic.orEmpty(), listings = emptyList(),
                loading = provider != null, notice = if (mappedType == null) AssetFormNotice.REVISAR_TIPO else AssetFormNotice.COMPLETADO,
                providerQuote = null, saveProviderPrice = false,
            )
        }
        if (provider == null) return
        lookupJob = viewModelScope.launch {
            when (val quote = twelveData.discover(listing.ticker, market.mic)) {
                is LookupResult.Success -> update {
                    copy(currency = quote.value.currency, providerQuote = quote.value, loading = false, notice = AssetFormNotice.COMPLETADO)
                }
                is LookupResult.Failure -> update { copy(loading = false, notice = AssetFormNotice.CONFIRMAR_DIVISA) }
            }
        }
    }

    fun searchCrypto() {
        if (mutableState.value.cryptoQuery.isBlank()) return
        lookupJob = viewModelScope.launch {
            update { copy(loading = true, failure = null, notice = null, cryptoResults = emptyList()) }
            when (val result = coinGecko.search(mutableState.value.cryptoQuery)) {
                is LookupResult.Failure -> update { copy(loading = false, failure = result.reason) }
                is LookupResult.Success -> update {
                    copy(
                        loading = false, cryptoResults = result.value,
                        notice = if (result.value.isEmpty()) AssetFormNotice.SIN_RESULTADOS else null,
                    )
                }
            }
        }
    }

    fun selectCrypto(coin: CryptoSearchItem) = update {
        copy(
            name = coin.name, ticker = coin.symbol.uppercase(), market = "CoinGecko",
            quoteProvider = QuoteProvider.COINGECKO, quoteSymbol = coin.id, quoteMic = "",
            cryptoResults = emptyList(), notice = AssetFormNotice.COMPLETADO,
        )
    }

    fun save(existing: Asset?, onSaved: () -> Unit) {
        val current = mutableState.value
        viewModelScope.launch {
            runCatching {
                val asset = Asset(
                    id = existing?.id ?: UUID.randomUUID().toString(), name = current.name.trim(),
                    ticker = current.ticker.trim(), isin = normalizeIsin(current.isin), type = current.type,
                    market = current.market.trim(), currency = current.currency, archived = existing?.archived ?: false,
                    quoteProvider = current.quoteProvider,
                    quoteSymbol = current.quoteSymbol.trim().takeIf { current.quoteProvider != null },
                    quoteMic = current.quoteMic.trim().takeIf { current.quoteProvider == QuoteProvider.TWELVE_DATA && it.isNotBlank() },
                )
                investments.saveAsset(asset)
                current.providerQuote?.takeIf { current.saveProviderPrice }?.let { quote ->
                    investments.addProviderPrice(asset.id, quote.price, quote.currency, quote.asOfEpochMillis, quote.quality)
                }
            }.onSuccess { onSaved() }
                .onFailure { error -> update { copy(saveError = error.message.orEmpty()) } }
        }
    }

    private inline fun update(transform: AssetFormState.() -> AssetFormState) {
        mutableState.value = mutableState.value.transform()
    }

    private companion object { const val NEW_ASSET = "__new__" }
}
