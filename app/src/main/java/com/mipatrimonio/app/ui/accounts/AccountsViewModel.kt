package com.mipatrimonio.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.AccountDependencies
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Portfolio
import java.math.BigDecimal
import java.time.LocalDate
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

data class CurrencyTotal(
    val currency: String,
    val amountMinor: Long,
)

private data class AccountSnapshot(
    val accounts: List<ManagedAccount>,
    val totals: List<CurrencyTotal>,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<ManagedAccount> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
    val dependencies: Map<String, AccountDependencies> = emptyMap(),
    val hideAmounts: Boolean = false,
    val error: String? = null,
)

class AccountsViewModel(
    private val ledger: LedgerRepository,
    private val investments: InvestmentRepository,
    settings: SettingsRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val dependencies = MutableStateFlow<Map<String, AccountDependencies>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)

    private val accountSnapshot = combine(
        ledger.accounts,
        ledger.transactions,
        ledger.transfers,
        investments.operations,
    ) { accounts, transactions, transfers, operations ->
        val managedAccounts = accounts
            .map { account ->
                ManagedAccount(
                    account,
                    BalanceCalculator.balance(account, transactions, transfers, operations, today()),
                )
            }
            .sortedBy { it.account.name.lowercase() }
        AccountSnapshot(
            accounts = managedAccounts,
            totals = managedAccounts
                .filterNot { it.account.archived }
                .groupBy { it.account.currency }
                .map { (currency, items) ->
                    CurrencyTotal(
                        currency,
                        items.fold(0L) { total, item -> Math.addExact(total, item.balanceMinor) },
                    )
                }
                .sortedBy { it.currency },
        )
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        accountSnapshot,
        settings.settings,
        combine(dependencies, error) { deps, message -> deps to message },
    ) { snapshot, currentSettings, local ->
        AccountsUiState(
            isLoading = false,
            accounts = snapshot.accounts,
            totals = snapshot.totals,
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
        createLinkedPortfolio: Boolean = false,
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
        launchAction(onSaved) {
            ledger.saveAccount(account)
            if (existing == null && type == AccountType.INVERSION && createLinkedPortfolio) {
                investments.savePortfolio(
                    Portfolio(
                        id = UUID.randomUUID().toString(),
                        name = account.name,
                        createdAt = System.currentTimeMillis(),
                        defaultAccountId = account.id,
                    ),
                )
            }
        }
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
