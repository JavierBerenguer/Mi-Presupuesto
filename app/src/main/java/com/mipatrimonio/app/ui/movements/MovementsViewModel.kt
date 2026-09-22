package com.mipatrimonio.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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
) {
    val activeAccounts: List<Account> get() = accounts.filterNot(Account::archived)
    val hasActiveFilters: Boolean get() = filters != MovementFilters()
}

class MovementsViewModel(
    private val ledger: LedgerRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private data class SourceData(
        val accounts: List<Account>,
        val categories: List<Category>,
        val items: List<MovementItem>,
        val baseCurrency: String,
    )

    private val filters = MutableStateFlow(MovementFilters())
    private val error = MutableStateFlow<String?>(null)

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
        )
    }

    val uiState: StateFlow<MovementsUiState> = combine(sourceData, filters, error) { data, currentFilters, currentError ->
        MovementsUiState(
            isLoading = false,
            accounts = data.accounts,
            categories = data.categories,
            allItems = data.items,
            visibleItems = applyFilters(data.items, currentFilters, data.accounts, data.categories),
            filters = currentFilters,
            baseCurrency = data.baseCurrency,
            error = currentError,
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

    fun clearError() {
        error.value = null
    }

    fun duplicate(transaction: Transaction) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                ledger.saveTransaction(
                    transaction.copy(
                        id = UUID.randomUUID().toString(),
                        date = LocalDate.now(),
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
