package com.mipatrimonio.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.RecurringGenerator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurringListItem(val rule: RecurringRule, val nextDate: LocalDate?)

data class RecurringListUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringListItem> = emptyList(),
    val hideAmounts: Boolean = false,
)

class RecurringListViewModel(
    private val repository: RecurringRepository,
    settings: SettingsRepository,
    private val onCancelled: (String) -> Unit = {},
    private val onScheduled: (RecurringRule) -> Unit = {},
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    val uiState: StateFlow<RecurringListUiState> = combine(repository.rules, settings.settings) { rules, config ->
        RecurringListUiState(
            isLoading = false,
            items = rules.map { RecurringListItem(it, RecurringGenerator.nextDate(it)) },
            hideAmounts = config.hideAmounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListUiState())

    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch {
        repository.setArchived(id, archived)
        if (archived) {
            onCancelled(id)
        } else {
            repository.generatePending(today())
            repository.getRule(id)?.let(onScheduled)
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.deleteRule(id)
        onCancelled(id)
    }
}

data class RecurringFormValues(
    val kind: RecurringKind = RecurringKind.GASTO,
    val amount: String = "",
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val description: String = "",
    val merchant: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val periodQuantity: String = "1",
    val periodUnit: RecurringPeriodUnit = RecurringPeriodUnit.MES,
    val hasExpiration: Boolean = false,
    val endDate: LocalDate = LocalDate.now().plusYears(1),
    val reminder: ReminderOption = ReminderOption.NO,
    val reminderCustomDays: String = "0",
)

data class RecurringFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val values: RecurringFormValues = RecurringFormValues(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val error: RecurringFormError? = null,
) {
    val activeAccounts get() = accounts.filterNot(Account::archived)
    val destinationAccounts get() = activeAccounts.filter { it.id != values.accountId }
}

enum class RecurringFormError { ACCOUNT, AMOUNT, PERIOD, CUSTOM_REMINDER, END_DATE, SAVE }

class RecurringFormViewModel(
    private val repository: RecurringRepository,
    private val ledger: LedgerRepository,
    private val ruleId: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onSaved: (RecurringRule) -> Unit = {},
) : ViewModel() {
    private data class FormStatus(
        val loading: Boolean,
        val notFound: Boolean,
        val saving: Boolean,
        val error: RecurringFormError?,
    )

    private val values = MutableStateFlow(RecurringFormValues(startDate = today(), endDate = today().plusYears(1)))
    private val loading = MutableStateFlow(true)
    private val notFound = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<RecurringFormError?>(null)
    private var original: RecurringRule? = null
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (ruleId != null) {
                original = repository.getRule(ruleId)
                val rule = original
                if (rule == null) notFound.value = true else values.value = rule.toFormValues()
            } else {
                val accounts = ledger.accounts.first().filterNot(Account::archived)
                values.update { current ->
                    current.copy(
                        accountId = accounts.firstOrNull()?.id,
                        destinationAccountId = accounts.drop(1).firstOrNull()?.id,
                    )
                }
            }
            loading.value = false
        }
    }

    val uiState: StateFlow<RecurringFormUiState> = combine(
        values,
        combine(ledger.accounts, ledger.categories) { accounts, categories -> accounts to categories },
        combine(loading, notFound, saving, error) { load, missing, save, message ->
            FormStatus(load, missing, save, message)
        },
    ) { form, data, status ->
        RecurringFormUiState(
            isLoading = status.loading,
            isEditing = ruleId != null,
            notFound = status.notFound,
            values = form,
            accounts = data.first,
            categories = data.second,
            isSaving = status.saving,
            error = status.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringFormUiState(isEditing = ruleId != null))

    fun setKind(value: RecurringKind) = update { it.copy(kind = value) }
    fun setAmount(value: String) = update { it.copy(amount = value) }
    fun setAccount(value: String?) = update { current ->
        current.copy(
            accountId = value,
            destinationAccountId = current.destinationAccountId.takeIf { it != value }
                ?: uiState.value.activeAccounts.firstOrNull { it.id != value }?.id,
        )
    }
    fun setDestinationAccount(value: String?) = update { it.copy(destinationAccountId = value) }
    fun setCategory(value: String?) = update { it.copy(categoryId = value) }
    fun setDescription(value: String) = update { it.copy(description = value) }
    fun setMerchant(value: String) = update { it.copy(merchant = value) }
    fun setStartDate(value: LocalDate) = update { it.copy(startDate = value) }
    fun setPeriodQuantity(value: String) = update { it.copy(periodQuantity = value) }
    fun setPeriodUnit(value: RecurringPeriodUnit) = update { it.copy(periodUnit = value) }
    fun setHasExpiration(value: Boolean) = update { it.copy(hasExpiration = value) }
    fun setEndDate(value: LocalDate) = update { it.copy(endDate = value) }
    fun setReminder(value: ReminderOption) = update { it.copy(reminder = value) }
    fun setReminderCustomDays(value: String) = update { it.copy(reminderCustomDays = value) }

    fun save() {
        val form = values.value
        val account = uiState.value.accounts.find { it.id == form.accountId && !it.archived }
            ?: return fail(RecurringFormError.ACCOUNT)
        val amountMinor = runCatching {
            MoneyMath.parse(form.amount)?.let { MoneyMath.toMinor(it, account.currency) }
        }.getOrNull()?.takeIf { it > 0 } ?: return fail(RecurringFormError.AMOUNT)
        val quantity = form.periodQuantity.toIntOrNull()?.takeIf { it > 0 }
            ?: return fail(RecurringFormError.PERIOD)
        val customDays = if (form.reminder == ReminderOption.PERSONALIZADO) {
            form.reminderCustomDays.toIntOrNull()?.takeIf { it >= 0 }
                ?: return fail(RecurringFormError.CUSTOM_REMINDER)
        } else null
        if (form.hasExpiration && form.endDate.isBefore(form.startDate)) {
            return fail(RecurringFormError.END_DATE)
        }
        val previous = original
        val rule = RecurringRule(
            id = previous?.id ?: UUID.randomUUID().toString(),
            kind = form.kind,
            amountMinor = amountMinor,
            currency = account.currency,
            accountId = account.id,
            destinationAccountId = form.destinationAccountId.takeIf { form.kind == RecurringKind.TRANSFERENCIA },
            categoryId = form.categoryId,
            description = form.description,
            merchant = form.merchant,
            startDate = form.startDate,
            periodQuantity = quantity,
            periodUnit = form.periodUnit,
            endDate = form.endDate.takeIf { form.hasExpiration },
            reminder = form.reminder,
            reminderCustomDays = customDays,
            lastGeneratedDate = previous?.lastGeneratedDate,
            archived = previous?.archived ?: false,
            createdAt = previous?.createdAt ?: clock(),
            updatedAt = clock(),
        )
        viewModelScope.launch {
            saving.value = true
            runCatching {
                repository.saveRule(rule)
                repository.generatePending(today())
            }
                .onSuccess {
                    onSaved(rule)
                    savedChannel.send(Unit)
                }
                .onFailure { error.value = RecurringFormError.SAVE }
            saving.value = false
        }
    }

    private fun update(transform: (RecurringFormValues) -> RecurringFormValues) {
        values.update(transform)
        error.value = null
    }

    private fun fail(value: RecurringFormError) { error.value = value }
}

private fun RecurringRule.toFormValues() = RecurringFormValues(
    kind = kind,
    amount = MoneyMath.toDecimal(amountMinor, currency).stripTrailingZeros().toPlainString().replace('.', ','),
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    description = description,
    merchant = merchant,
    startDate = startDate,
    periodQuantity = periodQuantity.toString(),
    periodUnit = periodUnit,
    hasExpiration = endDate != null,
    endDate = endDate ?: startDate.plusYears(1),
    reminder = reminder,
    reminderCustomDays = (reminderCustomDays ?: 0).toString(),
)
