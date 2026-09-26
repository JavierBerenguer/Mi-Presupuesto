package com.mipatrimonio.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Portfolio
import java.util.UUID
import kotlinx.coroutines.launch
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

sealed interface MoreAccountError {
    data object BlankName : MoreAccountError
    data object InvalidBalance : MoreAccountError
    data object BalanceTooLarge : MoreAccountError
    data class Repository(val message: String) : MoreAccountError
}

class MoreViewModel(
    private val ledger: LedgerRepository,
    private val investments: InvestmentRepository,
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

    fun createAccount(
        name: String,
        type: AccountType,
        currency: String,
        initialBalance: String,
        createLinkedPortfolio: Boolean,
        onResult: (MoreAccountError?) -> Unit,
    ) {
        val parsed = if (initialBalance.isBlank()) java.math.BigDecimal.ZERO else MoneyMath.parse(initialBalance)
        if (name.isBlank() || parsed == null) {
            onResult(if (name.isBlank()) MoreAccountError.BlankName else MoreAccountError.InvalidBalance)
            return
        }
        val minor = runCatching { MoneyMath.toMinor(parsed, currency) }.getOrElse {
            onResult(MoreAccountError.BalanceTooLarge)
            return
        }
        viewModelScope.launch {
            try {
                val id = UUID.randomUUID().toString()
                ledger.saveAccount(Account(id, name.trim(), type, currency, minor, false, System.currentTimeMillis()))
                if (type == AccountType.INVERSION && createLinkedPortfolio) {
                    investments.savePortfolio(Portfolio(UUID.randomUUID().toString(), name.trim(), System.currentTimeMillis(), id))
                }
                onResult(null)
            } catch (error: IllegalArgumentException) {
                onResult(MoreAccountError.Repository(error.message.orEmpty()))
            }
        }
    }
}
