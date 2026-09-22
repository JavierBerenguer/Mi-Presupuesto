package com.mipatrimonio.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.usecase.Period
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    ledger: LedgerRepository,
    investments: InvestmentRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private data class LedgerData(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val transfers: List<Transfer>,
        val categories: List<Category>,
        val budgets: List<Budget>,
    )

    private data class InvestmentData(
        val portfolios: List<Portfolio>,
        val assets: List<Asset>,
        val operations: List<InvestmentOperation>,
        val prices: Map<String, AssetPrice>,
    )

    private val selectedPeriod = MutableStateFlow(Period.MES)
    val period: StateFlow<Period> = selectedPeriod

    private val ledgerData = combine(
        ledger.accounts, ledger.transactions, ledger.transfers, ledger.categories, ledger.budgets, ::LedgerData,
    )
    private val investmentData = combine(
        investments.portfolios, investments.assets, investments.operations, investments.latestPrices, ::InvestmentData,
    )

    /** `null` mientras no llega el primer dato. */
    val state: StateFlow<HomeState?> = combine(
        ledgerData, investmentData, settings.settings, selectedPeriod,
    ) { l, inv, config, period ->
        buildHomeState(
            baseCurrency = config.baseCurrency,
            accounts = l.accounts,
            transactions = l.transactions,
            transfers = l.transfers,
            categories = l.categories,
            budgets = l.budgets,
            portfolios = inv.portfolios,
            assets = inv.assets,
            operations = inv.operations,
            prices = inv.prices,
            today = LocalDate.now(),
            period = period,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Categorías para nombrar y colorear la distribución de gastos. */
    val categories: StateFlow<List<Category>> = ledger.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPeriod(period: Period) {
        selectedPeriod.value = period
    }
}
