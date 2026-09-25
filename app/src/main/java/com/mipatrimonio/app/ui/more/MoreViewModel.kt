package com.mipatrimonio.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MoreAccountItem(
    val account: Account,
    val balanceMinor: Long,
)

data class CurrencyTotal(
    val currency: String,
    val amountMinor: Long,
)

data class MoreUiState(
    val isLoading: Boolean = true,
    val accounts: List<MoreAccountItem> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
)

class MoreViewModel(
    ledger: LedgerRepository,
    investments: InvestmentRepository,
) : ViewModel() {
    val uiState: StateFlow<MoreUiState> = combine(
        ledger.accounts,
        ledger.transactions,
        ledger.transfers,
        investments.operations,
    ) { accounts, transactions, transfers, operations ->
        val accountItems = accounts
            .asSequence()
            .filterNot { it.archived }
            .map { account ->
                MoreAccountItem(
                    account,
                    BalanceCalculator.balance(account, transactions, transfers, operations),
                )
            }
            .sortedBy { it.account.name.lowercase() }
            .toList()
        val totals = accountItems
            .groupBy { it.account.currency }
            .map { (currency, items) ->
                CurrencyTotal(currency, items.fold(0L) { total, item -> Math.addExact(total, item.balanceMinor) })
            }
            .sortedBy { it.currency }
        MoreUiState(isLoading = false, accounts = accountItems, totals = totals)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoreUiState(),
    )
}
