package com.mipatrimonio.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.AccountDependencies
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.data.sync.mapLiquidityMovements
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.MoneyMath
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf
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
    val balanceDate: LocalDate? = null,
)

data class CurrencyTotal(
    val currency: String,
    val amountMinor: Long,
)

private data class AccountSnapshot(
    val accounts: List<ManagedAccount>,
    val totals: List<CurrencyTotal>,
    val balanceRecords: List<AccountBalanceRecord>,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<ManagedAccount> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
    val dependencies: Map<String, AccountDependencies> = emptyMap(),
    val balanceRecords: List<AccountBalanceRecord> = emptyList(),
    val hideAmounts: Boolean = false,
    val error: String? = null,
)

class AccountsViewModel(
    private val ledger: LedgerRepository,
    settings: SettingsRepository,
    sync: SyncRepository? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val dependencies = MutableStateFlow<Map<String, AccountDependencies>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)

    private val accountSnapshot = combine(
        ledger.accounts,
        ledger.transactions,
        ledger.transfers,
        sync?.movimientos ?: flowOf(emptyList()),
        ledger.accountBalances,
    ) { accounts, transactions, transfers, remoteMovements, balanceRecords ->
        val liquidity = mapLiquidityMovements(remoteMovements, accounts).entries
        val managedAccounts = accounts
            .map { account ->
                ManagedAccount(
                    account,
                    BalanceCalculator.balance(account, transactions, transfers, today(), liquidity, balanceRecords),
                    balanceRecords.filter { it.accountId == account.id && it.deletedAt == null && !it.date.isAfter(today()) }
                        .maxByOrNull { it.date }?.date,
                )
            }
            .sortedBy { it.account.name.lowercase() }
        AccountSnapshot(
            accounts = managedAccounts,
            balanceRecords = balanceRecords,
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
            balanceRecords = snapshot.balanceRecords,
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
        shortName: String,
        institution: String,
        color: Int?,
        liquidity: Boolean,
        useInApp: Boolean,
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
            shortName = shortName.trim().takeIf { it.isNotEmpty() },
            institution = institution.trim().takeIf { it.isNotEmpty() },
            color = color,
            liquidity = liquidity,
            useInApp = useInApp,
        )
        launchAction(onSaved) {
            ledger.saveAccount(account)
        }
    }

    fun setArchived(account: Account, archived: Boolean, onSaved: () -> Unit) {
        launchAction(onSaved) { ledger.setAccountArchived(account.id, archived) }
    }

    fun delete(account: Account, onDeleted: () -> Unit) {
        launchAction(onDeleted) { ledger.deleteAccount(account.id) }
    }

    fun saveBalance(account: Account, existing: AccountBalanceRecord?, date: LocalDate, amount: String, onSaved: () -> Unit) {
        val parsed = MoneyMath.parse(amount)?.let { runCatching { MoneyMath.toMinor(it, account.currency) }.getOrNull() }
        if (parsed == null) {
            error.value = "El saldo no es válido"
            return
        }
        launchAction(onSaved) {
            ledger.saveAccountBalance(
                AccountBalanceRecord(existing?.id ?: UUID.randomUUID().toString(), account.id, date, parsed, System.currentTimeMillis()),
            )
        }
    }

    fun deleteBalance(record: AccountBalanceRecord) {
        launchAction({}) { ledger.deleteAccountBalance(record.id) }
    }

    private fun launchAction(onSuccess: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { error.value = null; onSuccess() }
                .onFailure { error.value = it.message.orEmpty() }
        }
    }
}
