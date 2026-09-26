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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val portfolio: Portfolio? = null,
    val asset: Asset? = null,
    val row: PositionRow? = null,
    val operations: List<InvestmentOperation> = emptyList(),
    val accountsById: Map<String, Account> = emptyMap(),
    val latestPrice: AssetPrice? = null,
    val baseCurrency: String = Currencies.EUR,
    val hideAmounts: Boolean = false,
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
            .sortedWith(compareByDescending<InvestmentOperation> { it.date }.thenByDescending { it.createdAt })
        val price = data.prices[assetId]?.takeIf { it.currency == asset?.currency }
        val row = if (portfolio != null && asset != null && operations.isNotEmpty()) {
            val position = PositionCalculator.compute(operations)
            PositionRow(portfolio, asset, PositionCalculator.value(position, price?.price), price)
        } else null
        AssetDetailUiState(
            isLoading = false,
            portfolio = portfolio,
            asset = asset,
            row = row,
            operations = operations,
            accountsById = accounts.associateBy { it.id },
            latestPrice = price,
            baseCurrency = config.baseCurrency,
            hideAmounts = config.hideAmounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetDetailUiState())

    fun addOperation(
        portfolio: Portfolio,
        asset: Asset,
        type: OperationType,
        date: LocalDate,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        feesMinor: Long,
        accountId: String?,
        note: String,
        onResult: (String?) -> Unit,
    ) = launch(onResult) {
        investments.addOperation(
            InvestmentOperation(
                UUID.randomUUID().toString(), portfolio.id, asset.id, type, date,
                if (type == OperationType.COMISION) BigDecimal.ONE else quantity,
                unitPrice, if (type == OperationType.COMISION) 0 else feesMinor,
                asset.currency, note.trim(), System.currentTimeMillis(), accountId,
            ),
        )
    }

    fun deleteOperation(operation: InvestmentOperation, onResult: (String?) -> Unit) =
        launch(onResult) { investments.deleteOperation(operation) }

    fun updateOperation(
        existing: InvestmentOperation,
        portfolio: Portfolio,
        asset: Asset,
        type: OperationType,
        date: LocalDate,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        feesMinor: Long,
        accountId: String?,
        note: String,
        onResult: (String?) -> Unit,
    ) = launch(onResult) {
        investments.addOperation(
            existing.copy(
                portfolioId = portfolio.id, assetId = asset.id, type = type, date = date,
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
