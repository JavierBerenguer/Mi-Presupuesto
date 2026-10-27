package com.mipatrimonio.app.ui.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
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

data class NetWorthState(val snapshot: FinanceSnapshot, val period: Period, val series: List<NetWorthPoint>)

class NetWorthViewModel(ledger: LedgerRepository, settings: SettingsRepository) : ViewModel() {
    private data class LedgerData(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val transfers: List<Transfer>,
    )

    private val selectedPeriod = MutableStateFlow(Period.ANIO)
    private val ledgerData = combine(ledger.accounts, ledger.transactions, ledger.transfers, ::LedgerData)

    val state: StateFlow<NetWorthState?> = combine(ledgerData, settings.settings, selectedPeriod) { data, config, period ->
        val today = LocalDate.now()
        val firstActivity = (data.transactions.map { it.date } + data.transfers.map { it.date }).minOrNull()
        NetWorthState(
            snapshot = SnapshotBuilder.build(config.baseCurrency, data.accounts, data.transactions, data.transfers, today),
            period = period,
            series = HistoryCalculator.netWorthSeries(
                config.baseCurrency, data.accounts, data.transactions, data.transfers,
                HistoryCalculator.sampleDates(period, today, firstActivity), today,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(period: Period) { selectedPeriod.value = period }
}
