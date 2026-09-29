package com.mipatrimonio.app.ui.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.*
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val portfolio: Portfolio? = null,
    val allPortfolios: List<Portfolio> = emptyList(),
    val asset: Asset? = null,
    val row: PositionRow? = null,
    val operations: List<InvestmentOperation> = emptyList(),
    val allOperations: List<InvestmentOperation> = emptyList(),
    val accountsById: Map<String, Account> = emptyMap(),
    val latestPrice: AssetPrice? = null,
    val baseCurrency: String = Currencies.EUR,
    val hideAmounts: Boolean = false,
    val transferLabels: Map<String, Pair<String, String>> = emptyMap(),
)

class AssetDetailViewModel(
    private val portfolioId: String,
    private val assetId: String,
    private val investments: InvestmentRepository,
    ledger: LedgerRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val investmentData = combine(
        investments.portfolios, investments.assets, investments.operations, investments.latestPrices,
    ) { portfolios, assets, operations, prices -> DetailInvestmentData(portfolios, assets, operations, prices) }

    val uiState: StateFlow<AssetDetailUiState> = combine(
        investmentData, ledger.accounts, settings.settings,
    ) { data, accounts, config ->
        val portfolio = data.portfolios.firstOrNull { it.id == portfolioId }
        val asset = data.assets.firstOrNull { it.id == assetId }
        val operations = data.operations.filter { it.portfolioId == portfolioId && it.assetId == assetId }
            .sortedWith(
                compareByDescending<InvestmentOperation> { it.date }
                    .thenByDescending { it.time }
                    .thenByDescending { it.createdAt },
            )
        val price = data.prices[assetId]?.takeIf { it.currency == asset?.currency }
        val row = if (portfolio != null && asset != null && operations.isNotEmpty()) {
            val position = PositionCalculator.compute(operations)
            PositionRow(portfolio, asset, PositionCalculator.value(position, price?.price), price)
        } else null
        AssetDetailUiState(
            isLoading = false,
            portfolio = portfolio,
            allPortfolios = data.portfolios,
            asset = asset,
            row = row,
            operations = operations,
            allOperations = data.operations,
            accountsById = accounts.associateBy { it.id },
            latestPrice = price,
            baseCurrency = config.baseCurrency,
            hideAmounts = config.hideAmounts,
            transferLabels = data.operations.mapNotNull { operation ->
                operation.transferGroupId?.let { it to operation }
            }.groupBy({ it.first }, { it.second })
                .mapNotNull { (groupId, pair) ->
                    val source = pair.firstOrNull { it.type == OperationType.TRASPASO_SALIDA }
                    val destination = pair.firstOrNull { it.type == OperationType.TRASPASO_ENTRADA }
                    val sourceName = source?.let { op -> data.portfolios.firstOrNull { it.id == op.portfolioId }?.name }
                    val destinationName = destination?.let { op -> data.portfolios.firstOrNull { it.id == op.portfolioId }?.name }
                    if (sourceName == null || destinationName == null) null
                    else groupId to (sourceName to destinationName)
                }.toMap(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetDetailUiState())

    fun addOperation(
        portfolio: Portfolio,
        asset: Asset,
        type: OperationType,
        dateTime: LocalDateTime,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        feesMinor: Long,
        accountId: String?,
        note: String,
        onResult: (String?) -> Unit,
    ) = launch(onResult) {
        investments.addOperation(
            InvestmentOperation(
                UUID.randomUUID().toString(), portfolio.id, asset.id, type, dateTime.toLocalDate(),
                if (type == OperationType.COMISION) BigDecimal.ONE else quantity,
                unitPrice, if (type == OperationType.COMISION) 0 else feesMinor,
                asset.currency, note.trim(), System.currentTimeMillis(), accountId, dateTime.toLocalTime(),
            ),
        )
    }

    fun deleteOperation(operation: InvestmentOperation, onResult: (String?) -> Unit) =
        launch(onResult) { investments.deleteOperation(operation) }

    fun saveTransfer(
        source: Portfolio,
        destination: Portfolio,
        asset: Asset,
        quantity: BigDecimal,
        networkFeeQuantity: BigDecimal,
        dateTime: LocalDateTime,
        existingGroupId: String? = null,
        onResult: (String?) -> Unit,
    ) = launch(onResult) {
        investments.saveCryptoTransfer(source.id, destination.id, asset.id, quantity, networkFeeQuantity, dateTime, existingGroupId)
    }

    fun updateOperation(
        existing: InvestmentOperation,
        portfolio: Portfolio,
        asset: Asset,
        type: OperationType,
        dateTime: LocalDateTime,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        feesMinor: Long,
        accountId: String?,
        note: String,
        onResult: (String?) -> Unit,
    ) = launch(onResult) {
        investments.addOperation(
            existing.copy(
                portfolioId = portfolio.id, assetId = asset.id, type = type,
                date = dateTime.toLocalDate(), time = dateTime.toLocalTime(),
                quantity = if (type == OperationType.COMISION) BigDecimal.ONE else quantity,
                unitPrice = unitPrice, feesMinor = if (type == OperationType.COMISION) 0 else feesMinor,
                currency = asset.currency, accountId = accountId, note = note.trim(),
            ),
        )
    }

    fun setManualPrice(asset: Asset, price: BigDecimal, onResult: (String?) -> Unit) =
        launch(onResult) { investments.setManualPrice(asset.id, price, asset.currency) }

    private fun launch(onResult: (String?) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); onResult(null) } catch (error: IllegalArgumentException) { onResult(error.message) }
        }
    }
}

private data class DetailInvestmentData(
    val portfolios: List<Portfolio>,
    val assets: List<Asset>,
    val operations: List<InvestmentOperation>,
    val prices: Map<String, AssetPrice>,
)
