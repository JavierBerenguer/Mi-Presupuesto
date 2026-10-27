package com.mipatrimonio.app.ui.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.data.sync.mapLiquidityMovements
import com.mipatrimonio.app.data.sync.PortfolioSummaryParser
import com.mipatrimonio.app.data.sync.PortfolioSummaryResult
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.LiquidityBalanceCalculator
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.model.Account
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
    val snapshot: FinanceSnapshot, val period: Period, val series: List<NetWorthPoint>,
    val portfolioValueMinor: Long = 0L, val portfolioGenerated: String? = null,
) {
    val totalMinor: Long get() = Math.addExact(snapshot.netWorth.cashMinor, portfolioValueMinor)
}

class NetWorthViewModel(ledger: LedgerRepository, settings: SettingsRepository, sync: SyncRepository? = null) : ViewModel() {
    private data class LedgerData(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val transfers: List<Transfer>,
    )

    private val selectedPeriod = MutableStateFlow(Period.ANIO)
    private val ledgerData = combine(ledger.accounts, ledger.transactions, ledger.transfers, ::LedgerData)
    private val remoteMovements = sync?.movimientos ?: kotlinx.coroutines.flow.flowOf(emptyList())
    private val remoteSummary = sync?.resumen ?: kotlinx.coroutines.flow.flowOf(null)

    val state: StateFlow<NetWorthState?> = combine(ledgerData, settings.settings, selectedPeriod, remoteMovements, remoteSummary) { data, config, period, movements, rawSummary ->
        val today = LocalDate.now()
        val liquidity = mapLiquidityMovements(movements, data.accounts).entries
        val firstActivity = (data.transactions.map { it.date } + data.transfers.map { it.date } + liquidity.map { it.date }).minOrNull()
        val balances = data.accounts.map { AccountBalance(it, BalanceCalculator.balance(it, data.transactions, data.transfers, today, liquidity)) }
        val summary = rawSummary?.let { PortfolioSummaryParser.parse(it.datosJson) as? PortfolioSummaryResult.Success }?.summary
        val dates = HistoryCalculator.sampleDates(period, today, firstActivity)
        val includedIds = data.accounts.filter { !it.archived && it.currency == config.baseCurrency }.mapTo(hashSetOf(), Account::id)
        NetWorthState(
            snapshot = FinanceSnapshot(config.baseCurrency, balances, NetWorthCalculator.compute(config.baseCurrency, balances)),
            period = period,
            series = HistoryCalculator.netWorthSeries(
                config.baseCurrency, data.accounts, data.transactions, data.transfers,
                dates, today,
            ).map { point -> point.copy(totalMinor = Math.addExact(point.totalMinor, LiquidityBalanceCalculator.total(liquidity, includedIds, point.date))) },
            portfolioValueMinor = summary?.total?.valueMinor ?: 0L,
            portfolioGenerated = summary?.generated,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(period: Period) { selectedPeriod.value = period }
}
