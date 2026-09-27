package com.mipatrimonio.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.AccountDependencies
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.MoneyMath
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AccountFilter { ACTIVAS, ARCHIVADAS, TODAS }

fun filterAccounts(accounts: List<ManagedAccount>, filter: AccountFilter): List<ManagedAccount> = accounts.filter {
    when (filter) {
        AccountFilter.ACTIVAS -> !it.account.archived
        AccountFilter.ARCHIVADAS -> it.account.archived
        AccountFilter.TODAS -> true
    }
}

data class ManagedAccount(
    val account: Account,
    val balanceMinor: Long,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<ManagedAccount> = emptyList(),
    val dependencies: Map<String, AccountDependencies> = emptyMap(),
    val hideAmounts: Boolean = false,
    val error: String? = null,
)

class AccountsViewModel(
    private val ledger: LedgerRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val dependencies = MutableStateFlow<Map<String, AccountDependencies>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        ledger.accounts,
        ledger.transactions,
        ledger.transfers,
        settings.settings,
        combine(dependencies, error) { deps, message -> deps to message },
    ) { accounts, transactions, transfers, currentSettings, local ->
        AccountsUiState(
            isLoading = false,
            accounts = accounts.map { account ->
                ManagedAccount(account, BalanceCalculator.balance(account, transactions, transfers))
            },
            dependencies = local.first,
            hideAmounts = currentSettings.hideAmounts,
            error = local.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun inspect(accountId: String) {
        viewModelScope.launch {
            runCatching { ledger.accountDependencies(accountId) }
                .onSuccess { dependencies.value += accountId to it }
                .onFailure { error.value = it.message }
        }
    }

    fun clearError() {
        error.value = null
    }

    fun save(
        existing: Account?,
        name: String,
        type: AccountType,
        currency: String,
        initialBalanceText: String,
        onSaved: () -> Unit,
    ) {
        if (name.isBlank()) {
            error.value = "El nombre es obligatorio"
            return
        }
        val decimal = if (initialBalanceText.isBlank()) BigDecimal.ZERO else MoneyMath.parse(initialBalanceText)
        val minor = decimal?.let { runCatching { MoneyMath.toMinor(it, currency) }.getOrNull() }
        if (minor == null) {
            error.value = "El saldo inicial no es válido"
            return
        }
        val account = Account(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            currency = currency,
            initialBalanceMinor = minor,
            archived = existing?.archived ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        launchAction(onSaved) { ledger.saveAccount(account) }
    }

    fun setArchived(account: Account, archived: Boolean, onSaved: () -> Unit) {
        launchAction(onSaved) { ledger.setAccountArchived(account.id, archived) }
    }

    fun delete(account: Account, onDeleted: () -> Unit) {
        launchAction(onDeleted) { ledger.deleteAccount(account.id) }
    }

    private fun launchAction(onSuccess: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { error.value = null; onSuccess() }
                .onFailure { error.value = it.message.orEmpty() }
        }
    }
}
