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
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssetFilter { CON_POSICION, SIN_POSICION, ARCHIVADOS }

data class ManagedAsset(val asset: Asset, val quantity: BigDecimal, val valueMinor: Long?, val hasOperations: Boolean)

fun filterAssets(assets: List<ManagedAsset>, query: String, filter: AssetFilter): List<ManagedAsset> = assets.filter { item ->
    val matches = query.isBlank() || listOf(item.asset.name, item.asset.ticker, item.asset.isin, item.asset.market)
        .any { it.contains(query.trim(), ignoreCase = true) }
    matches && when (filter) {
        AssetFilter.CON_POSICION -> item.quantity.signum() > 0
        AssetFilter.SIN_POSICION -> item.quantity.signum() == 0
        AssetFilter.ARCHIVADOS -> false
    }
}

data class AssetsUiState(
    val isLoading: Boolean = true,
    val assets: List<ManagedAsset> = emptyList(),
    val dependencies: Map<String, AssetDependencies> = emptyMap(),
    val hideAmounts: Boolean = false,
    val error: String? = null,
)

class AssetsViewModel(
    private val investments: InvestmentRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val dependencies = MutableStateFlow<Map<String, AssetDependencies>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AssetsUiState> = combine(
        investments.assets,
        investments.operations,
        investments.latestPrices,
        settings.settings,
        combine(dependencies, error) { deps, message -> deps to message },
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

    fun save(
        existing: Asset,
        name: String,
        ticker: String,
        isin: String,
        type: AssetType,
        market: String,
        currency: String,
        onSaved: () -> Unit,
    ) = launchAction(onSaved) {
        investments.saveAsset(existing.copy(name = name.trim(), ticker = ticker.trim(), isin = isin.trim(), type = type, market = market.trim(), currency = currency))
    }

    fun delete(asset: Asset, onDeleted: () -> Unit) = launchAction(onDeleted) { investments.deleteAsset(asset.id) }

    private fun launchAction(onSuccess: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { error.value = null; onSuccess() }
                .onFailure { error.value = it.message.orEmpty() }
        }
    }
}
