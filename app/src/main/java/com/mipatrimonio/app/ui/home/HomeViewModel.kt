package com.mipatrimonio.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.data.sync.mapLiquidityMovements
import com.mipatrimonio.app.data.sync.PortfolioSummaryParser
import com.mipatrimonio.app.data.sync.PortfolioSummaryResult
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.usecase.Period
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    ledger: LedgerRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository? = null,
) : ViewModel() {
    private data class LedgerData(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val transfers: List<Transfer>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val accountBalances: List<AccountBalanceRecord>,
    )
    private data class LedgerCore(
        val accounts: List<Account>, val transactions: List<Transaction>, val transfers: List<Transfer>,
        val categories: List<Category>, val budgets: List<Budget>,
    )

    private val selectedPeriod = MutableStateFlow(Period.MES)
    val period: StateFlow<Period> = selectedPeriod
    private val selectedEvolutionSeries = MutableStateFlow(EvolutionSeries.TOTAL)

    private val ledgerData = combine(
        ledger.accounts, ledger.transactions, ledger.transfers, ledger.categories, ledger.budgets, ::LedgerCore,
    ).combine(ledger.accountBalances) { core, balances ->
        LedgerData(core.accounts, core.transactions, core.transfers, core.categories, core.budgets, balances)
    }
    private val remoteMovements = sync?.movimientos ?: kotlinx.coroutines.flow.flowOf(emptyList())
    private val remoteSummary = sync?.resumen ?: kotlinx.coroutines.flow.flowOf(null)
    private val summarySelection = combine(remoteSummary, selectedEvolutionSeries) { summary, selection ->
        summary to selection
    }
    /** `null` mientras no llega el primer dato. */
    val state: StateFlow<HomeState?> = combine(
        ledgerData, settings.settings, selectedPeriod, remoteMovements, summarySelection,
    ) { l, config, period, movements, summaryAndSelection ->
        val (rawSummary, evolutionSelection) = summaryAndSelection
        val liquidity = mapLiquidityMovements(movements, l.accounts).entries
        val summary = rawSummary?.let { PortfolioSummaryParser.parse(it.datosJson) as? PortfolioSummaryResult.Success }?.summary
        buildHomeState(
            baseCurrency = config.baseCurrency,
            accounts = l.accounts,
            transactions = l.transactions,
            transfers = l.transfers,
            categories = l.categories,
            budgets = l.budgets,
            today = LocalDate.now(),
            period = period,
            hideAmounts = config.hideAmounts,
            liquidityMovements = liquidity,
            portfolioSummary = summary,
            accountBalanceRecords = l.accountBalances,
            evolutionSeries = evolutionSelection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Categorías para nombrar y colorear la distribución de gastos. */
    val categories: StateFlow<List<Category>> = ledger.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPeriod(period: Period) {
        selectedPeriod.value = period
    }

    fun selectEvolutionSeries(series: EvolutionSeries) {
        selectedEvolutionSeries.value = series
    }

    fun setHideAmounts(hidden: Boolean) {
        viewModelScope.launch { settings.setHideAmounts(hidden) }
    }

    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing

    /** Deslizar hacia abajo: sincroniza con Supabase (los errores se ven en Ajustes › Sincronización). */
    fun refresh() {
        val repository = sync ?: return
        viewModelScope.launch {
            refreshing.value = true
            runCatching { repository.synchronize() }
            refreshing.value = false
        }
    }
}
