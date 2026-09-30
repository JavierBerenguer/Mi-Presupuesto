package com.mipatrimonio.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetsUiState(
    val loading: Boolean = true,
    val statuses: List<BudgetStatus> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val baseCurrency: String = "EUR",
    val hideAmounts: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val expenseStatistics: MonthlyStatistics = MonthlyStatistics(emptyList(), 0, 0),
    val incomeStatistics: MonthlyStatistics = MonthlyStatistics(emptyList(), 0, 0),
) {
    val expenseCategories: List<Category>
        get() = categories.filterNot { it.archived }
}

data class BudgetDraft(
    val id: String? = null,
    val name: String = "",
    val amount: String = "",
    val currency: String = "EUR",
    val period: BudgetPeriod = BudgetPeriod.MENSUAL,
    val startDate: LocalDate = YearMonth.now().atDay(1),
    val endDate: LocalDate? = null,
    val alertThresholdPct: Int = 90,
    val categoryRules: List<BudgetCategoryRule> = emptyList(),
)

sealed interface BudgetSaveResult {
    data object Success : BudgetSaveResult
    data object InvalidName : BudgetSaveResult
    data object InvalidLimit : BudgetSaveResult
    data object InvalidDates : BudgetSaveResult
    data object MissingUniqueEnd : BudgetSaveResult
    data object InvalidThreshold : BudgetSaveResult
    data object Duplicate : BudgetSaveResult
    data class Failure(val message: String?) : BudgetSaveResult
}

class BudgetsViewModel(
    private val ledger: LedgerRepository,
    settings: SettingsRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<BudgetsUiState> = combine(
        ledger.budgets, ledger.transactions, ledger.categories, settings.settings, selectedMonth,
    ) { budgets, transactions, categories, config, month ->
        val currentDate = today()
        val statuses = budgets.asSequence()
            .filterNot(Budget::archived)
            .map { BudgetCalculator.status(it, transactions, categories, month.atDay(1), currentDate) }
            .filter(BudgetStatus::applies)
            .toList()
        BudgetsUiState(
            loading = false,
            statuses = statuses,
            budgets = budgets,
            transactions = transactions,
            categories = categories,
            baseCurrency = config.baseCurrency,
            hideAmounts = config.hideAmounts,
            selectedMonth = month,
            today = currentDate,
            expenseStatistics = monthlyStatistics(
                transactions, categories, config.baseCurrency, month, StatisticsKind.GASTOS, currentDate,
            ),
            incomeStatistics = monthlyStatistics(
                transactions, categories, config.baseCurrency, month, StatisticsKind.INGRESOS, currentDate,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }

    fun save(draft: BudgetDraft, onResult: (BudgetSaveResult) -> Unit) {
        val state = uiState.value
        val built = buildBudget(draft, state.budgets)
        if (built.first != null) return onResult(requireNotNull(built.first))
        val budget = requireNotNull(built.second)
        viewModelScope.launch {
            runCatching { ledger.saveBudget(budget) }
                .onSuccess { onResult(BudgetSaveResult.Success) }
                .onFailure { onResult(BudgetSaveResult.Failure(it.message)) }
        }
    }

    fun duplicate(budget: Budget, onResult: (BudgetSaveResult) -> Unit) {
        save(
            BudgetDraft(
                name = "${budget.name} (copia)",
                amount = MoneyMath.toDecimal(budget.limitMinor, budget.currency).toPlainString(),
                currency = budget.currency,
                period = budget.period,
                startDate = budget.startDate,
                endDate = budget.endDate,
                alertThresholdPct = budget.alertThresholdPct,
                categoryRules = budget.categoryRules,
            ),
            onResult,
        )
    }

    fun setArchived(budget: Budget, archived: Boolean) {
        viewModelScope.launch { ledger.setBudgetArchived(budget.id, archived) }
    }

    fun delete(budget: Budget) { viewModelScope.launch { ledger.deleteBudget(budget.id) } }
}

data class BudgetFormUiState(
    val loading: Boolean = true,
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val baseCurrency: String = "EUR",
)

/** Estado independiente del formulario para que sus validaciones y guardado sean comprobables sin Compose. */
class BudgetFormViewModel(
    private val ledger: LedgerRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<BudgetFormUiState> = combine(
        ledger.budgets, ledger.categories, settings.settings,
    ) { budgets, categories, config ->
        BudgetFormUiState(false, budgets, categories.filterNot { it.archived }, config.baseCurrency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetFormUiState())

    fun save(draft: BudgetDraft, onResult: (BudgetSaveResult) -> Unit) {
        val state = uiState.value
        val result = buildBudget(draft, state.budgets)
        if (result.first != null) return onResult(requireNotNull(result.first))
        viewModelScope.launch {
            runCatching { ledger.saveBudget(requireNotNull(result.second)) }
                .onSuccess { onResult(BudgetSaveResult.Success) }
                .onFailure { onResult(BudgetSaveResult.Failure(it.message)) }
        }
    }
}

data class BudgetDetailUiState(
    val loading: Boolean = true,
    val budget: Budget? = null,
    val current: BudgetStatus? = null,
    val history: List<BudgetStatus> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
)

class BudgetDetailViewModel(
    private val ledger: LedgerRepository,
    private val budgetId: String,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    val uiState: StateFlow<BudgetDetailUiState> = combine(
        ledger.budgets, ledger.transactions, ledger.categories,
    ) { budgets, transactions, categories ->
        val budget = budgets.find { it.id == budgetId }
        if (budget == null) BudgetDetailUiState(loading = false) else {
            val currentDate = today()
            val current = BudgetCalculator.status(budget, transactions, categories, currentDate, currentDate)
            val history = BudgetCalculator.recentWindows(budget, current.range.endInclusive).map {
                range -> BudgetCalculator.statusForRange(budget, transactions, categories, range, currentDate)
            }
            BudgetDetailUiState(
                loading = false,
                budget = budget,
                current = current,
                history = history,
                transactions = if (current.applies) {
                    BudgetCalculator.matchingTransactions(
                        budget, transactions, categories, current.range, currentDate,
                    )
                } else emptyList(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetDetailUiState())

    fun archive() { viewModelScope.launch { ledger.setBudgetArchived(budgetId, true) } }
    fun delete() { viewModelScope.launch { ledger.deleteBudget(budgetId) } }
}

private fun buildBudget(
    draft: BudgetDraft,
    budgets: List<Budget>,
): Pair<BudgetSaveResult?, Budget?> {
    if (draft.name.isBlank()) return BudgetSaveResult.InvalidName to null
    val limit = runCatching { MoneyMath.parse(draft.amount)?.let { MoneyMath.toMinor(it, draft.currency) } }.getOrNull()
    if (limit == null || limit <= 0L) return BudgetSaveResult.InvalidLimit to null
    if (draft.endDate?.isBefore(draft.startDate) == true) return BudgetSaveResult.InvalidDates to null
    if (draft.period == BudgetPeriod.UNICO && draft.endDate == null) return BudgetSaveResult.MissingUniqueEnd to null
    if (draft.alertThresholdPct !in 50..100) return BudgetSaveResult.InvalidThreshold to null
    val existing = draft.id?.let { id -> budgets.find { it.id == id } }
    val budget = Budget(
        draft.id ?: UUID.randomUUID().toString(), draft.name.trim(), limit, existing?.currency ?: draft.currency,
        draft.period, draft.startDate, draft.endDate, draft.alertThresholdPct,
        draft.categoryRules.distinctBy(BudgetCategoryRule::categoryId), existing?.archived ?: false,
    )
    return if (isDuplicate(budgets, budget)) BudgetSaveResult.Duplicate to null else null to budget
}
