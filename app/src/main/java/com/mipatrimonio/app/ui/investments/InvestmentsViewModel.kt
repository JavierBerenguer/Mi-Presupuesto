package com.mipatrimonio.app.ui.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.FinanceSnapshot
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InvestmentsUiState(
    val snapshot: FinanceSnapshot? = null,
    val portfolios: List<Portfolio> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val operationsByPosition: Map<Pair<String, String>, List<InvestmentOperation>> = emptyMap(),
    val latestPrices: Map<String, AssetPrice> = emptyMap(),
    val summaries: List<PortfolioSummary> = emptyList(),
    val totalCostMinor: Long = 0L,
    val totalUnrealizedMinor: Long = 0L,
    val totalUnrealizedPct: BigDecimal? = null,
    val excludedCurrencies: Set<String> = emptySet(),
    val unpricedAssets: List<String> = emptyList(),
) {
    val isLoading: Boolean get() = snapshot == null
}

class InvestmentsViewModel(
    private val ledger: LedgerRepository,
    private val investments: InvestmentRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val ledgerData = combine(
        ledger.accounts,
        ledger.transactions,
        ledger.transfers,
    ) { accounts, transactions, transfers -> LedgerData(accounts, transactions, transfers) }

    private val investmentData = combine(
        investments.portfolios,
        investments.assets,
        investments.operations,
        investments.latestPrices,
    ) { portfolios, assets, operations, prices ->
        InvestmentData(portfolios, assets, operations, prices)
    }

    val state: StateFlow<InvestmentsUiState> = combine(
        ledgerData,
        investmentData,
        settings.settings,
    ) { ledgerValues, investmentValues, currentSettings ->
        val snapshot = SnapshotBuilder.build(
            baseCurrency = currentSettings.baseCurrency,
            accounts = ledgerValues.accounts,
            transactions = ledgerValues.transactions,
            transfers = ledgerValues.transfers,
            portfolios = investmentValues.portfolios,
            assets = investmentValues.assets,
            operations = investmentValues.operations,
            prices = investmentValues.prices,
        )
        val summaries = summarize(snapshot.positions, currentSettings.baseCurrency)
        InvestmentsUiState(
            snapshot = snapshot,
            portfolios = investmentValues.portfolios.sortedWith(compareBy({ it.createdAt }, { it.name })),
            assets = investmentValues.assets.sortedWith(compareBy({ it.name }, { it.ticker })),
            operationsByPosition = investmentValues.operations
                .groupBy { it.portfolioId to it.assetId }
                .mapValues { (_, operations) ->
                    operations.sortedWith(
                        compareByDescending<InvestmentOperation> { it.date }
                            .thenByDescending { it.createdAt },
                    )
                },
            latestPrices = investmentValues.prices,
            summaries = summaries,
            totalCostMinor = summaries.fold(0L) { total, summary -> addExact(total, summary.costMinor) },
            totalUnrealizedMinor = summaries.fold(0L) { total, summary -> addExact(total, summary.unrealizedMinor) },
            totalUnrealizedPct = totalUnrealizedPct(snapshot.positions, currentSettings.baseCurrency),
            excludedCurrencies = summaries.flatMapTo(sortedSetOf()) { it.excludedCurrencies },
            unpricedAssets = summaries.flatMap { it.unpricedAssets }.distinct().sorted(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InvestmentsUiState(),
    )

    fun savePortfolio(name: String, onResult: (String?) -> Unit) {
        launchAction(onResult) {
            investments.savePortfolio(
                Portfolio(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun saveAsset(
        name: String,
        ticker: String,
        isin: String,
        type: AssetType,
        market: String,
        currency: String,
        onResult: (String?) -> Unit,
    ) {
        launchAction(onResult) {
            investments.saveAsset(
                Asset(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    ticker = ticker.trim(),
                    isin = isin.trim(),
                    type = type,
                    market = market.trim(),
                    currency = currency,
                ),
            )
        }
    }

    fun addOperation(
        portfolio: Portfolio,
        asset: Asset,
        type: OperationType,
        date: LocalDate,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        feesMinor: Long,
        note: String,
        onResult: (String?) -> Unit,
    ) {
        val operation = InvestmentOperation(
            id = UUID.randomUUID().toString(),
            portfolioId = portfolio.id,
            assetId = asset.id,
            type = type,
            date = date,
            quantity = if (type == OperationType.COMISION) BigDecimal.ONE else quantity,
            unitPrice = unitPrice,
            feesMinor = if (type == OperationType.COMISION) 0L else feesMinor,
            currency = asset.currency,
            note = note.trim(),
            createdAt = System.currentTimeMillis(),
        )
        PositionCalculator.validate(operation)?.let {
            onResult(it)
            return
        }
        launchAction(onResult) { investments.addOperation(operation) }
    }

    fun deleteOperation(operation: InvestmentOperation, onResult: (String?) -> Unit) {
        launchAction(onResult) { investments.deleteOperation(operation) }
    }

    fun setManualPrice(asset: Asset, price: BigDecimal, onResult: (String?) -> Unit) {
        launchAction(onResult) { investments.setManualPrice(asset.id, price, asset.currency) }
    }

    private fun launchAction(onResult: (String?) -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                onResult(null)
            } catch (error: IllegalArgumentException) {
                onResult(error.message)
            }
        }
    }
}

private data class LedgerData(
    val accounts: List<com.mipatrimonio.app.domain.model.Account>,
    val transactions: List<com.mipatrimonio.app.domain.model.Transaction>,
    val transfers: List<com.mipatrimonio.app.domain.model.Transfer>,
)

private data class InvestmentData(
    val portfolios: List<Portfolio>,
    val assets: List<Asset>,
    val operations: List<InvestmentOperation>,
    val prices: Map<String, AssetPrice>,
)
