package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.MoneyMath
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountWithBalance(
    val account: Account,
    val balanceMinor: Long,
)

sealed interface AccountFormError {
    data object BlankName : AccountFormError
    data object InvalidInitialBalance : AccountFormError
    data class Repository(val message: String) : AccountFormError
}

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountWithBalance> = emptyList(),
    val formError: AccountFormError? = null,
)

class AccountsViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val formError = MutableStateFlow<AccountFormError?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        repository.accounts,
        repository.transactions,
        repository.transfers,
        formError,
    ) { accounts, transactions, transfers, error ->
        AccountsUiState(
            isLoading = false,
            accounts = accounts.map { account ->
                AccountWithBalance(
                    account = account,
                    balanceMinor = BalanceCalculator.balance(account, transactions, transfers),
                )
            },
            formError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(),
    )

    fun clearFormError() {
        formError.value = null
    }

    fun saveAccount(
        existing: Account?,
        name: String,
        type: AccountType,
        currency: String,
        initialBalanceText: String,
        onSaved: () -> Unit,
    ) {
        if (name.isBlank()) {
            formError.value = AccountFormError.BlankName
            return
        }
        val amount = if (initialBalanceText.isBlank()) java.math.BigDecimal.ZERO else MoneyMath.parse(initialBalanceText)
        val initialBalance = amount?.let { runCatching { MoneyMath.toMinor(it, existing?.currency ?: currency) }.getOrNull() }
        if (initialBalance == null) {
            formError.value = AccountFormError.InvalidInitialBalance
            return
        }

        val account = Account(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            currency = existing?.currency ?: currency,
            initialBalanceMinor = initialBalance,
            archived = existing?.archived ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch {
            runCatching { repository.saveAccount(account) }
                .onSuccess {
                    formError.value = null
                    onSaved()
                }
                .onFailure { error ->
                    formError.value = AccountFormError.Repository(error.message.orEmpty())
                }
        }
    }

    fun setArchived(account: Account, archived: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.setAccountArchived(account.id, archived) }
                .onSuccess {
                    formError.value = null
                    onSaved()
                }
                .onFailure { error ->
                    formError.value = AccountFormError.Repository(error.message.orEmpty())
                }
        }
    }
}
