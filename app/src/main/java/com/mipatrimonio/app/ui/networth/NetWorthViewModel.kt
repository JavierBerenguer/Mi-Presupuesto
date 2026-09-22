package com.mipatrimonio.app.ui.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.usecase.FinanceSnapshot
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.NetWorthPoint
import com.mipatrimonio.app.domain.usecase.Period
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class NetWorthState(
    val snapshot: FinanceSnapshot,
    val period: Period,
    val series: List<NetWorthPoint>,
)

class NetWorthViewModel(
    ledger: LedgerRepository,
    investments: InvestmentRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private data class LedgerData(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val transfers: List<Transfer>,
    )

    private data class InvestmentData(
        val portfolios: List<Portfolio>,
        val assets: List<Asset>,
        val operations: List<InvestmentOperation>,
        val prices: Map<String, AssetPrice>,
    )

    private val selectedPeriod = MutableStateFlow(Period.ANIO)

    private val ledgerData = combine(ledger.accounts, ledger.transactions, ledger.transfers, ::LedgerData)
    private val investmentData = combine(
        investments.portfolios, investments.assets, investments.operations, investments.latestPrices, ::InvestmentData,
    )

    val state: StateFlow<NetWorthState?> = combine(
        ledgerData, investmentData, settings.settings, selectedPeriod,
    ) { l, inv, config, period ->
        val today = LocalDate.now()
        val firstActivity = (l.transactions.map { it.date } + l.transfers.map { it.date } + inv.operations.map { it.date }).minOrNull()
        NetWorthState(
            snapshot = SnapshotBuilder.build(
                config.baseCurrency, l.accounts, l.transactions, l.transfers,
                inv.portfolios, inv.assets, inv.operations, inv.prices,
            ),
            period = period,
            series = HistoryCalculator.netWorthSeries(
                config.baseCurrency, l.accounts, l.transactions, l.transfers, inv.assets, inv.operations,
                HistoryCalculator.sampleDates(period, today, firstActivity),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(period: Period) {
        selectedPeriod.value = period
    }
}
