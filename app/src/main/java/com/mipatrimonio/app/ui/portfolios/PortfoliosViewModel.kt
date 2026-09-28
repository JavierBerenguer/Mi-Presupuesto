package com.mipatrimonio.app.ui.portfolios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.PortfolioDependencies
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import com.mipatrimonio.app.ui.investments.summarize
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PortfolioFilter { ACTIVAS, ARCHIVADAS, TODAS }

data class ManagedPortfolio(
    val portfolio: Portfolio,
    val defaultAccount: Account?,
    val openAssets: Int,
    val valueMinor: Long,
)

fun filterPortfolios(items: List<ManagedPortfolio>, filter: PortfolioFilter) = items.filter {
    when (filter) {
        PortfolioFilter.ACTIVAS -> !it.portfolio.archived
        PortfolioFilter.ARCHIVADAS -> it.portfolio.archived
        PortfolioFilter.TODAS -> true
    }
}

data class PortfoliosUiState(
    val isLoading: Boolean = true,
    val portfolios: List<ManagedPortfolio> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val dependencies: Map<String, PortfolioDependencies> = emptyMap(),
    val baseCurrency: String = "EUR",
    val hideAmounts: Boolean = false,
    val error: String? = null,
)

class PortfoliosViewModel(
    private val ledger: LedgerRepository,
    private val investments: InvestmentRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val dependencies = MutableStateFlow<Map<String, PortfolioDependencies>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)
    private val local = combine(
        dependencies,
        error,
    ) { dependencies, error -> dependencies to error }

    private val investmentData = combine(
        investments.portfolios, investments.assets, investments.operations, investments.latestPrices,
    ) { portfolios, assets, operations, prices -> InvestmentData(portfolios, assets, operations, prices) }

    private val settingsAndLocal = combine(settings.settings, local) { current, localState -> current to localState }

    val uiState: StateFlow<PortfoliosUiState> = combine(
        ledger.accounts, ledger.transactions, ledger.transfers, investmentData, settingsAndLocal,
    ) { accounts, transactions, transfers, investment, combinedLocal ->
        val currentSettings = combinedLocal.first
        val localState = combinedLocal.second
        val portfolios = investment.portfolios
        val assets = investment.assets
        val operations = investment.operations
        val prices = investment.prices
        val snapshot = SnapshotBuilder.build(
            currentSettings.baseCurrency, accounts, transactions, transfers, portfolios, assets,
            operations, prices, today(),
        )
        val summaries = summarize(snapshot.positions, currentSettings.baseCurrency).associateBy { it.portfolio.id }
        val accountById = accounts.associateBy { it.id }
        PortfoliosUiState(
            isLoading = false,
            portfolios = portfolios.map { portfolio ->
                ManagedPortfolio(
                    portfolio,
                    portfolio.defaultAccountId?.let(accountById::get),
                    snapshot.openPositions.count { it.portfolio.id == portfolio.id },
                    summaries[portfolio.id]?.valueMinor ?: 0L,
                )
            }.sortedWith(compareBy({ it.portfolio.archived }, { it.portfolio.createdAt }, { it.portfolio.name })),
            accounts = accounts.filterNot { it.archived }.sortedBy { it.name },
            dependencies = localState.first,
            baseCurrency = currentSettings.baseCurrency,
            hideAmounts = currentSettings.hideAmounts,
            error = localState.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfoliosUiState())

    fun inspect(id: String) = viewModelScope.launch {
        runCatching { investments.portfolioDependencies(id) }
            .onSuccess { dependencies.value += id to it }
            .onFailure { error.value = it.message }
    }

    fun clearError() { error.value = null }

    fun save(existing: Portfolio?, name: String, accountId: String?, onSaved: () -> Unit) = launchAction(onSaved) {
        investments.savePortfolio(
            Portfolio(
                existing?.id ?: UUID.randomUUID().toString(), name.trim(),
                existing?.createdAt ?: System.currentTimeMillis(), accountId, existing?.archived ?: false,
            ),
        )
    }

    fun setArchived(portfolio: Portfolio, archived: Boolean, onSaved: () -> Unit) = launchAction(onSaved) {
        investments.setPortfolioArchived(portfolio.id, archived)
        if (archived) settings.setSelectedPortfolioId(null)
    }

    fun delete(portfolio: Portfolio, onDeleted: () -> Unit) = launchAction(onDeleted) {
        investments.deletePortfolio(portfolio.id)
    }

    private fun launchAction(onSuccess: () -> Unit, action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }
            .onSuccess { error.value = null; onSuccess() }
            .onFailure { error.value = it.message.orEmpty() }
    }
}

private data class InvestmentData(
    val portfolios: List<Portfolio>,
    val assets: List<com.mipatrimonio.app.domain.model.Asset>,
    val operations: List<com.mipatrimonio.app.domain.model.InvestmentOperation>,
    val prices: Map<String, com.mipatrimonio.app.domain.model.AssetPrice>,
)
