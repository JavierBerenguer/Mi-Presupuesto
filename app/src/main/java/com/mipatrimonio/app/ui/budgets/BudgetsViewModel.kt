package com.mipatrimonio.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.MoneyMath
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetsUiState(
    val loading: Boolean = true,
    val statuses: List<BudgetStatus> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val baseCurrency: String = "EUR",
    val hideAmounts: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val expenseStatistics: MonthlyStatistics = MonthlyStatistics(emptyList(), 0, 0),
    val incomeStatistics: MonthlyStatistics = MonthlyStatistics(emptyList(), 0, 0),
) {
    val expenseCategories: List<Category>
        get() = categories.filter { it.kind == CategoryKind.GASTO && !it.archived }
}

sealed interface BudgetSaveResult {
    data object Success : BudgetSaveResult
    data object InvalidLimit : BudgetSaveResult
    data object Duplicate : BudgetSaveResult
    data class Failure(val message: String?) : BudgetSaveResult
}

class BudgetsViewModel(
    private val ledger: LedgerRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<BudgetsUiState> = combine(
        ledger.budgets, ledger.transactions, ledger.categories, settings.settings, selectedMonth,
    ) { budgets, transactions, categories, config, month ->
        val reference = month.atDay(1)
        BudgetsUiState(
            loading = false,
            statuses = budgets.map { BudgetCalculator.status(it, transactions, categories, reference) },
            budgets = budgets,
            categories = categories,
            baseCurrency = config.baseCurrency,
            hideAmounts = config.hideAmounts,
            selectedMonth = month,
            expenseStatistics = monthlyStatistics(
                transactions, categories, config.baseCurrency, month, StatisticsKind.GASTOS,
            ),
            incomeStatistics = monthlyStatistics(
                transactions, categories, config.baseCurrency, month, StatisticsKind.INGRESOS,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun save(
        existing: Budget?,
        categoryId: String?,
        period: BudgetPeriod,
        limitText: String,
        onResult: (BudgetSaveResult) -> Unit,
    ) {
        val state = uiState.value
        val currency = existing?.currency ?: state.baseCurrency
        val limit = runCatching { MoneyMath.parse(limitText)?.let { MoneyMath.toMinor(it, currency) } }.getOrNull()
        if (limit == null || limit <= 0) {
            onResult(BudgetSaveResult.InvalidLimit)
            return
        }
        val budget = Budget(existing?.id ?: UUID.randomUUID().toString(), categoryId, period, limit, currency, false)
        if (isDuplicate(state.budgets, budget)) {
            onResult(BudgetSaveResult.Duplicate)
            return
        }
        viewModelScope.launch {
            runCatching { ledger.saveBudget(budget) }
                .onSuccess { onResult(BudgetSaveResult.Success) }
                .onFailure { onResult(BudgetSaveResult.Failure(it.message)) }
        }
    }

    fun delete(budget: Budget) {
        viewModelScope.launch { ledger.deleteBudget(budget.id) }
    }
}
