package com.mipatrimonio.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.MovementsBalanceCalculator
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.movementStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MovementsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val allItems: List<MovementItem> = emptyList(),
    val visibleItems: List<MovementItem> = emptyList(),
    val filters: MovementFilters = MovementFilters(),
    val baseCurrency: String = "EUR",
    val error: String? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val dayGroups: List<MovementDayGroup> = emptyList(),
    val hideAmounts: Boolean = false,
    val balanceMinor: Long = 0,
    val calculationMode: MovementsCalculationMode = MovementsCalculationMode.SALDO_ACTUAL,
    val dailyBalance: Boolean = true,
    val hideFuture: Boolean = false,
    val ignoreTransfers: Boolean = false,
    val includedAccountIds: Set<String> = emptySet(),
) {
    val activeAccounts: List<Account> get() = accounts.filterNot(Account::archived)
    val hasActiveFilters: Boolean get() = filters != MovementFilters()
}

class MovementsViewModel(
    private val ledger: LedgerRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private data class SourceData(
        val accounts: List<Account>,
        val categories: List<Category>,
        val items: List<MovementItem>,
        val baseCurrency: String,
        val hideAmounts: Boolean,
        val transactions: List<com.mipatrimonio.app.domain.model.Transaction>,
        val transfers: List<com.mipatrimonio.app.domain.model.Transfer>,
        val includedAccountIds: Set<String>,
        val allAccounts: Boolean,
        val calculationMode: MovementsCalculationMode,
        val dailyBalance: Boolean,
        val hideFuture: Boolean,
        val ignoreTransfers: Boolean,
    )

    private val filters = MutableStateFlow(MovementFilters())
    private val error = MutableStateFlow<String?>(null)
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    private val sourceData = combine(
        ledger.accounts,
        ledger.categories,
        ledger.transactions,
        ledger.transfers,
        settings.settings,
    ) { accounts, categories, transactions, transfers, currentSettings ->
        SourceData(
            accounts = accounts,
            categories = categories,
            items = transactions.map(MovementItem::Tx) + transfers.map(MovementItem::Move),
            baseCurrency = currentSettings.baseCurrency,
            hideAmounts = currentSettings.hideAmounts,
            transactions = transactions,
            transfers = transfers,
            includedAccountIds = currentSettings.movementsIncludedAccountIds,
            allAccounts = currentSettings.movementsAllAccounts,
            calculationMode = currentSettings.movementsCalculationMode,
            dailyBalance = currentSettings.movementsDailyBalance,
            hideFuture = currentSettings.movementsHideFuture,
            ignoreTransfers = currentSettings.movementsIgnoreTransfers,
        )
    }

    val uiState: StateFlow<MovementsUiState> = combine(
        sourceData, filters, error, selectedMonth,
    ) { data, currentFilters, currentError, month ->
        val currentDate = today()
        val monthFilters = currentFilters.copy(
            from = maxOf(currentFilters.from ?: month.atDay(1), month.atDay(1)),
            to = minOf(currentFilters.to ?: month.atEndOfMonth(), month.atEndOfMonth()),
        )
        val itemsAllowedByFutureSetting = if (data.hideFuture) {
            data.items.filter { movementStatus(it.date, currentDate) == MovementStatus.EJECUTADO }
        } else {
            data.items
        }
        val visible = applyFilters(itemsAllowedByFutureSetting, monthFilters, data.accounts, data.categories)
        val includedAccounts = if (data.allAccounts) {
            data.accounts.filterNot(Account::archived)
        } else {
            data.accounts.filter { it.id in data.includedAccountIds }
        }
        MovementsUiState(
            isLoading = false,
            accounts = data.accounts,
            categories = data.categories,
            allItems = data.items,
            visibleItems = visible,
            filters = currentFilters,
            baseCurrency = data.baseCurrency,
            error = currentError,
            selectedMonth = month,
            dayGroups = groupMovementsByDay(visible, data.baseCurrency),
            hideAmounts = data.hideAmounts,
            balanceMinor = MovementsBalanceCalculator.calculate(
                includedAccounts = includedAccounts,
                transactions = data.transactions,
                transfers = data.transfers,
                selectedMonth = month,
                baseCurrency = data.baseCurrency,
                mode = data.calculationMode,
                dailyBalance = data.dailyBalance,
                hideFuture = data.hideFuture,
                ignoreTransfers = data.ignoreTransfers,
                today = currentDate,
            ),
            calculationMode = data.calculationMode,
            dailyBalance = data.dailyBalance,
            hideFuture = data.hideFuture,
            ignoreTransfers = data.ignoreTransfers,
            includedAccountIds = data.includedAccountIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MovementsUiState(),
    )

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun setKind(kind: KindFilter) {
        filters.value = filters.value.copy(kind = kind)
    }

    fun setFilters(newFilters: MovementFilters) {
        filters.value = newFilters
    }

    fun clearFilters() {
        filters.value = MovementFilters()
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun setMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    fun setSource(source: SourceFilter) {
        filters.value = filters.value.copy(source = source)
    }

    fun clearError() {
        error.value = null
    }

    fun setCalculationMode(mode: MovementsCalculationMode) = updateSetting {
        settings.setMovementsCalculationMode(mode)
    }

    fun setDailyBalance(enabled: Boolean) = updateSetting {
        settings.setMovementsDailyBalance(enabled)
    }

    fun setHideFuture(enabled: Boolean) = updateSetting {
        settings.setMovementsHideFuture(enabled)
    }

    fun setIgnoreTransfers(enabled: Boolean) = updateSetting {
        settings.setMovementsIgnoreTransfers(enabled)
    }

    private fun updateSetting(update: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { update() }.onFailure { error.value = it.message }
        }
    }

    fun duplicate(transaction: Transaction) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                ledger.saveTransaction(
                    transaction.copy(
                        id = UUID.randomUUID().toString(),
                        date = today(),
                        source = TransactionSource.MANUAL,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }.onFailure { error.value = it.message }
        }
    }

    fun delete(item: MovementItem) {
        viewModelScope.launch {
            runCatching {
                when (item) {
                    is MovementItem.Tx -> ledger.deleteTransaction(item.transaction.id)
                    is MovementItem.Move -> ledger.deleteTransfer(item.transfer.id)
                }
            }.onFailure { error.value = it.message }
        }
    }
}
